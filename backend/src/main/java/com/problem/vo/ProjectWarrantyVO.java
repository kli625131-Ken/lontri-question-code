package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectWarrantyVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private String contractType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String serviceScope;
    private String contractStatus;
    private LocalDateTime contractSignedAt;
    private LocalDateTime acceptanceAt;
    private String warrantyTerm;
    private LocalDateTime expireAt;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String downloadUrl;
    private String notes;
}
