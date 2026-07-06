package com.problem.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "token.blacklist")
public class TokenBlacklistProperties {

    private boolean useRedis = false;

    private String keyPrefix = "auth:blacklist:";
}
