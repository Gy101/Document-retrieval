-- 已有数据库升级脚本（MySQL 8.0）
-- 新库直接执行 schema.sql 即可，无需本文件。
USE doc_format_check;

ALTER TABLE `check_task`
  ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数' AFTER `error_message`;

ALTER TABLE `check_task`
  MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
  COMMENT '状态：QUEUED/RUNNING/SUCCESS/FAILED/DEAD';

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
