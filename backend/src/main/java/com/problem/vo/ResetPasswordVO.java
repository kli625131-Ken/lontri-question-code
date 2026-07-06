package com.problem.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResetPasswordVO {
    private String temporaryPassword;
}
