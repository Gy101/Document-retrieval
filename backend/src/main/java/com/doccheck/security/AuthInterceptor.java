package com.doccheck.security;

import com.doccheck.common.ApiResponse;
import com.doccheck.common.UserContext;
import com.doccheck.domain.entity.SysUser;
import com.doccheck.mapper.SysUserMapper;
import com.doccheck.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 解析 Bearer Token，校验 Redis 登录态并把用户写入 ThreadLocal。
 * 注意：afterCompletion 必须清理，避免线程池复用导致用户串号。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = AuthService.extractToken(request.getHeader("Authorization"));
        Long userId = authService.resolveUserId(token);
        if (userId == null) {
            writeUnauthorized(response, "登录状态已失效，请重新登录");
            return false;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            writeUnauthorized(response, "账号不存在或已被禁用");
            return false;
        }
        UserContext.set(new UserContext.LoginUser(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(401, message)));
    }
}
