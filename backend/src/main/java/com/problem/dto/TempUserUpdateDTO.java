package com.problem.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TempUserUpdateDTO {
    private String realName;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime expireAt;
    private List<Long> projectIds;
}
