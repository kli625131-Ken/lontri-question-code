package com.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IssueCreateDTO {

    @NotNull(message = "\u9879\u76ee\u4e0d\u80fd\u4e3a\u7a7a")
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

    @NotBlank(message = "\u4e8b\u9879\u4e0d\u80fd\u4e3a\u7a7a")
    private String itemTitle;

    @NotBlank(message = "\u95ee\u9898\u73b0\u8c61\u4e0d\u80fd\u4e3a\u7a7a")
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
