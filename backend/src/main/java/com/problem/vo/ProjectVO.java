package com.problem.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectVO {
    private Long id;
    private String customerName;
    private String projectGroup;
    private String projectName;
    private String projectCode;
    private String parentProjectCode;
    private String projectLevel;
    private Integer isActive;
    private String description;
    private Integer reminderEnabled;
    private Integer remindAfterDays;
    private long childProjectCount;
    private long issueCount;
    private long openCount;
}
