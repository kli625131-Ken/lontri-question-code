package com.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TempUserCreateDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    @NotNull(message = "有效期不能为空")
    private LocalDateTime expireAt;
    private List<Long> projectIds;
}
