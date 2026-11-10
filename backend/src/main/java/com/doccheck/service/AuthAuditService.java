package com.doccheck.service;

import com.doccheck.domain.entity.AuthAudit;
import com.doccheck.mapper.AuthAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 认证审计写入；审计失败不能影响登录主流程。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private final AuthAuditMapper auditMapper;

    public void record(String username, String ip, String userAgent,
                       String action, boolean success, String message) {
        try {
            AuthAudit audit = new AuthAudit();
            audit.setUsername(truncate(username, 64));
            audit.setIp(truncate(ip, 64));
            audit.setUserAgent(truncate(userAgent, 512));
            audit.setActionType(action);
            audit.setSuccess(success ? 1 : 0);
            audit.setMessage(truncate(message, 500));
            audit.setCreatedAt(LocalDateTime.now());
            auditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("写入认证审计失败: {}", e.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
