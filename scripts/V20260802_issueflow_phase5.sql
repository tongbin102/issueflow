-- ============================================================
-- issueFlow Phase 5 增量 DDL + 种子（R1-R7）
-- 约定：建表 CREATE TABLE IF NOT EXISTS；加列用 information_schema 动态防重复；
--       菜单/权限种子 INSERT ... WHERE NOT EXISTS + UPDATE 用派生表子查询解析父 id。
--       全部语句可重复执行（幂等）。
-- 日期：2026-08-02
-- ============================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. flow_node 流程节点（status_code 与 IssueStatusEnum 0-4 一一对应且唯一）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `flow_node` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(50)  NOT NULL            COMMENT '节点名称',
  `code`        VARCHAR(50)  DEFAULT NULL        COMMENT '节点编码',
  `status_code` INT          NOT NULL            COMMENT '与 IssueStatusEnum 0-4 一一对应，唯一',
  `node_type`   TINYINT      NOT NULL DEFAULT 1  COMMENT '1开始 2审核 3结束',
  `color`       VARCHAR(20)  DEFAULT NULL        COMMENT '流程图节点颜色',
  `pos_x`       INT          DEFAULT 0           COMMENT '流程图 X 坐标',
  `pos_y`       INT          DEFAULT 0           COMMENT '流程图 Y 坐标',
  `sort`        INT          NOT NULL DEFAULT 0  COMMENT '同级排序号，升序',
  `description` VARCHAR(200) DEFAULT NULL        COMMENT '节点描述',
  `enabled`     TINYINT      NOT NULL DEFAULT 1  COMMENT '1启用 0禁用',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_status` (`status_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点';

-- ---------------------------------------------------------------------------
-- 2. flow_transition 流转规则（from/to 唯一；承接旧流程开关 config_key）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `flow_transition` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT,
  `from_node_id`   BIGINT   NOT NULL            COMMENT '源节点 id（flow_node.id）',
  `to_node_id`     BIGINT   NOT NULL            COMMENT '目标节点 id（flow_node.id）',
  `action_code`    VARCHAR(30) NOT NULL         COMMENT 'HistoryActionEnum code',
  `action_name`    VARCHAR(30) DEFAULT NULL     COMMENT '动作中文名',
  `allow_roles`    VARCHAR(100) DEFAULT NULL    COMMENT '逗号分隔角色码',
  `remark_required` TINYINT  NOT NULL DEFAULT 0 COMMENT '流转是否必填原因 0/1',
  `config_key`     VARCHAR(50) DEFAULT NULL     COMMENT '承接 flow_reject_enabled / flow_reopen_enabled',
  `enabled`        TINYINT  NOT NULL DEFAULT 1  COMMENT '1启用 0禁用',
  `sort`           INT      NOT NULL DEFAULT 0  COMMENT '排序号',
  `created_at`     DATETIME DEFAULT NULL,
  `updated_at`     DATETIME DEFAULT NULL,
  `deleted`        TINYINT  NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_transition` (`from_node_id`, `to_node_id`),
  KEY `idx_flow_transition_to` (`to_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程流转规则';

-- ---------------------------------------------------------------------------
-- 3. flow_node 种子（5 节点，status_code 0-4，与原 IssueStatusEnum 对齐）
-- ---------------------------------------------------------------------------
INSERT INTO `flow_node` (`name`,`code`,`status_code`,`node_type`,`color`,`pos_x`,`pos_y`,`sort`,`description`,`enabled`,`created_at`,`updated_at`)
SELECT '待处理','OPEN',0,1,'#909399',120,80,0,'问题创建后待处理',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `flow_node` WHERE `status_code`=0 AND `deleted`=0);

INSERT INTO `flow_node` (`name`,`code`,`status_code`,`node_type`,`color`,`pos_x`,`pos_y`,`sort`,`description`,`enabled`,`created_at`,`updated_at`)
SELECT '处理中','IN_PROGRESS',1,2,'#409EFF',320,80,1,'开发人员处理中',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `flow_node` WHERE `status_code`=1 AND `deleted`=0);

INSERT INTO `flow_node` (`name`,`code`,`status_code`,`node_type`,`color`,`pos_x`,`pos_y`,`sort`,`description`,`enabled`,`created_at`,`updated_at`)
SELECT '待验证','PENDING_VERIFY',2,2,'#E6A23C',520,80,2,'提交修复待测试验证',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `flow_node` WHERE `status_code`=2 AND `deleted`=0);

INSERT INTO `flow_node` (`name`,`code`,`status_code`,`node_type`,`color`,`pos_x`,`pos_y`,`sort`,`description`,`enabled`,`created_at`,`updated_at`)
SELECT '验证通过','VERIFIED',3,2,'#67C23A',720,80,3,'测试验证通过',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `flow_node` WHERE `status_code`=3 AND `deleted`=0);

INSERT INTO `flow_node` (`name`,`code`,`status_code`,`node_type`,`color`,`pos_x`,`pos_y`,`sort`,`description`,`enabled`,`created_at`,`updated_at`)
SELECT '已关闭','CLOSED',4,3,'#909399',920,80,4,'问题已关闭',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `flow_node` WHERE `status_code`=4 AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 4. flow_transition 种子（6 条，与 StateMachine 原 TRANSITIONS 逐条对齐）
-- ---------------------------------------------------------------------------
INSERT INTO `flow_transition` (`from_node_id`,`to_node_id`,`action_code`,`action_name`,`allow_roles`,`remark_required`,`config_key`,`enabled`,`sort`,`created_at`,`updated_at`)
SELECT fn.`id`, tn.`id`, 'CLAIM','认领','DEVELOPER,ADMIN',0,NULL,1,1,NOW(),NOW()
FROM `flow_node` fn, `flow_node` tn
WHERE fn.`status_code`=0 AND tn.`status_code`=1
  AND NOT EXISTS (SELECT 1 FROM `flow_transition` WHERE `from_node_id`=fn.`id` AND `to_node_id`=tn.`id` AND `deleted`=0);

INSERT INTO `flow_transition` (`from_node_id`,`to_node_id`,`action_code`,`action_name`,`allow_roles`,`remark_required`,`config_key`,`enabled`,`sort`,`created_at`,`updated_at`)
SELECT fn.`id`, tn.`id`, 'SUBMIT_FIX','提交修复','DEVELOPER,ADMIN',0,NULL,1,2,NOW(),NOW()
FROM `flow_node` fn, `flow_node` tn
WHERE fn.`status_code`=1 AND tn.`status_code`=2
  AND NOT EXISTS (SELECT 1 FROM `flow_transition` WHERE `from_node_id`=fn.`id` AND `to_node_id`=tn.`id` AND `deleted`=0);

INSERT INTO `flow_transition` (`from_node_id`,`to_node_id`,`action_code`,`action_name`,`allow_roles`,`remark_required`,`config_key`,`enabled`,`sort`,`created_at`,`updated_at`)
SELECT fn.`id`, tn.`id`, 'VERIFY_PASS','验证通过','TESTER,ADMIN',0,NULL,1,3,NOW(),NOW()
FROM `flow_node` fn, `flow_node` tn
WHERE fn.`status_code`=2 AND tn.`status_code`=3
  AND NOT EXISTS (SELECT 1 FROM `flow_transition` WHERE `from_node_id`=fn.`id` AND `to_node_id`=tn.`id` AND `deleted`=0);

INSERT INTO `flow_transition` (`from_node_id`,`to_node_id`,`action_code`,`action_name`,`allow_roles`,`remark_required`,`config_key`,`enabled`,`sort`,`created_at`,`updated_at`)
SELECT fn.`id`, tn.`id`, 'VERIFY_REJECT','验证回退','TESTER,ADMIN',1,'flow_reject_enabled',1,4,NOW(),NOW()
FROM `flow_node` fn, `flow_node` tn
WHERE fn.`status_code`=2 AND tn.`status_code`=1
  AND NOT EXISTS (SELECT 1 FROM `flow_transition` WHERE `from_node_id`=fn.`id` AND `to_node_id`=tn.`id` AND `deleted`=0);

INSERT INTO `flow_transition` (`from_node_id`,`to_node_id`,`action_code`,`action_name`,`allow_roles`,`remark_required`,`config_key`,`enabled`,`sort`,`created_at`,`updated_at`)
SELECT fn.`id`, tn.`id`, 'CLOSE','关闭','TESTER,ADMIN',0,NULL,1,5,NOW(),NOW()
FROM `flow_node` fn, `flow_node` tn
WHERE fn.`status_code`=3 AND tn.`status_code`=4
  AND NOT EXISTS (SELECT 1 FROM `flow_transition` WHERE `from_node_id`=fn.`id` AND `to_node_id`=tn.`id` AND `deleted`=0);

INSERT INTO `flow_transition` (`from_node_id`,`to_node_id`,`action_code`,`action_name`,`allow_roles`,`remark_required`,`config_key`,`enabled`,`sort`,`created_at`,`updated_at`)
SELECT fn.`id`, tn.`id`, 'REOPEN','重开','ADMIN',0,'flow_reopen_enabled',1,6,NOW(),NOW()
FROM `flow_node` fn, `flow_node` tn
WHERE fn.`status_code`=4 AND tn.`status_code`=0
  AND NOT EXISTS (SELECT 1 FROM `flow_transition` WHERE `from_node_id`=fn.`id` AND `to_node_id`=tn.`id` AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 5. organization 扩展：code / leader_id / status / description（三步序：加列→回填→唯一索引）
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='organization' AND COLUMN_NAME='code');
SET @sql := IF(@c1=0, 'ALTER TABLE `organization` ADD COLUMN `code` VARCHAR(50) DEFAULT NULL COMMENT ''组织编码，唯一'' AFTER `name`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='organization' AND COLUMN_NAME='leader_id');
SET @sql := IF(@c2=0, 'ALTER TABLE `organization` ADD COLUMN `leader_id` BIGINT DEFAULT NULL COMMENT ''部门负责人 user.id'' AFTER `parent_id`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='organization' AND COLUMN_NAME='status');
SET @sql := IF(@c3=0, 'ALTER TABLE `organization` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''1启用 0禁用'' AFTER `leader_id`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='organization' AND COLUMN_NAME='description');
SET @sql := IF(@c4=0, 'ALTER TABLE `organization` ADD COLUMN `description` VARCHAR(200) DEFAULT NULL COMMENT ''组织描述'' AFTER `status`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 存量 code 回填（CONCAT('ORG', LPAD(id,3,'0'))），再建唯一索引
UPDATE `organization` SET `code` = CONCAT('ORG', LPAD(`id`,3,'0')) WHERE `code` IS NULL OR `code` = '';
SET @c5 := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='organization' AND INDEX_NAME='uk_org_code');
SET @sql := IF(@c5=0, 'ALTER TABLE `organization` ADD UNIQUE KEY `uk_org_code` (`code`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 6. user 扩展：leader_id（上级领导）
-- ---------------------------------------------------------------------------
SET @c6 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='leader_id');
SET @sql := IF(@c6=0, 'ALTER TABLE `user` ADD COLUMN `leader_id` BIGINT DEFAULT NULL COMMENT ''上级领导 user.id'' AFTER `role_id`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 7. 菜单调整（R6）：原「项目管理」改名「项目配置」归入新顶级「项目管理」分组；新增「模块配置」
--    顶级排序：概览(1) → 问题管理(2) → 项目管理组(3) → 流程管理(4) → 系统管理(5)
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `name`='项目配置' WHERE `path`='/admin/projects' AND `type`=2 AND `deleted`=0;

INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '项目管理', '/admin/project', 0, 3, NULL, 'Management', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/project' AND `type`=2 AND `deleted`=0);

UPDATE `menu`
SET `parent_id` = (SELECT `pid` FROM (SELECT `id` AS `pid` FROM `menu` WHERE `path`='/admin/project' AND `type`=2 AND `deleted`=0) AS `_p`),
    `sort` = 1
WHERE `path`='/admin/projects' AND `type`=2 AND `deleted`=0
  AND `parent_id` <> (SELECT `pid` FROM (SELECT `id` AS `pid` FROM `menu` WHERE `path`='/admin/project' AND `type`=2 AND `deleted`=0) AS `_p2`);

INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '模块配置', '/admin/modules',
       (SELECT `id` FROM `menu` WHERE `path`='/admin/project' AND `type`=2 AND `deleted`=0),
       2, 'project:update', 'Tree', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/modules' AND `type`=2 AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 8. 权限种子：system:reset（数据初始化）授 ADMIN
-- ---------------------------------------------------------------------------
INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:reset', '数据初始化', 'system', 'reset', 2, 90, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:reset');

SET @admin_role := (SELECT `id` FROM `role` WHERE `code`='ADMIN');
INSERT INTO `role_permission` (`role_id`,`permission_id`,`created_at`,`updated_at`)
SELECT @admin_role, p.`id`, NOW(), NOW()
FROM `permission` p
WHERE p.`code`='system:reset'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.`role_id`=@admin_role AND rp.`permission_id`=p.`id`);

-- ---------------------------------------------------------------------------
-- 9. 菜单：系统管理 → 系统设置（R7 数据初始化入口）
-- ---------------------------------------------------------------------------
INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '系统设置', '/admin/system/settings',
       (SELECT `id` FROM `menu` WHERE `path`='/admin/system' AND `type`=2 AND `deleted`=0),
       5, 'system:reset', 'Setting', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/system/settings' AND `type`=2 AND `deleted`=0);
