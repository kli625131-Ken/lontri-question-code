package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminUserVO {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private Integer isAdmin;
    private Integer globalSearchEnabled;
    private String accountType;
    private LocalDateTime expireAt;
    private LocalDateTime createTime;
    private LocalDateTime lastLoginTime;
    private List<Long> projectIds;
}
