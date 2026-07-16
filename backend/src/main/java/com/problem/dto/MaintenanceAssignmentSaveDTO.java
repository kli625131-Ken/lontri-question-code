package com.problem.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceAssignmentSaveDTO {
    private LocalDateTime scheduledAt;
    private String floorName;
    private String taskItem;
    private String ownerName;
    private String status;
    private String notes;
}
