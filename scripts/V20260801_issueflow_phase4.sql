-- ============================================================
-- issueFlow Phase 4 增量 DDL + 种子（R1 / R2 / R4 / R5）
-- 约定：建表 CREATE TABLE IF NOT EXISTS；加列用 information_schema 动态防重复；
--       菜单种子 INSERT ... WHERE NOT EXISTS + UPDATE 用派生表子查询解析父 id。
--       全部语句可重复执行（幂等）。
-- 日期：2026-08-01
-- ============================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. module 表（邻接表：parent_id 自引用，0=根；同 menu 范式）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `module` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT       NOT NULL           COMMENT '所属项目 id（project.id，逻辑删除下不加外键）',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父模块 id，0=根',
  `name`        VARCHAR(50)  NOT NULL           COMMENT '模块名称（同父级下唯一，应用层校验）',
  `description` VARCHAR(200) DEFAULT NULL       COMMENT '模块描述',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '同级排序号，升序',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  KEY `idx_module_project` (`project_id`, `parent_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模块（树形，邻接表）';

-- ---------------------------------------------------------------------------
-- 2. module_dependency 表（单向 A 依赖 B；uk 防重；
--    service 层物理清空重建，deleted 列仅对齐 BaseEntity 范式）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `module_dependency` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT,
  `from_module_id` BIGINT   NOT NULL COMMENT '依赖方模块 id（A）',
  `to_module_id`   BIGINT   NOT NULL COMMENT '被依赖模块 id（B）',
  `created_at`     DATETIME DEFAULT NULL,
  `updated_at`     DATETIME DEFAULT NULL,
  `deleted`        TINYINT  NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dep_from_to` (`from_module_id`, `to_module_id`),
  KEY `idx_dep_to` (`to_module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块依赖（A 依赖 B，仅展示语义）';

-- ---------------------------------------------------------------------------
-- 3. issue 加 module_id（可空 + 索引，动态 ALTER 防重复）
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue' AND COLUMN_NAME='module_id');
SET @sql := IF(@c1=0,
  'ALTER TABLE `issue` ADD COLUMN `module_id` BIGINT DEFAULT NULL COMMENT ''所属模块 id（module.id，可空）'' AFTER `project_id`, ADD INDEX `idx_issue_module` (`module_id`)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 4. R4 菜单种子：新增一级「流程管理」（无 permission，登录可见，同「系统管理」父级范式）
--    顶级排序：概览(1) → 问题管理(2) → 项目管理(3) → 流程管理(4) → 系统管理(5)
-- ---------------------------------------------------------------------------
INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '流程管理', '/admin/flow', 0, 4, NULL, 'Operation', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/flow' AND `type`=2 AND `deleted`=0);

-- 4.1 「流程配置」父级：系统管理 → 流程管理（派生表绕过 MySQL 同表 UPDATE/SELECT 限制），子级 sort=1
UPDATE `menu`
SET `parent_id` = (SELECT `pid` FROM (
      SELECT `id` AS `pid` FROM `menu`
      WHERE `path`='/admin/flow' AND `type`=2 AND `deleted`=0) AS `_p`),
    `sort` = 1
WHERE `path`='/admin/flow-config' AND `type`=2 AND `deleted`=0
  AND `parent_id` <> (SELECT `pid` FROM (
      SELECT `id` AS `pid` FROM `menu`
      WHERE `path`='/admin/flow' AND `type`=2 AND `deleted`=0) AS `_p2`);

-- 4.2 「流程监控」父级：顶级(0) → 流程管理，子级 sort=2
UPDATE `menu`
SET `parent_id` = (SELECT `pid` FROM (
      SELECT `id` AS `pid` FROM `menu`
      WHERE `path`='/admin/flow' AND `type`=2 AND `deleted`=0) AS `_p`),
    `sort` = 2
WHERE `path`='/admin/flow-monitor' AND `type`=2 AND `deleted`=0
  AND `parent_id` <> (SELECT `pid` FROM (
      SELECT `id` AS `pid` FROM `menu`
      WHERE `path`='/admin/flow' AND `type`=2 AND `deleted`=0) AS `_p2`);

-- 说明：不新增权限码种子——模块写操作复用 `project:update`，permission 表零变更。
