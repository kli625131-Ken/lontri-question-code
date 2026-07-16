package com.problem.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceVisitCloseDTO {
    private String summary;
    private String conclusion;
    private LocalDateTime actualEndAt;
}
