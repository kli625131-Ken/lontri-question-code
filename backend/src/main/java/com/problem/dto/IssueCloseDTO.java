package com.problem.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IssueCloseDTO {
    private String content;
    private String causeCategory;
    private String causeDetail;
    private String customerFeedback;
    private String preventiveAction;
    private String followUpAction;
    private String reuseTags;
    private Integer knowledgeIncluded;
    private LocalDateTime completedAt;
}
