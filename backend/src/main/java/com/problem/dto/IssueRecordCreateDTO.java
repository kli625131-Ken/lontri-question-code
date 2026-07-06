package com.problem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IssueRecordCreateDTO {
    private String actionType;

    @NotBlank(message = "处理记录不能为空")
    private String content;
}
