-- ============================================================
-- issueFlow 数据库建表 SQL
-- 字符集 utf8mb4 / 存储引擎 InnoDB
-- 约定：逻辑删除字段 deleted，自增主键 id
-- 说明：使用 CREATE TABLE IF NOT EXISTS 以支持重复启动不报错
-- ============================================================

CREATE TABLE IF NOT EXISTS `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(30) NOT NULL,
  `name` VARCHAR(30) NOT NULL,
  `description` VARCHAR(200) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色字典';

CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `real_name` VARCHAR(50) DEFAULT NULL,
  `email` VARCHAR(100) DEFAULT NULL,
  `phone` VARCHAR(20) DEFAULT NULL,
  `role_id` BIGINT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_role` (`role_id`),
  CONSTRAINT `fk_user_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户(单角色)';

CREATE TABLE IF NOT EXISTS `issue` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `issue_no` VARCHAR(20) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT,
  `severity` TINYINT NOT NULL DEFAULT 2,
  `tags` VARCHAR(255) DEFAULT NULL,
  `reproduce_steps` TEXT,
  `env_os` VARCHAR(100) DEFAULT NULL,
  `env_browser` VARCHAR(100) DEFAULT NULL,
  `env_app_version` VARCHAR(50) DEFAULT NULL,
  `env_device` VARCHAR(100) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 0,
  `reporter_id` BIGINT NOT NULL,
  `assignee_id` BIGINT DEFAULT NULL,
  `closed_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_no` (`issue_no`),
  KEY `idx_issue_status` (`status`),
  KEY `idx_issue_reporter` (`reporter_id`),
  KEY `idx_issue_assignee` (`assignee_id`),
  KEY `idx_issue_created` (`created_at`),
  KEY `idx_issue_severity` (`severity`),
  KEY `idx_issue_version` (`env_app_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题主表';

CREATE TABLE IF NOT EXISTS `issue_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `issue_id` BIGINT NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `original_name` VARCHAR(255) DEFAULT NULL,
  `file_path` VARCHAR(500) NOT NULL,
  `file_size` BIGINT DEFAULT NULL,
  `content_type` VARCHAR(100) DEFAULT NULL,
  `uploader_id` BIGINT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_att_issue` (`issue_id`),
  CONSTRAINT `fk_att_issue` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题附件';

CREATE TABLE IF NOT EXISTS `issue_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `issue_id` BIGINT NOT NULL,
  `action` VARCHAR(30) NOT NULL,
  `from_status` TINYINT DEFAULT NULL,
  `to_status` TINYINT DEFAULT NULL,
  `operator_id` BIGINT NOT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_his_issue` (`issue_id`),
  KEY `idx_his_operator` (`operator_id`),
  KEY `idx_his_created` (`created_at`),
  KEY `idx_his_op_created` (`operator_id`,`created_at`),
  CONSTRAINT `fk_his_issue` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作历史';

CREATE TABLE IF NOT EXISTS `tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  `color` VARCHAR(20) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类标签字典';

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(50) NOT NULL,
  `config_value` TEXT,
  `description` VARCHAR(200) DEFAULT NULL,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题/流程/菜单配置';
