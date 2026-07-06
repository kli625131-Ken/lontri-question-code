package com.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.dto.IssueCloseDTO;
import com.problem.dto.IssueCreateDTO;
import com.problem.dto.IssueRecordCreateDTO;
import com.problem.dto.IssueReopenDTO;
import com.problem.dto.IssueUpdateDTO;
import com.problem.entity.OpsImportRowReview;
import com.problem.entity.OpsIssue;
import com.problem.entity.OpsIssueRecord;
import com.problem.entity.Project;
import com.problem.entity.User;
import com.problem.mapper.OpsImportRowReviewMapper;
import com.problem.mapper.OpsIssueMapper;
import com.problem.mapper.OpsIssueRecordMapper;
import com.problem.mapper.ProjectMapper;
import com.problem.support.CurrentUserAccessService;
import com.problem.vo.IssueDetailVO;
import com.problem.vo.IssueListItemVO;
import com.problem.vo.IssueOverviewVO;
import com.problem.vo.IssueRecordVO;
import com.problem.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueService {

    private static final int MAX_DEDUPE_KEY_LENGTH = 255;
    private static final int MAX_SHEET_SEGMENT_LENGTH = 48;
    private static final DateTimeFormatter ISSUE_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OpsIssueMapper opsIssueMapper;
    private final OpsIssueRecordMapper opsIssueRecordMapper;
    private final OpsImportRowReviewMapper opsImportRowReviewMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserAccessService currentUserAccessService;
    private final KnowledgeService knowledgeService;

    public PageResultVO<IssueListItemVO> listIssues(
        Long projectId,
        String currentStatus,
        String closureStatus,
        String categoryKeyword,
        String causeCategory,
        String source,
        String priority,
        String severity,
        String systemType,
        String tagKeyword,
        String ownerName,
        String keyword,
        LocalDateTime startDate,
        LocalDateTime endDate,
        long page,
        long pageSize
    ) {
        User user = currentUserAccessService.getCurrentUser();
        LambdaQueryWrapper<OpsIssue> wrapper = buildIssueListWrapper(user, projectId, currentStatus, closureStatus, categoryKeyword, causeCategory, source, priority, severity, systemType, tagKeyword, ownerName, keyword, startDate, endDate)
            .orderByDesc(OpsIssue::getReceivedAt, OpsIssue::getFoundAt, OpsIssue::getCreateTime);

        List<OpsIssue> issues = opsIssueMapper.selectList(wrapper);
        if (currentUserAccessService.hasNoVisibleProjects(user)) {
            return new PageResultVO<>(0, page < 1 ? 1 : page, pageSize < 1 ? 20 : pageSize, List.of());
        }
        Map<Long, Project> projectMap = loadProjectMap(issues.stream().map(OpsIssue::getProjectId).collect(Collectors.toSet()));
        List<IssueListItemVO> items = issues.stream()
            .map(issue -> toItemVO(issue, projectMap.get(issue.getProjectId())))
            .toList();
        return paginate(items, page, pageSize);
    }

    private LambdaQueryWrapper<OpsIssue> buildIssueListWrapper(
        User user,
        Long projectId,
        String currentStatus,
        String closureStatus,
        String categoryKeyword,
        String causeCategory,
        String source,
        String priority,
        String severity,
        String systemType,
        String tagKeyword,
        String ownerName,
        String keyword,
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {
        if (projectId != null) {
            currentUserAccessService.assertProjectAccess(projectId);
        }

        LambdaQueryWrapper<OpsIssue> wrapper = new LambdaQueryWrapper<>();
        currentUserAccessService.applyIssueListScope(wrapper, user);
        if (projectId != null) {
            wrapper.eq(OpsIssue::getProjectId, projectId);
        }
        if (StringUtils.hasText(currentStatus)) {
            wrapper.eq(OpsIssue::getCurrentStatus, currentStatus.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(closureStatus)) {
            wrapper.eq(OpsIssue::getClosureStatus, closureStatus.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(categoryKeyword)) {
            wrapper.like(OpsIssue::getCategoryPath, categoryKeyword.trim());
        }
        if (StringUtils.hasText(causeCategory)) {
            wrapper.like(OpsIssue::getCauseCategory, causeCategory.trim());
        }
        if (StringUtils.hasText(source)) {
            wrapper.eq(OpsIssue::getSource, source.trim());
        }
        if (StringUtils.hasText(priority)) {
            wrapper.eq(OpsIssue::getPriority, priority.trim());
        }
        if (StringUtils.hasText(severity)) {
            wrapper.like(OpsIssue::getSeverity, severity.trim());
        }
        if (StringUtils.hasText(systemType)) {
            wrapper.eq(OpsIssue::getSystemType, systemType.trim());
        }
        if (StringUtils.hasText(tagKeyword)) {
            wrapper.like(OpsIssue::getReuseTags, tagKeyword.trim());
        }
        if (StringUtils.hasText(ownerName)) {
            wrapper.like(OpsIssue::getOwnerName, ownerName.trim());
        }
        if (startDate != null) {
            wrapper.ge(OpsIssue::getReceivedAt, startDate);
        }
        if (endDate != null) {
            wrapper.le(OpsIssue::getReceivedAt, endDate);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query
                .like(OpsIssue::getIssueNo, trimmed)
                .or()
                .like(OpsIssue::getReporterName, trimmed)
                .or()
                .like(OpsIssue::getItemTitle, trimmed)
                .or()
                .like(OpsIssue::getDescription, trimmed)
                .or()
                .like(OpsIssue::getBuildingName, trimmed)
                .or()
                .like(OpsIssue::getFloorName, trimmed)
                .or()
                .like(OpsIssue::getAreaName, trimmed)
                .or()
                .like(OpsIssue::getSystemType, trimmed)
                .or()
                .like(OpsIssue::getDevicePoint, trimmed)
                .or()
                .like(OpsIssue::getLatestProgress, trimmed)
                .or()
                .like(OpsIssue::getNotes, trimmed)
                .or()
                .like(OpsIssue::getInternalConclusion, trimmed)
                .or()
                .like(OpsIssue::getCustomerFeedback, trimmed)
                .or()
                .like(OpsIssue::getCauseDetail, trimmed)
                .or()
                .like(OpsIssue::getPreventiveAction, trimmed)
                .or()
                .like(OpsIssue::getFollowUpAction, trimmed)
                .or()
                .like(OpsIssue::getReuseTags, trimmed));
        }
        return wrapper;
    }

    public IssueOverviewVO getOverview() {
        return getOverview(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public IssueOverviewVO getOverview(
        Long projectId,
        String currentStatus,
        String closureStatus,
        String categoryKeyword,
        String causeCategory,
        String source,
        String priority,
        String severity,
        String systemType,
        String tagKeyword,
        String ownerName,
        String keyword,
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {
        User user = currentUserAccessService.getCurrentUser();
        LambdaQueryWrapper<OpsIssue> wrapper = buildIssueListWrapper(user, projectId, currentStatus, closureStatus, categoryKeyword, causeCategory, source, priority, severity, systemType, tagKeyword, ownerName, keyword, startDate, endDate);
        if (currentUserAccessService.hasNoVisibleProjects(user)) {
            return new IssueOverviewVO(0, 0, 0, 0, 0, 0);
        }
        List<OpsIssue> issues = opsIssueMapper.selectList(wrapper);
        long openCount = issues.stream().filter(issue -> "OPEN".equals(issue.getCurrentStatus())).count();
        long inProgressCount = issues.stream().filter(issue -> "IN_PROGRESS".equals(issue.getCurrentStatus())).count();
        long closedCount = issues.stream().filter(issue -> "CLOSED".equals(issue.getCurrentStatus())).count();
        long overdueCount = issues.stream().filter(this::isOverdue).count();
        long pendingReviewCount = opsImportRowReviewMapper.selectCount(new LambdaQueryWrapper<OpsImportRowReview>()
                .eq(OpsImportRowReview::getReviewStatus, "NEEDS_REVIEW")
                .eq(OpsImportRowReview::getCommitStatus, "PENDING"))
            .longValue();
        return new IssueOverviewVO(issues.size(), openCount, inProgressCount, closedCount, overdueCount, pendingReviewCount);
    }

    public IssueDetailVO getIssueDetail(Long issueId) {
        currentUserAccessService.assertIssueAccess(issueId);
        OpsIssue issue = requireIssue(issueId);
        Project project = requireProject(issue.getProjectId());
        List<IssueRecordVO> records = opsIssueRecordMapper.selectList(new LambdaQueryWrapper<OpsIssueRecord>()
                .eq(OpsIssueRecord::getIssueId, issueId)
                .orderByAsc(OpsIssueRecord::getOperateTime))
            .stream()
            .map(this::toRecordVO)
            .toList();
        List<IssueListItemVO> similarIssues = findSimilarIssuesInternal(issue, 5);
        return IssueDetailVO.builder()
            .id(issue.getId())
            .issueNo(defaultIfBlank(issue.getIssueNo(), fallbackIssueNo(issue.getId())))
            .projectId(issue.getProjectId())
            .customerName(project.getCustomerName())
            .projectGroup(project.getProjectGroup())
            .projectName(project.getProjectName())
            .projectCode(project.getProjectCode())
            .source(issue.getSource())
            .sourceType(defaultIfBlank(issue.getSourceType(), "MANUAL"))
            .sourceBatchId(issue.getSourceBatchId())
            .sourceSheet(issue.getSourceSheet())
            .sourceRowNumber(issue.getSourceRowNumber())
            .reporterName(issue.getReporterName())
            .receivedAt(firstNonNull(issue.getReceivedAt(), issue.getFoundAt()))
            .itemTitle(defaultIfBlank(issue.getItemTitle(), issue.getDescription()))
            .description(defaultIfBlank(issue.getDescription(), issue.getItemTitle()))
            .categoryPath(issue.getCategoryPath())
            .buildingName(issue.getBuildingName())
            .floorName(issue.getFloorName())
            .areaName(issue.getAreaName())
            .systemType(issue.getSystemType())
            .devicePoint(issue.getDevicePoint())
            .foundAt(issue.getFoundAt())
            .impactScope(issue.getImpactScope())
            .priority(issue.getPriority())
            .severity(issue.getSeverity())
            .currentStatus(issue.getCurrentStatus())
            .closureStatus(issue.getClosureStatus())
            .ownerName(issue.getOwnerName())
            .latestProgress(issue.getLatestProgress())
            .completionStatus(issue.getCompletionStatus())
            .notes(issue.getNotes())
            .internalConclusion(issue.getInternalConclusion())
            .customerFeedback(issue.getCustomerFeedback())
            .causeCategory(issue.getCauseCategory())
            .causeDetail(issue.getCauseDetail())
            .preventiveAction(issue.getPreventiveAction())
            .followUpAction(issue.getFollowUpAction())
            .reuseTags(issue.getReuseTags())
            .knowledgeIncluded(defaultKnowledgeIncluded(issue.getKnowledgeIncluded()))
            .completedAt(issue.getCompletedAt())
            .createTime(issue.getCreateTime())
            .updateTime(issue.getUpdateTime())
            .overdue(isOverdue(issue))
            .remindAfterDays(issue.getRemindAfterDays())
            .records(records)
            .similarIssues(similarIssues)
            .build();
    }

    @Transactional
    public IssueListItemVO createIssue(IssueCreateDTO dto) {
        assertLeafProject(dto.getProjectId());
        User user = currentUserAccessService.getCurrentUser();
        Project project = requireProject(dto.getProjectId());

        OpsIssue issue = new OpsIssue();
        issue.setProjectId(dto.getProjectId());
        issue.setReporterName(trimToNull(dto.getReporterName()));
        issue.setCategoryPath(defaultIfBlank(dto.getCategoryPath(), "待确认问题"));
        issue.setBuildingName(defaultIfBlank(dto.getBuildingName(), "未确认"));
        issue.setFloorName(defaultIfBlank(dto.getFloorName(), "未确认"));
        issue.setAreaName(defaultIfBlank(dto.getAreaName(), "未确认"));
        issue.setSystemType(defaultIfBlank(dto.getSystemType(), "未确认"));
        issue.setDevicePoint(defaultIfBlank(dto.getDevicePoint(), "未确认"));
        LocalDateTime receivedAt = dto.getReceivedAt() == null ? LocalDateTime.now() : dto.getReceivedAt();
        issue.setReceivedAt(receivedAt);
        issue.setFoundAt(receivedAt);
        issue.setItemTitle(dto.getItemTitle().trim());
        issue.setDescription(defaultIfBlank(dto.getDescription(), dto.getItemTitle()).trim());
        issue.setImpactScope(trimToNull(dto.getImpactScope()));
        issue.setSeverity(trimToNull(dto.getSeverity()));
        issue.setPriority(defaultIfBlank(dto.getPriority(), "中"));
        issue.setOwnerName(defaultIfBlank(dto.getOwnerName(), "未分配"));
        issue.setLatestProgress(trimToNull(dto.getLatestProgress()));
        issue.setCompletionStatus(trimToNull(dto.getCompletionStatus()));
        issue.setCompletedAt(dto.getCompletedAt());
        issue.setNotes(trimToNull(dto.getNotes()));
        issue.setInternalConclusion(trimToNull(dto.getInternalConclusion()));
        issue.setCustomerFeedback(trimToNull(dto.getCustomerFeedback()));
        issue.setCauseCategory(defaultIfBlank(dto.getCauseCategory(), "原因待确认"));
        issue.setCauseDetail(trimToNull(dto.getCauseDetail()));
        issue.setPreventiveAction(trimToNull(dto.getPreventiveAction()));
        issue.setFollowUpAction(trimToNull(dto.getFollowUpAction()));
        issue.setSource(defaultIfBlank(dto.getSource(), "手动录入"));
        issue.setReuseTags(trimToNull(dto.getReuseTags()));
        issue.setKnowledgeIncluded(defaultKnowledgeIncluded(dto.getKnowledgeIncluded()));
        issue.setSourceType(normalizeSourceType(dto.getSourceType()));
        issue.setCurrentStatus(normalizeCurrentStatus(dto.getCurrentStatus(), dto.getLatestProgress(), dto.getCompletedAt()));
        issue.setClosureStatus(normalizeClosureStatus(issue.getCurrentStatus()));
        issue.setReminderEnabled(defaultFlag(dto.getReminderEnabled()));
        issue.setRemindAfterDays(defaultRemindDays(dto.getRemindAfterDays(), project.getRemindAfterDays()));
        issue.setCreatedBy(user.getId());
        issue.setUpdatedBy(user.getId());
        if (dto.getCreateTime() != null) {
            issue.setCreateTime(dto.getCreateTime());
        }
        issue.setDedupeKey(buildDedupeKey(issue.getProjectId(), issue.getReceivedAt(), issue.getItemTitle()));
        opsIssueMapper.insert(issue);
        assignIssueNo(issue);
        appendRecord(issue.getId(), "CREATED", null, issue.getCurrentStatus(), defaultIfBlank(issue.getLatestProgress(), "创建问题主单"), user.getUsername());
        syncKnowledgeState(issue);
        return toItemVO(requireIssue(issue.getId()), project);
    }

    @Transactional
    public IssueListItemVO updateIssue(Long issueId, IssueUpdateDTO dto) {
        currentUserAccessService.assertNotTemporary("编辑问题");
        currentUserAccessService.assertIssueAccess(issueId);
        OpsIssue issue = requireIssue(issueId);
        User user = currentUserAccessService.getCurrentUser();

        if (dto.getProjectId() != null && !Objects.equals(dto.getProjectId(), issue.getProjectId())) {
            assertLeafProject(dto.getProjectId());
            issue.setProjectId(dto.getProjectId());
        }
        if (dto.getReporterName() != null) {
            issue.setReporterName(trimToNull(dto.getReporterName()));
        }
        if (dto.getReceivedAt() != null) {
            issue.setReceivedAt(dto.getReceivedAt());
            issue.setFoundAt(dto.getReceivedAt());
        }
        if (dto.getItemTitle() != null && StringUtils.hasText(dto.getItemTitle())) {
            issue.setItemTitle(dto.getItemTitle().trim());
            if (!StringUtils.hasText(issue.getDescription())) {
                issue.setDescription(dto.getItemTitle().trim());
            }
        }
        if (dto.getDescription() != null && StringUtils.hasText(dto.getDescription())) {
            issue.setDescription(dto.getDescription().trim());
        }
        if (dto.getCategoryPath() != null) {
            issue.setCategoryPath(trimToNull(dto.getCategoryPath()));
        }
        if (dto.getBuildingName() != null) {
            issue.setBuildingName(trimToNull(dto.getBuildingName()));
        }
        if (dto.getFloorName() != null) {
            issue.setFloorName(trimToNull(dto.getFloorName()));
        }
        if (dto.getAreaName() != null) {
            issue.setAreaName(trimToNull(dto.getAreaName()));
        }
        if (dto.getSystemType() != null) {
            issue.setSystemType(trimToNull(dto.getSystemType()));
        }
        if (dto.getDevicePoint() != null) {
            issue.setDevicePoint(trimToNull(dto.getDevicePoint()));
        }
        if (dto.getCurrentStatus() != null || dto.getLatestProgress() != null || dto.getCompletedAt() != null) {
            String currentStatus = dto.getCurrentStatus() != null ? dto.getCurrentStatus() : issue.getCurrentStatus();
            String latestProgress = dto.getLatestProgress() != null ? dto.getLatestProgress() : issue.getLatestProgress();
            LocalDateTime completedAt = dto.getCompletedAt() != null ? dto.getCompletedAt() : issue.getCompletedAt();
            issue.setCurrentStatus(normalizeCurrentStatus(currentStatus, latestProgress, completedAt));
            issue.setClosureStatus(normalizeClosureStatus(issue.getCurrentStatus()));
        }
        if (dto.getImpactScope() != null) {
            issue.setImpactScope(trimToNull(dto.getImpactScope()));
        }
        if (dto.getSeverity() != null) {
            issue.setSeverity(trimToNull(dto.getSeverity()));
        }
        if (dto.getPriority() != null) {
            issue.setPriority(trimToNull(dto.getPriority()));
        }
        if (dto.getOwnerName() != null) {
            issue.setOwnerName(trimToNull(dto.getOwnerName()));
        }
        if (dto.getLatestProgress() != null) {
            issue.setLatestProgress(trimToNull(dto.getLatestProgress()));
        }
        if (dto.getCompletionStatus() != null) {
            issue.setCompletionStatus(trimToNull(dto.getCompletionStatus()));
        }
        if (dto.getCompletedAt() != null) {
            issue.setCompletedAt(dto.getCompletedAt());
        }
        if (dto.getNotes() != null) {
            issue.setNotes(trimToNull(dto.getNotes()));
        }
        if (dto.getInternalConclusion() != null) {
            issue.setInternalConclusion(trimToNull(dto.getInternalConclusion()));
        }
        if (dto.getCustomerFeedback() != null) {
            issue.setCustomerFeedback(trimToNull(dto.getCustomerFeedback()));
        }
        if (dto.getSource() != null) {
            issue.setSource(trimToNull(dto.getSource()));
        }
        if (dto.getSourceType() != null) {
            issue.setSourceType(normalizeSourceType(dto.getSourceType()));
        }
        if (dto.getCreateTime() != null) {
            issue.setCreateTime(dto.getCreateTime());
        }
        if (dto.getCauseCategory() != null) {
            issue.setCauseCategory(trimToNull(dto.getCauseCategory()));
        }
        if (dto.getCauseDetail() != null) {
            issue.setCauseDetail(trimToNull(dto.getCauseDetail()));
        }
        if (dto.getPreventiveAction() != null) {
            issue.setPreventiveAction(trimToNull(dto.getPreventiveAction()));
        }
        if (dto.getFollowUpAction() != null) {
            issue.setFollowUpAction(trimToNull(dto.getFollowUpAction()));
        }
        if (dto.getReuseTags() != null) {
            issue.setReuseTags(trimToNull(dto.getReuseTags()));
        }
        if (dto.getKnowledgeIncluded() != null) {
            issue.setKnowledgeIncluded(defaultKnowledgeIncluded(dto.getKnowledgeIncluded()));
        }
        if (dto.getReminderEnabled() != null) {
            issue.setReminderEnabled(defaultFlag(dto.getReminderEnabled()));
        }
        if (dto.getRemindAfterDays() != null) {
            issue.setRemindAfterDays(defaultRemindDays(dto.getRemindAfterDays(), issue.getRemindAfterDays()));
        }
        issue.setUpdatedBy(user.getId());
        issue.setDedupeKey(buildDedupeKey(issue.getProjectId(), firstNonNull(issue.getReceivedAt(), issue.getFoundAt()), issue.getItemTitle()));
        opsIssueMapper.updateById(issue);
        syncKnowledgeState(issue);
        return toItemVO(requireIssue(issueId), requireProject(issue.getProjectId()));
    }

    @Transactional
    public IssueRecordVO addRecord(Long issueId, IssueRecordCreateDTO dto) {
        currentUserAccessService.assertNotTemporary("追加处理记录");
        currentUserAccessService.assertIssueAccess(issueId);
        OpsIssue issue = requireIssue(issueId);
        User user = currentUserAccessService.getCurrentUser();
        String actionType = defaultIfBlank(dto.getActionType(), "FOLLOW_UP");
        String previousStatus = issue.getCurrentStatus();
        if (!"CLOSED".equals(issue.getCurrentStatus())) {
            issue.setCurrentStatus("IN_PROGRESS");
            issue.setClosureStatus("OPEN");
        }
        issue.setLatestProgress(dto.getContent().trim());
        issue.setUpdatedBy(user.getId());
        opsIssueMapper.updateById(issue);
        appendRecord(issueId, actionType, previousStatus, issue.getCurrentStatus(), dto.getContent().trim(), user.getUsername());
        return opsIssueRecordMapper.selectList(new LambdaQueryWrapper<OpsIssueRecord>()
                .eq(OpsIssueRecord::getIssueId, issueId)
                .orderByDesc(OpsIssueRecord::getOperateTime)
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .map(this::toRecordVO)
            .orElseThrow(() -> new IllegalArgumentException("处理记录创建失败"));
    }

    @Transactional
    public IssueListItemVO closeIssue(Long issueId, IssueCloseDTO dto) {
        currentUserAccessService.assertNotTemporary("关闭问题");
        currentUserAccessService.assertIssueAccess(issueId);
        OpsIssue issue = requireIssue(issueId);
        User user = currentUserAccessService.getCurrentUser();
        if (dto == null || !StringUtils.hasText(dto.getContent())) {
            throw new IllegalArgumentException("处理结论不能为空");
        }
        if (!StringUtils.hasText(dto.getCauseCategory())) {
            throw new IllegalArgumentException("归因分类不能为空");
        }
        if (!StringUtils.hasText(issue.getOwnerName()) || "未分配".equals(issue.getOwnerName())) {
            throw new IllegalArgumentException("\u5904\u7406\u4eba\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String previousStatus = issue.getCurrentStatus();
        issue.setCurrentStatus("CLOSED");
        issue.setClosureStatus("CLOSED");
        issue.setCompletedAt(dto.getCompletedAt() != null ? dto.getCompletedAt() : LocalDateTime.now());
        issue.setLatestProgress(dto.getContent().trim());
        issue.setInternalConclusion(dto.getContent().trim());
        issue.setCauseCategory(trimToNull(dto.getCauseCategory()));
        issue.setCauseDetail(trimToNull(dto.getCauseDetail()));
        issue.setCustomerFeedback(trimToNull(dto.getCustomerFeedback()));
        issue.setPreventiveAction(trimToNull(dto.getPreventiveAction()));
        issue.setFollowUpAction(trimToNull(dto.getFollowUpAction()));
        issue.setReuseTags(trimToNull(dto.getReuseTags()));
        issue.setKnowledgeIncluded(defaultKnowledgeIncluded(dto.getKnowledgeIncluded()));
        issue.setCompletionStatus(defaultIfBlank(issue.getCompletionStatus(), "已完成"));
        issue.setUpdatedBy(user.getId());
        opsIssueMapper.updateById(issue);
        appendRecord(issueId, "CLOSE", previousStatus, "CLOSED", dto.getContent().trim(), user.getUsername());
        syncKnowledgeState(issue);
        return toItemVO(requireIssue(issueId), requireProject(issue.getProjectId()));
    }

    @Transactional
    public IssueListItemVO reopenIssue(Long issueId, IssueReopenDTO dto) {
        currentUserAccessService.assertNotTemporary("重开问题");
        currentUserAccessService.assertIssueAccess(issueId);
        OpsIssue issue = requireIssue(issueId);
        User user = currentUserAccessService.getCurrentUser();
        String previousStatus = issue.getCurrentStatus();
        issue.setCurrentStatus("OPEN");
        issue.setClosureStatus("OPEN");
        issue.setCompletedAt(null);
        issue.setCompletionStatus("重新打开");
        issue.setLatestProgress(dto.getReason().trim());
        issue.setUpdatedBy(user.getId());
        opsIssueMapper.updateById(issue);
        appendRecord(issueId, "REOPEN", previousStatus, "OPEN", dto.getReason().trim(), user.getUsername());
        syncKnowledgeState(issue);
        return toItemVO(requireIssue(issueId), requireProject(issue.getProjectId()));
    }

    public List<IssueListItemVO> similarSearch(String keyword, Long projectId, Integer limit) {
        User user = currentUserAccessService.getCurrentUser();
        LambdaQueryWrapper<OpsIssue> wrapper = new LambdaQueryWrapper<OpsIssue>().orderByDesc(OpsIssue::getReceivedAt, OpsIssue::getFoundAt);
        if (projectId != null) {
            currentUserAccessService.assertProjectAccess(projectId);
            wrapper.eq(OpsIssue::getProjectId, projectId);
        } else if (!currentUserAccessService.canGlobalSearch(user)) {
            currentUserAccessService.applyIssueListScope(wrapper, user);
            if (currentUserAccessService.hasNoVisibleProjects(user)) {
                return List.of();
            }
        } else if (currentUserAccessService.isTemporary(user)) {
            wrapper.eq(OpsIssue::getCreatedBy, user.getId());
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query
                .like(OpsIssue::getItemTitle, trimmed)
                .or()
                .like(OpsIssue::getDescription, trimmed)
                .or()
                .like(OpsIssue::getLatestProgress, trimmed)
                .or()
                .like(OpsIssue::getCategoryPath, trimmed)
                .or()
                .like(OpsIssue::getCauseCategory, trimmed)
                .or()
                .like(OpsIssue::getPreventiveAction, trimmed)
                .or()
                .like(OpsIssue::getReuseTags, trimmed));
        }
        List<OpsIssue> issues = opsIssueMapper.selectList(wrapper);
        Map<Long, Project> projectMap = loadProjectMap(issues.stream().map(OpsIssue::getProjectId).collect(Collectors.toSet()));
        return issues.stream()
            .limit(limit == null || limit < 1 ? 10 : limit)
            .map(issue -> toItemVO(issue, projectMap.get(issue.getProjectId())))
            .toList();
    }

    public String buildDedupeKey(Long projectId, LocalDateTime receivedAt, String itemTitle) {
        String datePart = receivedAt == null ? "unknown" : receivedAt.toLocalDate().toString();
        String prefix = projectId + "|" + datePart;
        return buildBoundedDedupeKey(prefix, normalizeDedupeText(itemTitle));
    }

    public String buildImportDedupeKey(Long projectId, String sheetName, Integer rowNumber, LocalDateTime receivedAt, String itemTitle) {
        String datePart = receivedAt == null ? "unknown" : receivedAt.toLocalDate().toString();
        String sheetPart = abbreviate(normalizeDedupeText(sheetName), MAX_SHEET_SEGMENT_LENGTH);
        String prefix = projectId + "|" + defaultIfBlank(sheetPart, "sheet") + "|" + (rowNumber == null ? 0 : rowNumber) + "|" + datePart;
        return buildBoundedDedupeKey(prefix, normalizeDedupeText(itemTitle));
    }

    private List<IssueListItemVO> findSimilarIssuesInternal(OpsIssue issue, int limit) {
        User user = currentUserAccessService.getCurrentUser();
        LambdaQueryWrapper<OpsIssue> wrapper = new LambdaQueryWrapper<OpsIssue>()
            .ne(OpsIssue::getId, issue.getId())
            .orderByDesc(OpsIssue::getReceivedAt, OpsIssue::getFoundAt);
        if (!currentUserAccessService.canGlobalSearch(user)) {
            currentUserAccessService.applyIssueListScope(wrapper, user);
            if (currentUserAccessService.hasNoVisibleProjects(user)) {
                return List.of();
            }
        } else if (currentUserAccessService.isTemporary(user)) {
            wrapper.eq(OpsIssue::getCreatedBy, user.getId());
        }
        List<OpsIssue> similar = opsIssueMapper.selectList(wrapper);
        Map<Long, Project> projectMap = loadProjectMap(similar.stream().map(OpsIssue::getProjectId).collect(Collectors.toSet()));
        return similar.stream()
            .map(candidate -> scoreSimilarIssue(issue, candidate, projectMap.get(candidate.getProjectId())))
            .filter(candidate -> candidate.score() > 0)
            .sorted(Comparator.comparingInt(SimilarIssueCandidate::score).reversed()
                .thenComparing(candidate -> firstNonNull(candidate.issue().getReceivedAt(), candidate.issue().getFoundAt()), Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit)
            .map(candidate -> toItemVO(candidate.issue(), candidate.project(), candidate.reasons()))
            .toList();
    }

    private SimilarIssueCandidate scoreSimilarIssue(OpsIssue source, OpsIssue candidate, Project project) {
        int score = 0;
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (Objects.equals(source.getProjectId(), candidate.getProjectId())) {
            score += 20;
            reasons.add("同项目");
        }
        if (sameText(source.getCategoryPath(), candidate.getCategoryPath())) {
            score += Objects.equals(source.getProjectId(), candidate.getProjectId()) ? 40 : 30;
            reasons.add("同分类");
        }
        if (keywordHit(source.getItemTitle(), candidate.getItemTitle()) || keywordHit(source.getDescription(), candidate.getDescription())) {
            score += 18;
            reasons.add("标题/现象命中");
        }
        if (sameText(source.getCauseCategory(), candidate.getCauseCategory())) {
            score += 18;
            reasons.add("原因相同");
        }
        int tagHits = countTagHits(source.getReuseTags(), candidate.getReuseTags());
        if (tagHits > 0) {
            score += 16 + tagHits * 4;
            reasons.add("标签命中");
        }
        if (keywordHit(source.getLatestProgress(), candidate.getLatestProgress())
            || keywordHit(source.getPreventiveAction(), candidate.getPreventiveAction())
            || keywordHit(source.getFollowUpAction(), candidate.getFollowUpAction())) {
            score += 12;
            reasons.add("结论/预防命中");
        }
        return new SimilarIssueCandidate(candidate, project, score, new ArrayList<>(reasons));
    }

    private void appendRecord(Long issueId, String actionType, String fromStatus, String toStatus, String content, String operatorName) {
        OpsIssueRecord record = new OpsIssueRecord();
        record.setIssueId(issueId);
        record.setActionType(actionType);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setContent(content);
        record.setOperatorName(operatorName);
        record.setOperateTime(LocalDateTime.now());
        opsIssueRecordMapper.insert(record);
    }

    private void syncKnowledgeState(OpsIssue issue) {
        if ("CLOSED".equals(issue.getClosureStatus()) && Objects.equals(defaultKnowledgeIncluded(issue.getKnowledgeIncluded()), 1)) {
            knowledgeService.syncFromIssue(issue);
        } else {
            knowledgeService.disableByIssueId(issue.getId());
        }
    }

    private void assignIssueNo(OpsIssue issue) {
        if (issue.getId() == null) {
            return;
        }
        String issueNo = "ISS-" + ISSUE_NO_DATE_FORMATTER.format(LocalDateTime.now()) + "-" + String.format("%05d", issue.getId());
        issue.setIssueNo(issueNo);
        opsIssueMapper.updateById(issue);
    }

    private void assertLeafProject(Long projectId) {
        Project project = requireProject(projectId);
        if (StringUtils.hasText(project.getProjectLevel()) && !"PROJECT".equalsIgnoreCase(project.getProjectLevel())) {
            throw new IllegalArgumentException("问题主单必须绑定到最细一级项目");
        }
        if (Objects.equals(project.getIsActive(), 0)) {
            throw new IllegalArgumentException("已禁用项目不能新建或改绑问题");
        }
        currentUserAccessService.assertProjectAccess(projectId);
    }

    private OpsIssue requireIssue(Long issueId) {
        OpsIssue issue = opsIssueMapper.selectById(issueId);
        if (issue == null) {
            throw new IllegalArgumentException("问题不存在");
        }
        return issue;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        return project;
    }

    private Map<Long, Project> loadProjectMap(Set<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return projectMapper.selectBatchIds(projectIds).stream()
            .collect(Collectors.toMap(Project::getId, project -> project, (left, right) -> left, LinkedHashMap::new));
    }

    private PageResultVO<IssueListItemVO> paginate(List<IssueListItemVO> items, long page, long pageSize) {
        long safePage = page < 1 ? 1 : page;
        long safePageSize = pageSize < 1 ? 20 : pageSize;
        int fromIndex = (int) Math.min(items.size(), (safePage - 1) * safePageSize);
        int toIndex = (int) Math.min(items.size(), fromIndex + safePageSize);
        return new PageResultVO<>(items.size(), safePage, safePageSize, items.subList(fromIndex, toIndex));
    }

    private IssueListItemVO toItemVO(OpsIssue issue, Project project) {
        return toItemVO(issue, project, List.of());
    }

    private IssueListItemVO toItemVO(OpsIssue issue, Project project, List<String> matchReasons) {
        Project safeProject = project == null ? new Project() : project;
        return IssueListItemVO.builder()
            .id(issue.getId())
            .issueNo(defaultIfBlank(issue.getIssueNo(), fallbackIssueNo(issue.getId())))
            .projectId(issue.getProjectId())
            .customerName(safeProject.getCustomerName())
            .projectGroup(safeProject.getProjectGroup())
            .projectName(defaultIfBlank(safeProject.getProjectName(), "未命名项目"))
            .projectCode(safeProject.getProjectCode())
            .source(issue.getSource())
            .sourceType(defaultIfBlank(issue.getSourceType(), "MANUAL"))
            .reporterName(issue.getReporterName())
            .receivedAt(firstNonNull(issue.getReceivedAt(), issue.getFoundAt()))
            .itemTitle(defaultIfBlank(issue.getItemTitle(), issue.getDescription()))
            .description(defaultIfBlank(issue.getDescription(), issue.getItemTitle()))
            .categoryPath(issue.getCategoryPath())
            .buildingName(issue.getBuildingName())
            .floorName(issue.getFloorName())
            .areaName(issue.getAreaName())
            .systemType(issue.getSystemType())
            .devicePoint(issue.getDevicePoint())
            .foundAt(issue.getFoundAt())
            .impactScope(issue.getImpactScope())
            .priority(issue.getPriority())
            .severity(issue.getSeverity())
            .currentStatus(issue.getCurrentStatus())
            .closureStatus(issue.getClosureStatus())
            .ownerName(issue.getOwnerName())
            .latestProgress(issue.getLatestProgress())
            .completionStatus(issue.getCompletionStatus())
            .customerFeedback(issue.getCustomerFeedback())
            .causeCategory(issue.getCauseCategory())
            .preventiveAction(issue.getPreventiveAction())
            .reuseTags(issue.getReuseTags())
            .knowledgeIncluded(defaultKnowledgeIncluded(issue.getKnowledgeIncluded()))
            .completedAt(issue.getCompletedAt())
            .createTime(issue.getCreateTime())
            .updateTime(issue.getUpdateTime())
            .overdue(isOverdue(issue))
            .remindAfterDays(issue.getRemindAfterDays())
            .matchReasons(matchReasons)
            .build();
    }

    private IssueRecordVO toRecordVO(OpsIssueRecord record) {
        return IssueRecordVO.builder()
            .id(record.getId())
            .actionType(record.getActionType())
            .fromStatus(record.getFromStatus())
            .toStatus(record.getToStatus())
            .content(record.getContent())
            .operatorName(record.getOperatorName())
            .operateTime(record.getOperateTime())
            .build();
    }

    private boolean isOverdue(OpsIssue issue) {
        if (!Objects.equals(issue.getReminderEnabled(), 1)
            || "CLOSED".equals(issue.getClosureStatus())
            || firstNonNull(issue.getReceivedAt(), issue.getFoundAt()) == null) {
            return false;
        }
        int remindAfterDays = issue.getRemindAfterDays() == null || issue.getRemindAfterDays() < 1 ? 7 : issue.getRemindAfterDays();
        return firstNonNull(issue.getReceivedAt(), issue.getFoundAt()).plusDays(remindAfterDays).isBefore(LocalDateTime.now());
    }

    private Integer defaultFlag(Integer value) {
        return Objects.equals(value, 0) ? 0 : 1;
    }

    private Integer defaultKnowledgeIncluded(Integer value) {
        return Objects.equals(value, 0) ? 0 : 1;
    }

    private Integer defaultRemindDays(Integer value, Integer fallback) {
        if (value != null && value > 0) {
            return value;
        }
        if (fallback != null && fallback > 0) {
            return fallback;
        }
        return 7;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean keywordHit(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return false;
        }
        String normalizedSource = normalizeDedupeText(source);
        String normalizedTarget = normalizeDedupeText(target);
        if (normalizedSource.length() < 3 || normalizedTarget.length() < 3) {
            return false;
        }
        String seed = normalizedSource.length() > 12 ? normalizedSource.substring(0, 12) : normalizedSource;
        return normalizedTarget.contains(seed) || normalizedSource.contains(normalizedTarget.length() > 12 ? normalizedTarget.substring(0, 12) : normalizedTarget);
    }

    private int countTagHits(String sourceTags, String targetTags) {
        if (!StringUtils.hasText(sourceTags) || !StringUtils.hasText(targetTags)) {
            return 0;
        }
        Set<String> sourceSet = splitTags(sourceTags);
        Set<String> targetSet = splitTags(targetTags);
        sourceSet.retainAll(targetSet);
        return sourceSet.size();
    }

    private Set<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Collections.emptySet();
        }
        return java.util.Arrays.stream(tags.split("[,，\\s]+"))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String fallbackIssueNo(Long id) {
        return id == null ? "-" : "ISS-" + String.format("%05d", id);
    }

    private LocalDateTime firstNonNull(LocalDateTime primary, LocalDateTime fallback) {
        return primary != null ? primary : fallback;
    }

    private String normalizeSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return "MANUAL";
        }
        String value = sourceType.trim().toUpperCase(Locale.ROOT);
        if ("EXCEL".equals(value) || "MANUAL".equals(value)) {
            return value;
        }
        return "MANUAL";
    }

    private String normalizeCurrentStatus(String currentStatus, String latestProgress, LocalDateTime completedAt) {
        if (completedAt != null) {
            return "CLOSED";
        }
        if (StringUtils.hasText(currentStatus)) {
            String value = currentStatus.trim().toUpperCase(Locale.ROOT);
            if ("OPEN".equals(value) || "IN_PROGRESS".equals(value) || "PENDING_CONFIRM".equals(value) || "SUSPENDED".equals(value) || "CLOSED".equals(value)) {
                return value;
            }
        }
        if (StringUtils.hasText(latestProgress)) {
            return "IN_PROGRESS";
        }
        return "OPEN";
    }

    private String normalizeClosureStatus(String currentStatus) {
        return "CLOSED".equals(currentStatus) ? "CLOSED" : "OPEN";
    }

    private String buildBoundedDedupeKey(String prefix, String normalizedText) {
        String base = prefix + "|" + normalizedText;
        if (base.length() <= MAX_DEDUPE_KEY_LENGTH) {
            return base;
        }
        String hash = sha256(base);
        int remaining = MAX_DEDUPE_KEY_LENGTH - prefix.length() - hash.length() - 2;
        String abbreviatedText = remaining > 0 ? abbreviate(normalizedText, remaining) : "";
        return prefix + "|" + abbreviatedText + "|" + hash;
    }

    private String normalizeDedupeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", "");
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private record SimilarIssueCandidate(OpsIssue issue, Project project, int score, List<String> reasons) {
    }
}
