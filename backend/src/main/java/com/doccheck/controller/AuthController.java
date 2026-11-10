package com.doccheck.controller;

import com.doccheck.common.ApiResponse;
import com.doccheck.common.UserContext;
import com.doccheck.config.AuthProperties;
import com.doccheck.domain.dto.ChangePasswordRequest;
import com.doccheck.domain.dto.LoginRequest;
import com.doccheck.domain.dto.LoginResponse;
import com.doccheck.domain.dto.RegisterRequest;
import com.doccheck.domain.dto.UpdateProfileRequest;
import com.doccheck.domain.dto.UserProfileVO;
import com.doccheck.domain.dto.UserVO;
import com.doccheck.service.AuthAuditService;
import com.doccheck.service.AuthService;
import com.doccheck.service.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;
    private final AuthAuditService auditService;
    private final AuthProperties authProperties;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest http) {
        String ip = clientIp(http);
        String ua = http.getHeader("User-Agent");
        try {
            rateLimiter.checkLogin(ip, request.getUsername());
            LoginResponse response = authService.login(request);
            rateLimiter.clearLoginUser(request.getUsername());
            auditService.record(request.getUsername(), ip, ua, "LOGIN", true, "登录成功");
            return ApiResponse.ok(response);
        } catch (RuntimeException e) {
            auditService.record(request.getUsername(), ip, ua, "LOGIN", false, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request,
                                               HttpServletRequest http) {
        String ip = clientIp(http);
        String ua = http.getHeader("User-Agent");
        try {
            rateLimiter.checkRegister(ip);
            LoginResponse response = authService.register(request);
            auditService.record(request.getUsername(), ip, ua, "REGISTER", true, "注册成功");
            return ApiResponse.ok(response);
        } catch (RuntimeException e) {
            auditService.record(request.getUsername(), ip, ua, "REGISTER", false, e.getMessage());
            throw e;
        }
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.ok(authService.currentUser());
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile() {
        return ApiResponse.ok(authService.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(authService.updateProfile(request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                             HttpServletRequest http) {
        UserContext.LoginUser user = UserContext.get();
        try {
            authService.changePassword(request);
            auditService.record(user == null ? null : user.username(), clientIp(http),
                    http.getHeader("User-Agent"), "CHANGE_PASSWORD", true, "密码修改成功");
            return ApiResponse.ok();
        } catch (RuntimeException e) {
            auditService.record(user == null ? null : user.username(), clientIp(http),
                    http.getHeader("User-Agent"), "CHANGE_PASSWORD", false, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return ApiResponse.ok();
    }

    private String clientIp(HttpServletRequest request) {
        // 默认不信任客户端可伪造的转发头；只有部署在可信反向代理后才开启 auth.trust-proxy。
        if (authProperties.isTrustProxy()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
