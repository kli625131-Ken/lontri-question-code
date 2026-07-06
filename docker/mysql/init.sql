-- =============================================
-- 问题库运维平台 V1.0 - 数据库初始化脚本
-- MySQL 8.0+
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS problem_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE problem_db;

-- =============================================
-- 1. 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username        VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password        VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    real_name       VARCHAR(50) COMMENT '真实姓名',
    email           VARCHAR(100) COMMENT '邮箱',
    phone           VARCHAR(20) COMMENT '手机号',
    avatar_url      VARCHAR(255) COMMENT '头像URL',
    status          TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    last_login_time DATETIME COMMENT '最后登录时间',
    last_login_ip   VARCHAR(50) COMMENT '最后登录IP',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记：0-未删，1-已删',
    INDEX idx_username (username),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- =============================================
-- 初始化管理员账户（admin / admin123）
-- =============================================
-- 密码为BCrypt加密后的admin123
INSERT INTO sys_user (username, password, real_name, email, status) VALUES
('admin', '$2b$10$9s0t9cnsxaeTuDaeBqT1deNMA4H.aJVxZ2mVBYzZOONi3Y.zKzbxa', '系统管理员', 'admin@problem.com', 1);

-- =============================================
-- 初始化配置数据
-- =============================================
CREATE TABLE IF NOT EXISTS system_config (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key  VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value VARCHAR(500) COMMENT '配置值',
    config_type VARCHAR(50) DEFAULT 'string' COMMENT '配置类型',
    config_name VARCHAR(100) COMMENT '配置名称',
    description VARCHAR(255) COMMENT '配置描述',
    status      TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT DEFAULT 0,
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

INSERT INTO system_config (config_key, config_value, config_type, config_name, description) VALUES
('system.name', '问题库运维平台', 'string', '系统名称', '系统显示名称'),
('system.version', 'V1.0.0', 'string', '系统版本', '当前系统版本');

-- =============================================
-- 初始化字典数据
-- =============================================
CREATE TABLE IF NOT EXISTS sys_dict (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type   VARCHAR(50) NOT NULL COMMENT '字典类型',
    dict_code   VARCHAR(50) NOT NULL COMMENT '字典编码',
    dict_label  VARCHAR(100) COMMENT '字典标签',
    dict_value  VARCHAR(100) COMMENT '字典值',
    sort_order  INT DEFAULT 0,
    status      TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT DEFAULT 0,
    UNIQUE KEY uk_type_code (dict_type, dict_code),
    INDEX idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典表';

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('problem_status', 'pending', '待处理', '0', 1),
('problem_status', 'processing', '处理中', '1', 2),
('problem_status', 'resolved', '已解决', '2', 3),
('problem_status', 'closed', '已关闭', '3', 4),
('problem_priority', 'low', '低', '1', 1),
('problem_priority', 'medium', '中', '2', 2),
('problem_priority', 'high', '高', '3', 3),
('problem_priority', 'urgent', '紧急', '4', 4);

-- =============================================
-- 初始化完成
-- =============================================
