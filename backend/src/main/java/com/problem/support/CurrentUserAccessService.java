package com.problem.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.entity.OpsIssue;
import com.problem.entity.User;
import com.problem.entity.UserProject;
import com.problem.mapper.OpsIssueMapper;
import com.problem.mapper.UserMapper;
import com.problem.mapper.UserProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CurrentUserAccessService {

    private final UserMapper userMapper;
    private final UserProjectMapper userProjectMapper;
    private final OpsIssueMapper opsIssueMapper;

    public User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, username)
            .last("LIMIT 1"));
        if (user == null) {
            throw new IllegalArgumentException("当前登录用户不存在");
        }
        return user;
    }

    public boolean isAdmin(User user) {
        return Objects.equals(user.getIsAdmin(), 1) || "admin".equalsIgnoreCase(user.getUsername());
    }

    public boolean isTemporary(User user) {
        return user != null && "TEMP".equalsIgnoreCase(user.getAccountType());
    }

    public void assertAdmin() {
        if (!isAdmin(getCurrentUser())) {
            throw new AccessDeniedException("仅系统管理员可访问");
        }
    }

    public void assertNotTemporary(String actionName) {
        if (isTemporary(getCurrentUser())) {
            throw new AccessDeniedException("临时账号不可" + actionName);
        }
    }

    public boolean canGlobalSearch(User user) {
        return isAdmin(user) || Objects.equals(user.getGlobalSearchEnabled(), 1);
    }

    public Set<Long> getAccessibleProjectIds(User user) {
        if (isAdmin(user)) {
            return Collections.emptySet();
        }
        return userProjectMapper.selectList(new LambdaQueryWrapper<UserProject>().eq(UserProject::getUserId, user.getId()))
            .stream()
            .map(UserProject::getProjectId)
            .collect(Collectors.toSet());
    }

    public List<Long> scopeProjectIdsForList(User user) {
        if (isAdmin(user)) {
            return Collections.emptyList();
        }
        return getAccessibleProjectIds(user).stream().toList();
    }

    public boolean hasNoVisibleProjects(User user) {
        return !isAdmin(user) && getAccessibleProjectIds(user).isEmpty();
    }

    public void applyIssueListScope(LambdaQueryWrapper<OpsIssue> wrapper, User user) {
        List<Long> scopedProjectIds = scopeProjectIdsForList(user);
        if (!scopedProjectIds.isEmpty()) {
            wrapper.in(OpsIssue::getProjectId, scopedProjectIds);
        }
        if (isTemporary(user)) {
            wrapper.eq(OpsIssue::getCreatedBy, user.getId());
        }
    }

    public void assertProjectAccess(Long projectId) {
        User user = getCurrentUser();
        if (isAdmin(user)) {
            return;
        }
        if (!getAccessibleProjectIds(user).contains(projectId)) {
            throw new AccessDeniedException("无权访问该项目数据");
        }
    }

    public void assertIssueAccess(Long issueId) {
        OpsIssue issue = opsIssueMapper.selectById(issueId);
        if (issue == null) {
            throw new IllegalArgumentException("问题不存在");
        }
        assertProjectAccess(issue.getProjectId());
        User user = getCurrentUser();
        if (isTemporary(user) && !Objects.equals(issue.getCreatedBy(), user.getId())) {
            throw new AccessDeniedException("临时账号只能访问自己创建的问题");
        }
    }
}
