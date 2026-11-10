package com.doccheck.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 登录/注册审计流水，不记录密码和 Token。 */
@Data
@TableName("auth_audit")
public class AuthAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String ip;

    private String userAgent;

    /** LOGIN / REGISTER / LOGOUT / CHANGE_PASSWORD */
    private String actionType;

    private Integer success;

    private String message;

    private LocalDateTime createdAt;
}
