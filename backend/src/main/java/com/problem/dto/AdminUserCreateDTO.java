package com.problem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AdminUserCreateDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private Long roleId;
    private Integer status;
    private Integer globalSearchEnabled;
    private List<Long> projectIds;
}
