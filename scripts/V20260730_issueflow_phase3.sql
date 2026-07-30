-- ============================================================
-- issueFlow Phase 3 增量 DDL + 种子数据（R2 / R5 / R6）
-- 幂等：ALTER 防重复列；菜单 UPDATE 用子查询解析父 id；软删除 deleted=1
-- 日期：2026-07-30
-- ============================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. project 加 leader_id / member_ids（动态 ALTER 防重复）
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project' AND COLUMN_NAME='leader_id');
SET @sql := IF(@c1=0,
  'ALTER TABLE `project` ADD COLUMN `leader_id` BIGINT DEFAULT NULL COMMENT \'负责人 id（user.id）\' AFTER `status`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project' AND COLUMN_NAME='member_ids');
SET @sql := IF(@c2=0,
  'ALTER TABLE `project` ADD COLUMN `member_ids` VARCHAR(500) DEFAULT NULL COMMENT \'项目成员 id，逗号分隔（上限约 80 人）\' AFTER `leader_id`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 2. R5：流程配置 parent_id 指向「系统管理」（用子查询解析父 id，防硬编码）
--    派生表包裹以绕过 MySQL「不能在同一语句中 UPDATE 目标表又 SELECT 它」的限制
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `parent_id` = (SELECT `pid` FROM (
  SELECT `id` AS `pid` FROM `menu`
  WHERE `path` = '/admin/system' AND `type` = 2 AND `deleted` = 0
) AS `_p`)
WHERE `path` = '/admin/flow-config' AND `type` = 2 AND `deleted` = 0
  AND `parent_id` <> (SELECT `pid` FROM (
    SELECT `id` AS `pid` FROM `menu`
    WHERE `path` = '/admin/system' AND `type` = 2 AND `deleted` = 0
  ) AS `_p2`);

-- ---------------------------------------------------------------------------
-- 3. R6：系统设置菜单软删除（deleted=1，与 BaseEntity 软删约定一致）
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `deleted` = 1
WHERE `path` = '/admin/settings' AND `type` = 2 AND `deleted` = 0;

-- ---------------------------------------------------------------------------
-- 4. （可选）R2 测试数据：给默认项目补负责人 + 成员（幂等 UPDATE，按 username 解析用户）
--    仅用于联调演示；生产环境可省略
-- ---------------------------------------------------------------------------
UPDATE `project`
SET `leader_id` = (SELECT `id` FROM `user` WHERE `username` = 'dev_zhang' AND `deleted` = 0 LIMIT 1),
    `member_ids` = (
      SELECT GROUP_CONCAT(`id`) FROM `user`
      WHERE `username` IN ('dev_zhao','test_li','test_qian') AND `deleted` = 0
    )
WHERE `name` = '核心交易系统' AND `deleted` = 0
  AND (`leader_id` IS NULL OR `member_ids` IS NULL);
