-- 首次创建 MySQL 容器时执行：为应用创建最小权限账号
-- 仅授权 doc_format_check 库，避免应用直接使用 root
CREATE USER IF NOT EXISTS 'doccheck'@'%' IDENTIFIED BY 'doccheck123';
GRANT ALL PRIVILEGES ON doc_format_check.* TO 'doccheck'@'%';
FLUSH PRIVILEGES;
