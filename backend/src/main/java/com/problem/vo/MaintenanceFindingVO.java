package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MaintenanceFindingVO {
    private Long id;
    private Long visitId;
    private String floorName;
    private String areaName;
    private String issueDescription;
    private String handlingResult;
    private String completionStatus;
    private String causeAnalysis;
    private String followUpAction;
    private Integer quoteRequired;
    private Integer knowledgeIncluded;
    private LocalDateTime foundAt;
    private String sourceFilePath;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String sourceHash;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<MaintenanceAttachmentVO> attachments;
}
