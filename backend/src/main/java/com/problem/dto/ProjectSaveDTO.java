package com.problem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectSaveDTO {

    private String customerName;
    private String projectGroup;

    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    private String projectCode;
    private String parentProjectCode;
    private String projectLevel;
    private String description;
    private Integer reminderEnabled;
    private Integer remindAfterDays;
    private Integer isActive;
}
