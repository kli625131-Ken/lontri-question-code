package com.problem.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExcelImportParser {

    private static final String SHEET_TEMPLATE = "\u95ee\u9898\u5206\u6790\u6a21\u677f";
    private static final String SHEET_CONTACT = "\u9879\u76ee\u8fd0\u7ef4\u8054\u7cfb\u4eba";
    private static final String SHEET_CATEGORY = "\u95ee\u9898\u5206\u7c7b";
    private static final String SHEET_WARRANTY = "\u5408\u540c\u4e0e\u8d28\u4fdd";

    private static final String ROW_TYPE_ISSUE = "ISSUE";
    private static final String ROW_TYPE_CONTACT = "CONTACT";
    private static final String ROW_TYPE_WARRANTY = "WARRANTY";
    private static final String ROW_TYPE_CATEGORY = "CATEGORY";

    private static final String AUTO_APPROVED = "AUTO_APPROVED";
    private static final String NEEDS_REVIEW = "NEEDS_REVIEW";

    private static final String MSG_DATE_PENDING = "\u65e5\u671f\u5f85\u786e\u8ba4";
    private static final String MSG_DATE_NO_YEAR = "\u7f3a\u5c11\u5e74\u4efd\uff0c\u9700\u4eba\u5de5\u786e\u8ba4";
    private static final String MSG_DATE_INVALID = "\u65e5\u671f\u683c\u5f0f\u65e0\u6cd5\u89e3\u6790";
    private static final String MSG_MONTH_FILLED = "\u4ec5\u5305\u542b\u5e74\u6708\uff0c\u5df2\u9ed8\u8ba41\u53f7";
    private static final String MSG_STATUS_INFERRED = "\u72b6\u6001\u7531\u5904\u7406\u8fdb\u5c55\u63a8\u65ad\u4e3a\u5904\u7406\u4e2d";

    private static final String DEFAULT_SOURCE = "Excel/CSV \u5bfc\u5165";

    private static final String H_PROJECT = "\u9879\u76ee";
    private static final String H_POSITION = "\u804c\u4f4d";
    private static final String H_CONTACT_NAME = "\u8fd0\u7ef4\u8054\u7cfb\u4eba";
    private static final String H_CONTACT_INFO = "\u8054\u7cfb\u65b9\u5f0f";
    private static final String H_RESPONSIBILITY = "\u4e3b\u8981\u8d1f\u8d23\u4e8b\u9879";
    private static final String H_NOTES = "\u5907\u6ce8";

    private static final String H_LEVEL1 = "\u4e00\u7ea7\u5206\u7c7b";
    private static final String H_LEVEL2 = "\u4e8c\u7ea7\u5206\u7c7b";
    private static final String H_LEVEL3 = "\u4e09\u7ea7\u5206\u7c7b";
    private static final String H_PROBLEM_DESCRIPTION = "\u5177\u4f53\u95ee\u9898\u63cf\u8ff0";
    private static final String H_EXAMPLE_CASE = "\u6848\u5217";

    private static final String H_CONTRACT_SIGNED_AT = "\u5408\u540c\u7b7e\u8ba2\u65f6\u95f4";
    private static final String H_ACCEPTANCE_AT = "\u9a8c\u6536\u65f6\u95f4";
    private static final String H_WARRANTY_TERM = "\u8d28\u4fdd\u65f6\u95f4";
    private static final String H_EXPIRE_AT = "\u5230\u671f\u65f6\u95f4";
    private static final String H_CUSTOMER_NAME = "\u5ba2\u6237\u540d\u79f0";
    private static final String H_CONTRACT_TYPE = "\u5408\u540c\u7c7b\u578b";
    private static final String H_CONTRACT_RANGE = "\u5408\u540c\u671f\u9650";
    private static final String H_START_AT = "\u5f00\u59cb\u65e5\u671f";
    private static final String H_END_AT = "\u7ed3\u675f\u65e5\u671f";
    private static final String H_SERVICE_SCOPE = "\u670d\u52a1\u8303\u56f4";

    private static final String H_SOURCE = "\u95ee\u9898\u6765\u6e90";
    private static final String H_FEEDBACK_USER = "\u53cd\u9988\u4eba";
    private static final String H_OWNER = "\u5904\u7406\u4eba";
    private static final String H_OWNER_ALT = "\u8d1f\u8d23\u4eba";
    private static final String H_SITE_OWNER = "\u73b0\u573a\u5904\u7406\u4eba";
    private static final String H_COMPLETER = "\u5b8c\u6210\u4eba\u5458";

    private static final String H_PROJECT_NAME = "\u6240\u5c5e\u9879\u76ee";
    private static final String H_CUSTOMER = "\u5ba2\u6237";
    private static final String H_CATEGORY = "\u95ee\u9898\u5206\u7c7b";
    private static final String H_CATEGORY_ALT = "\u5206\u7c7b";
    private static final String H_PROBLEM_CATEGORY = "\u95ee\u9898\u5927\u7c7b";
    private static final String H_CAUSE_CATEGORY = "\u539f\u56e0\u5206\u7c7b";
    private static final String H_BUILDING = "\u5efa\u7b51/\u697c\u680b";
    private static final String H_FLOOR = "\u697c\u5c42";
    private static final String H_AREA = "\u533a\u57df/\u623f\u95f4";
    private static final String H_SYSTEM_TYPE = "\u7cfb\u7edf/\u8bbe\u5907\u7c7b\u578b";
    private static final String H_DEVICE_POINT = "\u8bbe\u5907\u7f16\u53f7/\u70b9\u4f4d";
    private static final String H_CUSTOMER_FEEDBACK = "\u5ba2\u6237\u53cd\u9988\u53e3\u5f84";
    private static final String H_REUSE_TAGS = "\u6807\u7b7e";
    private static final String H_FOUND_AT = "\u53d1\u73b0\u65f6\u95f4";
    private static final String H_RECEIVED_AT = "\u6536\u5230\u53cd\u9988\u65f6\u95f4";
    private static final String H_FEEDBACK_AT = "\u53cd\u9988\u65f6\u95f4";
    private static final String H_REQUEST_AT = "\u6536\u5230\u9700\u6c42\u65f6\u95f4";
    private static final String H_DESCRIPTION = "\u95ee\u9898\u63cf\u8ff0";
    private static final String H_ITEM = "\u4e8b\u9879";
    private static final String H_IMPACT_SCOPE = "\u5f71\u54cd\u8303\u56f4";
    private static final String H_SEVERITY = "\u4e25\u91cd\u7a0b\u5ea6";
    private static final String H_PRIORITY = "\u5904\u7406\u4f18\u5148\u7ea7";
    private static final String H_PRIORITY_ALT = "\u4f18\u5148\u7ea7";
    private static final String H_STATUS = "\u6700\u65b0\u72b6\u6001";
    private static final String H_STATUS_ALT = "\u72b6\u6001";
    private static final String H_COMPLETION = "\u5b8c\u6210\u60c5\u51b5";
    private static final String H_PROGRESS = "\u89e3\u51b3\u65b9\u6848/\u8fdb\u5c55";
    private static final String H_SOLUTION = "\u89e3\u51b3\u65b9\u6848";
    private static final String H_SALES_FEEDBACK = "\u552e\u540e\u53cd\u9988";
    private static final String H_COMPLETED_AT = "\u5b8c\u6210\u65f6\u95f4";
    private static final String H_SOLVED_AT = "\u89e3\u51b3\u65f6\u95f4";

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_CLOSED = "CLOSED";

    private static final List<String> CONTACT_HEADERS = List.of(
        "\u5e8f\u53f7", H_PROJECT, H_POSITION, H_CONTACT_NAME, H_CONTACT_INFO, H_RESPONSIBILITY, H_NOTES
    );
    private static final List<String> CATEGORY_HEADERS = List.of(
        H_LEVEL1, H_LEVEL2, H_LEVEL3, H_PROBLEM_DESCRIPTION, H_EXAMPLE_CASE
    );
    private static final List<String> WARRANTY_HEADERS = List.of(
        H_CUSTOMER_NAME, H_PROJECT, H_CONTRACT_TYPE, H_START_AT, H_END_AT, H_SERVICE_SCOPE, H_NOTES,
        H_CONTRACT_SIGNED_AT, H_ACCEPTANCE_AT, H_WARRANTY_TERM, H_EXPIRE_AT
    );
    private static final List<String> WARRANTY_PROJECT_HEADERS = List.of(H_PROJECT_NAME, H_PROJECT);
    private static final List<String> WARRANTY_CUSTOMER_HEADERS = List.of(H_CUSTOMER_NAME, H_CUSTOMER);
    private static final List<String> CONTRACT_TYPE_HEADERS = List.of(H_CONTRACT_TYPE, "\u7c7b\u578b", "\u5408\u540c\u540d\u79f0");
    private static final List<String> START_AT_HEADERS = List.of(H_START_AT, "\u8d77\u59cb\u65e5\u671f", "\u670d\u52a1\u5f00\u59cb\u65e5\u671f", "\u5408\u540c\u5f00\u59cb\u65e5\u671f");
    private static final List<String> END_AT_HEADERS = List.of(H_END_AT, H_EXPIRE_AT, "\u622a\u6b62\u65e5\u671f", "\u670d\u52a1\u7ed3\u675f\u65e5\u671f", "\u5408\u540c\u7ed3\u675f\u65e5\u671f");
    private static final List<String> CONTRACT_RANGE_HEADERS = List.of(H_CONTRACT_RANGE, "\u5408\u540c\u5468\u671f", "\u670d\u52a1\u671f", "\u8d28\u4fdd\u671f", H_WARRANTY_TERM);
    private static final List<String> SERVICE_SCOPE_HEADERS = List.of(H_SERVICE_SCOPE, "\u8303\u56f4", "\u7ef4\u4fdd\u8303\u56f4", "\u9879\u76ee\u8303\u56f4");

    private static final List<String> PROJECT_HEADERS = List.of(H_PROJECT_NAME, H_PROJECT, H_CUSTOMER);
    private static final List<String> SOURCE_HEADERS = List.of(H_SOURCE, H_FEEDBACK_USER, H_OWNER_ALT, H_OWNER);
    private static final List<String> CATEGORY_PATH_HEADERS = List.of(H_PROBLEM_CATEGORY, H_CATEGORY, H_CATEGORY_ALT);
    private static final List<String> FOUND_AT_HEADERS = List.of(H_FOUND_AT, H_RECEIVED_AT, H_FEEDBACK_AT, H_REQUEST_AT);
    private static final List<String> DESCRIPTION_HEADERS = List.of(H_DESCRIPTION, H_ITEM);
    private static final List<String> STATUS_HEADERS = List.of(H_STATUS, H_STATUS_ALT, H_COMPLETION);
    private static final List<String> PROGRESS_HEADERS = List.of(H_PROGRESS, H_SOLUTION, H_STATUS, H_COMPLETION, H_SALES_FEEDBACK);
    private static final List<String> OWNER_HEADERS = List.of(H_OWNER, H_SITE_OWNER, H_COMPLETER, H_OWNER_ALT);
    private static final List<String> COMPLETED_AT_HEADERS = List.of(H_COMPLETED_AT, H_SOLVED_AT);
    private static final List<String> PRIORITY_HEADERS = List.of(H_PRIORITY, H_PRIORITY_ALT);

    private static final Set<String> CLOSED_KEYWORDS = Set.of(
        "\u5df2\u5b8c\u6210", "\u5b8c\u6210", "\u5df2\u5173\u95ed", "\u5173\u95ed", "closed", "close", "\u53d6\u6d88"
    );
    private static final Set<String> IN_PROGRESS_KEYWORDS = Set.of(
        "\u5904\u7406\u4e2d", "\u8fdb\u884c\u4e2d", "\u8ddf\u8fdb\u4e2d", "\u6392\u67e5\u4e2d", "\u4fee\u590d\u4e2d", "\u5f85\u5230\u8d27"
    );
    private static final Set<String> OPEN_KEYWORDS = Set.of(
        "\u5f85\u5904\u7406", "\u5f85\u5b9a", "\u5f85\u53cd\u9988", "\u5f85\u786e\u8ba4", "open", "\u672a\u5904\u7406"
    );
    private static final Set<String> CLOSED_PROGRESS_KEYWORDS = Set.of(
        "\u6b63\u5e38", "\u53ef\u6b63\u5e38", "\u6062\u590d", "\u5df2\u6062\u590d", "\u6062\u590d\u6b63\u5e38",
        "\u5df2\u89e3\u51b3", "\u89e3\u51b3", "\u5df2\u66f4\u6362", "\u66f4\u6362\u81f3\u6b63\u5e38\u4f4d\u7f6e", "\u5df2\u4fee\u6539",
        "\u5df2\u914d\u7f6e", "\u5df2\u8c03\u6574", "\u5df2\u6309", "\u5df2\u91cd\u65b0", "\u5df2\u5904\u7406", "\u6b63\u5e38\u6a21\u5f0f"
    );
    private static final Set<String> UNCLOSED_PROGRESS_KEYWORDS = Set.of(
        "\u9700\u8981", "\u5f85", "\u6d4b\u8bd5", "\u89c2\u5bdf", "\u5076\u53d1", "\u65e0\u8fd4\u56de",
        "\u65e0\u6cd5", "\u672a", "\u8ba1\u5212", "\u9884\u8ba1", "\u6392\u67e5", "\u6293\u5305", "\u90ae\u4ef6",
        "\u6c9f\u901a", "\u5f85\u5b9a", "\u4e0d\u5173\u706f", "\u4e0d\u4f7f\u7528"
    );

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");

    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public ParsedWorkbook parse(InputStream inputStream, int defaultRemindAfterDays) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<ParsedRow> rows = new ArrayList<>();
            Map<String, Integer> sheetStats = new LinkedHashMap<>();
            for (Sheet sheet : workbook) {
                List<ParsedRow> sheetRows = parseSheet(sheet, defaultRemindAfterDays);
                rows.addAll(sheetRows);
                sheetStats.put(sheet.getSheetName(), sheetRows.size());
            }
            return new ParsedWorkbook(rows, sheetStats);
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel \u89e3\u6790\u5931\u8d25: " + e.getMessage(), e);
        }
    }

    private List<ParsedRow> parseSheet(Sheet sheet, int defaultRemindAfterDays) {
        String sheetName = sheet.getSheetName().trim();
        if (SHEET_TEMPLATE.equals(sheetName)) {
            return List.of();
        }
        if (SHEET_CONTACT.equals(sheetName)) {
            return parseContactSheet(sheet);
        }
        if (SHEET_CATEGORY.equals(sheetName)) {
            return parseCategorySheet(sheet);
        }
        if (SHEET_WARRANTY.equals(sheetName)) {
            return parseWarrantySheet(sheet);
        }
        if (isWarrantySheet(sheet)) {
            return parseWarrantySheet(sheet);
        }
        return parseIssueSheet(sheet, defaultRemindAfterDays);
    }

    private List<ParsedRow> parseContactSheet(Sheet sheet) {
        List<ParsedRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Map<String, String> raw = rowToMap(sheet.getRow(rowIndex), CONTACT_HEADERS);
            if (!hasBusinessValue(raw, H_PROJECT, H_CONTACT_NAME)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("projectName", value(raw, H_PROJECT));
            normalized.put("positionTitle", value(raw, H_POSITION));
            normalized.put("contactName", value(raw, H_CONTACT_NAME));
            normalized.put("contactInfo", value(raw, H_CONTACT_INFO));
            normalized.put("responsibility", value(raw, H_RESPONSIBILITY));
            normalized.put("notes", value(raw, H_NOTES));
            rows.add(new ParsedRow(sheet.getSheetName(), rowIndex + 1, ROW_TYPE_CONTACT, AUTO_APPROVED, "", normalized, new LinkedHashMap<>(raw)));
        }
        return rows;
    }

    private List<ParsedRow> parseCategorySheet(Sheet sheet) {
        List<ParsedRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Map<String, String> raw = rowToMap(sheet.getRow(rowIndex), CATEGORY_HEADERS);
            if (!hasBusinessValue(raw, H_LEVEL1)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("level1", value(raw, H_LEVEL1));
            normalized.put("level2", value(raw, H_LEVEL2));
            normalized.put("level3", value(raw, H_LEVEL3));
            normalized.put("problemDescription", value(raw, H_PROBLEM_DESCRIPTION));
            normalized.put("exampleCase", value(raw, H_EXAMPLE_CASE));
            rows.add(new ParsedRow(sheet.getSheetName(), rowIndex + 1, ROW_TYPE_CATEGORY, AUTO_APPROVED, "", normalized, new LinkedHashMap<>(raw)));
        }
        return rows;
    }

    private List<ParsedRow> parseWarrantySheet(Sheet sheet) {
        HeaderInfo headerInfo = findWarrantyHeader(sheet);
        if (headerInfo == null) {
            headerInfo = new HeaderInfo(0, WARRANTY_HEADERS);
        }
        List<ParsedRow> rows = new ArrayList<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Map<String, String> raw = rowToMap(sheet.getRow(rowIndex), headerInfo.headers());
            String projectName = firstNonBlank(values(raw, WARRANTY_PROJECT_HEADERS), sheet.getSheetName());
            String customerName = firstNonBlank(values(raw, WARRANTY_CUSTOMER_HEADERS));
            if (!StringUtils.hasText(projectName) && !StringUtils.hasText(customerName)) {
                continue;
            }
            String contractRangeText = firstNonBlank(values(raw, CONTRACT_RANGE_HEADERS));
            DateRangeParseResult range = parseDateRange(contractRangeText);
            DateParseResult contractAt = parseDate(value(raw, H_CONTRACT_SIGNED_AT), null, null);
            DateParseResult acceptanceAt = parseDate(value(raw, H_ACCEPTANCE_AT), null, null);
            DateParseResult startAt = parseDate(firstNonBlank(values(raw, START_AT_HEADERS)), null, null);
            DateParseResult endAt = parseDate(firstNonBlank(values(raw, END_AT_HEADERS)), null, null);
            LocalDateTime normalizedStartAt = firstNonNull(startAt.value(), range.startAt(), acceptanceAt.value());
            LocalDateTime normalizedEndAt = firstNonNull(endAt.value(), range.endAt(), inferEndAtFromTerm(contractRangeText, normalizedStartAt));

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("customerName", customerName);
            normalized.put("projectName", StringUtils.hasText(projectName) ? projectName : customerName);
            normalized.put("contractType", defaultIfBlank(firstNonBlank(values(raw, CONTRACT_TYPE_HEADERS)), "\u8d28\u4fdd"));
            normalized.put("startAt", asDateTimeString(normalizedStartAt));
            normalized.put("endAt", asDateTimeString(normalizedEndAt));
            normalized.put("serviceScope", firstNonBlank(values(raw, SERVICE_SCOPE_HEADERS)));
            normalized.put("notes", value(raw, H_NOTES));
            normalized.put("contractSignedAt", asDateTimeString(contractAt.value()));
            normalized.put("acceptanceAt", asDateTimeString(acceptanceAt.value()));
            normalized.put("warrantyTerm", contractRangeText);
            normalized.put("expireAt", asDateTimeString(normalizedEndAt));

            String reviewMessage = combineMessages(contractAt.message(), acceptanceAt.message(), startAt.message(), endAt.message(), range.message());
            rows.add(new ParsedRow(
                sheet.getSheetName(),
                rowIndex + 1,
                ROW_TYPE_WARRANTY,
                StringUtils.hasText(reviewMessage) ? NEEDS_REVIEW : AUTO_APPROVED,
                reviewMessage,
                normalized,
                new LinkedHashMap<>(raw)
            ));
        }
        return rows;
    }

    private boolean isWarrantySheet(Sheet sheet) {
        return findWarrantyHeader(sheet) != null
            || sheet.getSheetName().contains("\u5408\u540c")
            || sheet.getSheetName().contains("\u8d28\u4fdd");
    }

    private HeaderInfo findWarrantyHeader(Sheet sheet) {
        int maxHeaderRow = Math.min(sheet.getLastRowNum(), 5);
        for (int rowIndex = 0; rowIndex <= maxHeaderRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            List<String> headers = new ArrayList<>();
            for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                headers.add(normalizeHeader(cellString(row.getCell(cellIndex))));
            }
            List<String> trimmed = trimTrailingBlanks(headers);
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean hasProject = containsAny(trimmed, WARRANTY_PROJECT_HEADERS) || containsAny(trimmed, WARRANTY_CUSTOMER_HEADERS);
            boolean hasContract = containsAny(trimmed, CONTRACT_TYPE_HEADERS)
                || containsAny(trimmed, START_AT_HEADERS)
                || containsAny(trimmed, END_AT_HEADERS)
                || containsAny(trimmed, CONTRACT_RANGE_HEADERS)
                || containsAny(trimmed, SERVICE_SCOPE_HEADERS);
            if (hasProject && hasContract) {
                return new HeaderInfo(rowIndex, trimmed);
            }
        }
        return null;
    }

    private List<ParsedRow> parseIssueSheet(Sheet sheet, int defaultRemindAfterDays) {
        HeaderInfo headerInfo = findIssueHeader(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<RowYearHint> rowYearHints = buildIssueRowYearHints(sheet, headerInfo);
        List<ParsedRow> rows = new ArrayList<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Map<String, String> raw = rowToMap(sheet.getRow(rowIndex), headerInfo.headers());
            String description = firstNonBlank(values(raw, DESCRIPTION_HEADERS));
            if (!StringUtils.hasText(description)) {
                continue;
            }

            String projectName = firstNonBlank(values(raw, PROJECT_HEADERS), sheet.getSheetName());
            String foundAtText = firstNonBlank(values(raw, FOUND_AT_HEADERS));
            String completedAtText = firstNonBlank(values(raw, COMPLETED_AT_HEADERS));

            Integer inferredYear = inferNearestYear(rowIndex, rowYearHints);
            DateParseResult foundAt = parseDate(foundAtText, inferredYear, null);
            Integer completedYearHint = foundAt.value() != null ? Integer.valueOf(foundAt.value().getYear()) : inferredYear;
            DateParseResult completedAt = parseDate(completedAtText, completedYearHint, foundAt.value());

            String rawStatus = firstNonBlank(value(raw, H_STATUS), value(raw, H_STATUS_ALT));
            String completionText = value(raw, H_COMPLETION);
            String progressText = firstNonBlank(
                value(raw, H_PROGRESS),
                value(raw, H_SOLUTION),
                value(raw, H_SALES_FEEDBACK),
                value(raw, H_STATUS)
            );
            StatusParseResult statusResult = parseStatus(rawStatus, completionText, completedAt.value(), progressText);

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("projectName", projectName);
            normalized.put("source", firstNonBlank(values(raw, SOURCE_HEADERS), DEFAULT_SOURCE));
            normalized.put("categoryPath", defaultIfBlank(buildCategoryPath(values(raw, CATEGORY_PATH_HEADERS)), "\u5f85\u786e\u8ba4\u95ee\u9898"));
            normalized.put("buildingName", defaultIfBlank(value(raw, H_BUILDING), "\u672a\u786e\u8ba4"));
            normalized.put("floorName", defaultIfBlank(value(raw, H_FLOOR), "\u672a\u786e\u8ba4"));
            normalized.put("areaName", defaultIfBlank(value(raw, H_AREA), "\u672a\u786e\u8ba4"));
            normalized.put("systemType", defaultIfBlank(value(raw, H_SYSTEM_TYPE), "\u672a\u786e\u8ba4"));
            normalized.put("devicePoint", defaultIfBlank(value(raw, H_DEVICE_POINT), "\u672a\u786e\u8ba4"));
            normalized.put("foundAt", asDateTimeString(foundAt.value()));
            normalized.put("foundAtText", foundAtText);
            normalized.put("itemTitle", description);
            normalized.put("description", description);
            normalized.put("impactScope", value(raw, H_IMPACT_SCOPE));
            normalized.put("severity", value(raw, H_SEVERITY));
            normalized.put("priority", defaultIfBlank(firstNonBlank(values(raw, PRIORITY_HEADERS)), "\u4e2d"));
            normalized.put("ownerName", firstNonBlank(values(raw, OWNER_HEADERS)));
            normalized.put("currentStatus", statusResult.status());
            normalized.put("closureStatus", statusResult.closureStatus());
            normalized.put("latestProgress", progressText);
            normalized.put("completedAt", asDateTimeString(completedAt.value()));
            normalized.put("completedAtText", completedAtText);
            normalized.put("notes", value(raw, H_NOTES));
            normalized.put("causeCategory", defaultIfBlank(value(raw, H_CAUSE_CATEGORY), "\u539f\u56e0\u5f85\u786e\u8ba4"));
            normalized.put("customerFeedback", value(raw, H_CUSTOMER_FEEDBACK));
            normalized.put("reuseTags", value(raw, H_REUSE_TAGS));
            normalized.put("reminderEnabled", 1);
            normalized.put("remindAfterDays", defaultRemindAfterDays);

            String reviewMessage = combineMessages(foundAt.message(), completedAt.message(), statusResult.reviewMessage());
            rows.add(new ParsedRow(
                sheet.getSheetName(),
                rowIndex + 1,
                ROW_TYPE_ISSUE,
                StringUtils.hasText(reviewMessage) ? NEEDS_REVIEW : AUTO_APPROVED,
                reviewMessage,
                normalized,
                new LinkedHashMap<>(raw)
            ));
        }
        return rows;
    }

    private List<RowYearHint> buildIssueRowYearHints(Sheet sheet, HeaderInfo headerInfo) {
        List<RowYearHint> hints = new ArrayList<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Map<String, String> raw = rowToMap(sheet.getRow(rowIndex), headerInfo.headers());
            Integer explicitYear = firstExplicitYear(
                firstNonBlank(values(raw, FOUND_AT_HEADERS)),
                firstNonBlank(values(raw, COMPLETED_AT_HEADERS))
            );
            if (explicitYear != null) {
                hints.add(new RowYearHint(rowIndex, explicitYear));
            }
        }
        return hints;
    }

    private Integer firstExplicitYear(String... values) {
        for (String value : values) {
            Integer year = extractExplicitYear(value);
            if (year != null) {
                return year;
            }
        }
        return null;
    }

    private Integer extractExplicitYear(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        return year >= 2000 && year <= 2100 ? year : null;
    }

    private Integer inferNearestYear(int rowIndex, List<RowYearHint> rowYearHints) {
        if (rowYearHints.isEmpty()) {
            return null;
        }
        RowYearHint nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (RowYearHint hint : rowYearHints) {
            int distance = Math.abs(hint.rowIndex() - rowIndex);
            if (distance < nearestDistance || (distance == nearestDistance && hint.rowIndex() < rowIndex)) {
                nearest = hint;
                nearestDistance = distance;
            }
        }
        return nearest == null ? null : nearest.year();
    }

    private HeaderInfo findIssueHeader(Sheet sheet) {
        int maxHeaderRow = Math.min(sheet.getLastRowNum(), 5);
        for (int rowIndex = 0; rowIndex <= maxHeaderRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            List<String> headers = new ArrayList<>();
            for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                headers.add(normalizeHeader(cellString(row.getCell(cellIndex))));
            }
            List<String> trimmed = trimTrailingBlanks(headers);
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean hasDescription = containsAny(trimmed, DESCRIPTION_HEADERS);
            boolean hasContext = containsAny(trimmed, FOUND_AT_HEADERS)
                || containsAny(trimmed, STATUS_HEADERS)
                || containsAny(trimmed, PROJECT_HEADERS)
                || containsAny(trimmed, CATEGORY_PATH_HEADERS);
            if (hasDescription && hasContext) {
                return new HeaderInfo(rowIndex, trimmed);
            }
        }
        return null;
    }

    private boolean containsAny(List<String> source, List<String> candidates) {
        for (String candidate : candidates) {
            if (source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> trimTrailingBlanks(List<String> values) {
        int end = values.size();
        while (end > 0 && !StringUtils.hasText(values.get(end - 1))) {
            end--;
        }
        return new ArrayList<>(values.subList(0, end));
    }

    private Map<String, String> rowToMap(Row row, List<String> headers) {
        Map<String, String> data = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            data.put(headers.get(index), row == null ? "" : cellString(row.getCell(index)));
        }
        return data;
    }

    private boolean hasBusinessValue(Map<String, String> raw, String... keys) {
        for (String key : keys) {
            if (StringUtils.hasText(raw.getOrDefault(key, ""))) {
                return true;
            }
        }
        return false;
    }

    private String value(Map<String, String> raw, String key) {
        return raw.getOrDefault(key, "").trim();
    }

    private String[] values(Map<String, String> raw, List<String> keys) {
        String[] result = new String[keys.size()];
        for (int index = 0; index < keys.size(); index++) {
            result[index] = value(raw, keys.get(index));
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String firstNonBlank(String[] values, String fallback) {
        String value = firstNonBlank(values);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String buildCategoryPath(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(" / ", parts);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalizeHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
            .replace('\u3000', ' ')
            .replace(" ", "")
            .replace("\u00a0", "")
            .replace("?", "/")
            .replace("|", "")
            .replace("?", "")
            .replace(":", "");
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }
        if (cellType == CellType.STRING) {
            return cell.getStringCellValue().replace('\n', ' ').trim();
        }
        if (cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().format(DATE_TIME_FORMATTER);
        }
        return formatter.formatCellValue(cell).replace('\n', ' ').trim();
    }

    private DateParseResult parseDate(String rawValue, Integer inferredYear, LocalDateTime referenceDate) {
        if (!StringUtils.hasText(rawValue)) {
            return new DateParseResult(null, "");
        }
        String value = rawValue.trim()
            .replace('\u5e74', '-')
            .replace('\u6708', '-')
            .replace("\u65e5", "")
            .replace('\uff0f', '/')
            .replace('\uff0e', '.')
            .replace('\u3002', '.')
            .replace('\uff1a', ':')
            .replace('/', '-')
            .replace('.', '-');

        if (equalsAnyIgnoreCase(value, "TBD") || equalsAny(value, "\u5f85\u5b9a", "\u5f85\u786e\u8ba4", "--")) {
            return new DateParseResult(null, MSG_DATE_PENDING);
        }

        try {
            if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{1,2}:\\d{1,2}")) {
                return new DateParseResult(LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-M-d H:m:s")), "");
            }
            if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{1,2}")) {
                return new DateParseResult(LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-M-d H:m")), "");
            }
            if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                return new DateParseResult(LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-M-d")).atStartOfDay(), "");
            }
            if (value.matches("\\d{4}-\\d{1,2}")) {
                return new DateParseResult(LocalDate.parse(value + "-01", DateTimeFormatter.ofPattern("yyyy-M-d")).atStartOfDay(), MSG_MONTH_FILLED);
            }
            if (value.matches("\\d{1,2}-\\d{1,2}")) {
                if (inferredYear == null) {
                    return new DateParseResult(null, MSG_DATE_NO_YEAR);
                }
                LocalDateTime inferredDate = LocalDate.parse(inferredYear + "-" + value, DateTimeFormatter.ofPattern("yyyy-M-d")).atStartOfDay();
                if (referenceDate != null
                    && referenceDate.getMonthValue() == 12
                    && inferredDate.getMonthValue() == 1
                    && inferredDate.isBefore(referenceDate)) {
                    inferredDate = inferredDate.plusYears(1);
                }
                return new DateParseResult(inferredDate, "");
            }
            if (value.matches("\\d+(\\.\\d+)?")) {
                LocalDateTime dateTime = DateUtil.getJavaDate(Double.parseDouble(value))
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
                return new DateParseResult(dateTime, "");
            }
        } catch (DateTimeParseException | NumberFormatException ignored) {
            return new DateParseResult(null, MSG_DATE_INVALID);
        }
        return new DateParseResult(null, MSG_DATE_INVALID);
    }

    private DateRangeParseResult parseDateRange(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return new DateRangeParseResult(null, null, "");
        }
        Matcher matcher = Pattern.compile("20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2}").matcher(rawValue);
        List<LocalDateTime> dates = new ArrayList<>();
        while (matcher.find()) {
            DateParseResult parsed = parseDate(matcher.group(), null, null);
            if (parsed.value() != null) {
                dates.add(parsed.value());
            }
        }
        if (dates.size() >= 2) {
            return new DateRangeParseResult(dates.get(0), dates.get(1), "");
        }
        return new DateRangeParseResult(null, null, "");
    }

    private LocalDateTime inferEndAtFromTerm(String warrantyTerm, LocalDateTime startAt) {
        if (startAt == null || !StringUtils.hasText(warrantyTerm)) {
            return null;
        }
        Matcher yearMatcher = Pattern.compile("(\\d+)\\s*\\u5e74").matcher(warrantyTerm);
        if (yearMatcher.find()) {
            return startAt.plusYears(Long.parseLong(yearMatcher.group(1))).minusDays(1);
        }
        Matcher monthMatcher = Pattern.compile("(\\d+)\\s*(\\u4e2a?\\u6708|\\u6708)").matcher(warrantyTerm);
        if (monthMatcher.find()) {
            return startAt.plusMonths(Long.parseLong(monthMatcher.group(1))).minusDays(1);
        }
        return null;
    }

    private LocalDateTime firstNonNull(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private StatusParseResult parseStatus(String rawStatus, String completionText, LocalDateTime completedAt, String progressText) {
        String merged = (firstNonBlank(rawStatus) + " " + firstNonBlank(completionText) + " " + firstNonBlank(progressText)).toLowerCase(Locale.ROOT);
        String progress = firstNonBlank(progressText).toLowerCase(Locale.ROOT);
        if (containsAnyKeyword(merged, CLOSED_KEYWORDS)) {
            return new StatusParseResult(STATUS_CLOSED, STATUS_CLOSED, "");
        }
        if (containsAnyKeyword(merged, IN_PROGRESS_KEYWORDS)) {
            return new StatusParseResult(STATUS_IN_PROGRESS, STATUS_OPEN, "");
        }
        if (containsAnyKeyword(merged, OPEN_KEYWORDS)) {
            return new StatusParseResult(STATUS_OPEN, STATUS_OPEN, "");
        }
        if (completedAt != null) {
            return new StatusParseResult(STATUS_CLOSED, STATUS_CLOSED, "");
        }
        if (StringUtils.hasText(progress)) {
            if (containsAnyKeyword(progress, CLOSED_PROGRESS_KEYWORDS)
                && !containsAnyKeyword(progress, UNCLOSED_PROGRESS_KEYWORDS)) {
                return new StatusParseResult(STATUS_CLOSED, STATUS_CLOSED, "");
            }
            return new StatusParseResult(STATUS_IN_PROGRESS, STATUS_OPEN, MSG_STATUS_INFERRED);
        }
        return new StatusParseResult(STATUS_OPEN, STATUS_OPEN, "");
    }

    private boolean containsAnyKeyword(String value, Set<String> keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsAnyIgnoreCase(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String combineMessages(String... messages) {
        List<String> values = new ArrayList<>();
        for (String message : messages) {
            if (StringUtils.hasText(message) && !values.contains(message)) {
                values.add(message);
            }
        }
        return String.join("\uff1b", values);
    }

    private String asDateTimeString(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    public record ParsedWorkbook(List<ParsedRow> rows, Map<String, Integer> sheetStats) {
    }

    public record ParsedRow(
        String sheetName,
        int rowNumber,
        String rowType,
        String reviewStatus,
        String reviewMessage,
        Map<String, Object> normalizedData,
        Map<String, Object> rawData
    ) {
    }

    private record HeaderInfo(int headerRowIndex, List<String> headers) {
    }

    private record DateParseResult(LocalDateTime value, String message) {
    }

    private record DateRangeParseResult(LocalDateTime startAt, LocalDateTime endAt, String message) {
    }

    private record StatusParseResult(String status, String closureStatus, String reviewMessage) {
    }

    private record RowYearHint(int rowIndex, int year) {
    }
}
