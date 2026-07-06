package com.problem.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminUserUpdateDTO {
    private String realName;
    private String email;
    private String phone;
    private Long roleId;
    private Integer status;
    private Integer globalSearchEnabled;
    private List<Long> projectIds;
}
