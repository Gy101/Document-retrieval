package com.doccheck.service;

import com.doccheck.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter(redisTemplate);
    }

    @Test
    void blocksAfterAccountLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        java.util.concurrent.atomic.AtomicInteger ip = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger user = new java.util.concurrent.atomic.AtomicInteger();
        when(valueOps.increment(anyString())).thenAnswer(inv ->
                inv.getArgument(0, String.class).contains(":ip:")
                        ? (long) ip.incrementAndGet() : (long) user.incrementAndGet());
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.checkLogin("127.0.0.1", "admin"));
        }
        BizException e = assertThrows(BizException.class, () -> limiter.checkLogin("127.0.0.1", "admin"));
        org.junit.jupiter.api.Assertions.assertEquals(429, e.getCode());
    }

    @Test
    void redisFailureFailsOpen() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> limiter.checkLogin("127.0.0.1", "admin"));
    }

    @Test
    void clearLoginUserDeletesKey() {
        limiter.clearLoginUser("Admin");
        verify(redisTemplate).delete("auth:rate:login:user:admin");
    }
}
