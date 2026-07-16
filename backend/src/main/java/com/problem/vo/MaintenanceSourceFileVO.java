package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MaintenanceSourceFileVO {
    private Long id;
    private Long visitId;
    private String projectName;
    private String fileType;
    private String fileName;
    private String filePath;
    private String zipEntryPath;
    private String importStatus;
    private String message;
    private String sourceHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
