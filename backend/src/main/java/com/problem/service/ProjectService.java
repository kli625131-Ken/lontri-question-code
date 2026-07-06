package com.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.problem.dto.ProjectSaveDTO;
import com.problem.dto.ProjectWarrantySaveDTO;
import com.problem.entity.OpsIssue;
import com.problem.entity.OpsProjectContact;
import com.problem.entity.OpsProjectWarranty;
import com.problem.entity.Project;
import com.problem.entity.User;
import com.problem.mapper.OpsIssueMapper;
import com.problem.mapper.OpsProjectContactMapper;
import com.problem.mapper.OpsProjectWarrantyMapper;
import com.problem.mapper.ProjectMapper;
import com.problem.support.CurrentUserAccessService;
import com.problem.vo.ProjectContactVO;
import com.problem.vo.ProjectVO;
import com.problem.vo.ProjectWarrantyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final OpsIssueMapper opsIssueMapper;
    private final OpsProjectContactMapper opsProjectContactMapper;
    private final OpsProjectWarrantyMapper opsProjectWarrantyMapper;
    private final CurrentUserAccessService currentUserAccessService;

    @Value("${ops.upload.root-path:./uploads}")
    private String uploadRootPath;

    @Value("${ops.upload.contract-max-file-size-bytes:52428800}")
    private long contractMaxFileSizeBytes;

    @Transactional
    public ProjectVO createProject(ProjectSaveDTO dto) {
        currentUserAccessService.assertAdmin();
        Project project = new Project();
        applyProjectFields(project, dto, false);
        projectMapper.insert(project);
        return toProjectVO(project, Map.of(), Map.of());
    }

    @Transactional
    public ProjectVO updateProject(Long id, ProjectSaveDTO dto) {
        currentUserAccessService.assertAdmin();
        Project project = requireProject(id);
        String oldProjectCode = project.getProjectCode();
        applyProjectFields(project, dto, true);
        projectMapper.updateById(project);
        if (StringUtils.hasText(oldProjectCode) && !Objects.equals(oldProjectCode, project.getProjectCode())) {
            projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getParentProjectCode, oldProjectCode)
                .set(Project::getParentProjectCode, project.getProjectCode()));
        }
        return toProjectVO(requireProject(id), Map.of(), Map.of());
    }

    @Transactional
    public ProjectVO updateProjectStatus(Long id, int isActive) {
        currentUserAccessService.assertAdmin();
        Project project = requireProject(id);
        project.setIsActive(isActive == 1 ? 1 : 0);
        projectMapper.updateById(project);
        return toProjectVO(requireProject(id), Map.of(), Map.of());
    }

    public List<ProjectVO> listProjects() {
        User user = currentUserAccessService.getCurrentUser();
        List<Project> visibleProjects = loadVisibleProjects(user);
        if (visibleProjects.isEmpty()) {
            return List.of();
        }

        Map<Long, Project> projectById = visibleProjects.stream()
            .collect(Collectors.toMap(Project::getId, project -> project, (left, right) -> left, LinkedHashMap::new));
        Map<String, Project> projectByCode = visibleProjects.stream()
            .filter(project -> StringUtils.hasText(project.getProjectCode()))
            .collect(Collectors.toMap(Project::getProjectCode, project -> project, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<Project>> childrenByParentCode = visibleProjects.stream()
            .filter(project -> StringUtils.hasText(project.getParentProjectCode()))
            .collect(Collectors.groupingBy(Project::getParentProjectCode, LinkedHashMap::new, Collectors.toList()));

        List<Project> leafProjects = visibleProjects.stream()
            .filter(project -> isLeafProject(project))
            .toList();
        Map<Long, List<OpsIssue>> issuesByProject = loadIssuesByProject(leafProjects.stream().map(Project::getId).collect(Collectors.toSet()));

        return visibleProjects.stream()
            .sorted(projectComparator())
            .map(project -> {
                List<Project> descendantLeaves = collectDescendantLeaves(project, childrenByParentCode);
                Set<Long> descendantLeafIds = descendantLeaves.stream().map(Project::getId).collect(Collectors.toSet());
                long issueCount = descendantLeafIds.stream()
                    .mapToLong(projectId -> issuesByProject.getOrDefault(projectId, Collections.emptyList()).size())
                    .sum();
                long openCount = descendantLeafIds.stream()
                    .flatMap(projectId -> issuesByProject.getOrDefault(projectId, Collections.emptyList()).stream())
                    .filter(issue -> !"CLOSED".equals(issue.getClosureStatus()))
                    .count();
                return toProjectVO(project, Map.of(project.getId(), descendantLeaves), Map.of(project.getId(), List.of(issueCount, openCount)));
            })
            .toList();
    }

    public List<ProjectContactVO> listContacts(Long projectId) {
        Map<Long, Project> visibleProjectMap = loadVisibleProjectMap(currentUserAccessService.getCurrentUser());
        List<Long> leafProjectIds = resolveVisibleLeafProjectIds(projectId, visibleProjectMap);
        if (leafProjectIds.isEmpty()) {
            return List.of();
        }
        return opsProjectContactMapper.selectList(new LambdaQueryWrapper<OpsProjectContact>()
                .in(OpsProjectContact::getProjectId, leafProjectIds)
                .orderByAsc(OpsProjectContact::getProjectId, OpsProjectContact::getPositionTitle))
            .stream()
            .map(contact -> ProjectContactVO.builder()
                .id(contact.getId())
                .projectId(contact.getProjectId())
                .projectName(projectNameOf(visibleProjectMap, contact.getProjectId()))
                .positionTitle(contact.getPositionTitle())
                .contactName(contact.getContactName())
                .contactInfo(contact.getContactInfo())
                .responsibility(contact.getResponsibility())
                .notes(contact.getNotes())
                .build())
            .toList();
    }

    private void applyProjectFields(Project project, ProjectSaveDTO dto, boolean updating) {
        String projectName = requireText(dto.getProjectName(), "项目名称不能为空");
        String projectLevel = defaultIfBlank(dto.getProjectLevel(), "PROJECT").toUpperCase();
        if (!Set.of("CUSTOMER", "PROJECT_GROUP", "PROJECT").contains(projectLevel)) {
            throw new IllegalArgumentException("项目层级不正确");
        }

        String projectCode = defaultIfBlank(dto.getProjectCode(), buildProjectCode(projectLevel, projectName));
        Project duplicateName = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
            .eq(Project::getProjectName, projectName)
            .last("LIMIT 1"));
        if (duplicateName != null && (!updating || !Objects.equals(duplicateName.getId(), project.getId()))) {
            throw new IllegalArgumentException("项目名称已存在");
        }

        Project duplicateCode = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
            .eq(Project::getProjectCode, projectCode)
            .last("LIMIT 1"));
        if (duplicateCode != null && (!updating || !Objects.equals(duplicateCode.getId(), project.getId()))) {
            throw new IllegalArgumentException("项目编码已存在");
        }

        String parentProjectCode = trimToNull(dto.getParentProjectCode());
        if ("CUSTOMER".equals(projectLevel)) {
            parentProjectCode = null;
        } else if (!StringUtils.hasText(parentProjectCode)) {
            throw new IllegalArgumentException("项目或子项目必须选择上级项目");
        }
        validateParent(project, projectCode, parentProjectCode);

        Project parent = StringUtils.hasText(parentProjectCode) ? projectByCode(parentProjectCode) : null;
        String customerName = defaultIfBlank(dto.getCustomerName(), parent == null ? projectName : parent.getCustomerName());
        String projectGroup = defaultIfBlank(dto.getProjectGroup(), "PROJECT".equals(projectLevel) && parent != null ? parent.getProjectName() : customerName);
        if ("CUSTOMER".equals(projectLevel)) {
            customerName = projectName;
            projectGroup = projectName;
        }
        if ("PROJECT_GROUP".equals(projectLevel)) {
            projectGroup = projectName;
        }

        project.setProjectName(projectName);
        project.setProjectCode(projectCode);
        project.setParentProjectCode(parentProjectCode);
        project.setProjectLevel(projectLevel);
        project.setCustomerName(customerName);
        project.setProjectGroup(projectGroup);
        project.setDescription(trimToNull(dto.getDescription()));
        project.setReminderEnabled(dto.getReminderEnabled() == null ? 1 : (dto.getReminderEnabled() == 1 ? 1 : 0));
        project.setRemindAfterDays(dto.getRemindAfterDays() == null || dto.getRemindAfterDays() < 1 ? 7 : dto.getRemindAfterDays());
        project.setIsActive(dto.getIsActive() == null ? 1 : (dto.getIsActive() == 1 ? 1 : 0));
    }

    private void validateParent(Project project, String projectCode, String parentProjectCode) {
        if (!StringUtils.hasText(parentProjectCode)) {
            return;
        }
        if (Objects.equals(projectCode, parentProjectCode)) {
            throw new IllegalArgumentException("上级项目不能选择自身");
        }
        Project parent = projectByCode(parentProjectCode);
        if (parent == null) {
            throw new IllegalArgumentException("上级项目不存在");
        }
        if (project.getId() == null) {
            return;
        }
        String nextParentCode = parent.getParentProjectCode();
        while (StringUtils.hasText(nextParentCode)) {
            if (Objects.equals(nextParentCode, projectCode)) {
                throw new IllegalArgumentException("上级项目不能选择自己的下级项目");
            }
            Project next = projectByCode(nextParentCode);
            nextParentCode = next == null ? null : next.getParentProjectCode();
        }
    }

    private Project projectByCode(String projectCode) {
        return projectMapper.selectOne(new LambdaQueryWrapper<Project>()
            .eq(Project::getProjectCode, projectCode)
            .last("LIMIT 1"));
    }

    private Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        return project;
    }

    private ProjectVO toProjectVO(Project project, Map<Long, List<Project>> descendantLeavesByProject, Map<Long, List<Long>> countByProject) {
        List<Project> descendantLeaves = descendantLeavesByProject.getOrDefault(project.getId(), isLeafProject(project) ? List.of(project) : List.of());
        List<Long> counts = countByProject.get(project.getId());
        long issueCount = counts == null || counts.isEmpty() ? 0 : counts.get(0);
        long openCount = counts == null || counts.size() < 2 ? 0 : counts.get(1);
        return ProjectVO.builder()
            .id(project.getId())
            .customerName(project.getCustomerName())
            .projectGroup(project.getProjectGroup())
            .projectName(project.getProjectName())
            .projectCode(project.getProjectCode())
            .parentProjectCode(project.getParentProjectCode())
            .projectLevel(defaultIfBlank(project.getProjectLevel(), "PROJECT"))
            .isActive(project.getIsActive())
            .description(project.getDescription())
            .reminderEnabled(project.getReminderEnabled())
            .remindAfterDays(project.getRemindAfterDays())
            .childProjectCount(descendantLeaves.size())
            .issueCount(issueCount)
            .openCount(openCount)
            .build();
    }

    public List<ProjectWarrantyVO> listWarranties(Long projectId) {
        Map<Long, Project> visibleProjectMap = loadVisibleProjectMap(currentUserAccessService.getCurrentUser());
        List<Long> warrantyProjectIds = resolveVisibleWarrantyProjectIds(projectId, visibleProjectMap);
        if (warrantyProjectIds.isEmpty()) {
            return List.of();
        }
        return opsProjectWarrantyMapper.selectList(new LambdaQueryWrapper<OpsProjectWarranty>()
                .in(OpsProjectWarranty::getProjectId, warrantyProjectIds)
                .orderByDesc(OpsProjectWarranty::getEndAt, OpsProjectWarranty::getExpireAt))
            .stream()
            .map(warranty -> toWarrantyVO(warranty, projectNameOf(visibleProjectMap, warranty.getProjectId())))
            .toList();
    }

    @Transactional
    public ProjectWarrantyVO uploadWarrantyFile(Long projectId, MultipartFile file) {
        currentUserAccessService.assertAdmin();
        Project project = requireProject(projectId);
        validateWarrantyFile(file);

        String originalFileName = StringUtils.cleanPath(defaultIfBlank(file.getOriginalFilename(), "contract.pdf"));
        String extension = extensionOf(originalFileName);
        String storedFileName = UUID.randomUUID() + "." + extension;
        String projectSegment = safePathSegment(defaultIfBlank(project.getProjectCode(), "project-" + project.getId()));
        String relativePath = "contracts/" + projectSegment + "/" + storedFileName;
        Path root = Path.of(uploadRootPath).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("文件保存路径不合法");
        }

        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("合同文件保存失败，请稍后重试");
        }

        OpsProjectWarranty warranty = new OpsProjectWarranty();
        warranty.setProjectId(projectId);
        warranty.setContractType("合同原件");
        warranty.setServiceScope(project.getProjectName());
        warranty.setFileName(originalFileName);
        warranty.setFileType(file.getContentType());
        warranty.setFileSize(file.getSize());
        warranty.setFilePath(relativePath.replace("\\", "/"));
        warranty.setNotes("PDF合同原件，日期信息待补充");
        opsProjectWarrantyMapper.insert(warranty);
        return toWarrantyVO(warranty, project.getProjectName());
    }

    @Transactional
    public ProjectWarrantyVO createWarranty(Long projectId, ProjectWarrantySaveDTO dto) {
        currentUserAccessService.assertAdmin();
        Project project = requireProject(projectId);
        OpsProjectWarranty warranty = new OpsProjectWarranty();
        warranty.setProjectId(projectId);
        warranty.setContractType(defaultIfBlank(dto.getContractType(), "合同"));
        warranty.setStartAt(dto.getStartAt());
        warranty.setEndAt(dto.getEndAt());
        warranty.setServiceScope(trimToNull(dto.getServiceScope()));
        warranty.setWarrantyTerm(trimToNull(dto.getWarrantyTerm()));
        warranty.setExpireAt(dto.getEndAt());
        warranty.setNotes(trimToNull(dto.getNotes()));
        opsProjectWarrantyMapper.insert(warranty);
        return toWarrantyVO(warranty, project.getProjectName());
    }

    @Transactional
    public ProjectWarrantyVO updateWarranty(Long projectId, Long warrantyId, ProjectWarrantySaveDTO dto) {
        currentUserAccessService.assertAdmin();
        Project project = requireProject(projectId);
        OpsProjectWarranty warranty = opsProjectWarrantyMapper.selectById(warrantyId);
        if (warranty == null) {
            throw new IllegalArgumentException("合同记录不存在");
        }
        Map<Long, Project> visibleProjectMap = loadVisibleProjectMap(currentUserAccessService.getCurrentUser());
        if (!resolveVisibleWarrantyProjectIds(projectId, visibleProjectMap).contains(warranty.getProjectId())) {
            throw new IllegalArgumentException("合同记录不属于当前项目范围");
        }
        warranty.setContractType(defaultIfBlank(dto.getContractType(), warranty.getContractType()));
        warranty.setStartAt(dto.getStartAt());
        warranty.setEndAt(dto.getEndAt());
        warranty.setServiceScope(trimToNull(dto.getServiceScope()));
        warranty.setWarrantyTerm(trimToNull(dto.getWarrantyTerm()));
        warranty.setExpireAt(dto.getEndAt());
        warranty.setNotes(trimToNull(dto.getNotes()));
        opsProjectWarrantyMapper.updateById(warranty);
        Project warrantyProject = visibleProjectMap.get(warranty.getProjectId());
        return toWarrantyVO(warranty, warrantyProject == null ? project.getProjectName() : warrantyProject.getProjectName());
    }

    private ProjectWarrantyVO toWarrantyVO(OpsProjectWarranty warranty, String projectName) {
        String downloadUrl = StringUtils.hasText(warranty.getFilePath()) ? "/uploads/" + warranty.getFilePath() : null;
        return ProjectWarrantyVO.builder()
            .id(warranty.getId())
            .projectId(warranty.getProjectId())
            .projectName(projectName)
            .contractType(warranty.getContractType())
            .startAt(warranty.getStartAt())
            .endAt(warranty.getEndAt())
            .serviceScope(warranty.getServiceScope())
            .contractStatus(resolveContractStatus(warranty.getStartAt(), warranty.getEndAt()))
            .contractSignedAt(warranty.getContractSignedAt())
            .acceptanceAt(warranty.getAcceptanceAt())
            .warrantyTerm(warranty.getWarrantyTerm())
            .expireAt(warranty.getExpireAt())
            .fileName(warranty.getFileName())
            .fileType(warranty.getFileType())
            .fileSize(warranty.getFileSize())
            .downloadUrl(downloadUrl)
            .notes(warranty.getNotes())
            .build();
    }

    private String resolveContractStatus(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            return "待补充";
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(endAt)) {
            return "已过期";
        }
        long daysToEnd = ChronoUnit.DAYS.between(now.toLocalDate(), endAt.toLocalDate());
        if (daysToEnd < 30) {
            return "即将到期";
        }
        if (!now.isBefore(startAt) && !now.isAfter(endAt)) {
            return "生效中/在保";
        }
        return "待补充";
    }

    private Map<Long, Project> loadVisibleProjectMap(User user) {
        return loadVisibleProjects(user).stream()
            .collect(Collectors.toMap(Project::getId, project -> project, (left, right) -> left, LinkedHashMap::new));
    }

    private List<Project> loadVisibleProjects(User user) {
        List<Project> allProjects = projectMapper.selectList(new LambdaQueryWrapper<Project>().orderByAsc(Project::getProjectName));
        if (allProjects.isEmpty()) {
            return List.of();
        }
        if (currentUserAccessService.isAdmin(user)) {
            return allProjects;
        }

        Set<Long> accessibleProjectIds = currentUserAccessService.getAccessibleProjectIds(user);
        if (accessibleProjectIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Project> projectById = allProjects.stream()
            .collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, Project> projectByCode = allProjects.stream()
            .filter(project -> StringUtils.hasText(project.getProjectCode()))
            .collect(Collectors.toMap(Project::getProjectCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        Set<Long> visibleIds = new LinkedHashSet<>(accessibleProjectIds);
        ArrayDeque<Project> queue = new ArrayDeque<>();
        accessibleProjectIds.stream().map(projectById::get).filter(Objects::nonNull).forEach(queue::add);
        while (!queue.isEmpty()) {
            Project current = queue.removeFirst();
            if (!StringUtils.hasText(current.getParentProjectCode())) {
                continue;
            }
            Project parent = projectByCode.get(current.getParentProjectCode());
            if (parent != null && visibleIds.add(parent.getId())) {
                queue.add(parent);
            }
        }
        return allProjects.stream().filter(project -> visibleIds.contains(project.getId())).toList();
    }

    private List<Long> resolveVisibleLeafProjectIds(Long projectId, Map<Long, Project> visibleProjectMap) {
        Project selected = visibleProjectMap.get(projectId);
        if (selected == null) {
            throw new IllegalArgumentException("项目不存在或当前账号无权访问");
        }
        if (isLeafProject(selected)) {
            return List.of(selected.getId());
        }

        Map<String, List<Project>> childrenByParentCode = visibleProjectMap.values().stream()
            .filter(project -> StringUtils.hasText(project.getParentProjectCode()))
            .collect(Collectors.groupingBy(Project::getParentProjectCode, LinkedHashMap::new, Collectors.toList()));
        return collectDescendantLeaves(selected, childrenByParentCode).stream().map(Project::getId).toList();
    }

    private List<Long> resolveVisibleWarrantyProjectIds(Long projectId, Map<Long, Project> visibleProjectMap) {
        Project selected = visibleProjectMap.get(projectId);
        if (selected == null) {
            throw new IllegalArgumentException("项目不存在或当前账号无权访问");
        }
        LinkedHashSet<Long> projectIds = new LinkedHashSet<>();
        projectIds.add(selected.getId());
        if (!isLeafProject(selected)) {
            Map<String, List<Project>> childrenByParentCode = visibleProjectMap.values().stream()
                .filter(project -> StringUtils.hasText(project.getParentProjectCode()))
                .collect(Collectors.groupingBy(Project::getParentProjectCode, LinkedHashMap::new, Collectors.toList()));
            collectDescendantLeaves(selected, childrenByParentCode).stream()
                .map(Project::getId)
                .forEach(projectIds::add);
        }
        return projectIds.stream().toList();
    }

    private List<Project> collectDescendantLeaves(Project root, Map<String, List<Project>> childrenByParentCode) {
        if (root == null) {
            return List.of();
        }
        if (isLeafProject(root)) {
            return List.of(root);
        }
        List<Project> leaves = new ArrayList<>();
        ArrayDeque<Project> queue = new ArrayDeque<>(childrenByParentCode.getOrDefault(root.getProjectCode(), List.of()));
        while (!queue.isEmpty()) {
            Project current = queue.removeFirst();
            if (isLeafProject(current)) {
                leaves.add(current);
                continue;
            }
            queue.addAll(childrenByParentCode.getOrDefault(current.getProjectCode(), List.of()));
        }
        return leaves;
    }

    private Map<Long, List<OpsIssue>> loadIssuesByProject(Set<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return opsIssueMapper.selectList(new LambdaQueryWrapper<OpsIssue>().in(OpsIssue::getProjectId, projectIds))
            .stream()
            .collect(Collectors.groupingBy(OpsIssue::getProjectId, Collectors.mapping(Function.identity(), Collectors.toList())));
    }

    private boolean isLeafProject(Project project) {
        return project != null && !"CUSTOMER".equalsIgnoreCase(project.getProjectLevel()) && !"PROJECT_GROUP".equalsIgnoreCase(project.getProjectLevel());
    }

    private Comparator<Project> projectComparator() {
        return Comparator
            .comparingInt((Project project) -> switch (defaultIfBlank(project.getProjectLevel(), "PROJECT")) {
                case "CUSTOMER" -> 0;
                case "PROJECT_GROUP" -> 1;
                default -> 2;
            })
            .thenComparing(project -> defaultIfBlank(project.getCustomerName(), ""))
            .thenComparing(project -> defaultIfBlank(project.getProjectGroup(), ""))
            .thenComparing(project -> defaultIfBlank(project.getProjectName(), ""));
    }

    private String projectNameOf(Map<Long, Project> visibleProjectMap, Long projectId) {
        Project project = visibleProjectMap.get(projectId);
        return project == null ? "未命名项目" : project.getProjectName();
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String buildProjectCode(String level, String source) {
        String prefix = switch (defaultIfBlank(level, "PROJECT")) {
            case "CUSTOMER" -> "CUS";
            case "PROJECT_GROUP" -> "GRP";
            default -> "PRJ";
        };
        String normalized = source.toUpperCase()
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = Integer.toHexString(source.hashCode()).toUpperCase();
        }
        return prefix + "-" + normalized;
    }

    private void validateWarrantyFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的合同文件");
        }
        String originalFileName = StringUtils.cleanPath(defaultIfBlank(file.getOriginalFilename(), ""));
        if (!"pdf".equals(extensionOf(originalFileName))) {
            throw new IllegalArgumentException("合同原件仅支持 PDF 文件");
        }
        if (file.getSize() > contractMaxFileSizeBytes) {
            throw new IllegalArgumentException("合同文件超过 50MB，请压缩后再上传");
        }
    }

    private String extensionOf(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private String safePathSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
