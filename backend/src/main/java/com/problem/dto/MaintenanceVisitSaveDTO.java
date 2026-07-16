package com.problem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceVisitSaveDTO {
    @NotNull(message = "项目不能为空")
    private Long projectId;
    private String visitTitle;
    private String servicePeriod;
    private Integer serviceYear;
    private Integer serviceQuarter;
    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private String status;
    private String summary;
    private String conclusion;
}
