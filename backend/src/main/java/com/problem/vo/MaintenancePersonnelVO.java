package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MaintenancePersonnelVO {
    private Long id;
    private Long visitId;
    private String personName;
    private String phone;
    private String roleName;
    private String notes;
    private String sourceFilePath;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String sourceHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
