package com.problem.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ImportRowReviewUpdateDTO {
    private Map<String, Object> normalizedData;
    private String reviewStatus;
    private String reviewMessage;
}
