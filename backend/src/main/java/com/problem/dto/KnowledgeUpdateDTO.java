package com.problem.dto;

import lombok.Data;

@Data
public class KnowledgeUpdateDTO {
    private String title;
    private String faultCode;
    private String symptomSummary;
    private String causeSummary;
    private String solutionSummary;
    private String preventionSummary;
    private String tags;
    private String status;
}
