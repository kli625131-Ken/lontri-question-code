package com.problem.common;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    public void revokeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            long expirationTime = claims.getExpiration().getTime();
            long ttlMillis = expirationTime - System.currentTimeMillis();

            if (ttlMillis <= 0) {
                cleanupExpiredTokens();
                return;
            }

            String tokenKey = toTokenKey(token);
            revokedTokens.put(tokenKey, expirationTime);

            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (properties.isUseRedis() && redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(buildRedisKey(tokenKey), "1", Duration.ofMillis(ttlMillis));
                } catch (Exception e) {
                    log.warn("Failed to write revoked token to Redis, using in-memory fallback", e);
                }
            }

            cleanupExpiredTokens();
        } catch (Exception ignored) {
            // Ignore malformed or expired tokens during logout cleanup.
        }
    }

    public boolean isTokenRevoked(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        cleanupExpiredTokens();
        String tokenKey = toTokenKey(token);
        if (revokedTokens.containsKey(tokenKey)) {
            return true;
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (!properties.isUseRedis() || redisTemplate == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(buildRedisKey(tokenKey)));
        } catch (Exception e) {
            log.warn("Failed to check revoked token from Redis, using in-memory fallback", e);
            return false;
        }
    }

    private void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private String buildRedisKey(String tokenKey) {
        return properties.getKeyPrefix() + tokenKey;
    }

    private String toTokenKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
