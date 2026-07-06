package com.problem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IssueReopenDTO {

    @NotBlank(message = "重开原因不能为空")
    private String reason;
}
