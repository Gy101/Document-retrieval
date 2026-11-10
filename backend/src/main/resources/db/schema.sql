-- =====================================================================
-- 通用文档格式智能校验系统 - MySQL 8.0 初始化脚本
-- 数据库：doc_format_check（由连接串 createDatabaseIfNotExist=true 或手工创建）
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci
-- 说明：所有业务表均带 created_at/updated_at；模板与任务采用逻辑删除。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
  `display_name`  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '显示名',
  `email`         VARCHAR(128) NULL COMMENT '邮箱（可选）',
  `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS `doc_template` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`            VARCHAR(120) NOT NULL COMMENT '模板名称',
  `description`     VARCHAR(500) NOT NULL DEFAULT '' COMMENT '模板说明',
  `category`        VARCHAR(64)  NOT NULL DEFAULT '通用' COMMENT '模板分类',
  `scope`           VARCHAR(16)  NOT NULL DEFAULT 'PERSONAL' COMMENT '可见范围：PUBLIC公共/PERSONAL个人',
  `owner_id`        BIGINT       NOT NULL COMMENT '创建人ID',
  `original_name`   VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `storage_path`    VARCHAR(500) NOT NULL COMMENT '本地存储相对路径',
  `file_size`       BIGINT       NOT NULL DEFAULT 0 COMMENT '文件字节数',
  `file_hash`       CHAR(64)     NOT NULL DEFAULT '' COMMENT 'SHA-256',
  `content_type`    VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'MIME 类型',
  `parse_status`    VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '解析状态：PENDING/SUCCESS/FAILED',
  `metadata_version` INT         NOT NULL DEFAULT 1 COMMENT '元数据版本号',
  `metadata_json`   LONGTEXT     NULL COMMENT '预解析的结构化格式元数据 JSON',
  `parse_message`   VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '解析失败原因',
  `download_count`  INT          NOT NULL DEFAULT 0 COMMENT '下载次数',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_template_scope_owner` (`scope`, `owner_id`, `deleted`),
  KEY `idx_template_category` (`category`),
  KEY `idx_template_created` (`created_at`),
  KEY `idx_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档格式模板表';

CREATE TABLE IF NOT EXISTS `check_task` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_no`         VARCHAR(40)  NOT NULL COMMENT '任务编号',
  `user_id`         BIGINT       NOT NULL COMMENT '提交人ID',
  `template_id`     BIGINT       NOT NULL COMMENT '模板ID',
  `template_name`   VARCHAR(120) NOT NULL DEFAULT '' COMMENT '模板名称快照',
  `doc_name`        VARCHAR(255) NOT NULL COMMENT '待校验文件名',
  `doc_path`        VARCHAR(500) NOT NULL COMMENT '待校验文件存储相对路径',
  `doc_size`        BIGINT       NOT NULL DEFAULT 0 COMMENT '文件字节数',
  `doc_hash`        CHAR(64)     NOT NULL DEFAULT '' COMMENT 'SHA-256',
  `custom_rules`    TEXT         NULL COMMENT '用户自定义校验规则（自然语言/JSON）',
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'QUEUED' COMMENT '状态：QUEUED/RUNNING/SUCCESS/FAILED/DEAD',
  `progress`        INT          NOT NULL DEFAULT 0 COMMENT '进度 0-100',
  `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `stage`           VARCHAR(100) NOT NULL DEFAULT '排队中' COMMENT '当前阶段描述',
  `error_message`   VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '失败原因',
  `summary`         TEXT         NULL COMMENT '校验结论摘要',
  `total_diff_count` INT         NOT NULL DEFAULT 0 COMMENT '差异总数',
  `high_count`      INT          NOT NULL DEFAULT 0 COMMENT '高危差异数',
  `middle_count`    INT          NOT NULL DEFAULT 0 COMMENT '中危差异数',
  `low_count`       INT          NOT NULL DEFAULT 0 COMMENT '低危差异数',
  `document_metadata` LONGTEXT   NULL COMMENT '待校验文档格式元数据 JSON（供预览）',
  `result_json`     LONGTEXT     NULL COMMENT 'DSH 原始返回 JSON（审计用）',
  `duration_ms`     BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `started_at`      DATETIME     NULL COMMENT '开始处理时间',
  `finished_at`     DATETIME     NULL COMMENT '结束时间',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_task_user_created` (`user_id`, `created_at`),
  KEY `idx_task_status_updated` (`status`, `updated_at`),
  KEY `idx_task_template` (`template_id`),
  KEY `idx_task_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='格式校验任务表';

CREATE TABLE IF NOT EXISTS `check_diff` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`         BIGINT       NOT NULL COMMENT '任务ID',
  `diff_no`         VARCHAR(20)  NOT NULL COMMENT '差异序号，如 D001',
  `seq`             INT          NOT NULL DEFAULT 0 COMMENT '文档顺序号，用于排序',
  `location`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '人类可读位置描述',
  `anchor_path`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '前端预览定位路径，如 body.p[3]',
  `section_index`   INT          NULL COMMENT '节索引',
  `paragraph_index` INT          NULL COMMENT '段落全局索引',
  `table_index`     INT          NULL COMMENT '表格索引',
  `row_index`       INT          NULL COMMENT '表格行索引',
  `col_index`       INT          NULL COMMENT '表格列索引',
  `char_start`      INT          NULL COMMENT '字符起始偏移',
  `char_end`        INT          NULL COMMENT '字符结束偏移',
  `diff_type`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '差异类型：字体/段落/编号/表格/图片/页面/页眉页脚',
  `severity`        VARCHAR(16)  NOT NULL DEFAULT 'middle' COMMENT '严重等级：high/middle/low',
  `element_type`    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '元素类型：text/paragraph/table/image/header/footer/page',
  `template_format` TEXT         NULL COMMENT '模板要求格式',
  `actual_format`   TEXT         NULL COMMENT '实际格式',
  `content_snippet` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '差异位置文本片段',
  `suggestion`      TEXT         NULL COMMENT '修正建议',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_diff_task_seq` (`task_id`, `seq`),
  KEY `idx_diff_task_severity` (`task_id`, `severity`),
  KEY `idx_diff_task_type` (`task_id`, `diff_type`),
  KEY `idx_diff_task_anchor` (`task_id`, `anchor_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='格式差异明细表';

-- 认证审计：只记录认证动作，不记录密码/Token。
CREATE TABLE IF NOT EXISTS `auth_audit` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(64)  NULL COMMENT '登录名（失败时可能是输入值）',
  `ip`          VARCHAR(64)  NULL COMMENT '客户端 IP',
  `user_agent`  VARCHAR(512) NULL COMMENT 'User-Agent',
  `action_type` VARCHAR(32)  NOT NULL COMMENT '动作：LOGIN/REGISTER/LOGOUT/CHANGE_PASSWORD',
  `success`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否成功：1成功 0失败',
  `message`     VARCHAR(500) NULL COMMENT '结果说明',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_auth_audit_user_time` (`username`, `created_at`),
  KEY `idx_auth_audit_action_time` (`action_type`, `created_at`),
  KEY `idx_auth_audit_ip_time` (`ip`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认证审计流水';
