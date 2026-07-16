package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeVO {
    private Long id;
    private Long issueId;
    private String issueNo;
    private Long projectId;
    private String sourceType;
    private String sourceRefType;
    private Long sourceRefId;
    private String sourceName;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String customerName;
    private String projectGroup;
    private String projectName;
    private String projectCode;
    private String title;
    private String faultCode;
    private String symptomSummary;
    private String causeSummary;
    private String solutionSummary;
    private String preventionSummary;
    private String tags;
    private String status;
    private Integer qualityScore;
    private String qualityStatus;
    private String qualityIssues;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
