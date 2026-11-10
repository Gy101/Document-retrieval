package com.doccheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.doccheck.common.BizException;
import com.doccheck.common.UserContext;
import com.doccheck.config.AuthProperties;
import com.doccheck.domain.dto.ChangePasswordRequest;
import com.doccheck.domain.dto.LoginRequest;
import com.doccheck.domain.dto.LoginResponse;
import com.doccheck.domain.dto.ProfileStatsVO;
import com.doccheck.domain.dto.ProfileTaskVO;
import com.doccheck.domain.dto.RegisterRequest;
import com.doccheck.domain.dto.UpdateProfileRequest;
import com.doccheck.domain.dto.UserProfileVO;
import com.doccheck.domain.dto.UserVO;
import com.doccheck.domain.entity.CheckTask;
import com.doccheck.domain.entity.DocTemplate;
import com.doccheck.domain.entity.SysUser;
import com.doccheck.mapper.CheckTaskMapper;
import com.doccheck.mapper.DocTemplateMapper;
import com.doccheck.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 登录态采用「随机 Token + Redis」方案：服务端无状态、可水平扩展，退出登录即时失效。
 * Redis Key：auth:token:{token} -> userId，TTL 默认 8 小时。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String TOKEN_PREFIX = "auth:token:";

    private final SysUserMapper userMapper;
    private final DocTemplateMapper templateMapper;
    private final CheckTaskMapper taskMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername().trim()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }
        return issueToken(user);
    }

    /**
     * 注册普通用户。用户名唯一性先查一次，最终仍由数据库唯一索引兜底，避免并发重复注册。
     */
    public LoginResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BizException(409, "用户名已被占用，请换一个");
        }

        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(StringUtils.hasText(request.getDisplayName())
                ? request.getDisplayName().trim() : username);
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleted(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "用户名已被占用，请换一个");
        }

        // 注册成功直接签发登录态，前端无需再跳一次登录。
        return issueToken(user);
    }

    public void logout(String authorization) {
        String token = extractToken(authorization);
        if (StringUtils.hasText(token)) {
            redisTemplate.delete(TOKEN_PREFIX + token);
        }
    }

    public Long resolveUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public UserVO currentUser() {
        return toVO(requireCurrentUser());
    }

    /**
     * 个人中心聚合：账号资料 + 模板/任务/差异统计 + 最近 5 条任务。
     */
    public UserProfileVO profile() {
        SysUser user = requireCurrentUser();

        ProfileStatsVO stats = taskMapper.selectProfileStats(user.getId());
        if (stats == null) {
            stats = new ProfileStatsVO();
        }
        Long templateCount = templateMapper.selectCount(Wrappers.<DocTemplate>lambdaQuery()
                .eq(DocTemplate::getOwnerId, user.getId()));
        stats.setTemplateCount(templateCount == null ? 0 : templateCount);

        List<CheckTask> recent = taskMapper.selectList(Wrappers.<CheckTask>lambdaQuery()
                .eq(CheckTask::getUserId, user.getId())
                .orderByDesc(CheckTask::getCreatedAt)
                .last("LIMIT 5"));

        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setStats(stats);
        vo.setRecentTasks(recent.stream().map(this::toProfileTask).toList());
        return vo;
    }

    public UserVO updateProfile(UpdateProfileRequest request) {
        SysUser user = requireCurrentUser();
        user.setDisplayName(request.getDisplayName().trim());
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toVO(user);
    }

    public void changePassword(ChangePasswordRequest request) {
        SysUser user = requireCurrentUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(400, "原密码不正确");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BizException(400, "新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private LoginResponse issueToken(SysUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long ttlSeconds = Duration.ofHours(authProperties.getTokenTtlHours()).toSeconds();
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, String.valueOf(user.getId()),
                Duration.ofSeconds(ttlSeconds));
        return new LoginResponse(token, ttlSeconds, toVO(user));
    }

    private UserVO toVO(SysUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getEmail(), user.getRole());
    }

    private ProfileTaskVO toProfileTask(CheckTask task) {
        ProfileTaskVO vo = new ProfileTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTemplateName(task.getTemplateName());
        vo.setDocName(task.getDocName());
        vo.setStatus(task.getStatus());
        vo.setTotalDiffCount(task.getTotalDiffCount());
        vo.setHighCount(task.getHighCount());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private SysUser requireCurrentUser() {
        UserContext.LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BizException(401, "未登录");
        }
        SysUser user = userMapper.selectById(loginUser.id());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(401, "账号不存在或已被禁用");
        }
        return user;
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim() : null;
    }

    public static String extractToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String value = authorization.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }
}
