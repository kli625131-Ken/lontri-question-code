package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeImportReportVO {
    private String fileName;
    private int sheetCount;
    private int totalRows;
    private int insertedRows;
    private int updatedRows;
    private int skippedRows;
    private int importedRows;
    private int reviewRows;
    private List<RowMessage> messages;

    @Data
    @Builder
    public static class RowMessage {
        private String level;
        private String sheetName;
        private Integer rowNumber;
        private String message;
    }
}
