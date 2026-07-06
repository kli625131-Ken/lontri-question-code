-- LONTRI 运维平台开发环境数据库初始化
-- 适用于 backend/src/main/resources/application-dev.yml 当前配置

CREATE DATABASE IF NOT EXISTS problem_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'lontri'@'localhost' IDENTIFIED BY 'change_me_app_password';
ALTER USER 'lontri'@'localhost' IDENTIFIED BY 'change_me_app_password';

GRANT ALL PRIVILEGES ON problem_db.* TO 'lontri'@'localhost';
FLUSH PRIVILEGES;
