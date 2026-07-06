package com.problem.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectContactVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private String positionTitle;
    private String contactName;
    private String contactInfo;
    private String responsibility;
    private String notes;
}
