package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class IssueListItemVO {
    private Long id;
    private String issueNo;
    private Long projectId;
    private String customerName;
    private String projectGroup;
    private String projectName;
    private String projectCode;
    private String source;
    private String sourceType;
    private String reporterName;
    private LocalDateTime receivedAt;
    private String itemTitle;
    private String description;
    private String categoryPath;
    private String buildingName;
    private String floorName;
    private String areaName;
    private String systemType;
    private String devicePoint;
    private LocalDateTime foundAt;
    private String impactScope;
    private String priority;
    private String severity;
    private String currentStatus;
    private String closureStatus;
    private String ownerName;
    private String latestProgress;
    private String completionStatus;
    private String customerFeedback;
    private String causeCategory;
    private String preventiveAction;
    private String reuseTags;
    private Integer knowledgeIncluded;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private boolean overdue;
    private Integer remindAfterDays;
    private List<String> matchReasons;
}
