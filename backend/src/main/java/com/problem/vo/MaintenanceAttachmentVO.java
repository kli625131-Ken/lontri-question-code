package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MaintenanceAttachmentVO {
    private Long id;
    private Long findingId;
    private Long visitId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private String previewUrl;
    private String downloadUrl;
    private Long uploadedBy;
    private String uploadedByName;
    private LocalDateTime createdAt;
    private Boolean canDelete;
}
