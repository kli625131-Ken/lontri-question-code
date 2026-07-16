package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MaintenanceAssignmentVO {
    private Long id;
    private Long visitId;
    private LocalDateTime scheduledAt;
    private String floorName;
    private String taskItem;
    private String ownerName;
    private String status;
    private String notes;
    private String sourceFilePath;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String sourceHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
