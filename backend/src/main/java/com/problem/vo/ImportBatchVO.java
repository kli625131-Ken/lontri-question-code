package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ImportBatchVO {
    private Long id;
    private String originalFileName;
    private String batchStatus;
    private Integer totalRows;
    private Integer reviewRows;
    private Integer readyRows;
    private Integer committedRows;
    private Integer skippedRows;
    private Map<String, Object> summary;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createTime;
}
