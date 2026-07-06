package com.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.dto.AdminUserCreateDTO;
import com.problem.dto.AdminUserUpdateDTO;
import com.problem.dto.ProjectAuthorizationDTO;
import com.problem.dto.RoleUpdateDTO;
import com.problem.dto.TempUserCreateDTO;
import com.problem.dto.TempUserUpdateDTO;
import com.problem.entity.Project;
import com.problem.entity.Role;
import com.problem.entity.User;
import com.problem.entity.UserProject;
import com.problem.mapper.ProjectMapper;
import com.problem.mapper.RoleMapper;
import com.problem.mapper.UserMapper;
import com.problem.mapper.UserProjectMapper;
import com.problem.support.CurrentUserAccessService;
import com.problem.vo.AdminUserVO;
import com.problem.vo.ResetPasswordVO;
import com.problem.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String ACCOUNT_NORMAL = "NORMAL";
    private static final String ACCOUNT_TEMP = "TEMP";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_ENGINEER = "ENGINEER";
    private static final String ROLE_TEMP_WORKER = "TEMP_WORKER";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserProjectMapper userProjectMapper;
    private final ProjectMapper projectMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserAccessService currentUserAccessService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.default-password:change_me_default_password}")
    private String defaultPassword;

    public List<AdminUserVO> listUsers(String accountType) {
        currentUserAccessService.assertAdmin();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime);
        if (StringUtils.hasText(accountType)) {
            wrapper.eq(User::getAccountType, accountType.trim().toUpperCase());
        }
        List<User> users = userMapper.selectList(wrapper);
        return toUserVOs(users);
    }

    @Transactional
    public AdminUserVO createUser(AdminUserCreateDTO dto) {
        currentUserAccessService.assertAdmin();
        assertUsernameAvailable(dto.getUsername(), null);
        Role role = dto.getRoleId() == null ? roleByCode(ROLE_ENGINEER) : requireRole(dto.getRoleId());

        User user = new User();
        user.setUsername(dto.getUsername().trim());
        user.setPassword(passwordEncoder.encode(defaultIfBlank(dto.getPassword(), defaultPassword)));
        user.setRealName(trimToNull(dto.getRealName()));
        user.setEmail(trimToNull(dto.getEmail()));
        user.setPhone(trimToNull(dto.getPhone()));
        user.setRoleId(role.getId());
        user.setStatus(defaultStatus(dto.getStatus()));
        user.setIsAdmin(ROLE_ADMIN.equals(role.getRoleCode()) ? 1 : 0);
        user.setGlobalSearchEnabled(defaultFlag(dto.getGlobalSearchEnabled()));
        user.setAccountType(ACCOUNT_NORMAL);
        user.setDeleted(0);
        userMapper.insert(user);
        saveProjectIds(user.getId(), dto.getProjectIds());
        return toUserVO(userMapper.selectById(user.getId()));
    }

    @Transactional
    public AdminUserVO updateUser(Long id, AdminUserUpdateDTO dto) {
        currentUserAccessService.assertAdmin();
        User user = requireUser(id);
        Role role = dto.getRoleId() == null ? null : requireRole(dto.getRoleId());

        if (dto.getRealName() != null) {
            user.setRealName(trimToNull(dto.getRealName()));
        }
        if (dto.getEmail() != null) {
            user.setEmail(trimToNull(dto.getEmail()));
        }
        if (dto.getPhone() != null) {
            user.setPhone(trimToNull(dto.getPhone()));
        }
        if (role != null) {
            user.setRoleId(role.getId());
            user.setIsAdmin(ROLE_ADMIN.equals(role.getRoleCode()) ? 1 : 0);
        }
        if (dto.getStatus() != null) {
            user.setStatus(defaultStatus(dto.getStatus()));
        }
        if (dto.getGlobalSearchEnabled() != null) {
            user.setGlobalSearchEnabled(defaultFlag(dto.getGlobalSearchEnabled()));
        }
        userMapper.updateById(user);
        if (dto.getProjectIds() != null) {
            saveProjectIds(user.getId(), dto.getProjectIds());
        }
        return toUserVO(userMapper.selectById(id));
    }

    @Transactional
    public AdminUserVO updateUserStatus(Long id, Integer status) {
        currentUserAccessService.assertAdmin();
        User user = requireUser(id);
        user.setStatus(defaultStatus(status));
        userMapper.updateById(user);
        return toUserVO(userMapper.selectById(id));
    }

    @Transactional
    public ResetPasswordVO resetPassword(Long id) {
        currentUserAccessService.assertAdmin();
        User user = requireUser(id);
        user.setPassword(passwordEncoder.encode(defaultPassword));
        userMapper.updateById(user);
        return new ResetPasswordVO(defaultPassword);
    }

    public List<Long> listProjectIds(Long userId) {
        currentUserAccessService.assertAdmin();
        requireUser(userId);
        return loadProjectIds(userId);
    }

    @Transactional
    public List<Long> saveProjectIds(Long userId, ProjectAuthorizationDTO dto) {
        currentUserAccessService.assertAdmin();
        requireUser(userId);
        saveProjectIds(userId, dto == null ? Collections.emptyList() : dto.getProjectIds());
        return loadProjectIds(userId);
    }

    public List<RoleVO> listRoles() {
        currentUserAccessService.assertAdmin();
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId))
            .stream()
            .map(this::toRoleVO)
            .toList();
    }

    @Transactional
    public RoleVO updateRole(Long id, RoleUpdateDTO dto) {
        currentUserAccessService.assertAdmin();
        Role role = requireRole(id);
        role.setDescription(trimToNull(dto == null ? null : dto.getDescription()));
        roleMapper.updateById(role);
        return toRoleVO(roleMapper.selectById(id));
    }

    @Transactional
    public AdminUserVO createTempUser(TempUserCreateDTO dto) {
        currentUserAccessService.assertAdmin();
        if (dto.getExpireAt() == null || !dto.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("临时账号有效期必须晚于当前时间");
        }
        assertUsernameAvailable(dto.getUsername(), null);
        Role role = roleByCode(ROLE_TEMP_WORKER);

        User user = new User();
        user.setUsername(dto.getUsername().trim());
        user.setPassword(passwordEncoder.encode(defaultIfBlank(dto.getPassword(), defaultPassword)));
        user.setRealName(trimToNull(dto.getRealName()));
        user.setEmail(trimToNull(dto.getEmail()));
        user.setPhone(trimToNull(dto.getPhone()));
        user.setRoleId(role.getId());
        user.setStatus(1);
        user.setIsAdmin(0);
        user.setGlobalSearchEnabled(0);
        user.setAccountType(ACCOUNT_TEMP);
        user.setExpireAt(dto.getExpireAt());
        user.setDeleted(0);
        userMapper.insert(user);
        saveProjectIds(user.getId(), dto.getProjectIds());
        return toUserVO(userMapper.selectById(user.getId()));
    }

    @Transactional
    public AdminUserVO updateTempUser(Long id, TempUserUpdateDTO dto) {
        currentUserAccessService.assertAdmin();
        User user = requireUser(id);
        if (!ACCOUNT_TEMP.equalsIgnoreCase(user.getAccountType())) {
            throw new IllegalArgumentException("目标用户不是临时账号");
        }
        if (dto.getRealName() != null) {
            user.setRealName(trimToNull(dto.getRealName()));
        }
        if (dto.getEmail() != null) {
            user.setEmail(trimToNull(dto.getEmail()));
        }
        if (dto.getPhone() != null) {
            user.setPhone(trimToNull(dto.getPhone()));
        }
        if (dto.getStatus() != null) {
            user.setStatus(defaultStatus(dto.getStatus()));
        }
        if (dto.getExpireAt() != null) {
            user.setExpireAt(dto.getExpireAt());
        }
        userMapper.updateById(user);
        if (dto.getProjectIds() != null) {
            saveProjectIds(user.getId(), dto.getProjectIds());
        }
        return toUserVO(userMapper.selectById(id));
    }

    private void assertUsernameAvailable(String username, Long excludeId) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, username.trim())
            .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private Role requireRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        return role;
    }

    private Role roleByCode(String roleCode) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
            .eq(Role::getRoleCode, roleCode)
            .last("LIMIT 1"));
        if (role == null) {
            throw new AccessDeniedException("系统角色尚未初始化");
        }
        return role;
    }

    private List<AdminUserVO> toUserVOs(List<User> users) {
        Map<Long, Role> roleMap = roleMapper.selectList(new LambdaQueryWrapper<Role>())
            .stream()
            .collect(Collectors.toMap(Role::getId, role -> role, (left, right) -> left, LinkedHashMap::new));
        Map<Long, List<Long>> projectMap = loadProjectIds(users.stream().map(User::getId).collect(Collectors.toSet()));
        return users.stream()
            .map(user -> toUserVO(user, roleMap.get(user.getRoleId()), projectMap.getOrDefault(user.getId(), List.of())))
            .toList();
    }

    private AdminUserVO toUserVO(User user) {
        Role role = user.getRoleId() == null ? null : roleMapper.selectById(user.getRoleId());
        return toUserVO(user, role, loadProjectIds(user.getId()));
    }

    private AdminUserVO toUserVO(User user, Role role, List<Long> projectIds) {
        return AdminUserVO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .roleId(user.getRoleId())
            .roleCode(role == null ? null : role.getRoleCode())
            .roleName(role == null ? null : role.getRoleName())
            .status(user.getStatus())
            .isAdmin(user.getIsAdmin())
            .globalSearchEnabled(user.getGlobalSearchEnabled())
            .accountType(defaultIfBlank(user.getAccountType(), ACCOUNT_NORMAL))
            .expireAt(user.getExpireAt())
            .createTime(user.getCreateTime())
            .lastLoginTime(user.getLastLoginTime())
            .projectIds(projectIds)
            .build();
    }

    private RoleVO toRoleVO(Role role) {
        return RoleVO.builder()
            .id(role.getId())
            .roleCode(role.getRoleCode())
            .roleName(role.getRoleName())
            .description(role.getDescription())
            .createTime(role.getCreateTime())
            .updateTime(role.getUpdateTime())
            .build();
    }

    private void saveProjectIds(Long userId, List<Long> projectIds) {
        jdbcTemplate.update("DELETE FROM sys_user_project WHERE user_id = ?", userId);
        if (projectIds == null || projectIds.isEmpty()) {
            return;
        }
        List<Long> distinctProjectIds = projectIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Long> validIds = projectMapper.selectBatchIds(distinctProjectIds).stream().map(Project::getId).toList();
        for (Long projectId : validIds) {
            UserProject userProject = new UserProject();
            userProject.setUserId(userId);
            userProject.setProjectId(projectId);
            userProjectMapper.insert(userProject);
        }
    }

    private List<Long> loadProjectIds(Long userId) {
        return userProjectMapper.selectList(new LambdaQueryWrapper<UserProject>().eq(UserProject::getUserId, userId))
            .stream()
            .map(UserProject::getProjectId)
            .toList();
    }

    private Map<Long, List<Long>> loadProjectIds(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userProjectMapper.selectList(new LambdaQueryWrapper<UserProject>().in(UserProject::getUserId, userIds))
            .stream()
            .collect(Collectors.groupingBy(UserProject::getUserId, Collectors.mapping(UserProject::getProjectId, Collectors.toList())));
    }

    private Integer defaultStatus(Integer status) {
        return Objects.equals(status, 0) ? 0 : 1;
    }

    private Integer defaultFlag(Integer value) {
        return Objects.equals(value, 1) ? 1 : 0;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
