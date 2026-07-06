package com.problem.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请重新登录"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),

    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),

    USERNAME_PASSWORD_ERROR(401, "用户名或密码错误"),
    TOKEN_EXPIRED(401, "Token 已过期"),
    TOKEN_INVALID(401, "Token 无效"),
    USER_DISABLED(403, "用户已被禁用");

    private final Integer code;
    private final String message;
}
