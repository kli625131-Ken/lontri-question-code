package com.problem.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IssueUpdateDTO {
    private Long projectId;
    private String reporterName;
    private String categoryPath;
    private String source;
    private String buildingName;
    private String floorName;
    private String areaName;
    private String systemType;
    private String devicePoint;
    private LocalDateTime receivedAt;
    private String itemTitle;
    private String description;
    private String currentStatus;
    private String impactScope;
    private String severity;
    private String priority;
    private String ownerName;
    private String latestProgress;
    private String completionStatus;
    private LocalDateTime completedAt;
    private String notes;
    private String internalConclusion;
    private String customerFeedback;
    private String sourceType;
    private LocalDateTime createTime;
    private String causeCategory;
    private String causeDetail;
    private String preventiveAction;
    private String followUpAction;
    private String reuseTags;
    private Integer knowledgeIncluded;
    private Integer reminderEnabled;
    private Integer remindAfterDays;
}
