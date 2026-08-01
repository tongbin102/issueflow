-- ============================================================
-- issueFlow 线上数据库迁移脚本
-- 目的：为 issue_attachment / issue_history / tag 三表补充 updated_at 字段
-- 背景：实体类继承 BaseEntity，BaseEntity 含 updatedAt（FieldFill.INSERT_UPDATE），
--       但建表 SQL 未包含该列，导致 MyBatis-Plus 生成的 SELECT 缺少列而报错。
-- 幂等性：通过 information_schema 判断列是否已存在，避免重复执行 ALTER 报错。
-- 适用：MySQL 5.7+ / 8.0
-- 执行方式（命令行示例）：
--   mysql -u<user> -p<pass> <db_name> < scripts/migrate-add-updated-at.sql
-- ============================================================
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS `issueflow_add_updated_at`$$
CREATE PROCEDURE `issueflow_add_updated_at`()
BEGIN
  -- issue_attachment
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'issue_attachment'
      AND COLUMN_NAME = 'updated_at'
  ) THEN
    ALTER TABLE `issue_attachment`
      ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
      AFTER `created_at`;
  END IF;

  -- issue_history
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'issue_history'
      AND COLUMN_NAME = 'updated_at'
  ) THEN
    ALTER TABLE `issue_history`
      ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
      AFTER `created_at`;
  END IF;

  -- tag
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tag'
      AND COLUMN_NAME = 'updated_at'
  ) THEN
    ALTER TABLE `tag`
      ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
      AFTER `created_at`;
  END IF;
END$$

DELIMITER ;

CALL `issueflow_add_updated_at`();
DROP PROCEDURE IF EXISTS `issueflow_add_updated_at`;
