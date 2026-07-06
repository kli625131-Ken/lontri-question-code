package com.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.problem.dto.ImportRowReviewUpdateDTO;
import com.problem.entity.OpsCategoryDict;
import com.problem.entity.OpsImportBatch;
import com.problem.entity.OpsImportRowReview;
import com.problem.entity.OpsIssue;
import com.problem.entity.OpsIssueRecord;
import com.problem.entity.OpsProjectContact;
import com.problem.entity.OpsProjectWarranty;
import com.problem.entity.Project;
import com.problem.entity.User;
import com.problem.entity.UserProject;
import com.problem.mapper.OpsCategoryDictMapper;
import com.problem.mapper.OpsImportBatchMapper;
import com.problem.mapper.OpsImportRowReviewMapper;
import com.problem.mapper.OpsIssueMapper;
import com.problem.mapper.OpsIssueRecordMapper;
import com.problem.mapper.OpsProjectContactMapper;
import com.problem.mapper.OpsProjectWarrantyMapper;
import com.problem.mapper.ProjectMapper;
import com.problem.mapper.UserMapper;
import com.problem.mapper.UserProjectMapper;
import com.problem.support.CurrentUserAccessService;
import com.problem.support.ExcelImportParser;
import com.problem.vo.ImportBatchDetailVO;
import com.problem.vo.ImportBatchVO;
import com.problem.vo.ImportRowReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISSUE_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OpsImportBatchMapper opsImportBatchMapper;
    private final OpsImportRowReviewMapper opsImportRowReviewMapper;
    private final OpsIssueMapper opsIssueMapper;
    private final OpsIssueRecordMapper opsIssueRecordMapper;
    private final OpsProjectContactMapper opsProjectContactMapper;
    private final OpsProjectWarrantyMapper opsProjectWarrantyMapper;
    private final OpsCategoryDictMapper opsCategoryDictMapper;
    private final ProjectMapper projectMapper;
    private final UserProjectMapper userProjectMapper;
    private final UserMapper userMapper;
    private final CurrentUserAccessService currentUserAccessService;
    private final ExcelImportParser excelImportParser;
    private final IssueService issueService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ImportBatchDetailVO previewExcel(MultipartFile file, int defaultRemindAfterDays) {
        currentUserAccessService.assertNotTemporary("使用导入中心");
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        User operator = currentUserAccessService.getCurrentUser();
        try (InputStream inputStream = file.getInputStream()) {
            OpsImportBatch batch = createBatch(file.getOriginalFilename(), inputStream, operator, defaultRemindAfterDays);
            return getBatchDetail(batch.getId());
        } catch (Exception e) {
            throw new IllegalArgumentException("导入预览失败：" + e.getMessage(), e);
        }
    }

    public List<ImportBatchVO> listBatches() {
        currentUserAccessService.assertNotTemporary("使用导入中心");
        return opsImportBatchMapper.selectList(new LambdaQueryWrapper<OpsImportBatch>().orderByDesc(OpsImportBatch::getCreateTime))
            .stream()
            .map(this::toBatchVO)
            .toList();
    }

    public ImportBatchDetailVO getBatchDetail(Long batchId) {
        currentUserAccessService.assertNotTemporary("使用导入中心");
        OpsImportBatch batch = requireBatch(batchId);
        List<ImportRowReviewVO> rows = opsImportRowReviewMapper.selectList(new LambdaQueryWrapper<OpsImportRowReview>()
                .eq(OpsImportRowReview::getBatchId, batchId)
                .orderByAsc(OpsImportRowReview::getSheetName, OpsImportRowReview::getRowNumber))
            .stream()
            .map(this::toRowVO)
            .toList();
        return ImportBatchDetailVO.builder()
            .batch(toBatchVO(batch))
            .rows(rows)
            .build();
    }

    @Transactional
    public ImportRowReviewVO updateRow(Long batchId, Long rowId, ImportRowReviewUpdateDTO dto) {
        currentUserAccessService.assertNotTemporary("使用导入中心");
        OpsImportRowReview row = requireRow(batchId, rowId);
        if (dto.getNormalizedData() != null) {
            row.setNormalizedData(writeJson(dto.getNormalizedData()));
        }
        row.setReviewStatus(StringUtils.hasText(dto.getReviewStatus()) ? dto.getReviewStatus().trim() : "CONFIRMED");
        if (StringUtils.hasText(dto.getReviewMessage())) {
            row.setReviewMessage(dto.getReviewMessage().trim());
        } else if (!"NEEDS_REVIEW".equals(row.getReviewStatus())) {
            row.setReviewMessage("");
        }
        opsImportRowReviewMapper.updateById(row);
        refreshBatchStatus(batchId);
        return toRowVO(requireRow(batchId, rowId));
    }

    @Transactional
    public ImportBatchDetailVO commitBatch(Long batchId) {
        currentUserAccessService.assertNotTemporary("使用导入中心");
        User operator = currentUserAccessService.getCurrentUser();
        commitBatchInternal(batchId, operator);
        return getBatchDetail(batchId);
    }

    @Transactional
    public void bootstrapFromFile(Path filePath, int defaultRemindAfterDays) {
        if (filePath == null || !Files.exists(filePath)) {
            return;
        }
        Long issueCount = opsIssueMapper.selectCount(new LambdaQueryWrapper<>());
        Long batchCount = opsImportBatchMapper.selectCount(new LambdaQueryWrapper<>());
        if ((issueCount != null && issueCount > 0) || (batchCount != null && batchCount > 0)) {
            return;
        }
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, "admin")
            .last("LIMIT 1"));
        if (admin == null) {
            return;
        }
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            OpsImportBatch batch = createBatch(filePath.getFileName().toString(), inputStream, admin, defaultRemindAfterDays);
            commitBatchInternal(batch.getId(), admin);
        } catch (Exception e) {
            throw new IllegalArgumentException("初始化历史 Excel 数据失败：" + e.getMessage(), e);
        }
    }

    @Transactional
    public void resetOperationalData() {
        opsIssueRecordMapper.delete(new LambdaQueryWrapper<>());
        opsIssueMapper.delete(new LambdaQueryWrapper<>());
        opsImportRowReviewMapper.delete(new LambdaQueryWrapper<>());
        opsImportBatchMapper.delete(new LambdaQueryWrapper<>());
        opsProjectContactMapper.delete(new LambdaQueryWrapper<>());
        opsProjectWarrantyMapper.delete(new LambdaQueryWrapper<>());
        opsCategoryDictMapper.delete(new LambdaQueryWrapper<>());
        userProjectMapper.delete(new LambdaQueryWrapper<>());
        projectMapper.delete(new LambdaQueryWrapper<>());
    }

    private OpsImportBatch createBatch(String fileName, InputStream inputStream, User operator, int defaultRemindAfterDays) {
        ExcelImportParser.ParsedWorkbook parsedWorkbook = excelImportParser.parse(inputStream, defaultRemindAfterDays);
        List<ExcelImportParser.ParsedRow> parsedRows = parsedWorkbook.rows();
        Map<String, Object> summary = buildSummary(parsedWorkbook);

        OpsImportBatch batch = new OpsImportBatch();
        batch.setOriginalFileName(fileName);
        batch.setBatchStatus(parsedRows.stream().anyMatch(row -> "NEEDS_REVIEW".equals(row.reviewStatus())) ? "PENDING_REVIEW" : "READY_TO_COMMIT");
        batch.setTotalRows(parsedRows.size());
        batch.setReviewRows((int) parsedRows.stream().filter(row -> "NEEDS_REVIEW".equals(row.reviewStatus())).count());
        batch.setCommittedRows(0);
        batch.setSkippedRows(0);
        batch.setSummaryJson(writeJson(summary));
        batch.setCreatedBy(operator.getId());
        batch.setCreatedByName(operator.getUsername());
        opsImportBatchMapper.insert(batch);

        for (ExcelImportParser.ParsedRow parsedRow : parsedRows) {
            OpsImportRowReview row = new OpsImportRowReview();
            row.setBatchId(batch.getId());
            row.setSheetName(parsedRow.sheetName());
            row.setRowNumber(parsedRow.rowNumber());
            row.setRowType(parsedRow.rowType());
            row.setReviewStatus(parsedRow.reviewStatus());
            row.setReviewMessage(parsedRow.reviewMessage());
            row.setCommitStatus("PENDING");
            row.setNormalizedData(writeJson(parsedRow.normalizedData()));
            row.setRawData(writeJson(parsedRow.rawData()));
            opsImportRowReviewMapper.insert(row);
        }
        return batch;
    }

    private void commitBatchInternal(Long batchId, User operator) {
        OpsImportBatch batch = requireBatch(batchId);
        List<OpsImportRowReview> rows = opsImportRowReviewMapper.selectList(new LambdaQueryWrapper<OpsImportRowReview>()
            .eq(OpsImportRowReview::getBatchId, batchId)
            .orderByAsc(OpsImportRowReview::getSheetName, OpsImportRowReview::getRowNumber));

        int committed = 0;
        int skipped = 0;
        for (OpsImportRowReview row : rows) {
            if ("COMMITTED".equals(row.getCommitStatus()) || "SKIPPED".equals(row.getCommitStatus())) {
                continue;
            }
            if ("NEEDS_REVIEW".equals(row.getReviewStatus())) {
                continue;
            }
            boolean imported = importRow(row, operator);
            row.setCommitStatus(imported ? "COMMITTED" : "SKIPPED");
            opsImportRowReviewMapper.updateById(row);
            if (imported) {
                committed++;
            } else {
                skipped++;
            }
        }

        batch.setCommittedRows((batch.getCommittedRows() == null ? 0 : batch.getCommittedRows()) + committed);
        batch.setSkippedRows((batch.getSkippedRows() == null ? 0 : batch.getSkippedRows()) + skipped);
        refreshBatchStatus(batchId);
    }

    private boolean importRow(OpsImportRowReview row, User operator) {
        Map<String, Object> normalized = readJsonMap(row.getNormalizedData());
        Map<String, Object> raw = readJsonMap(row.getRawData());
        return switch (row.getRowType()) {
            case "ISSUE" -> importIssue(row, normalized, raw, operator);
            case "CONTACT" -> importContact(row, normalized, operator);
            case "WARRANTY" -> importWarranty(normalized, operator);
            case "CATEGORY" -> importCategory(normalized);
            default -> false;
        };
    }

    private boolean importIssue(OpsImportRowReview row, Map<String, Object> normalized, Map<String, Object> raw, User operator) {
        String projectName = asString(normalized.get("projectName"));
        String itemTitle = firstNonBlank(asString(normalized.get("itemTitle")), asString(normalized.get("description")), readRawValue(raw, "事项"));
        LocalDateTime receivedAt = firstNonNull(asDateTime(normalized.get("receivedAt")), asDateTime(normalized.get("foundAt")));
        if (!StringUtils.hasText(projectName) || !StringUtils.hasText(itemTitle) || receivedAt == null) {
            row.setReviewStatus("NEEDS_REVIEW");
            row.setReviewMessage("问题主单缺少必要字段，请补充后再提交");
            return false;
        }
        Project project = findOrCreateProject(projectName, operator);
        String dedupeKey = issueService.buildImportDedupeKey(project.getId(), row.getSheetName(), row.getRowNumber(), receivedAt, itemTitle);
        OpsIssue existing = opsIssueMapper.selectOne(new LambdaQueryWrapper<OpsIssue>()
            .eq(OpsIssue::getDedupeKey, dedupeKey)
            .last("LIMIT 1"));
        if (existing != null) {
            row.setReviewMessage(appendMessage(row.getReviewMessage(), "检测到重复记录，已跳过"));
            return false;
        }

        OpsIssue issue = new OpsIssue();
        issue.setProjectId(project.getId());
        issue.setSource(defaultIfBlank(asString(normalized.get("source")), "Excel/CSV 导入"));
        issue.setSourceType(defaultIfBlank(asString(normalized.get("sourceType")), "EXCEL").toUpperCase(Locale.ROOT));
        issue.setSourceBatchId(row.getBatchId());
        issue.setSourceSheet(row.getSheetName());
        issue.setSourceRowNumber(row.getRowNumber());
        issue.setReporterName(firstNonBlank(asString(normalized.get("reporterName")), readRawValue(raw, "反馈人")));
        issue.setReceivedAt(receivedAt);
        issue.setCategoryPath(defaultIfBlank(asString(normalized.get("categoryPath")), "待确认问题"));
        issue.setBuildingName(defaultIfBlank(asString(normalized.get("buildingName")), "未确认"));
        issue.setFloorName(defaultIfBlank(asString(normalized.get("floorName")), "未确认"));
        issue.setAreaName(defaultIfBlank(asString(normalized.get("areaName")), "未确认"));
        issue.setSystemType(defaultIfBlank(asString(normalized.get("systemType")), "未确认"));
        issue.setDevicePoint(defaultIfBlank(asString(normalized.get("devicePoint")), "未确认"));
        issue.setFoundAt(receivedAt);
        issue.setItemTitle(itemTitle);
        issue.setDescription(defaultIfBlank(asString(normalized.get("description")), itemTitle));
        issue.setImpactScope(trimToNull(asString(normalized.get("impactScope"))));
        issue.setSeverity(trimToNull(asString(normalized.get("severity"))));
        issue.setPriority(defaultIfBlank(asString(normalized.get("priority")), "中"));
        issue.setCurrentStatus(defaultIfBlank(asString(normalized.get("currentStatus")), "OPEN").toUpperCase(Locale.ROOT));
        issue.setClosureStatus(defaultIfBlank(asString(normalized.get("closureStatus")), "OPEN").toUpperCase(Locale.ROOT));
        issue.setOwnerName(defaultIfBlank(asString(normalized.get("ownerName")), "未分配"));
        issue.setLatestProgress(trimToNull(asString(normalized.get("latestProgress"))));
        issue.setCompletionStatus(trimToNull(firstNonBlank(asString(normalized.get("completionStatus")), readRawValue(raw, "完成情况"))));
        issue.setCompletedAt(asDateTime(normalized.get("completedAt")));
        issue.setNotes(trimToNull(asString(normalized.get("notes"))));
        issue.setCauseCategory(defaultIfBlank(asString(normalized.get("causeCategory")), "原因待确认"));
        issue.setCustomerFeedback(trimToNull(asString(normalized.get("customerFeedback"))));
        issue.setReuseTags(trimToNull(asString(normalized.get("reuseTags"))));
        issue.setRawSnapshot(row.getRawData());
        issue.setDedupeKey(dedupeKey);
        issue.setReminderEnabled(asInteger(normalized.get("reminderEnabled"), 1));
        issue.setRemindAfterDays(asInteger(normalized.get("remindAfterDays"), project.getRemindAfterDays() == null ? 7 : project.getRemindAfterDays()));
        issue.setKnowledgeIncluded(1);
        issue.setCreatedBy(operator.getId());
        issue.setUpdatedBy(operator.getId());
        opsIssueMapper.insert(issue);
        issue.setIssueNo(buildIssueNo(issue.getId()));
        opsIssueMapper.updateById(issue);

        OpsIssueRecord record = new OpsIssueRecord();
        record.setIssueId(issue.getId());
        record.setActionType("IMPORTED");
        record.setFromStatus(null);
        record.setToStatus(issue.getCurrentStatus());
        record.setContent(defaultIfBlank(issue.getLatestProgress(), "由 Excel 历史台账导入"));
        record.setOperatorName(operator.getUsername());
        record.setOperateTime(LocalDateTime.now());
        opsIssueRecordMapper.insert(record);
        return true;
    }

    private boolean importContact(OpsImportRowReview row, Map<String, Object> normalized, User operator) {
        String projectName = asString(normalized.get("projectName"));
        if (!StringUtils.hasText(projectName)) {
            return false;
        }
        Project project = findOrCreateProject(projectName, operator);
        String contactName = asString(normalized.get("contactName"));
        OpsProjectContact existing = opsProjectContactMapper.selectOne(new LambdaQueryWrapper<OpsProjectContact>()
            .eq(OpsProjectContact::getProjectId, project.getId())
            .eq(OpsProjectContact::getContactName, contactName)
            .eq(OpsProjectContact::getPositionTitle, asString(normalized.get("positionTitle")))
            .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }
        OpsProjectContact contact = new OpsProjectContact();
        contact.setProjectId(project.getId());
        contact.setPositionTitle(trimToNull(asString(normalized.get("positionTitle"))));
        contact.setContactName(trimToNull(contactName));
        contact.setContactInfo(trimToNull(asString(normalized.get("contactInfo"))));
        contact.setResponsibility(trimToNull(asString(normalized.get("responsibility"))));
        contact.setNotes(trimToNull(asString(normalized.get("notes"))));
        contact.setSourceSheet(row.getSheetName());
        contact.setSourceRowNumber(row.getRowNumber());
        contact.setRawSnapshot(row.getRawData());
        opsProjectContactMapper.insert(contact);
        return true;
    }

    private boolean importWarranty(Map<String, Object> normalized, User operator) {
        String projectName = asString(normalized.get("projectName"));
        if (!StringUtils.hasText(projectName)) {
            return false;
        }
        Project project = findOrCreateProject(projectName, asString(normalized.get("customerName")), operator);
        String contractType = defaultIfBlank(asString(normalized.get("contractType")), "质保");
        LocalDateTime startAt = firstNonNull(asDateTime(normalized.get("startAt")), asDateTime(normalized.get("acceptanceAt")));
        LocalDateTime endAt = firstNonNull(asDateTime(normalized.get("endAt")), asDateTime(normalized.get("expireAt")));
        String warrantyTerm = trimToNull(asString(normalized.get("warrantyTerm")));
        OpsProjectWarranty existing = findExistingWarranty(project.getId(), contractType, startAt, endAt, warrantyTerm);
        if (existing != null) {
            existing.setContractType(contractType);
            existing.setStartAt(startAt);
            existing.setEndAt(endAt);
            existing.setServiceScope(trimToNull(asString(normalized.get("serviceScope"))));
            existing.setContractSignedAt(asDateTime(normalized.get("contractSignedAt")));
            existing.setAcceptanceAt(asDateTime(normalized.get("acceptanceAt")));
            existing.setWarrantyTerm(warrantyTerm);
            existing.setExpireAt(endAt);
            existing.setNotes(trimToNull(asString(normalized.get("notes"))));
            opsProjectWarrantyMapper.updateById(existing);
            return true;
        }
        OpsProjectWarranty warranty = new OpsProjectWarranty();
        warranty.setProjectId(project.getId());
        warranty.setContractType(contractType);
        warranty.setStartAt(startAt);
        warranty.setEndAt(endAt);
        warranty.setServiceScope(trimToNull(asString(normalized.get("serviceScope"))));
        warranty.setContractSignedAt(asDateTime(normalized.get("contractSignedAt")));
        warranty.setAcceptanceAt(asDateTime(normalized.get("acceptanceAt")));
        warranty.setWarrantyTerm(warrantyTerm);
        warranty.setExpireAt(endAt);
        warranty.setNotes(trimToNull(asString(normalized.get("notes"))));
        opsProjectWarrantyMapper.insert(warranty);
        return true;
    }

    private OpsProjectWarranty findExistingWarranty(Long projectId, String contractType, LocalDateTime startAt, LocalDateTime endAt, String warrantyTerm) {
        return opsProjectWarrantyMapper.selectList(new LambdaQueryWrapper<OpsProjectWarranty>()
                .eq(OpsProjectWarranty::getProjectId, projectId))
            .stream()
            .filter(warranty -> Objects.equals(defaultIfBlank(warranty.getContractType(), "质保"), contractType))
            .filter(warranty -> Objects.equals(warranty.getStartAt(), startAt) || (warranty.getStartAt() == null && startAt == null))
            .filter(warranty -> Objects.equals(warranty.getEndAt(), endAt) || (warranty.getEndAt() == null && endAt == null))
            .filter(warranty -> StringUtils.hasText(warrantyTerm) ? Objects.equals(warranty.getWarrantyTerm(), warrantyTerm) || warranty.getWarrantyTerm() == null : true)
            .findFirst()
            .orElse(null);
    }

    private boolean importCategory(Map<String, Object> normalized) {
        OpsCategoryDict existing = opsCategoryDictMapper.selectOne(new LambdaQueryWrapper<OpsCategoryDict>()
            .eq(OpsCategoryDict::getLevel1, asString(normalized.get("level1")))
            .eq(OpsCategoryDict::getLevel2, asString(normalized.get("level2")))
            .eq(OpsCategoryDict::getLevel3, asString(normalized.get("level3")))
            .last("LIMIT 1"));
        if (existing != null) {
            existing.setProblemDescription(trimToNull(asString(normalized.get("problemDescription"))));
            existing.setExampleCase(trimToNull(asString(normalized.get("exampleCase"))));
            opsCategoryDictMapper.updateById(existing);
            return true;
        }
        OpsCategoryDict category = new OpsCategoryDict();
        category.setLevel1(trimToNull(asString(normalized.get("level1"))));
        category.setLevel2(trimToNull(asString(normalized.get("level2"))));
        category.setLevel3(trimToNull(asString(normalized.get("level3"))));
        category.setProblemDescription(trimToNull(asString(normalized.get("problemDescription"))));
        category.setExampleCase(trimToNull(asString(normalized.get("exampleCase"))));
        opsCategoryDictMapper.insert(category);
        return true;
    }

    private Project findOrCreateProject(String projectName, User operator) {
        return findOrCreateProject(projectName, "", operator);
    }

    private Project findOrCreateProject(String projectName, String customerNameOverride, User operator) {
        ProjectIdentity identity = normalizeProjectIdentity(projectName);
        if (StringUtils.hasText(customerNameOverride)) {
            identity = new ProjectIdentity(customerNameOverride.trim(), customerNameOverride.trim(), identity.projectName());
        }
        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
            .eq(Project::getProjectName, identity.projectName())
            .last("LIMIT 1"));
        String customerName = identity.customerName();
        String projectGroup = identity.projectGroup();
        String parentProjectCode = buildProjectCode("GRP", customerName + "-" + projectGroup);
        if (project == null) {
            project = new Project();
            project.setProjectName(identity.projectName());
            project.setProjectCode(buildProjectCode("PRJ", identity.projectName()));
            project.setCustomerName(customerName);
            project.setProjectGroup(projectGroup);
            project.setParentProjectCode(parentProjectCode);
            project.setProjectLevel("PROJECT");
            project.setDescription("由 Excel 样板台账自动生成");
            project.setReminderEnabled(1);
            project.setRemindAfterDays(7);
            project.setIsActive(1);
            projectMapper.insert(project);
        } else {
            project.setCustomerName(customerName);
            project.setProjectGroup(projectGroup);
            project.setParentProjectCode(parentProjectCode);
            project.setProjectLevel(defaultIfBlank(project.getProjectLevel(), "PROJECT"));
            project.setIsActive(1);
            projectMapper.updateById(project);
        }
        grantProjectAccessIfNeeded(operator, project.getId());
        return project;
    }

    private void grantProjectAccessIfNeeded(User operator, Long projectId) {
        if (currentUserAccessService.isAdmin(operator)) {
            return;
        }
        UserProject existing = userProjectMapper.selectOne(new LambdaQueryWrapper<UserProject>()
            .eq(UserProject::getUserId, operator.getId())
            .eq(UserProject::getProjectId, projectId)
            .last("LIMIT 1"));
        if (existing == null) {
            UserProject userProject = new UserProject();
            userProject.setUserId(operator.getId());
            userProject.setProjectId(projectId);
            userProjectMapper.insert(userProject);
        }
    }

    private void refreshBatchStatus(Long batchId) {
        OpsImportBatch batch = requireBatch(batchId);
        List<OpsImportRowReview> rows = opsImportRowReviewMapper.selectList(new LambdaQueryWrapper<OpsImportRowReview>()
            .eq(OpsImportRowReview::getBatchId, batchId));
        int reviewRows = (int) rows.stream().filter(row -> "NEEDS_REVIEW".equals(row.getReviewStatus())).count();
        int committedRows = (int) rows.stream().filter(row -> "COMMITTED".equals(row.getCommitStatus())).count();
        int skippedRows = (int) rows.stream().filter(row -> "SKIPPED".equals(row.getCommitStatus())).count();
        int readyRows = (int) rows.stream()
            .filter(row -> !"NEEDS_REVIEW".equals(row.getReviewStatus()))
            .filter(row -> "PENDING".equals(row.getCommitStatus()))
            .count();
        batch.setReviewRows(reviewRows);
        batch.setCommittedRows(committedRows);
        batch.setSkippedRows(skippedRows);
        if (reviewRows > 0 && committedRows > 0) {
            batch.setBatchStatus("PARTIALLY_COMMITTED");
        } else if (reviewRows > 0) {
            batch.setBatchStatus("PENDING_REVIEW");
        } else if (readyRows > 0) {
            batch.setBatchStatus("READY_TO_COMMIT");
        } else if (committedRows > 0) {
            batch.setBatchStatus("COMMITTED");
        } else {
            batch.setBatchStatus("READY_TO_COMMIT");
        }
        opsImportBatchMapper.updateById(batch);
    }

    private OpsImportBatch requireBatch(Long batchId) {
        OpsImportBatch batch = opsImportBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("导入批次不存在");
        }
        return batch;
    }

    private OpsImportRowReview requireRow(Long batchId, Long rowId) {
        OpsImportRowReview row = opsImportRowReviewMapper.selectById(rowId);
        if (row == null || !Objects.equals(row.getBatchId(), batchId)) {
            throw new IllegalArgumentException("导入行不存在");
        }
        return row;
    }

    private ImportBatchVO toBatchVO(OpsImportBatch batch) {
        int readyRows = Math.max(
            0,
            (batch.getTotalRows() == null ? 0 : batch.getTotalRows())
                - (batch.getReviewRows() == null ? 0 : batch.getReviewRows())
                - (batch.getCommittedRows() == null ? 0 : batch.getCommittedRows())
                - (batch.getSkippedRows() == null ? 0 : batch.getSkippedRows())
        );
        return ImportBatchVO.builder()
            .id(batch.getId())
            .originalFileName(batch.getOriginalFileName())
            .batchStatus(batch.getBatchStatus())
            .totalRows(batch.getTotalRows())
            .reviewRows(batch.getReviewRows())
            .readyRows(readyRows)
            .committedRows(batch.getCommittedRows())
            .skippedRows(batch.getSkippedRows())
            .summary(readJsonMap(batch.getSummaryJson()))
            .createdBy(batch.getCreatedBy())
            .createdByName(batch.getCreatedByName())
            .createTime(batch.getCreateTime())
            .build();
    }

    private ImportRowReviewVO toRowVO(OpsImportRowReview row) {
        return ImportRowReviewVO.builder()
            .id(row.getId())
            .sheetName(row.getSheetName())
            .rowNumber(row.getRowNumber())
            .rowType(row.getRowType())
            .reviewStatus(row.getReviewStatus())
            .reviewMessage(row.getReviewMessage())
            .commitStatus(row.getCommitStatus())
            .normalizedData(readJsonMap(row.getNormalizedData()))
            .rawData(readJsonMap(row.getRawData()))
            .build();
    }

    private Map<String, Object> buildSummary(ExcelImportParser.ParsedWorkbook parsedWorkbook) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sheetStats", parsedWorkbook.sheetStats());
        Map<String, Long> rowTypeStats = parsedWorkbook.rows().stream()
            .collect(Collectors.groupingBy(ExcelImportParser.ParsedRow::rowType, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> reviewStats = parsedWorkbook.rows().stream()
            .collect(Collectors.groupingBy(ExcelImportParser.ParsedRow::reviewStatus, LinkedHashMap::new, Collectors.counting()));
        summary.put("rowTypeStats", rowTypeStats);
        summary.put("reviewStats", reviewStats);
        return summary;
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 反序列化失败：" + e.getMessage(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 序列化失败：" + e.getMessage(), e);
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer asInteger(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private LocalDateTime asDateTime(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value), DATE_TIME_FORMATTER);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private ProjectIdentity normalizeProjectIdentity(String rawProjectName) {
        String raw = defaultIfBlank(rawProjectName, "未确认项目").trim();
        String normalized = raw
            .replace('—', '-')
            .replace('_', '-')
            .replaceAll("\\s+", "")
            .trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.contains("ABB") && upper.contains("P6")) {
            return new ProjectIdentity("ABB", "ABB", "上海ABB-P6");
        }
        if (normalized.contains("上海交通大学") || normalized.contains("上海交大") || normalized.contains("交大")) {
            return new ProjectIdentity("上海交通大学", "上海交大", "上海交通大学闵行校区");
        }
        if (normalized.contains("博滔")) {
            return new ProjectIdentity("博滔", "博滔", raw);
        }
        String[] tokens = normalized.split("[-/]+");
        String customerName = tokens.length == 0 || !StringUtils.hasText(tokens[0]) ? normalized : tokens[0];
        return new ProjectIdentity(customerName, customerName, raw);
    }

    private record ProjectIdentity(String customerName, String projectGroup, String projectName) {
    }

    private String normalizeCustomerName(String projectName) {
        String normalized = projectName.replace('—', '-').replace('_', '-').trim();
        if (normalized.toUpperCase(Locale.ROOT).contains("ABB")) {
            return "ABB";
        }
        if (normalized.contains("交大")) {
            return "上海交大";
        }
        if (normalized.contains("博湃")) {
            return "博湃";
        }
        String[] tokens = normalized.split("[-/\\s]+");
        return tokens.length == 0 ? normalized : tokens[0];
    }

    private String normalizeProjectGroup(String projectName, String customerName) {
        String normalized = projectName.replace('—', '-').replace('_', '-').trim();
        String[] tokens = normalized.split("[-/\\s]+");
        if (tokens.length >= 3) {
            return tokens[0] + "-" + tokens[1];
        }
        return customerName + "项目组";
    }

    private String buildProjectCode(String prefix, String source) {
        String letters = source.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("(^-|-$)", "");
        String code = StringUtils.hasText(letters) ? letters : Integer.toHexString(source.hashCode()).toUpperCase(Locale.ROOT);
        return prefix + "-" + code;
    }

    private String buildIssueNo(Long issueId) {
        return "ISS-" + ISSUE_NO_DATE_FORMATTER.format(LocalDateTime.now()) + "-" + String.format("%05d", issueId);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private LocalDateTime firstNonNull(LocalDateTime primary, LocalDateTime fallback) {
        return primary != null ? primary : fallback;
    }

    private String readRawValue(Map<String, Object> raw, String key) {
        return raw == null ? "" : asString(raw.get(key));
    }

    private String appendMessage(String original, String next) {
        if (!StringUtils.hasText(original)) {
            return next;
        }
        if (original.contains(next)) {
            return original;
        }
        return original + "；" + next;
    }
}
