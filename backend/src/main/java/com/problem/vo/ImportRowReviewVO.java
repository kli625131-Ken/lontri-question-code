package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ImportRowReviewVO {
    private Long id;
    private String sheetName;
    private Integer rowNumber;
    private String rowType;
    private String reviewStatus;
    private String reviewMessage;
    private String commitStatus;
    private Map<String, Object> normalizedData;
    private Map<String, Object> rawData;
}
