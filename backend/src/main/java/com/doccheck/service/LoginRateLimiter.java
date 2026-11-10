package com.doccheck.service;

import com.doccheck.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 认证入口限流。固定窗口计数，Redis 故障时降级放行，避免认证整体不可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final int LOGIN_IP_LIMIT = 10;
    private static final int LOGIN_USER_LIMIT = 5;
    private static final int REGISTER_IP_LIMIT = 5;

    private final StringRedisTemplate redisTemplate;

    public void checkLogin(String ip, String username) {
        check("auth:rate:login:ip:" + safe(ip), LOGIN_IP_LIMIT, Duration.ofMinutes(1), "登录尝试过于频繁，请稍后再试");
        check("auth:rate:login:user:" + safe(username).toLowerCase(), LOGIN_USER_LIMIT,
                Duration.ofMinutes(1), "该账号尝试次数过多，请 1 分钟后再试");
    }

    public void checkRegister(String ip) {
        check("auth:rate:register:ip:" + safe(ip), REGISTER_IP_LIMIT,
                Duration.ofHours(1), "注册过于频繁，请稍后再试");
    }

    /** 登录成功后清除该账号的失败窗口，避免正常用户被自己的成功登录继续限流。 */
    public void clearLoginUser(String username) {
        try {
            redisTemplate.delete("auth:rate:login:user:" + safe(username).toLowerCase());
        } catch (RuntimeException e) {
            log.debug("清理登录限流计数失败: {}", e.getMessage());
        }
    }

    private void check(String key, int limit, Duration window, String message) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            if (count != null && count > limit) {
                throw new BizException(429, message);
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("认证限流不可用，降级放行: {}", e.getMessage());
        }
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.trim();
    }
}
