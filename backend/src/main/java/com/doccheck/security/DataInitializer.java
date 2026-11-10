package com.doccheck.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.doccheck.config.AuthProperties;
import com.doccheck.domain.entity.SysUser;
import com.doccheck.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 首次启动初始化演示管理员。生产环境请通过迁移脚本管理账号，
 * 或至少通过环境变量覆盖默认密码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Override
    public void run(ApplicationArguments args) {
        String username = authProperties.getDemoUsername();
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(authProperties.getDemoPassword()));
        user.setDisplayName("演示管理员");
        user.setRole("ADMIN");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        log.info("已初始化演示账号: {} / {}（请尽快修改）", username, authProperties.getDemoPassword());
    }
}
