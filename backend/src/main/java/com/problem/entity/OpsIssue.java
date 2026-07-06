package com.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_issue")
public class OpsIssue {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String issueNo;
    private Long projectId;
    private String source;
    private String sourceType;
    private Long sourceBatchId;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String reporterName;
    private LocalDateTime receivedAt;
    private String itemTitle;
    private String categoryPath;
    private String buildingName;
    private String floorName;
    private String areaName;
    private String systemType;
    private String devicePoint;
    private LocalDateTime foundAt;
    private String description;
    private String impactScope;
    private String severity;
    private String priority;
    private String currentStatus;
    private String closureStatus;
    private String ownerName;
    private String latestProgress;
    private String completionStatus;
    private LocalDateTime completedAt;
    private String notes;
    private String internalConclusion;
    private String customerFeedback;
    private String causeCategory;
    private String causeDetail;
    private String preventiveAction;
    private String followUpAction;
    private String reuseTags;
    private Integer knowledgeIncluded;
    private String rawSnapshot;
    private String dedupeKey;
    private Integer reminderEnabled;
    private Integer remindAfterDays;
    private LocalDateTime lastRemindedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
