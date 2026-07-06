package com.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.dto.KnowledgeUpdateDTO;
import com.problem.entity.OpsIssue;
import com.problem.entity.OpsKnowledge;
import com.problem.entity.Project;
import com.problem.entity.User;
import com.problem.mapper.OpsIssueMapper;
import com.problem.mapper.OpsKnowledgeMapper;
import com.problem.mapper.ProjectMapper;
import com.problem.support.CurrentUserAccessService;
import com.problem.vo.KnowledgeVO;
import com.problem.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String SOURCE_ISSUE_LEDGER = "ISSUE_LEDGER";
    public static final String SOURCE_COMPANY_EXCEL = "COMPANY_EXCEL";

    private final OpsKnowledgeMapper opsKnowledgeMapper;
    private final OpsIssueMapper opsIssueMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserAccessService currentUserAccessService;

    public PageResultVO<KnowledgeVO> listKnowledge(
        Long projectId,
        String sourceType,
        String systemType,
        String faultCode,
        String tagKeyword,
        String causeCategory,
        String keyword,
        String status,
        long page,
        long pageSize
    ) {
        User user = currentUserAccessService.getCurrentUser();
        LambdaQueryWrapper<OpsKnowledge> wrapper = new LambdaQueryWrapper<OpsKnowledge>()
            .orderByDesc(OpsKnowledge::getUpdateTime, OpsKnowledge::getCreateTime);
        applyProjectScope(wrapper, user, projectId);
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(OpsKnowledge::getSourceType, sourceType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OpsKnowledge::getStatus, normalizeStatus(status));
        } else {
            wrapper.eq(OpsKnowledge::getStatus, STATUS_PUBLISHED);
        }
        if (StringUtils.hasText(faultCode)) {
            wrapper.eq(OpsKnowledge::getFaultCode, faultCode.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(tagKeyword)) {
            wrapper.like(OpsKnowledge::getTags, tagKeyword.trim());
        }
        if (StringUtils.hasText(causeCategory)) {
            wrapper.like(OpsKnowledge::getCauseSummary, causeCategory.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query
                .like(OpsKnowledge::getTitle, trimmed)
                .or()
                .like(OpsKnowledge::getSymptomSummary, trimmed)
                .or()
                .like(OpsKnowledge::getCauseSummary, trimmed)
                .or()
                .like(OpsKnowledge::getSolutionSummary, trimmed)
                .or()
                .like(OpsKnowledge::getPreventionSummary, trimmed)
                .or()
                .like(OpsKnowledge::getTags, trimmed));
        }
        List<OpsKnowledge> rows = opsKnowledgeMapper.selectList(wrapper);
        Map<Long, OpsIssue> issueMap = loadIssueMap(rows.stream().map(OpsKnowledge::getIssueId).collect(Collectors.toSet()));
        if (StringUtils.hasText(systemType)) {
            String trimmed = systemType.trim();
            rows = rows.stream()
                .filter(row -> {
                    OpsIssue issue = issueMap.get(row.getIssueId());
                    return issue != null && trimmed.equals(issue.getSystemType());
                })
                .toList();
        }
        Map<Long, Project> projectMap = loadProjectMap(rows.stream().map(OpsKnowledge::getProjectId).collect(Collectors.toSet()));
        List<KnowledgeVO> items = rows.stream()
            .map(row -> toVO(row, issueMap.get(row.getIssueId()), projectMap.get(row.getProjectId())))
            .toList();
        return paginate(items, page, pageSize);
    }

    public KnowledgeVO getKnowledge(Long id) {
        OpsKnowledge knowledge = requireKnowledge(id);
        assertKnowledgeAccess(knowledge);
        OpsIssue issue = knowledge.getIssueId() == null ? null : opsIssueMapper.selectById(knowledge.getIssueId());
        Project project = knowledge.getProjectId() == null ? null : projectMapper.selectById(knowledge.getProjectId());
        return toVO(knowledge, issue, project);
    }

    @Transactional
    public KnowledgeVO syncFromIssueId(Long issueId) {
        currentUserAccessService.assertIssueAccess(issueId);
        OpsIssue issue = requireIssue(issueId);
        if (!"CLOSED".equals(issue.getCurrentStatus()) && !"CLOSED".equals(issue.getClosureStatus())) {
            throw new IllegalArgumentException("Only closed issues can be archived into knowledge");
        }
        if (Objects.equals(issue.getKnowledgeIncluded(), 0)) {
            disableByIssueId(issueId);
            throw new IllegalArgumentException("This issue is not marked for knowledge inclusion");
        }
        return toVO(syncFromIssue(issue), issue, projectMapper.selectById(issue.getProjectId()));
    }

    @Transactional
    public OpsKnowledge syncFromIssue(OpsIssue issue) {
        OpsKnowledge knowledge = findByIssueId(issue.getId());
        User user = currentUserAccessService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        if (knowledge == null) {
            knowledge = new OpsKnowledge();
            knowledge.setIssueId(issue.getId());
            knowledge.setProjectId(issue.getProjectId());
            knowledge.setCreatedBy(user.getId());
            knowledge.setCreateTime(now);
            knowledge.setDeleted(0);
        }
        knowledge.setProjectId(issue.getProjectId());
        knowledge.setSourceType(SOURCE_ISSUE_LEDGER);
        knowledge.setSourceName("日常客户反馈问题台账");
        knowledge.setSourceSheet(null);
        knowledge.setSourceRowNumber(null);
        knowledge.setTitle(defaultIfBlank(issue.getItemTitle(), issue.getDescription()));
        knowledge.setFaultCode(inferFaultCode(issue));
        knowledge.setSymptomSummary(defaultIfBlank(issue.getDescription(), issue.getItemTitle()));
        knowledge.setCauseSummary(defaultIfBlank(issue.getCauseDetail(), issue.getCauseCategory()));
        knowledge.setSolutionSummary(defaultIfBlank(issue.getInternalConclusion(), issue.getLatestProgress()));
        knowledge.setPreventionSummary(defaultIfBlank(issue.getPreventiveAction(), issue.getFollowUpAction()));
        knowledge.setTags(trimToNull(issue.getReuseTags()));
        knowledge.setStatus(STATUS_PUBLISHED);
        knowledge.setUpdatedBy(user.getId());
        knowledge.setUpdateTime(now);
        if (knowledge.getId() == null) {
            opsKnowledgeMapper.insert(knowledge);
        } else {
            opsKnowledgeMapper.updateById(knowledge);
        }
        return knowledge;
    }

    @Transactional
    public int syncClosedIssues() {
        User user = currentUserAccessService.getCurrentUser();
        LambdaQueryWrapper<OpsIssue> wrapper = new LambdaQueryWrapper<OpsIssue>()
            .eq(OpsIssue::getClosureStatus, "CLOSED")
            .eq(OpsIssue::getKnowledgeIncluded, 1);
        currentUserAccessService.applyIssueListScope(wrapper, user);
        if (currentUserAccessService.hasNoVisibleProjects(user)) {
            return 0;
        }
        List<OpsIssue> issues = opsIssueMapper.selectList(wrapper);
        issues.forEach(this::syncFromIssue);
        return issues.size();
    }

    @Transactional
    public int importCompanyExcel(MultipartFile file) {
        currentUserAccessService.assertNotTemporary("import knowledge document");
        validateExcelFile(file);
        User user = currentUserAccessService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        String sourceName = defaultIfBlank(file.getOriginalFilename(), "公司问题经验库");
        int imported = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                ImportedSheet importedSheet;
                try {
                    importedSheet = readSheet(sheet, formatter);
                } catch (RuntimeException e) {
                    throw importFailure(sourceName, sheet.getSheetName(), null, "读取工作表失败", e);
                }
                if (importedSheet.headers().isEmpty()) {
                    continue;
                }
                for (ImportedRow row : importedSheet.rows()) {
                    try {
                        OpsKnowledge knowledge = buildKnowledgeFromRow(row, sourceName, user.getId(), now);
                        if (knowledge == null) {
                            continue;
                        }
                        OpsKnowledge existing = findByDocumentRow(sourceName, row.sheetName(), row.rowNumber());
                        if (existing == null) {
                            opsKnowledgeMapper.insert(knowledge);
                        } else {
                            knowledge.setId(existing.getId());
                            knowledge.setCreatedBy(existing.getCreatedBy());
                            knowledge.setCreateTime(existing.getCreateTime());
                            opsKnowledgeMapper.updateById(knowledge);
                        }
                        imported++;
                    } catch (IllegalArgumentException e) {
                        throw e;
                    } catch (DataAccessException e) {
                        throw importFailure(sourceName, row.sheetName(), row.rowNumber(), "数据库保存失败", e);
                    } catch (RuntimeException e) {
                        throw importFailure(sourceName, row.sheetName(), row.rowNumber(), "解析行数据失败", e);
                    }
                }
            }
            if (imported == 0) {
                throw new IllegalArgumentException("知识库导入失败：文件 " + sourceName + " 未识别到可入库的问题经验行，请检查表头和内容");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (EncryptedDocumentException e) {
            throw new IllegalArgumentException("知识库导入失败：文件 " + sourceName + " 已加密或无法读取，请先解除保护后重新上传", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("知识库导入失败：文件 " + sourceName + " 读取失败，请确认文件未损坏且格式为 xls/xlsx", e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("知识库导入失败：文件 " + sourceName + " 处理失败：" + rootCauseMessage(e), e);
        }

        return imported;
    }

    @Transactional
    public KnowledgeVO updateKnowledge(Long id, KnowledgeUpdateDTO dto) {
        currentUserAccessService.assertNotTemporary("edit knowledge");
        OpsKnowledge knowledge = requireKnowledge(id);
        assertKnowledgeAccess(knowledge);
        User user = currentUserAccessService.getCurrentUser();
        if (dto.getTitle() != null) {
            knowledge.setTitle(trimToNull(dto.getTitle()));
        }
        if (dto.getFaultCode() != null) {
            knowledge.setFaultCode(normalizeFaultCode(dto.getFaultCode()));
        }
        if (dto.getSymptomSummary() != null) {
            knowledge.setSymptomSummary(trimToNull(dto.getSymptomSummary()));
        }
        if (dto.getCauseSummary() != null) {
            knowledge.setCauseSummary(trimToNull(dto.getCauseSummary()));
        }
        if (dto.getSolutionSummary() != null) {
            knowledge.setSolutionSummary(trimToNull(dto.getSolutionSummary()));
        }
        if (dto.getPreventionSummary() != null) {
            knowledge.setPreventionSummary(trimToNull(dto.getPreventionSummary()));
        }
        if (dto.getTags() != null) {
            knowledge.setTags(trimToNull(dto.getTags()));
        }
        knowledge.setUpdatedBy(user.getId());
        knowledge.setUpdateTime(LocalDateTime.now());
        opsKnowledgeMapper.updateById(knowledge);
        return getKnowledge(id);
    }

    @Transactional
    public KnowledgeVO publishKnowledge(Long id) {
        return updateStatus(id, STATUS_PUBLISHED);
    }

    @Transactional
    public KnowledgeVO disableKnowledge(Long id) {
        return updateStatus(id, STATUS_DISABLED);
    }

    @Transactional
    public void disableByIssueId(Long issueId) {
        OpsKnowledge knowledge = findByIssueId(issueId);
        if (knowledge == null || STATUS_DISABLED.equals(knowledge.getStatus())) {
            return;
        }
        User user = currentUserAccessService.getCurrentUser();
        knowledge.setStatus(STATUS_DISABLED);
        knowledge.setUpdatedBy(user.getId());
        knowledge.setUpdateTime(LocalDateTime.now());
        opsKnowledgeMapper.updateById(knowledge);
    }

    private KnowledgeVO updateStatus(Long id, String status) {
        currentUserAccessService.assertNotTemporary("change knowledge status");
        OpsKnowledge knowledge = requireKnowledge(id);
        assertKnowledgeAccess(knowledge);
        User user = currentUserAccessService.getCurrentUser();
        knowledge.setStatus(status);
        knowledge.setUpdatedBy(user.getId());
        knowledge.setUpdateTime(LocalDateTime.now());
        opsKnowledgeMapper.updateById(knowledge);
        return getKnowledge(id);
    }

    private void applyProjectScope(LambdaQueryWrapper<OpsKnowledge> wrapper, User user, Long projectId) {
        if (projectId != null) {
            currentUserAccessService.assertProjectAccess(projectId);
            wrapper.eq(OpsKnowledge::getProjectId, projectId);
            return;
        }
        if (currentUserAccessService.isAdmin(user)) {
            return;
        }
        List<Long> scopedProjectIds = currentUserAccessService.scopeProjectIdsForList(user);
        if (scopedProjectIds.isEmpty()) {
            wrapper.eq(OpsKnowledge::getSourceType, SOURCE_COMPANY_EXCEL);
        } else {
            wrapper.and(scope -> scope
                .in(OpsKnowledge::getProjectId, scopedProjectIds)
                .or()
                .eq(OpsKnowledge::getSourceType, SOURCE_COMPANY_EXCEL));
        }
    }

    private void assertKnowledgeAccess(OpsKnowledge knowledge) {
        if (SOURCE_COMPANY_EXCEL.equals(knowledge.getSourceType()) || knowledge.getProjectId() == null) {
            return;
        }
        currentUserAccessService.assertProjectAccess(knowledge.getProjectId());
    }

    private OpsKnowledge findByIssueId(Long issueId) {
        return opsKnowledgeMapper.selectOne(new LambdaQueryWrapper<OpsKnowledge>()
            .eq(OpsKnowledge::getIssueId, issueId)
            .last("LIMIT 1"));
    }

    private OpsKnowledge findByDocumentRow(String sourceName, String sheetName, Integer rowNumber) {
        return opsKnowledgeMapper.selectOne(new LambdaQueryWrapper<OpsKnowledge>()
            .eq(OpsKnowledge::getSourceType, SOURCE_COMPANY_EXCEL)
            .eq(OpsKnowledge::getSourceName, sourceName)
            .eq(OpsKnowledge::getSourceSheet, sheetName)
            .eq(OpsKnowledge::getSourceRowNumber, rowNumber)
            .last("LIMIT 1"));
    }

    private OpsKnowledge requireKnowledge(Long id) {
        OpsKnowledge knowledge = opsKnowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge entry not found");
        }
        return knowledge;
    }

    private OpsIssue requireIssue(Long issueId) {
        OpsIssue issue = opsIssueMapper.selectById(issueId);
        if (issue == null) {
            throw new IllegalArgumentException("Issue not found");
        }
        return issue;
    }

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        String name = defaultIfBlank(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("Only .xlsx and .xls files are supported");
        }
    }

    private ImportedSheet readSheet(Sheet sheet, DataFormatter formatter) {
        int headerIndex = -1;
        List<String> headers = new ArrayList<>();
        for (Row row : sheet) {
            List<String> values = readRow(row, formatter);
            if (values.stream().filter(StringUtils::hasText).count() >= 2) {
                headerIndex = row.getRowNum();
                headers = values.stream().map(this::normalizeHeader).toList();
                break;
            }
        }
        if (headerIndex < 0) {
            return new ImportedSheet(List.of(), List.of());
        }

        List<ImportedRow> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            List<String> values = readRow(row, formatter);
            if (values.stream().noneMatch(StringUtils::hasText)) {
                continue;
            }
            Map<String, String> data = new LinkedHashMap<>();
            int max = Math.min(headers.size(), values.size());
            for (int column = 0; column < max; column++) {
                String header = headers.get(column);
                if (StringUtils.hasText(header)) {
                    data.put(header, values.get(column));
                }
            }
            rows.add(new ImportedRow(sheet.getSheetName(), i + 1, data));
        }
        return new ImportedSheet(headers, rows);
    }

    private List<String> readRow(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return values;
        }
        for (int column = 0; column < lastCellNum; column++) {
            Cell cell = row.getCell(column);
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    private OpsKnowledge buildKnowledgeFromRow(ImportedRow row, String sourceName, Long userId, LocalDateTime now) {
        Map<String, String> data = row.data();
        String title = firstMatching(data, "客户反馈的问题", "客户反馈现象", "故障现象描述", "问题描述", "问题", "常见问题", "标题");
        String category = firstMatching(data, "分类", "类型", "设备", "问题类型", "故障分类");
        String solution = firstMatching(data, "排查和验证步骤", "排查方向与方案", "排查方向", "排查方法", "排查步骤", "验证与排查步骤", "解决方案", "处理方案", "方案");
        String project = firstMatching(data, "适用项目", "项目", "项目名称");
        String notes = firstMatching(data, "其他特殊情况说明", "备注", "案例分析", "说明");

        title = defaultIfBlank(title, category);
        String symptom = compactJoin("；", title, category);
        String cause = defaultIfBlank(category, row.sheetName());
        String prevention = compactJoin("；", notes, StringUtils.hasText(project) ? "适用项目：" + project : null);
        if (!hasEnoughDocumentData(title, symptom, cause, solution, prevention)) {
            return null;
        }

        OpsKnowledge knowledge = new OpsKnowledge();
        knowledge.setIssueId(null);
        knowledge.setProjectId(null);
        knowledge.setSourceType(SOURCE_COMPANY_EXCEL);
        knowledge.setSourceName(sourceName);
        knowledge.setSourceSheet(row.sheetName());
        knowledge.setSourceRowNumber(row.rowNumber());
        knowledge.setTitle(limit(defaultIfBlank(title, row.sheetName() + " 第" + row.rowNumber() + "行"), 255));
        knowledge.setFaultCode(inferFaultCode(compactJoin(" ", row.sheetName(), title, category, solution, notes)));
        knowledge.setSymptomSummary(trimToNull(symptom));
        knowledge.setCauseSummary(trimToNull(cause));
        knowledge.setSolutionSummary(trimToNull(solution));
        knowledge.setPreventionSummary(trimToNull(prevention));
        knowledge.setTags(limit(compactTags(row.sheetName(), category, project, sourceName), 500));
        knowledge.setStatus(STATUS_PUBLISHED);
        knowledge.setCreatedBy(userId);
        knowledge.setUpdatedBy(userId);
        knowledge.setCreateTime(now);
        knowledge.setUpdateTime(now);
        knowledge.setDeleted(0);
        return knowledge;
    }

    private boolean hasEnoughDocumentData(String title, String symptom, String cause, String solution, String prevention) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        if (StringUtils.hasText(solution)) {
            return true;
        }
        return compactJoin("", symptom, cause, prevention).length() >= 20;
    }

    private IllegalArgumentException importFailure(String sourceName, String sheetName, Integer rowNumber, String stage, Throwable cause) {
        StringBuilder message = new StringBuilder("知识库导入失败：文件 ")
            .append(defaultIfBlank(sourceName, "-"));
        if (StringUtils.hasText(sheetName)) {
            message.append("，工作表 ").append(sheetName);
        }
        if (rowNumber != null) {
            message.append("，第 ").append(rowNumber).append(" 行");
        }
        message.append("，").append(stage);
        String rootMessage = rootCauseMessage(cause);
        if (StringUtils.hasText(rootMessage)) {
            message.append("：").append(rootMessage);
        }
        return new IllegalArgumentException(message.toString(), cause);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor == null ? null : cursor.getMessage();
        return StringUtils.hasText(message) ? message : throwable.getClass().getSimpleName();
    }

    private String firstMatching(Map<String, String> data, String... candidates) {
        for (String candidate : candidates) {
            String matched = data.entrySet().stream()
                .filter(entry -> entry.getKey().contains(candidate))
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
            if (StringUtils.hasText(matched)) {
                return matched.trim();
            }
        }
        return null;
    }

    private String inferFaultCode(OpsIssue issue) {
        return inferFaultCode(String.join(" ",
            defaultIfBlank(issue.getCategoryPath(), ""),
            defaultIfBlank(issue.getSystemType(), ""),
            defaultIfBlank(issue.getDevicePoint(), ""),
            defaultIfBlank(issue.getItemTitle(), ""),
            defaultIfBlank(issue.getDescription(), ""),
            defaultIfBlank(issue.getCauseCategory(), ""),
            defaultIfBlank(issue.getCauseDetail(), ""),
            defaultIfBlank(issue.getLatestProgress(), "")
        ));
    }

    private String inferFaultCode(String text) {
        String value = defaultIfBlank(text, "").toLowerCase(Locale.ROOT);
        if (containsAny(value, "485", "mqtt", "socket", "gateway", "network", "offline", "timeout", "网关", "网络", "通讯", "通信", "离线", "超时")) {
            return "COM";
        }
        if (containsAny(value, "power", "电源", "断电", "供电", "空开")) {
            return "PWR";
        }
        if (containsAny(value, "config", "setting", "参数", "配置", "策略", "联动", "定时")) {
            return "CFG";
        }
        if (containsAny(value, "software", "service", "api", "500", "404", "页面", "接口", "服务", "平台", "保存失败")) {
            return "SW";
        }
        if (containsAny(value, "温度", "湿度", "环境", "遮挡", "施工")) {
            return "ENV";
        }
        if (containsAny(value, "device", "sensor", "controller", "设备", "传感器", "控制器", "灯具", "读卡器", "交换机", "面板")) {
            return "DEV";
        }
        return "OTHER";
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeFaultCode(String faultCode) {
        String value = defaultIfBlank(faultCode, "OTHER").toUpperCase(Locale.ROOT);
        return switch (value) {
            case "COM", "DEV", "CFG", "SW", "PWR", "ENV", "OTHER" -> value;
            default -> "OTHER";
        };
    }

    private String normalizeStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        return STATUS_DISABLED.equals(value) ? STATUS_DISABLED : STATUS_PUBLISHED;
    }

    private Map<Long, OpsIssue> loadIssueMap(Set<Long> issueIds) {
        Set<Long> safeIds = issueIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (safeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return opsIssueMapper.selectBatchIds(safeIds).stream()
            .collect(Collectors.toMap(OpsIssue::getId, issue -> issue, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, Project> loadProjectMap(Set<Long> projectIds) {
        Set<Long> safeIds = projectIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (safeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return projectMapper.selectBatchIds(safeIds).stream()
            .collect(Collectors.toMap(Project::getId, project -> project, (left, right) -> left, LinkedHashMap::new));
    }

    private PageResultVO<KnowledgeVO> paginate(List<KnowledgeVO> items, long page, long pageSize) {
        long safePage = page < 1 ? 1 : page;
        long safePageSize = pageSize < 1 ? 20 : pageSize;
        int fromIndex = (int) Math.min(items.size(), (safePage - 1) * safePageSize);
        int toIndex = (int) Math.min(items.size(), fromIndex + safePageSize);
        return new PageResultVO<>(items.size(), safePage, safePageSize, items.subList(fromIndex, toIndex));
    }

    private KnowledgeVO toVO(OpsKnowledge knowledge, OpsIssue issue, Project project) {
        Project safeProject = project == null ? new Project() : project;
        return KnowledgeVO.builder()
            .id(knowledge.getId())
            .issueId(knowledge.getIssueId())
            .issueNo(issue == null ? null : issue.getIssueNo())
            .projectId(knowledge.getProjectId())
            .sourceType(knowledge.getSourceType())
            .sourceName(knowledge.getSourceName())
            .sourceSheet(knowledge.getSourceSheet())
            .sourceRowNumber(knowledge.getSourceRowNumber())
            .customerName(safeProject.getCustomerName())
            .projectGroup(safeProject.getProjectGroup())
            .projectName(safeProject.getProjectName())
            .projectCode(safeProject.getProjectCode())
            .title(knowledge.getTitle())
            .faultCode(knowledge.getFaultCode())
            .symptomSummary(knowledge.getSymptomSummary())
            .causeSummary(knowledge.getCauseSummary())
            .solutionSummary(knowledge.getSolutionSummary())
            .preventionSummary(knowledge.getPreventionSummary())
            .tags(knowledge.getTags())
            .status(knowledge.getStatus())
            .createTime(knowledge.getCreateTime())
            .updateTime(knowledge.getUpdateTime())
            .build();
    }

    private String normalizeHeader(String value) {
        return defaultIfBlank(value, "").replaceAll("\\s+", "");
    }

    private String compactTags(String... values) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            for (String item : value.split("[,，;；、\\s]+")) {
                if (StringUtils.hasText(item)) {
                    tags.add(item.trim());
                }
            }
        }
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    private String compactJoin(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(delimiter, parts);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record ImportedSheet(List<String> headers, List<ImportedRow> rows) {
    }

    private record ImportedRow(String sheetName, Integer rowNumber, Map<String, String> data) {
    }
}
