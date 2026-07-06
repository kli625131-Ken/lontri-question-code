package com.problem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProjectAuthorizationDTO {
    private List<Long> projectIds;
}
