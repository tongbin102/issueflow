-- ============================================================
-- issueFlow Phase 2 增量 DDL + 种子数据
-- 内容：issue_relation / permission / role_permission 三表；
--       menu 加 type 列；全模块种子（组织/项目/用户/角色权限/菜单按端/问题含关联/sys_config）
-- 约定：CREATE TABLE IF NOT EXISTS + INSERT ... WHERE NOT EXISTS / INSERT IGNORE，可重复执行
--       父子/映射关系全部通过 name/code 子查询解析，禁止硬编码 ID
-- ============================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 新表：issue_relation（仅存前置边，后置由反向推导）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `issue_relation` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `issue_id`   BIGINT       NOT NULL COMMENT '当前问题 X',
  `related_id` BIGINT       NOT NULL COMMENT '关联问题 P（rel_type=1 表示 P 是 X 的前置）',
  `rel_type`   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=related_id 是 issue_id 的前置任务',
  `created_at` DATETIME     DEFAULT NULL,
  `updated_at` DATETIME     DEFAULT NULL,
  `deleted`    INT          DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ir` (`issue_id`,`related_id`,`rel_type`),
  KEY `idx_ir_related` (`related_id`,`rel_type`),
  KEY `idx_ir_issue` (`issue_id`,`rel_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题关联表（前置边）';

-- ---------------------------------------------------------------------------
-- 2. 新表：permission（权限目录）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `permission` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(100) NOT NULL COMMENT 'module:resource:action',
  `name`       VARCHAR(100) NOT NULL COMMENT '权限名称',
  `module`     VARCHAR(50)  DEFAULT NULL COMMENT '模块',
  `action`     VARCHAR(30)  DEFAULT NULL COMMENT '动作',
  `type`       TINYINT      DEFAULT 2 COMMENT '1=前台端 2=后台端（授权页分组）',
  `sort`       INT          DEFAULT 0,
  `created_at` DATETIME     DEFAULT NULL,
  `updated_at` DATETIME     DEFAULT NULL,
  `deleted`    INT          DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限目录';

-- ---------------------------------------------------------------------------
-- 3. 新表：role_permission（角色-权限映射）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role_permission` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT,
  `role_id`       BIGINT   NOT NULL,
  `permission_id` BIGINT   NOT NULL,
  `created_at`    DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rp` (`role_id`,`permission_id`),
  KEY `idx_rp_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限映射';

-- ---------------------------------------------------------------------------
-- 4. 既有 menu 加端维度（防重复列）
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu' AND COLUMN_NAME='type');
SET @sql := IF(@c=0,
  'ALTER TABLE `menu` ADD COLUMN `type` TINYINT NOT NULL DEFAULT 2 COMMENT \'1前台端 2后台端\' AFTER `icon`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 5. 权限目录种子（附录全量 27 条；INSERT IGNORE 幂等）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `permission` (`code`, `name`, `module`, `action`, `type`, `sort`, `created_at`, `updated_at`) VALUES
  ('dashboard:view',   '仪表盘查看',   'dashboard',   'view',   1, 1, NOW(), NOW()),
  ('issue:list',       '问题查看',     'issue',       'list',   2, 2, NOW(), NOW()),
  ('issue:create',     '问题创建',     'issue',       'create', 2, 3, NOW(), NOW()),
  ('issue:update',     '问题编辑',     'issue',       'update', 2, 4, NOW(), NOW()),
  ('issue:delete',     '问题删除',     'issue',       'delete', 2, 5, NOW(), NOW()),
  ('issue:export',     '问题导出',     'issue',       'export', 2, 6, NOW(), NOW()),
  ('project:list',     '项目查看',     'project',     'list',   2, 7, NOW(), NOW()),
  ('project:create',   '项目创建',     'project',     'create', 2, 8, NOW(), NOW()),
  ('project:update',   '项目编辑',     'project',     'update', 2, 9, NOW(), NOW()),
  ('project:delete',   '项目删除',     'project',     'delete', 2, 10, NOW(), NOW()),
  ('project:export',   '项目导出',     'project',     'export', 2, 11, NOW(), NOW()),
  ('user:list',        '用户查看',     'user',        'list',   2, 12, NOW(), NOW()),
  ('user:create',      '用户创建',     'user',        'create', 2, 13, NOW(), NOW()),
  ('user:update',      '用户编辑',     'user',        'update', 2, 14, NOW(), NOW()),
  ('user:delete',      '用户删除',     'user',        'delete', 2, 15, NOW(), NOW()),
  ('organization:list',     '组织查看',   'organization','list',   2, 16, NOW(), NOW()),
  ('organization:create',   '组织创建',   'organization','create', 2, 17, NOW(), NOW()),
  ('organization:update',   '组织编辑',   'organization','update', 2, 18, NOW(), NOW()),
  ('organization:delete',   '组织删除',   'organization','delete', 2, 19, NOW(), NOW()),
  ('menu:list',        '菜单查看',     'menu',        'list',   2, 20, NOW(), NOW()),
  ('menu:create',      '菜单创建',     'menu',        'create', 2, 21, NOW(), NOW()),
  ('menu:update',      '菜单编辑',     'menu',        'update', 2, 22, NOW(), NOW()),
  ('menu:delete',      '菜单删除',     'menu',        'delete', 2, 23, NOW(), NOW()),
  ('role:list',        '角色查看',     'role',        'list',   2, 24, NOW(), NOW()),
  ('role:create',      '角色创建',     'role',        'create', 2, 25, NOW(), NOW()),
  ('role:update',      '角色编辑',     'role',        'update', 2, 26, NOW(), NOW()),
  ('role:delete',      '角色删除',     'role',        'delete', 2, 27, NOW(), NOW()),
  ('role:assign',      '角色授权',     'role',        'assign', 2, 28, NOW(), NOW()),
  ('settings:view',    '设置查看',     'settings',    'view',   2, 29, NOW(), NOW()),
  ('settings:update',  '设置修改',     'settings',    'update', 2, 30, NOW(), NOW()),
  ('flow:view',        '流程查看',     'flow',        'view',   2, 31, NOW(), NOW()),
  ('flow:config',      '流程配置',     'flow',        'config', 2, 32, NOW(), NOW());

-- ---------------------------------------------------------------------------
-- 6. 角色权限映射种子（ADMIN 全量；职能角色最小集；INSERT IGNORE 幂等）
-- ---------------------------------------------------------------------------
-- ADMIN：全部权限
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `created_at`)
SELECT r.id, p.id, NOW()
FROM `role` r, `permission` p
WHERE r.`code` = 'ADMIN' AND p.`id` IS NOT NULL;

-- SUBMITTER / DEVELOPER / TESTER：issue:list/create/update + dashboard:view
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `created_at`)
SELECT r.id, p.id, NOW()
FROM `role` r, `permission` p
WHERE r.`code` IN ('SUBMITTER', 'DEVELOPER', 'TESTER')
  AND p.`code` IN ('issue:list', 'issue:create', 'issue:update', 'dashboard:view');

-- ---------------------------------------------------------------------------
-- 7. 菜单种子（按端：后台 type=2 / 前台 type=1；path+type 去重幂等）
-- ---------------------------------------------------------------------------
-- 后台端（type=2）顶级
INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '概览',     '/admin/index',        0, 1, NULL,           'DataLine',    2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/index' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '问题管理', '/admin/issues',       0, 2, 'issue:list',    'Tickets',    2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/issues' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '项目管理', '/admin/projects',      0, 3, 'project:list',  'Folder',     2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/projects' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '流程监控', '/admin/flow-monitor',  0, 4, 'flow:view',      'Switch',     2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/flow-monitor' AND `type`=2 AND `deleted`=0);

-- 系统管理（父级，无权限即登录可见）
INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '系统管理', '/admin/system',        0, 5, NULL,            'Setting',    2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/system' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '流程配置', '/admin/flow-config',   0, 6, 'flow:config',   'Tools',     2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/flow-config' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '系统设置', '/admin/settings',      0, 7, 'settings:update','Brush',     2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/settings' AND `type`=2 AND `deleted`=0);

-- 系统管理子项（parent_id 由 name+type 子查询解析）
INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '用户管理', '/admin/system/users',        (SELECT `id` FROM `menu` WHERE `name`='系统管理' AND `type`=2 AND `deleted`=0), 1, 'user:list',        'User',         2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/system/users' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '组织管理', '/admin/system/organizations',(SELECT `id` FROM `menu` WHERE `name`='系统管理' AND `type`=2 AND `deleted`=0), 2, 'organization:list','OfficeBuilding',2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/system/organizations' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '菜单管理', '/admin/system/menus',       (SELECT `id` FROM `menu` WHERE `name`='系统管理' AND `type`=2 AND `deleted`=0), 3, 'menu:list',        'Grid',         2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/system/menus' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '角色管理', '/admin/system/roles',       (SELECT `id` FROM `menu` WHERE `name`='系统管理' AND `type`=2 AND `deleted`=0), 4, 'role:list',        'UserFilled',   2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/system/roles' AND `type`=2 AND `deleted`=0);

-- 前台端（type=1）顶级
INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '工作台',   '/user',          0, 1, NULL,         'HomeFilled', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/user' AND `type`=1 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '我的问题', '/user/my-issues', 0, 2, NULL,         'Tickets',   1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/user/my-issues' AND `type`=1 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '提交问题', '/user/submit-issue',0,3, NULL,         'EditPen',   1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/user/submit-issue' AND `type`=1 AND `deleted`=0);

INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '个人看板', '/user/stats',    0, 4, NULL,         'DataLine',  1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/user/stats' AND `type`=1 AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 8. 流程配置（sys_config）
-- ---------------------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
SELECT 'flow_reopen_enabled', '1', '允许重开已关闭问题', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='flow_reopen_enabled');

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
SELECT 'flow_reject_enabled', '1', '允许驳回问题', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='flow_reject_enabled');

-- ---------------------------------------------------------------------------
-- 9. 组织层级种子（父子用 name 子查询解析；幂等）
-- ---------------------------------------------------------------------------
INSERT INTO `organization` (`name`, `parent_id`, `sort`, `created_at`, `updated_at`)
SELECT '某某科技', 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `organization` WHERE `name`='某某科技' AND `deleted`=0);

INSERT INTO `organization` (`name`, `parent_id`, `sort`, `created_at`, `updated_at`)
SELECT '研发部', (SELECT `id` FROM `organization` WHERE `name`='某某科技' AND `deleted`=0), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `organization` WHERE `name`='研发部' AND `deleted`=0);

INSERT INTO `organization` (`name`, `parent_id`, `sort`, `created_at`, `updated_at`)
SELECT '平台组', (SELECT `id` FROM `organization` WHERE `name`='研发部' AND `deleted`=0), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `organization` WHERE `name`='平台组' AND `deleted`=0);

INSERT INTO `organization` (`name`, `parent_id`, `sort`, `created_at`, `updated_at`)
SELECT '应用组', (SELECT `id` FROM `organization` WHERE `name`='研发部' AND `deleted`=0), 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `organization` WHERE `name`='应用组' AND `deleted`=0);

INSERT INTO `organization` (`name`, `parent_id`, `sort`, `created_at`, `updated_at`)
SELECT '测试部', (SELECT `id` FROM `organization` WHERE `name`='某某科技' AND `deleted`=0), 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `organization` WHERE `name`='测试部' AND `deleted`=0);

INSERT INTO `organization` (`name`, `parent_id`, `sort`, `created_at`, `updated_at`)
SELECT '产品部', (SELECT `id` FROM `organization` WHERE `name`='某某科技' AND `deleted`=0), 3, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `organization` WHERE `name`='产品部' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 10. 项目种子（默认项目已存在；新增 3 个；幂等）
-- ---------------------------------------------------------------------------
INSERT INTO `project` (`name`, `description`, `status`, `created_at`, `updated_at`)
SELECT '核心交易系统', '核心交易系统项目', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `project` WHERE `name`='核心交易系统' AND `deleted`=0);

INSERT INTO `project` (`name`, `description`, `status`, `created_at`, `updated_at`)
SELECT '移动 App', '移动端应用项目', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `project` WHERE `name`='移动 App' AND `deleted`=0);

INSERT INTO `project` (`name`, `description`, `status`, `created_at`, `updated_at`)
SELECT '数据中台', '数据平台项目', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `project` WHERE `name`='数据中台' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 11. 用户种子（职能角色；密码 BCrypt('admin123') 与默认管理员一致；幂等）
-- ---------------------------------------------------------------------------
-- 密码哈希：$2b$10$eK/tSXitHTRwiWWeCNYM3OlCZRYSvWGJ069D9.1cdHrjBQW.AfBEy  (admin123)
SET @PWD := '$2b$10$eK/tSXitHTRwiWWeCNYM3OlCZRYSvWGJ069D9.1cdHrjBQW.AfBEy';

INSERT INTO `user` (`username`, `password`, `real_name`, `email`, `role_id`, `status`, `created_at`, `updated_at`)
SELECT 'dev_zhang', @PWD, '张开发', 'dev_zhang@example.com', (SELECT `id` FROM `role` WHERE `code`='DEVELOPER'), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username`='dev_zhang' AND `deleted`=0);

INSERT INTO `user` (`username`, `password`, `real_name`, `email`, `role_id`, `status`, `created_at`, `updated_at`)
SELECT 'dev_zhao', @PWD, '赵研发', 'dev_zhao@example.com', (SELECT `id` FROM `role` WHERE `code`='DEVELOPER'), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username`='dev_zhao' AND `deleted`=0);

INSERT INTO `user` (`username`, `password`, `real_name`, `email`, `role_id`, `status`, `created_at`, `updated_at`)
SELECT 'test_li', @PWD, '李测试', 'test_li@example.com', (SELECT `id` FROM `role` WHERE `code`='TESTER'), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username`='test_li' AND `deleted`=0);

INSERT INTO `user` (`username`, `password`, `real_name`, `email`, `role_id`, `status`, `created_at`, `updated_at`)
SELECT 'test_qian', @PWD, '钱测试', 'test_qian@example.com', (SELECT `id` FROM `role` WHERE `code`='TESTER'), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username`='test_qian' AND `deleted`=0);

INSERT INTO `user` (`username`, `password`, `real_name`, `email`, `role_id`, `status`, `created_at`, `updated_at`)
SELECT 'submit_wang', @PWD, '王提交', 'submit_wang@example.com', (SELECT `id` FROM `role` WHERE `code`='SUBMITTER'), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username`='submit_wang' AND `deleted`=0);

INSERT INTO `user` (`username`, `password`, `real_name`, `email`, `role_id`, `status`, `created_at`, `updated_at`)
SELECT 'submit_sun', @PWD, '孙提交', 'submit_sun@example.com', (SELECT `id` FROM `role` WHERE `code`='SUBMITTER'), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username`='submit_sun' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 12. 问题种子（≥12 条，覆盖状态/严重等级/标签/项目；幂等按 issue_no）
-- ---------------------------------------------------------------------------
INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0001', '登录页在 Safari 崩溃', 'Safari 15 下点击登录按钮白屏崩溃', 1, '崩溃,兼容', 1, (SELECT id FROM `user` WHERE username='submit_wang'), (SELECT id FROM `user` WHERE username='dev_zhang'), (SELECT id FROM `project` WHERE name='核心交易系统' AND deleted=0 LIMIT 1), '1.0.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0001' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0002', '支付回调偶发超时', '高并发下支付回调超时率升高', 0, '性能', 2, (SELECT id FROM `user` WHERE username='submit_wang'), (SELECT id FROM `user` WHERE username='dev_zhang'), (SELECT id FROM `project` WHERE name='核心交易系统' AND deleted=0 LIMIT 1), '1.0.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0002' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0003', '订单状态不一致', '订单中心与交易状态不同步', 1, '数据', 0, (SELECT id FROM `user` WHERE username='submit_sun'), (SELECT id FROM `user` WHERE username='dev_zhao'), (SELECT id FROM `project` WHERE name='核心交易系统' AND deleted=0 LIMIT 1), '1.0.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0003' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0004', '移动端首页白屏', '冷启动首页白屏，需多次重试', 0, '崩溃,UI', 3, (SELECT id FROM `user` WHERE username='submit_wang'), (SELECT id FROM `user` WHERE username='dev_zhao'), (SELECT id FROM `project` WHERE name='移动 App' AND deleted=0 LIMIT 1), '2.1.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0004' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0005', 'App 推送延迟', '消息推送延迟 5 分钟以上', 2, '性能', 1, (SELECT id FROM `user` WHERE username='submit_sun'), (SELECT id FROM `user` WHERE username='dev_zhang'), (SELECT id FROM `project` WHERE name='移动 App' AND deleted=0 LIMIT 1), '2.1.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0005' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0006', '报表导出乱码', '导出 CSV 中文乱码', 2, 'UI', 2, (SELECT id FROM `user` WHERE username='submit_wang'), (SELECT id FROM `user` WHERE username='dev_zhao'), (SELECT id FROM `project` WHERE name='数据中台' AND deleted=0 LIMIT 1), '3.0.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0006' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`, `closed_at`)
SELECT 'IS-20250801-0007', '数据同步失败', '离线任务同步失败率上升', 1, '数据', 4, (SELECT id FROM `user` WHERE username='submit_sun'), (SELECT id FROM `user` WHERE username='dev_zhao'), (SELECT id FROM `project` WHERE name='数据中台' AND deleted=0 LIMIT 1), '3.0.0', NOW(), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0007' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0008', '权限校验绕过', '越权访问管理接口风险', 0, '安全', 3, (SELECT id FROM `user` WHERE username='submit_wang'), (SELECT id FROM `user` WHERE username='dev_zhang'), (SELECT id FROM `project` WHERE name='核心交易系统' AND deleted=0 LIMIT 1), '1.0.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0008' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0009', '审计日志缺失', '关键操作无审计日志', 1, '安全', 1, (SELECT id FROM `user` WHERE username='submit_sun'), (SELECT id FROM `user` WHERE username='dev_zhao'), (SELECT id FROM `project` WHERE name='核心交易系统' AND deleted=0 LIMIT 1), '1.0.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0009' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0010', '缓存击穿', '热点key失效导致数据库压力飙升', 2, '性能', 0, (SELECT id FROM `user` WHERE username='submit_wang'), (SELECT id FROM `user` WHERE username='dev_zhao'), (SELECT id FROM `project` WHERE name='数据中台' AND deleted=0 LIMIT 1), '3.0.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0010' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0011', '搜索结果不准确', '搜索相关性差', 3, 'UI', 0, (SELECT id FROM `user` WHERE username='submit_sun'), (SELECT id FROM `user` WHERE username='dev_zhang'), (SELECT id FROM `project` WHERE name='移动 App' AND deleted=0 LIMIT 1), '2.1.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0011' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`, `closed_at`)
SELECT 'IS-20250801-0012', '批量导入卡顿', '万级数据导入卡死', 2, '性能', 4, (SELECT id FROM `user` WHERE username='submit_wang'), (SELECT id FROM `user` WHERE username='dev_zhao'), (SELECT id FROM `project` WHERE name='数据中台' AND deleted=0 LIMIT 1), '3.0.0', NOW(), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0012' AND `deleted`=0);

INSERT INTO `issue` (`issue_no`, `title`, `description`, `severity`, `tags`, `status`, `reporter_id`, `assignee_id`, `project_id`, `env_app_version`, `created_at`, `updated_at`)
SELECT 'IS-20250801-0013', '验证码刷新无效', '点击刷新验证码无变化', 3, 'UI,兼容', 1, (SELECT id FROM `user` WHERE username='submit_sun'), (SELECT id FROM `user` WHERE username='dev_zhang'), (SELECT id FROM `project` WHERE name='核心交易系统' AND deleted=0 LIMIT 1), '1.0.0', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue` WHERE `issue_no`='IS-20250801-0013' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 13. 问题关联种子（前置边建模：仅落前置边；链式 A→B→C 与 D→E，验证防环）
--     edge(issue_id=X, related_id=P) ⇔ P 是 X 的前置
-- ---------------------------------------------------------------------------
-- 0001 前置 0002；0002 前置 0003（链式：0003→0002→0001）
INSERT INTO `issue_relation` (`issue_id`, `related_id`, `rel_type`, `created_at`, `updated_at`)
SELECT (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0001' AND deleted=0),
       (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0002' AND deleted=0), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_relation` WHERE issue_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0001' AND deleted=0) AND related_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0002' AND deleted=0) AND rel_type=1 AND deleted=0);

INSERT INTO `issue_relation` (`issue_id`, `related_id`, `rel_type`, `created_at`, `updated_at`)
SELECT (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0002' AND deleted=0),
       (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0003' AND deleted=0), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_relation` WHERE issue_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0002' AND deleted=0) AND related_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0003' AND deleted=0) AND rel_type=1 AND deleted=0);

-- 0004 前置 0005（D→E）
INSERT INTO `issue_relation` (`issue_id`, `related_id`, `rel_type`, `created_at`, `updated_at`)
SELECT (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0004' AND deleted=0),
       (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0005' AND deleted=0), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_relation` WHERE issue_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0004' AND deleted=0) AND related_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0005' AND deleted=0) AND rel_type=1 AND deleted=0);

-- 0006 前置 0007
INSERT INTO `issue_relation` (`issue_id`, `related_id`, `rel_type`, `created_at`, `updated_at`)
SELECT (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0006' AND deleted=0),
       (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0007' AND deleted=0), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_relation` WHERE issue_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0006' AND deleted=0) AND related_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0007' AND deleted=0) AND rel_type=1 AND deleted=0);

-- 0008 前置 0009；0009 前置 0010（第二组链式）
INSERT INTO `issue_relation` (`issue_id`, `related_id`, `rel_type`, `created_at`, `updated_at`)
SELECT (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0008' AND deleted=0),
       (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0009' AND deleted=0), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_relation` WHERE issue_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0008' AND deleted=0) AND related_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0009' AND deleted=0) AND rel_type=1 AND deleted=0);

INSERT INTO `issue_relation` (`issue_id`, `related_id`, `rel_type`, `created_at`, `updated_at`)
SELECT (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0009' AND deleted=0),
       (SELECT id FROM `issue` WHERE issue_no='IS-20250801-0010' AND deleted=0), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `issue_relation` WHERE issue_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0009' AND deleted=0) AND related_id=(SELECT id FROM `issue` WHERE issue_no='IS-20250801-0010' AND deleted=0) AND rel_type=1 AND deleted=0);

-- 防环验证用例（联调用，不应写入如下环边；仅作注释说明）：
-- 尝试 0003 前置 0001（edge(3,1)）时，BFS 从 3 出发命中 1（3→2→1），应被拒（RELATION_CYCLE）。
