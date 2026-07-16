package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MaintenanceImportReportVO {
    private String rootPath;
    private Integer scannedFiles;
    private Integer recognizedProjects;
    private Integer importedVisits;
    private Integer importedAssignments;
    private Integer importedPersonnel;
    private Integer importedFindings;
    private Integer importedQuoteItems;
    private Integer importedKnowledge;
    private Integer skippedRows;
    private Integer failedRows;
    private List<RowMessage> messages;

    @Data
    @Builder
    public static class RowMessage {
        private String level;
        private String projectName;
        private String filePath;
        private String sheetName;
        private Integer rowNumber;
        private String message;
    }
}
