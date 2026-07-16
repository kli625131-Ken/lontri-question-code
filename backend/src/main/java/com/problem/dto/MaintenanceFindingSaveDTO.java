package com.problem.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceFindingSaveDTO {
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
}
