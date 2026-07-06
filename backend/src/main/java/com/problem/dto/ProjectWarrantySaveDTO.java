package com.problem.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectWarrantySaveDTO {
    private String contractType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String serviceScope;
    private String warrantyTerm;
    private String notes;
}
