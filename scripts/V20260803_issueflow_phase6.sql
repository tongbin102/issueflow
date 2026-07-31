-- ============================================================
-- issueFlow Phase 6 增量 DDL + 种子
-- 内容：① issue_type 建表 + 6 条种子；② issue.type_id + 索引 + 存量回填 OTHER；
--       ③ 前台菜单调整（问题管理分组 / 我的问题挂父 / 移除提交问题 / 看板排序）；
--       ④ 后台菜单（平铺「问题类型」+ 系统管理下「网站设置」）；
--       ⑤ 菜单 icon 合法性修复（Tree→Grid）+ 清理僵尸菜单 /admin/settings；
--       ⑥ 5 个新权限码 + 授 ADMIN；⑦ sys_config 的 site.* 七键默认值。
-- 约定：建表 CREATE TABLE IF NOT EXISTS；加列/加索引用 information_schema 动态防重复；
--       种子 INSERT ... SELECT ... WHERE NOT EXISTS；父 id 用派生表子查询解析。
--       全部语句可重复执行（幂等）。role_permission 表无 updated_at 列，种子不得携带。
-- 前置：目标库须已执行 Phase 1-5 脚本（不可在空库上首次运行本脚本）。
-- 部署顺序硬约束：先执行本脚本、再重启后端（StateMachine @PostConstruct 读 flow 表）。
-- 日期：2026-08-03
-- ============================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. issue_type 问题类型表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `issue_type` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(50)  NOT NULL            COMMENT '类型名称',
  `code`        VARCHAR(50)  NOT NULL            COMMENT '类型编码（大写），供程序判断与 i18n key 拼接',
  `description` VARCHAR(200) DEFAULT NULL        COMMENT '描述',
  `sort`        INT          NOT NULL DEFAULT 0  COMMENT '升序展示',
  `enabled`     TINYINT      NOT NULL DEFAULT 1  COMMENT '1启用 0停用',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_type_code` (`code`, `deleted`),
  KEY `idx_issue_type_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题类型';

-- ---------------------------------------------------------------------------
-- 2. issue_type 6 条种子（BUG/FEATURE/PERFORMANCE/UI/QUESTION/OTHER）
-- ---------------------------------------------------------------------------
INSERT INTO `issue_type` (`name`,`code`,`description`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT '缺陷','BUG','功能不符合预期或程序错误',1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_type` WHERE `code`='BUG' AND `deleted`=0);

INSERT INTO `issue_type` (`name`,`code`,`description`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT '新功能','FEATURE','新功能与需求建议',2,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_type` WHERE `code`='FEATURE' AND `deleted`=0);

INSERT INTO `issue_type` (`name`,`code`,`description`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT '性能问题','PERFORMANCE','响应缓慢、卡顿、资源占用异常',3,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_type` WHERE `code`='PERFORMANCE' AND `deleted`=0);

INSERT INTO `issue_type` (`name`,`code`,`description`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT '界面样式','UI','界面显示、样式与交互问题',4,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_type` WHERE `code`='UI' AND `deleted`=0);

INSERT INTO `issue_type` (`name`,`code`,`description`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT '咨询','QUESTION','使用咨询与疑问',5,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_type` WHERE `code`='QUESTION' AND `deleted`=0);

INSERT INTO `issue_type` (`name`,`code`,`description`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT '其他','OTHER','其他未分类问题',6,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_type` WHERE `code`='OTHER' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 3. issue 加列 type_id（动态 DDL，幂等）
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue' AND COLUMN_NAME='type_id');
SET @sql := IF(@c1=0, 'ALTER TABLE `issue` ADD COLUMN `type_id` BIGINT DEFAULT NULL COMMENT ''问题类型 issue_type.id'' AFTER `severity`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 4. issue 加索引 idx_issue_type（幂等）
-- ---------------------------------------------------------------------------
SET @c2 := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue' AND INDEX_NAME='idx_issue_type');
SET @sql := IF(@c2=0, 'ALTER TABLE `issue` ADD KEY `idx_issue_type` (`type_id`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 5. 存量回填：type_id IS NULL → OTHER
-- ---------------------------------------------------------------------------
UPDATE `issue`
SET `type_id` = (SELECT `id` FROM `issue_type` WHERE `code`='OTHER' AND `deleted`=0 LIMIT 1)
WHERE `type_id` IS NULL;

-- ---------------------------------------------------------------------------
-- 6. 前台菜单：新增「问题管理」分组（一级，type=1）
-- ---------------------------------------------------------------------------
INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '问题管理', '/user/issue', 0, 2, NULL, 'Tickets', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/user/issue' AND `type`=1 AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 7. 前台菜单：「我的问题」改挂「问题管理」下，sort=1，icon 改 Document
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `parent_id` = (SELECT `pid` FROM (SELECT `id` AS `pid` FROM `menu` WHERE `path`='/user/issue' AND `type`=1 AND `deleted`=0) AS `_p`),
    `sort` = 1,
    `icon` = 'Document'
WHERE `path`='/user/my-issues' AND `type`=1 AND `deleted`=0
  AND (`parent_id` <> (SELECT `pid` FROM (SELECT `id` AS `pid` FROM `menu` WHERE `path`='/user/issue' AND `type`=1 AND `deleted`=0) AS `_p2`)
       OR `icon` <> 'Document');

-- ---------------------------------------------------------------------------
-- 8. 前台菜单：移除「提交问题」（逻辑删除；路由侧保留 redirect 防旧书签白屏）
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `deleted`=1 WHERE `path`='/user/submit-issue' AND `type`=1 AND `deleted`=0;

-- ---------------------------------------------------------------------------
-- 9. 前台菜单：「个人看板」sort 调整为 3
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `sort`=3 WHERE `path`='/user/stats' AND `type`=1 AND `deleted`=0 AND `sort`<>3;

-- ---------------------------------------------------------------------------
-- 10. 后台菜单：同级平铺新增「问题类型」（Q5：与「问题管理」兄弟；问题管理页 0 改动）
--     顶级排序腾位：项目管理组 3→4、流程管理 4→5、系统管理 5→6（幂等：仅在目标 sort 未就位时更新）
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `sort`=6 WHERE `path`='/admin/system' AND `type`=2 AND `deleted`=0 AND `sort`<>6;
UPDATE `menu` SET `sort`=5 WHERE `path`='/admin/flow' AND `type`=2 AND `deleted`=0 AND `sort`<>5;
UPDATE `menu` SET `sort`=4 WHERE `path`='/admin/project' AND `type`=2 AND `deleted`=0 AND `sort`<>4;

INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '问题类型', '/admin/issue-types', 0, 3, 'issue:type:list', 'CollectionTag', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/issue-types' AND `type`=2 AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 11. 后台菜单：系统管理 → 新增「网站设置」（排在「系统设置」之前）
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `sort`=6 WHERE `path`='/admin/system/settings' AND `type`=2 AND `deleted`=0 AND `sort`<>6;

INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '网站设置', '/admin/system/site',
       (SELECT `id` FROM `menu` WHERE `path`='/admin/system' AND `type`=2 AND `deleted`=0),
       5, 'site:config:update', 'Monitor', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/system/site' AND `type`=2 AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 12. 菜单 icon 全量合法性修复
--     12.1 模块配置 Tree（@element-plus/icons-vue 不存在）→ Grid
--     12.2 全量兜底：任何仍非法的 icon 统一替换为 Grid（白名单比对）
--     12.3 清理僵尸菜单 /admin/settings（Phase2 遗留，无对应路由，点击必 404）
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `icon`='Grid' WHERE `icon`='Tree' AND `deleted`=0;

UPDATE `menu` SET `icon`='Grid'
WHERE `deleted`=0 AND `icon` IS NOT NULL AND `icon`<>''
  AND `icon` NOT IN (
    'HomeFilled','Tickets','EditPen','DataLine','Management','Folder','Grid','Switch',
    'Operation','Tools','Setting','User','UserFilled','OfficeBuilding','Document',
    'CollectionTag','Monitor','Menu','List','Brush','FullScreen','Aim','Star','Bell',
    'Search','Plus','Delete','Edit','Refresh','Download','Upload','Link','Filter',
    'Histogram','PieChart','TrendCharts','DataBoard','DataAnalysis','Odometer','Notebook'
  );

UPDATE `menu` SET `deleted`=1 WHERE `path`='/admin/settings' AND `type`=2 AND `deleted`=0;

-- ---------------------------------------------------------------------------
-- 13. 权限种子：5 个新权限码 + 授 ADMIN（role_permission 无 updated_at 列！）
-- ---------------------------------------------------------------------------
INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'issue:type:list', '问题类型查看', 'issueType', 'list', 2, 101, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='issue:type:list');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'issue:type:create', '问题类型新增', 'issueType', 'create', 2, 102, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='issue:type:create');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'issue:type:update', '问题类型编辑', 'issueType', 'update', 2, 103, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='issue:type:update');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'issue:type:delete', '问题类型删除', 'issueType', 'delete', 2, 104, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='issue:type:delete');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'site:config:update', '网站设置维护', 'site', 'update', 2, 105, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='site:config:update');

SET @admin_role := (SELECT `id` FROM `role` WHERE `code`='ADMIN');

INSERT INTO `role_permission` (`role_id`,`permission_id`,`created_at`)
SELECT @admin_role, p.`id`, NOW()
FROM `permission` p
WHERE p.`code` IN ('issue:type:list','issue:type:create','issue:type:update','issue:type:delete','site:config:update')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.`role_id`=@admin_role AND rp.`permission_id`=p.`id`);

-- ---------------------------------------------------------------------------
-- 14. sys_config：site.* 七键默认值（已存在则不覆盖，避免二次执行冲掉管理员配置）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`,`config_value`,`description`,`created_at`,`updated_at`)
SELECT 'site.name', 'issueFlow', '站点名称', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='site.name');

INSERT INTO `sys_config` (`config_key`,`config_value`,`description`,`created_at`,`updated_at`)
SELECT 'site.short_name', 'IF', '站点简称', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='site.short_name');

INSERT INTO `sys_config` (`config_key`,`config_value`,`description`,`created_at`,`updated_at`)
SELECT 'site.subtitle', '问题跟踪与流程管理平台', '站点副标题', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='site.subtitle');

INSERT INTO `sys_config` (`config_key`,`config_value`,`description`,`created_at`,`updated_at`)
SELECT 'site.default_theme', 'light', '前台默认主题 light/dark/blue/green', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='site.default_theme');

INSERT INTO `sys_config` (`config_key`,`config_value`,`description`,`created_at`,`updated_at`)
SELECT 'site.default_locale', 'zh-CN', '默认语言 zh-CN/en-US', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='site.default_locale');

INSERT INTO `sys_config` (`config_key`,`config_value`,`description`,`created_at`,`updated_at`)
SELECT 'site.copyright', '(c) 2026 issueFlow', '版权信息', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='site.copyright');

INSERT INTO `sys_config` (`config_key`,`config_value`,`description`,`created_at`,`updated_at`)
SELECT 'site.icp', '', 'ICP 备案号', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='site.icp');
