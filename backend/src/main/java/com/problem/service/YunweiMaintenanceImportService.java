package com.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.entity.OpsMaintenanceAssignment;
import com.problem.entity.OpsMaintenanceFinding;
import com.problem.entity.OpsMaintenancePersonnel;
import com.problem.entity.OpsMaintenanceQuoteItem;
import com.problem.entity.OpsMaintenanceSourceFile;
import com.problem.entity.OpsMaintenanceVisit;
import com.problem.entity.Project;
import com.problem.entity.User;
import com.problem.mapper.OpsMaintenanceAssignmentMapper;
import com.problem.mapper.OpsMaintenanceFindingMapper;
import com.problem.mapper.OpsMaintenancePersonnelMapper;
import com.problem.mapper.OpsMaintenanceQuoteItemMapper;
import com.problem.mapper.OpsMaintenanceSourceFileMapper;
import com.problem.mapper.OpsMaintenanceVisitMapper;
import com.problem.mapper.ProjectMapper;
import com.problem.support.CurrentUserAccessService;
import com.problem.vo.MaintenanceImportReportVO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
@RequiredArgsConstructor
public class YunweiMaintenanceImportService {

    private static final Set<String> STRUCTURED_EXTENSIONS = Set.of(".xlsx", ".xls");
    private static final Set<String> SOURCE_FILE_EXTENSIONS = Set.of(".xlsx", ".xls", ".pdf", ".jpg", ".jpeg", ".png", ".webp");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[-_./]?(\\d{2})[-_./]?(\\d{2})");
    private static final Pattern SHORT_YEAR_DATE_PATTERN = Pattern.compile("(?<!\\d)(\\d{2})(\\d{2})(\\d{2})(?!\\d)");

    private final OpsMaintenanceVisitMapper visitMapper;
    private final OpsMaintenanceAssignmentMapper assignmentMapper;
    private final OpsMaintenancePersonnelMapper personnelMapper;
    private final OpsMaintenanceFindingMapper findingMapper;
    private final OpsMaintenanceQuoteItemMapper quoteItemMapper;
    private final OpsMaintenanceSourceFileMapper sourceFileMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserAccessService currentUserAccessService;
    private final KnowledgeService knowledgeService;

    @Value("${ops.maintenance.yunwei-root:../docs/yunwei}")
    private String yunweiRoot;

    @Transactional
    public MaintenanceImportReportVO importYunwei() {
        currentUserAccessService.assertAdmin();
        ImportContext context = new ImportContext(resolveRoot());
        if (!Files.exists(context.rootPath)) {
            context.warn(null, null, null, null, "运维资料目录不存在：" + context.rootPath);
            return context.toReport();
        }
        try {
            List<SourceDocument> documents = scanDocuments(context.rootPath);
            context.scannedFiles = documents.size();
            for (SourceDocument document : documents) {
                importDocument(document, context);
            }
        } catch (IOException e) {
            context.fail(null, null, null, null, "扫描运维资料失败：" + e.getMessage());
        }
        context.recognizedProjects = context.recognizedProjectNames.size();
        return context.toReport();
    }

    private Path resolveRoot() {
        Path configured = Path.of(yunweiRoot).toAbsolutePath().normalize();
        if (Files.exists(configured)) {
            return configured;
        }
        return Path.of("docs/yunwei").toAbsolutePath().normalize();
    }

    private List<SourceDocument> scanDocuments(Path root) throws IOException {
        List<SourceDocument> documents = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String extension = extensionOf(file.getFileName().toString());
                String relativePath = root.relativize(file).toString().replace("\\", "/");
                if (".zip".equals(extension)) {
                    scanZip(file, relativePath, documents);
                } else if (SOURCE_FILE_EXTENSIONS.contains(extension)) {
                    documents.add(SourceDocument.local(root, file, relativePath, extension));
                }
            }
        }
        documents.sort(Comparator.comparing(SourceDocument::displayPath));
        return documents;
    }

    private void scanZip(Path zipPath, String relativeZipPath, List<SourceDocument> documents) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), Charset.forName("GBK"))) {
            zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .forEach(entry -> addZipDocument(zipPath, relativeZipPath, entry, documents));
        } catch (IOException gbkFailure) {
            try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
                zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .forEach(entry -> addZipDocument(zipPath, relativeZipPath, entry, documents));
            } catch (IOException ignored) {
                // 单个 zip 读取失败时由后续缺失导入报告体现，避免阻断其他文件。
            }
        }
    }

    private void addZipDocument(Path zipPath, String relativeZipPath, ZipEntry entry, List<SourceDocument> documents) {
        String entryName = normalizeZipEntryName(entry.getName());
        String extension = extensionOf(entryName);
        if (SOURCE_FILE_EXTENSIONS.contains(extension)) {
            documents.add(SourceDocument.zipped(zipPath, relativeZipPath, entry.getName(), entryName, extension));
        }
    }

    private void importDocument(SourceDocument document, ImportContext context) {
        String projectName = inferProjectName(document.displayPath());
        if (!StringUtils.hasText(projectName)) {
            context.skip(null, document.displayPath(), null, null, "未识别项目，已跳过");
            return;
        }
        Project project = findProject(projectName);
        if (project == null) {
            context.skip(projectName, document.displayPath(), null, null, "系统中未找到项目：" + projectName);
            return;
        }
        context.recognizedProjectNames.add(projectName);
        VisitKey visitKey = inferVisitKey(document, projectName);
        OpsMaintenanceVisit visit = upsertVisit(project, document, visitKey, context);
        upsertSourceFile(visit, projectName, document, "IMPORTED", null);

        if (!STRUCTURED_EXTENSIONS.contains(document.extension())) {
            return;
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(document.bytes()))) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                parseSheet(visit, document, sheet, formatter, context);
            }
        } catch (Exception e) {
            upsertSourceFile(visit, projectName, document, "FAILED", e.getMessage());
            context.fail(projectName, document.displayPath(), null, null, "Excel 读取失败：" + rootMessage(e));
        }
    }

    private OpsMaintenanceVisit upsertVisit(Project project, SourceDocument document, VisitKey key, ImportContext context) {
        String sourceHash = sha256("VISIT|" + project.getId() + "|" + key.periodKey() + "|" + key.sourceGroup());
        OpsMaintenanceVisit visit = selectVisitByHash(sourceHash);
        boolean inserted = visit == null;
        if (visit == null) {
            visit = new OpsMaintenanceVisit();
            visit.setProjectId(project.getId());
            visit.setVisitNo("YW-" + key.date().toString().replace("-", "") + "-" + abbreviate(sourceHash, 8));
            visit.setCreatedBy(currentUserAccessService.getCurrentUser().getId());
            visit.setCreateTime(LocalDateTime.now());
            visit.setDeleted(0);
        }
        visit.setProjectId(project.getId());
        visit.setVisitTitle(project.getProjectName() + " 历史运维导入");
        visit.setServiceYear(key.year());
        visit.setServiceQuarter(key.quarter());
        visit.setServicePeriod(key.year() + " Q" + key.quarter());
        visit.setPlannedStartAt(key.date().atStartOfDay());
        visit.setActualStartAt(key.date().atStartOfDay());
        visit.setStatus(MaintenanceService.STATUS_CLOSED);
        visit.setSummary("由历史运维资料清洗导入：" + key.sourceGroup());
        visit.setConclusion("历史资料导入，具体问题和报价以现场记录、报价清单为准。");
        visit.setSourceFilePath(document.displayPath());
        visit.setSourceSheet(null);
        visit.setSourceRowNumber(null);
        visit.setSourceHash(sourceHash);
        visit.setUpdatedBy(currentUserAccessService.getCurrentUser().getId());
        visit.setUpdateTime(LocalDateTime.now());
        if (inserted) {
            visitMapper.insert(visit);
            context.importedVisits++;
        } else {
            visitMapper.updateById(visit);
        }
        return visit;
    }

    private void parseSheet(OpsMaintenanceVisit visit, SourceDocument document, Sheet sheet, DataFormatter formatter, ImportContext context) {
        List<RowData> rows = readRows(sheet, formatter);
        if (rows.isEmpty()) {
            return;
        }
        String sheetName = sheet.getSheetName();
        HeaderMatch header = findHeader(rows);
        String normalizedSheet = normalize(sheetName);
        String normalizedFile = normalize(document.displayPath());
        try {
            if (normalizedSheet.contains("分工安排")) {
                parseAssignments(visit, document, sheetName, rows, header, context);
            } else if (isPersonnelSheet(normalizedSheet, header)) {
                parsePersonnel(visit, document, sheetName, rows, header, context);
            } else if (isQuoteSheet(normalizedSheet, normalizedFile, header)) {
                parseQuoteItems(visit, document, sheetName, rows, header, context);
            } else if (isFindingSheet(normalizedSheet, header)) {
                parseFindings(visit, document, sheetName, rows, header, context);
            } else if (isChecklistSheet(normalizedSheet, header)) {
                parseChecklistFindings(visit, document, sheetName, rows, header, context);
            } else {
                context.skip(null, document.displayPath(), sheetName, null, "未识别工作表类型");
            }
        } catch (RuntimeException e) {
            context.fail(null, document.displayPath(), sheetName, null, "工作表解析失败：" + rootMessage(e));
        }
    }

    private void parseAssignments(OpsMaintenanceVisit visit, SourceDocument document, String sheetName, List<RowData> rows, HeaderMatch header, ImportContext context) {
        if (header == null) {
            context.skip(null, document.displayPath(), sheetName, null, "分工安排未识别表头");
            return;
        }
        for (RowData row : rowsAfter(rows, header)) {
            String task = first(row, header, "事项", "任务");
            if (!StringUtils.hasText(task)) {
                continue;
            }
            String sourceHash = rowHash("ASSIGNMENT", document, sheetName, row.rowNumber(), row.values());
            OpsMaintenanceAssignment assignment = selectAssignmentByHash(sourceHash);
            boolean inserted = assignment == null;
            if (assignment == null) {
                assignment = new OpsMaintenanceAssignment();
                assignment.setVisitId(visit.getId());
                assignment.setDeleted(0);
            }
            assignment.setVisitId(visit.getId());
            assignment.setScheduledAt(combineDateTime(first(row, header, "日期"), first(row, header, "时间"), visit.getPlannedStartAt()));
            assignment.setFloorName(trimToNull(first(row, header, "楼层")));
            assignment.setTaskItem(task);
            assignment.setOwnerName(trimToNull(first(row, header, "负责人", "负责")));
            assignment.setStatus(defaultIfBlank(first(row, header, "状态"), "IMPORTED"));
            assignment.setNotes(trimToNull(first(row, header, "备注")));
            applySource(assignment, document, sheetName, row.rowNumber(), sourceHash);
            if (inserted) {
                assignmentMapper.insert(assignment);
                context.importedAssignments++;
            } else {
                assignmentMapper.updateById(assignment);
            }
        }
    }

    private void parsePersonnel(OpsMaintenanceVisit visit, SourceDocument document, String sheetName, List<RowData> rows, HeaderMatch header, ImportContext context) {
        if (header == null) {
            return;
        }
        for (RowData row : rowsAfter(rows, header)) {
            String name = first(row, header, "姓名", "人员", "负责人");
            if (!StringUtils.hasText(name) || "姓名".equals(name)) {
                continue;
            }
            String sourceHash = rowHash("PERSONNEL", document, sheetName, row.rowNumber(), row.values());
            OpsMaintenancePersonnel personnel = selectPersonnelByHash(sourceHash);
            boolean inserted = personnel == null;
            if (personnel == null) {
                personnel = new OpsMaintenancePersonnel();
                personnel.setVisitId(visit.getId());
                personnel.setDeleted(0);
            }
            personnel.setVisitId(visit.getId());
            personnel.setPersonName(name);
            personnel.setPhone(trimToNull(first(row, header, "电话", "手机", "联系方式")));
            personnel.setRoleName(defaultIfBlank(first(row, header, "角色", "岗位"), "现场运维"));
            personnel.setNotes(trimToNull(first(row, header, "备注")));
            applySource(personnel, document, sheetName, row.rowNumber(), sourceHash);
            if (inserted) {
                personnelMapper.insert(personnel);
                context.importedPersonnel++;
            } else {
                personnelMapper.updateById(personnel);
            }
        }
    }

    private void parseFindings(OpsMaintenanceVisit visit, SourceDocument document, String sheetName, List<RowData> rows, HeaderMatch header, ImportContext context) {
        if (header == null) {
            return;
        }
        for (RowData row : rowsAfter(rows, header)) {
            FindingFields fields = findingFields(row, header);
            upsertFinding(visit, document, sheetName, row.rowNumber(), row.values(), fields, context);
        }
    }

    private void parseChecklistFindings(OpsMaintenanceVisit visit, SourceDocument document, String sheetName, List<RowData> rows, HeaderMatch header, ImportContext context) {
        if (header == null || header.indexOf("检查项") < 0) {
            return;
        }
        int itemIndex = header.indexOf("检查项");
        int categoryIndex = header.indexOf("分类");
        RowData headerRow = header.row();
        for (RowData row : rowsAfter(rows, header)) {
            String item = valueAt(row, itemIndex);
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String category = valueAt(row, categoryIndex);
            for (int column = itemIndex + 1; column < row.values().size(); column++) {
                String cell = valueAt(row, column);
                if (!StringUtils.hasText(cell) || isNormalMark(cell)) {
                    continue;
                }
                String floor = valueAt(headerRow, column);
                if (!StringUtils.hasText(floor) || "备注".equals(floor)) {
                    continue;
                }
                String issue = compactJoin("；", category, item, cell);
                FindingFields fields = new FindingFields(floor, null, issue, cell, inferCompletion(cell), null, null);
                upsertFinding(visit, document, sheetName, row.rowNumber() * 1000 + column, row.values(), fields, context);
            }
        }
    }

    private void parseQuoteItems(OpsMaintenanceVisit visit, SourceDocument document, String sheetName, List<RowData> rows, HeaderMatch header, ImportContext context) {
        if (header == null) {
            return;
        }
        for (RowData row : rowsAfter(rows, header)) {
            String itemName = first(row, header, "事项", "设备", "灯具名称", "名称", "fixture name", "description");
            String areaName = first(row, header, "区域", "area", "楼层");
            if (!StringUtils.hasText(itemName) && !StringUtils.hasText(areaName)) {
                continue;
            }
            if (isSummaryRow(itemName) || isSummaryRow(areaName)) {
                continue;
            }
            String sourceHash = rowHash("QUOTE", document, sheetName, row.rowNumber(), row.values());
            OpsMaintenanceQuoteItem item = selectQuoteItemByHash(sourceHash);
            boolean inserted = item == null;
            if (item == null) {
                item = new OpsMaintenanceQuoteItem();
                item.setVisitId(visit.getId());
                item.setDeleted(0);
            }
            BigDecimal quantity = parseDecimal(first(row, header, "数量", "qty"));
            BigDecimal unitPrice = parseDecimal(first(row, header, "单价", "unit price"));
            BigDecimal amount = parseDecimal(first(row, header, "金额", "合计", "total"));
            if (amount == null && quantity != null && unitPrice != null) {
                amount = quantity.multiply(unitPrice);
            }
            item.setVisitId(visit.getId());
            item.setAreaName(trimToNull(areaName));
            item.setItemName(defaultIfBlank(itemName, areaName));
            item.setQuantity(quantity == null ? BigDecimal.ZERO : quantity);
            item.setUnitName(trimToNull(first(row, header, "单位", "unit")));
            item.setUnitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice);
            item.setAmount(amount == null ? BigDecimal.ZERO : amount);
            item.setNotes(trimToNull(first(row, header, "备注", "说明")));
            applySource(item, document, sheetName, row.rowNumber(), sourceHash);
            if (inserted) {
                quoteItemMapper.insert(item);
                context.importedQuoteItems++;
            } else {
                quoteItemMapper.updateById(item);
            }
        }
    }

    private void upsertFinding(OpsMaintenanceVisit visit, SourceDocument document, String sheetName, int rowNumber, List<String> rowValues, FindingFields fields, ImportContext context) {
        if (!StringUtils.hasText(fields.issueDescription()) || isIgnorableFinding(fields.issueDescription())) {
            context.skippedRows++;
            return;
        }
        if (isSummaryRow(fields.issueDescription())) {
            context.skippedRows++;
            return;
        }
        String sourceHash = rowHash("FINDING", document, sheetName, rowNumber, rowValues);
        OpsMaintenanceFinding finding = selectFindingByHash(sourceHash);
        boolean inserted = finding == null;
        if (finding == null) {
            finding = new OpsMaintenanceFinding();
            finding.setVisitId(visit.getId());
            finding.setCreatedBy(currentUserAccessService.getCurrentUser().getId());
            finding.setDeleted(0);
        }
        boolean unresolved = isUnresolved(fields.completionStatus(), fields.handlingResult(), fields.issueDescription());
        finding.setVisitId(visit.getId());
        finding.setFloorName(trimToNull(fields.floorName()));
        finding.setAreaName(trimToNull(fields.areaName()));
        finding.setIssueDescription(fields.issueDescription());
        finding.setHandlingResult(trimToNull(fields.handlingResult()));
        finding.setCompletionStatus(defaultIfBlank(fields.completionStatus(), inferCompletion(compactJoin(" ", fields.handlingResult(), fields.issueDescription()))));
        finding.setCauseAnalysis(trimToNull(fields.causeAnalysis()));
        finding.setFollowUpAction(trimToNull(fields.followUpAction()));
        finding.setQuoteRequired(containsAny(compactJoin(" ", fields.followUpAction(), fields.handlingResult(), fields.issueDescription()), "报价", "更换", "维修") ? 1 : 0);
        finding.setKnowledgeIncluded(unresolved ? 0 : 1);
        finding.setFoundAt(visit.getPlannedStartAt());
        finding.setUpdatedBy(currentUserAccessService.getCurrentUser().getId());
        applySource(finding, document, sheetName, rowNumber, sourceHash);
        if (inserted) {
            findingMapper.insert(finding);
            context.importedFindings++;
        } else {
            findingMapper.updateById(finding);
        }
        if (inserted && !unresolved && StringUtils.hasText(finding.getHandlingResult())) {
            knowledgeService.syncFromMaintenanceFinding(visit, finding);
            context.importedKnowledge++;
        }
    }

    private FindingFields findingFields(RowData row, HeaderMatch header) {
        String floor = first(row, header, "楼层", "floor");
        String area = first(row, header, "位置/区域", "位置", "区域", "涉及点位", "area");
        String issue = first(row, header, "问题描述", "现场巡检问题", "具体问题", "主要表现", "问题", "检查项");
        String handling = first(row, header, "处理情况", "进一步查验情况", "处理建议", "查验情况", "处理");
        String completion = first(row, header, "完成情况", "进展", "已解决", "待解决", "状态");
        String cause = first(row, header, "原因", "原因分析", "问题类别", "分类");
        String followUp = first(row, header, "下一步行动", "后续动作", "后续维修", "整改建议");
        return new FindingFields(floor, area, issue, handling, completion, cause, followUp);
    }

    private HeaderMatch findHeader(List<RowData> rows) {
        for (RowData row : rows) {
            long meaningful = row.values().stream().filter(StringUtils::hasText).count();
            if (meaningful >= 2 && row.values().stream().anyMatch(value -> containsAny(value, "楼层", "问题", "事项", "姓名", "区域", "检查项", "报价", "数量"))) {
                return new HeaderMatch(row, buildHeaderMap(row));
            }
        }
        return null;
    }

    private Map<String, Integer> buildHeaderMap(RowData row) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int i = 0; i < row.values().size(); i++) {
            String value = normalize(row.values().get(i));
            if (StringUtils.hasText(value)) {
                headers.put(value, i);
            }
        }
        return headers;
    }

    private List<RowData> readRows(Sheet sheet, DataFormatter formatter) {
        List<RowData> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<String> values = new ArrayList<>();
            short lastCellNum = row.getLastCellNum();
            if (lastCellNum < 0) {
                continue;
            }
            for (int i = 0; i < Math.min(lastCellNum, 60); i++) {
                Cell cell = row.getCell(i);
                values.add(cellText(cell, formatter));
            }
            if (values.stream().anyMatch(StringUtils::hasText)) {
                rows.add(new RowData(row.getRowNum() + 1, values));
            }
        }
        return rows;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        try {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toString();
            }
        } catch (RuntimeException ignored) {
        }
        return formatter.formatCellValue(cell).trim();
    }

    private List<RowData> rowsAfter(List<RowData> rows, HeaderMatch header) {
        return rows.stream().filter(row -> row.rowNumber() > header.row().rowNumber()).toList();
    }

    private boolean isPersonnelSheet(String normalizedSheet, HeaderMatch header) {
        return normalizedSheet.contains("人员") || normalizedSheet.contains("报备")
            || (header != null && header.indexOf("姓名") >= 0 && header.indexOf("电话") >= 0);
    }

    private boolean isQuoteSheet(String normalizedSheet, String normalizedFile, HeaderMatch header) {
        return normalizedSheet.contains("报价") || normalizedFile.contains("报价")
            || normalizedSheet.contains("备品报价清单")
            || (header != null && header.indexOf("数量") >= 0 && (header.indexOf("事项") >= 0 || header.indexOf("灯具名称") >= 0 || header.indexOf("设备") >= 0));
    }

    private boolean isFindingSheet(String normalizedSheet, HeaderMatch header) {
        return normalizedSheet.contains("运维记录") || normalizedSheet.contains("灯控运维汇总")
            || normalizedSheet.contains("检查详细记录表") || normalizedSheet.contains("问题分类汇总")
            || (header != null && (header.indexOf("问题描述") >= 0 || header.indexOf("现场巡检问题") >= 0 || header.indexOf("具体问题") >= 0));
    }

    private boolean isChecklistSheet(String normalizedSheet, HeaderMatch header) {
        return normalizedSheet.contains("检查清单") || normalizedSheet.equals("记录")
            || (header != null && header.indexOf("检查项") >= 0);
    }

    private OpsMaintenanceVisit selectVisitByHash(String sourceHash) {
        return visitMapper.selectOne(new LambdaQueryWrapper<OpsMaintenanceVisit>().eq(OpsMaintenanceVisit::getSourceHash, sourceHash).last("LIMIT 1"));
    }

    private OpsMaintenanceAssignment selectAssignmentByHash(String sourceHash) {
        return assignmentMapper.selectOne(new LambdaQueryWrapper<OpsMaintenanceAssignment>().eq(OpsMaintenanceAssignment::getSourceHash, sourceHash).last("LIMIT 1"));
    }

    private OpsMaintenancePersonnel selectPersonnelByHash(String sourceHash) {
        return personnelMapper.selectOne(new LambdaQueryWrapper<OpsMaintenancePersonnel>().eq(OpsMaintenancePersonnel::getSourceHash, sourceHash).last("LIMIT 1"));
    }

    private OpsMaintenanceFinding selectFindingByHash(String sourceHash) {
        return findingMapper.selectOne(new LambdaQueryWrapper<OpsMaintenanceFinding>().eq(OpsMaintenanceFinding::getSourceHash, sourceHash).last("LIMIT 1"));
    }

    private OpsMaintenanceQuoteItem selectQuoteItemByHash(String sourceHash) {
        return quoteItemMapper.selectOne(new LambdaQueryWrapper<OpsMaintenanceQuoteItem>().eq(OpsMaintenanceQuoteItem::getSourceHash, sourceHash).last("LIMIT 1"));
    }

    private void upsertSourceFile(OpsMaintenanceVisit visit, String projectName, SourceDocument document, String status, String message) {
        String sourceHash = sha256("FILE|" + document.displayPath());
        OpsMaintenanceSourceFile file = sourceFileMapper.selectOne(new LambdaQueryWrapper<OpsMaintenanceSourceFile>()
            .eq(OpsMaintenanceSourceFile::getSourceHash, sourceHash)
            .last("LIMIT 1"));
        if (file == null) {
            file = new OpsMaintenanceSourceFile();
            file.setSourceHash(sourceHash);
            file.setCreateTime(LocalDateTime.now());
            file.setDeleted(0);
        }
        file.setVisitId(visit.getId());
        file.setProjectName(projectName);
        file.setFileType(document.extension().replace(".", "").toUpperCase(Locale.ROOT));
        file.setFileName(document.fileName());
        file.setFilePath(document.outerPath());
        file.setZipEntryPath(document.zipEntryPath());
        file.setImportStatus(status);
        file.setMessage(message);
        file.setUpdateTime(LocalDateTime.now());
        if (file.getId() == null) {
            sourceFileMapper.insert(file);
        } else {
            sourceFileMapper.updateById(file);
        }
    }

    private Project findProject(String projectName) {
        return projectMapper.selectOne(new LambdaQueryWrapper<Project>()
            .eq(Project::getProjectName, projectName)
            .eq(Project::getDeleted, 0)
            .last("LIMIT 1"));
    }

    private String inferProjectName(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.contains("abb") && normalized.contains("p6")) {
            return "上海ABB-P6";
        }
        if (normalized.contains("bp")) {
            return "上海BP办公楼";
        }
        if (normalized.contains("loreal") || path.contains("欧莱雅")) {
            if (normalized.contains("jh") || path.contains("静华")) {
                return "上海欧莱雅静华";
            }
            return "上海欧莱雅越洋";
        }
        return null;
    }

    private VisitKey inferVisitKey(SourceDocument document, String projectName) {
        LocalDate date = extractDate(document.displayPath());
        Integer quarter = extractQuarter(document.displayPath());
        if (date == null) {
            int year = extractYear(document.displayPath());
            int safeQuarter = quarter == null ? 1 : quarter;
            date = LocalDate.of(year, (safeQuarter - 1) * 3 + 1, 1);
        }
        if (quarter == null) {
            quarter = (date.getMonthValue() - 1) / 3 + 1;
        }
        String sourceGroup = document.sourceGroup();
        return new VisitKey(date, date.getYear(), quarter, projectName + "|" + date.getYear() + "Q" + quarter, sourceGroup);
    }

    private LocalDate extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        LocalDate latest = null;
        while (matcher.find()) {
            try {
                latest = LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            } catch (RuntimeException ignored) {
            }
        }
        if (latest != null) {
            return latest;
        }
        Matcher shortMatcher = SHORT_YEAR_DATE_PATTERN.matcher(text);
        while (shortMatcher.find()) {
            try {
                int year = 2000 + Integer.parseInt(shortMatcher.group(1));
                int month = Integer.parseInt(shortMatcher.group(2));
                int day = Integer.parseInt(shortMatcher.group(3));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    latest = LocalDate.of(year, month, day);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return latest;
    }

    private Integer extractQuarter(String text) {
        if (containsAny(text, "第一季度", "q1", "Q1")) return 1;
        if (containsAny(text, "第二季度", "q2", "Q2")) return 2;
        if (containsAny(text, "第三季度", "q3", "Q3")) return 3;
        if (containsAny(text, "第四季度", "q4", "Q4")) return 4;
        return null;
    }

    private int extractYear(String text) {
        Matcher matcher = Pattern.compile("(20\\d{2})").matcher(text);
        int year = LocalDate.now().getYear();
        while (matcher.find()) {
            year = Integer.parseInt(matcher.group(1));
        }
        Matcher shortYear = Pattern.compile("(?<!\\d)(2[5-9]|3[0-9])年度").matcher(text);
        while (shortYear.find()) {
            year = 2000 + Integer.parseInt(shortYear.group(1));
        }
        return year;
    }

    private LocalDateTime combineDateTime(String dateText, String timeText, LocalDateTime fallback) {
        LocalDate date = parseDate(dateText);
        LocalTime time = parseTime(timeText);
        if (date == null && fallback != null) {
            date = fallback.toLocalDate();
        }
        if (date == null) {
            return fallback;
        }
        return LocalDateTime.of(date, time == null ? LocalTime.MIN : time);
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.matches("\\d{5}(\\.0)?")) {
                return LocalDate.of(1899, 12, 30).plusDays(Long.parseLong(text.replace(".0", "")));
            }
            return LocalDate.parse(text.substring(0, Math.min(text.length(), 10)));
        } catch (RuntimeException ignored) {
            return extractDate(text);
        }
    }

    private LocalTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String text = value.trim();
            if (text.length() == 5) {
                text = text + ":00";
            }
            return LocalTime.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String first(RowData row, HeaderMatch header, String... keys) {
        for (String key : keys) {
            int index = header.indexOf(key);
            if (index >= 0) {
                String value = valueAt(row, index);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private String valueAt(RowData row, int index) {
        if (index < 0 || index >= row.values().size()) {
            return null;
        }
        return row.values().get(index);
    }

    private boolean isNormalMark(String value) {
        String text = value.trim();
        return "√".equals(text) || "✓".equals(text) || "正常".equals(text) || "ok".equalsIgnoreCase(text);
    }

    private boolean isIgnorableFinding(String value) {
        String text = normalize(value);
        return text.isBlank() || text.equals("无异常") || text.equals("无") || text.equals("正常");
    }

    private boolean isSummaryRow(String value) {
        return StringUtils.hasText(value) && containsAny(value, "合计", "小计", "完成率", "异常数量", "正常记录", "检查问题分类汇总");
    }

    private String inferCompletion(String text) {
        if (containsAny(text, "待整改", "待解决", "待确认", "待核对", "未解决")) return "待整改";
        if (containsAny(text, "已解决", "已修复", "完成", "正常", "√")) return "已解决";
        return "已记录";
    }

    private boolean isUnresolved(String completion, String handling, String issue) {
        String text = compactJoin(" ", completion, handling, issue);
        return containsAny(text, "待整改", "待解决", "待确认", "待核对", "未解决");
    }

    private boolean containsAny(String value, String... tokens) {
        String text = defaultIfBlank(value, "").toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replaceAll("[^0-9.\\-]", "");
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String rowHash(String prefix, SourceDocument document, String sheetName, int rowNumber, List<String> rowValues) {
        return sha256(prefix + "|" + document.displayPath() + "|" + sheetName + "|" + rowNumber + "|" + String.join("|", rowValues));
    }

    private void applySource(OpsMaintenanceAssignment entity, SourceDocument document, String sheetName, Integer rowNumber, String sourceHash) {
        entity.setSourceFilePath(document.displayPath());
        entity.setSourceSheet(sheetName);
        entity.setSourceRowNumber(rowNumber);
        entity.setSourceHash(sourceHash);
    }

    private void applySource(OpsMaintenancePersonnel entity, SourceDocument document, String sheetName, Integer rowNumber, String sourceHash) {
        entity.setSourceFilePath(document.displayPath());
        entity.setSourceSheet(sheetName);
        entity.setSourceRowNumber(rowNumber);
        entity.setSourceHash(sourceHash);
    }

    private void applySource(OpsMaintenanceFinding entity, SourceDocument document, String sheetName, Integer rowNumber, String sourceHash) {
        entity.setSourceFilePath(document.displayPath());
        entity.setSourceSheet(sheetName);
        entity.setSourceRowNumber(rowNumber);
        entity.setSourceHash(sourceHash);
    }

    private void applySource(OpsMaintenanceQuoteItem entity, SourceDocument document, String sheetName, Integer rowNumber, String sourceHash) {
        entity.setSourceFilePath(document.displayPath());
        entity.setSourceSheet(sheetName);
        entity.setSourceRowNumber(rowNumber);
        entity.setSourceHash(sourceHash);
    }

    private String normalizeZipEntryName(String name) {
        return name;
    }

    private String extensionOf(String value) {
        int index = value == null ? -1 : value.lastIndexOf('.');
        return index < 0 ? "" : value.substring(index).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return defaultIfBlank(value, "").replaceAll("[\\s/／\\\\()（）\\n\\r]+", "").toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
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

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor == null || cursor.getMessage() == null ? throwable.getClass().getSimpleName() : cursor.getMessage();
    }

    private record SourceDocument(Path root, Path file, String relativePath, String extension, String zipEntryRaw, String zipEntryPath) {
        static SourceDocument local(Path root, Path file, String relativePath, String extension) {
            return new SourceDocument(root, file, relativePath, extension, null, null);
        }

        static SourceDocument zipped(Path zipFile, String relativeZipPath, String zipEntryRaw, String zipEntryPath, String extension) {
            return new SourceDocument(null, zipFile, relativeZipPath, extension, zipEntryRaw, zipEntryPath);
        }

        byte[] bytes() throws IOException {
            if (zipEntryRaw == null) {
                return Files.readAllBytes(file);
            }
            try (ZipFile zipFile = new ZipFile(file.toFile(), Charset.forName("GBK"))) {
                return zipFile.getInputStream(zipFile.getEntry(zipEntryRaw)).readAllBytes();
            } catch (IOException gbkFailure) {
                try (ZipFile zipFile = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
                    return zipFile.getInputStream(zipFile.getEntry(zipEntryRaw)).readAllBytes();
                }
            }
        }

        String displayPath() {
            return zipEntryPath == null ? relativePath : relativePath + "::" + zipEntryPath;
        }

        String outerPath() {
            return relativePath;
        }

        String fileName() {
            if (zipEntryPath != null) {
                int index = zipEntryPath.lastIndexOf('/');
                return index < 0 ? zipEntryPath : zipEntryPath.substring(index + 1);
            }
            int index = relativePath.lastIndexOf('/');
            return index < 0 ? relativePath : relativePath.substring(index + 1);
        }

        String sourceGroup() {
            String path = zipEntryPath == null ? relativePath : relativePath + "::" + zipEntryPath;
            int index = path.lastIndexOf('/');
            return index < 0 ? path : path.substring(0, index);
        }
    }

    private record VisitKey(LocalDate date, int year, int quarter, String periodKey, String sourceGroup) {
    }

    private record RowData(int rowNumber, List<String> values) {
    }

    private record HeaderMatch(RowData row, Map<String, Integer> headers) {
        int indexOf(String key) {
            String normalizedKey = key == null ? "" : key.replaceAll("[\\s/／\\\\()（）\\n\\r]+", "").toLowerCase(Locale.ROOT);
            if (headers.containsKey(normalizedKey)) {
                return headers.get(normalizedKey);
            }
            for (Map.Entry<String, Integer> entry : headers.entrySet()) {
                if (entry.getKey().contains(normalizedKey) || normalizedKey.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return -1;
        }
    }

    private record FindingFields(String floorName, String areaName, String issueDescription, String handlingResult, String completionStatus, String causeAnalysis, String followUpAction) {
    }

    private class ImportContext {
        private final Path rootPath;
        private int scannedFiles;
        private int recognizedProjects;
        private int importedVisits;
        private int importedAssignments;
        private int importedPersonnel;
        private int importedFindings;
        private int importedQuoteItems;
        private int importedKnowledge;
        private int skippedRows;
        private int failedRows;
        private final Set<String> recognizedProjectNames = new HashSet<>();
        private final List<MaintenanceImportReportVO.RowMessage> messages = new ArrayList<>();

        private ImportContext(Path rootPath) {
            this.rootPath = rootPath;
        }

        void skip(String projectName, String filePath, String sheetName, Integer rowNumber, String message) {
            skippedRows++;
            add("WARN", projectName, filePath, sheetName, rowNumber, message);
        }

        void warn(String projectName, String filePath, String sheetName, Integer rowNumber, String message) {
            add("WARN", projectName, filePath, sheetName, rowNumber, message);
        }

        void fail(String projectName, String filePath, String sheetName, Integer rowNumber, String message) {
            failedRows++;
            add("ERROR", projectName, filePath, sheetName, rowNumber, message);
        }

        void add(String level, String projectName, String filePath, String sheetName, Integer rowNumber, String message) {
            if (messages.size() >= 300) {
                return;
            }
            messages.add(MaintenanceImportReportVO.RowMessage.builder()
                .level(level)
                .projectName(projectName)
                .filePath(filePath)
                .sheetName(sheetName)
                .rowNumber(rowNumber)
                .message(message)
                .build());
        }

        MaintenanceImportReportVO toReport() {
            return MaintenanceImportReportVO.builder()
                .rootPath(rootPath.toString())
                .scannedFiles(scannedFiles)
                .recognizedProjects(recognizedProjects)
                .importedVisits(importedVisits)
                .importedAssignments(importedAssignments)
                .importedPersonnel(importedPersonnel)
                .importedFindings(importedFindings)
                .importedQuoteItems(importedQuoteItems)
                .importedKnowledge(importedKnowledge)
                .skippedRows(skippedRows)
                .failedRows(failedRows)
                .messages(messages)
                .build();
        }
    }
}
