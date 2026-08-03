-- ===========================================================================
-- issueFlow Phase10：数据管理（备份 / 恢复）
--
-- 【本脚本做四件事】
--   §1 建表：backup_record（备份记录）/ restore_record（恢复记录）
--   §2 field_config 预埋 5 列（本期不启用，供需求二「高级属性」后续扩展）
--   §3 sys_config 6 条数据管理配置项（复用既有 sys_config，不新建配置表）
--   §4 菜单 / 权限迁移：
--        4.1 「备份设置」→「数据管理」更名 + 路由改为 /admin/system/data-management
--        4.2 下线「数据维护」菜单（若存在）
--        4.3 注册 system:data:view|backup|download|delete|upload|restore|config 七个权限码
--        4.4 全部授予 ADMIN（仅超管可见可用）
--        4.5 旧权限码 system:backup:export 保留不删（兼容一版，前端已不再引用）
--
-- 【依赖】
--   必须在 V20260806d_fix_project_ref_order.sql 之后执行：
--     · §2 依赖 #17 V20260806_dynamic_field.sql 建的 field_config 表
--     · §4.1 依赖 #12 W1 把 menu id=19 改名为「备份设置」、#15 W4 的 menu 终态
--     · §4.3 依赖 #5 phase2 建的 permission / role_permission 表
--
-- 【幂等】
--   建表 CREATE TABLE IF NOT EXISTS；
--   加列 information_schema 探测 + PREPARE/EXECUTE（MySQL 不支持 ADD COLUMN IF NOT EXISTS）；
--   种子 INSERT ... SELECT ... WHERE NOT EXISTS；
--   更名 UPDATE 带 `name <> 目标值` 守卫。可重复执行，二次运行影响 0 行。
--
-- 【表结构约定 —— 勿臆造】
--   permission 表实测字段：id, code, name, module, action, type, sort,
--                          created_at, updated_at, deleted（无 parent_id）
--   role_permission 表**无 updated_at 列**，种子不得携带（Phase6 血泪教训）。
--   menu 表字段：id, name, path, parent_id, sort, permission, icon, type,
--                created_at, updated_at, deleted
--   sys_config 表字段：id, config_key, config_value, description, updated_at
--                （无 created_at / deleted，见 entity/SysConfig.java）
--
-- 【字符集】中文字面量，必须 utf8mb4，见下方 SET NAMES。
-- 执行环境：24 号机 MySQL8，issueflow_db 库
-- 日期：2026-08-03（Phase10）
-- ===========================================================================
SET NAMES utf8mb4;

-- ===========================================================================
-- §1 建表
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1.1 backup_record —— 备份记录
--     一次备份 = 一条记录。task_id 为业务主键（UUID），与 Redis dm:task:{taskId} 对应。
--     file_path 仅存**相对备份根目录**的路径，绝不存绝对路径（安全红线：日志/接口不外泄全路径）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `backup_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`       VARCHAR(64)  NOT NULL                COMMENT '任务号（UUID，无横线），对应 Redis dm:task:{taskId}',
  `file_name`     VARCHAR(255) NOT NULL                COMMENT '备份文件名，形如 issueflow_backup_20260803_101530_FULL.zip',
  `file_path`     VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '相对备份根目录的路径（绝不存绝对路径）',
  `file_size`     BIGINT       NOT NULL DEFAULT 0      COMMENT '文件字节数，进行中为 0',
  `checksum`      VARCHAR(80)  DEFAULT NULL            COMMENT 'SHA-256 摘要（小写 hex），用于下载/恢复前完整性校验',
  `backup_type`   VARCHAR(32)  NOT NULL DEFAULT 'FULL' COMMENT '备份类型：FULL 全量 / DB_ONLY 仅数据库 / CONFIG_ONLY 仅配置',
  `source`        VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 手动 / AUTO 定时 / UPLOAD 外部上传 / PRE_RESTORE 恢复前自动备份',
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELED',
  `phase`         VARCHAR(32)  NOT NULL DEFAULT 'INIT' COMMENT '阶段：INIT/LOCK/DUMP_DB/DUMP_CONFIG/PACKAGE/CHECKSUM/PERSIST/DONE',
  `progress`      INT          NOT NULL DEFAULT 0      COMMENT '进度百分比 0-100',
  `error_msg`     VARCHAR(1000) DEFAULT NULL           COMMENT '失败原因（已脱敏，绝不含密码/全路径/dump 片段）',
  `db_name`       VARCHAR(64)  DEFAULT NULL            COMMENT '备份时的数据库名',
  `app_version`   VARCHAR(64)  DEFAULT NULL            COMMENT '备份时的应用版本，恢复时做兼容性提示',
  `table_count`   INT          NOT NULL DEFAULT 0      COMMENT '备份包含的表数量',
  `remark`        VARCHAR(500) DEFAULT NULL            COMMENT '备注（用户填写）',
  `operator_id`   BIGINT       DEFAULT NULL            COMMENT '操作人 user.id',
  `operator_name` VARCHAR(64)  DEFAULT NULL            COMMENT '操作人姓名快照',
  `started_at`    DATETIME     DEFAULT NULL            COMMENT '开始时间',
  `finished_at`   DATETIME     DEFAULT NULL            COMMENT '结束时间',
  `duration_ms`   BIGINT       NOT NULL DEFAULT 0      COMMENT '耗时毫秒',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_backup_task` (`task_id`),
  KEY `idx_backup_status`  (`status`),
  KEY `idx_backup_created` (`created_at`),
  KEY `idx_backup_source`  (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据备份记录（Phase10 数据管理）';

-- ---------------------------------------------------------------------------
-- 1.2 restore_record —— 恢复记录
--     pre_backup_id 指向恢复前自动生成的「安全备份」，便于回退。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `restore_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`        VARCHAR(64)  NOT NULL                COMMENT '任务号（UUID，无横线），对应 Redis dm:task:{taskId}',
  `backup_id`      BIGINT       DEFAULT NULL            COMMENT '所恢复的 backup_record.id；上传恢复时为新建的 UPLOAD 记录 id',
  `backup_file_name` VARCHAR(255) NOT NULL DEFAULT ''   COMMENT '所恢复的备份文件名快照',
  `restore_source` VARCHAR(32)  NOT NULL DEFAULT 'LOCAL' COMMENT '恢复来源：LOCAL 本地备份 / UPLOAD 上传文件',
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELED',
  `phase`          VARCHAR(32)  NOT NULL DEFAULT 'INIT' COMMENT '阶段：INIT/LOCK/VALIDATE/PRE_BACKUP/UNPACK/IMPORT_DB/REFRESH_CACHE/DONE',
  `progress`       INT          NOT NULL DEFAULT 0      COMMENT '进度百分比 0-100',
  `error_msg`      VARCHAR(1000) DEFAULT NULL           COMMENT '失败原因（已脱敏）',
  `pre_backup_id`  BIGINT       DEFAULT NULL            COMMENT '恢复前自动安全备份的 backup_record.id',
  `affected_tables` INT         NOT NULL DEFAULT 0      COMMENT '恢复涉及的表数量',
  `operator_id`    BIGINT       DEFAULT NULL            COMMENT '操作人 user.id',
  `operator_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '操作人姓名快照',
  `started_at`     DATETIME     DEFAULT NULL            COMMENT '开始时间',
  `finished_at`    DATETIME     DEFAULT NULL            COMMENT '结束时间',
  `duration_ms`    BIGINT       NOT NULL DEFAULT 0      COMMENT '耗时毫秒',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_restore_task` (`task_id`),
  KEY `idx_restore_status`  (`status`),
  KEY `idx_restore_created` (`created_at`),
  KEY `idx_restore_backup`  (`backup_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据恢复记录（Phase10 数据管理）';

-- ===========================================================================
-- §2 field_config 预埋 5 列（需求二「高级属性」区块的后续扩展位，本期仅建列不启用）
--    MySQL 不支持 ADD COLUMN IF NOT EXISTS，逐列 information_schema 探测。
--    ⚠ `unique` 是 MySQL 保留字，必须反引号包裹。
-- ===========================================================================

-- 2.1 unique：是否唯一校验
SET @fc_uniq_exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_config' AND COLUMN_NAME = 'unique'
);
SET @fc_uniq_sql := IF(
  @fc_uniq_exist = 0,
  'ALTER TABLE `field_config` ADD COLUMN `unique` TINYINT NULL DEFAULT 0 COMMENT ''是否唯一校验：0 否 1 是（Phase10 预埋，暂未启用）''',
  'SELECT 1'
);
PREPARE fc_uniq_stmt FROM @fc_uniq_sql; EXECUTE fc_uniq_stmt; DEALLOCATE PREPARE fc_uniq_stmt;

-- 2.2 regex_rule：自定义正则校验
SET @fc_regex_exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_config' AND COLUMN_NAME = 'regex_rule'
);
SET @fc_regex_sql := IF(
  @fc_regex_exist = 0,
  'ALTER TABLE `field_config` ADD COLUMN `regex_rule` VARCHAR(255) NULL DEFAULT NULL COMMENT ''自定义正则校验表达式（Phase10 预埋，暂未启用）''',
  'SELECT 1'
);
PREPARE fc_regex_stmt FROM @fc_regex_sql; EXECUTE fc_regex_stmt; DEALLOCATE PREPARE fc_regex_stmt;

-- 2.3 visible_roles：可见角色白名单
SET @fc_vr_exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_config' AND COLUMN_NAME = 'visible_roles'
);
SET @fc_vr_sql := IF(
  @fc_vr_exist = 0,
  'ALTER TABLE `field_config` ADD COLUMN `visible_roles` VARCHAR(255) NULL DEFAULT NULL COMMENT ''可见角色码，英文逗号分隔；空=全部可见（Phase10 预埋，暂未启用）''',
  'SELECT 1'
);
PREPARE fc_vr_stmt FROM @fc_vr_sql; EXECUTE fc_vr_stmt; DEALLOCATE PREPARE fc_vr_stmt;

-- 2.4 readonly_scope：只读范围
SET @fc_rs_exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_config' AND COLUMN_NAME = 'readonly_scope'
);
SET @fc_rs_sql := IF(
  @fc_rs_exist = 0,
  'ALTER TABLE `field_config` ADD COLUMN `readonly_scope` VARCHAR(64) NULL DEFAULT NULL COMMENT ''只读范围：NONE/CREATE/EDIT/ALL；空=不只读（Phase10 预埋，暂未启用）''',
  'SELECT 1'
);
PREPARE fc_rs_stmt FROM @fc_rs_sql; EXECUTE fc_rs_stmt; DEALLOCATE PREPARE fc_rs_stmt;

-- 2.5 remark：字段备注
SET @fc_rm_exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_config' AND COLUMN_NAME = 'remark'
);
SET @fc_rm_sql := IF(
  @fc_rm_exist = 0,
  'ALTER TABLE `field_config` ADD COLUMN `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT ''字段备注，仅管理端可见（Phase10 预埋，暂未启用）''',
  'SELECT 1'
);
PREPARE fc_rm_stmt FROM @fc_rm_sql; EXECUTE fc_rm_stmt; DEALLOCATE PREPARE fc_rm_stmt;

-- ===========================================================================
-- §3 sys_config 数据管理配置项（复用既有 sys_config，不新建配置表）
--    注意：sys_config 无 created_at / deleted 列，只有 id/config_key/config_value/
--          description/updated_at。
--    ⚠ 安全红线：此处**不存放任何数据库密码**，DB 连接凭据一律从 Spring 环境变量取。
-- ===========================================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
SELECT 'data.management.backup.dir', '/data/issueflow/backups', '备份文件存储根目录（容器内路径，需挂载持久卷，权限 0700）', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'data.management.backup.dir');

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
SELECT 'data.management.backup.retain.count', '20', '备份文件最大保留份数，超出后按时间从旧到新清理；0 表示不限制', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'data.management.backup.retain.count');

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
SELECT 'data.management.backup.retain.days', '30', '备份文件最大保留天数，超期自动清理；0 表示不限制', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'data.management.backup.retain.days');

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
SELECT 'data.management.upload.max.size.mb', '512', '上传恢复包的最大体积（MB），超出直接拒收', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'data.management.upload.max.size.mb');

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
SELECT 'data.management.task.timeout.seconds', '1800', '单个备份/恢复任务超时时间（秒），超时判失败并释放全局锁', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'data.management.task.timeout.seconds');

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
SELECT 'data.management.restore.pre.backup.enabled', 'true', '恢复前是否自动生成安全备份（强烈建议开启，便于回退）', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'data.management.restore.pre.backup.enabled');

-- ===========================================================================
-- §4 菜单 / 权限迁移
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 4.1 「备份设置」→「数据管理」：改名 + 改路由 + 换权限码
--     W1（#12）曾把 /admin/system/settings 的菜单名从「系统设置」改为「备份设置」，
--     本期统一更名为「数据管理」，路由同步迁到 /admin/system/data-management。
--     守卫条件确保重复执行影响 0 行。
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `name`       = '数据管理',
    `path`       = '/admin/system/data-management',
    `permission` = 'system:data:view',
    `icon`       = 'Coin',
    `updated_at` = NOW()
WHERE `path` IN ('/admin/system/settings', '/admin/system/data-management')
  AND `deleted` = 0
  AND (`name` <> '数据管理' OR `path` <> '/admin/system/data-management' OR `permission` <> 'system:data:view');

-- 兜底：若历史库中根本没有这条菜单，则补插一条（parent_id=5「系统管理」，沿用 W4 终态）
-- icon 取 Element Plus 白名单内的 Coin；sort=90 承接原「备份设置」号段。
INSERT INTO `menu` (`name`, `path`, `parent_id`, `sort`, `permission`, `icon`, `type`, `created_at`, `updated_at`)
SELECT '数据管理', '/admin/system/data-management', 5, 90, 'system:data:view', 'Coin', 2, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `menu` WHERE `path` = '/admin/system/data-management' AND `deleted` = 0
);

-- ---------------------------------------------------------------------------
-- 4.2 下线「数据维护」菜单（需求三①：系统设置移除数据维护入口）
--     历史库中该菜单可能以「数据维护」/「数据初始化」两种名称存在，一并逻辑删除。
--     同时回收其权限码 system:reset 的 ADMIN 授权 —— 入口已移除，权限不再暴露。
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `deleted` = 1, `updated_at` = NOW()
WHERE `deleted` = 0
  AND (`name` IN ('数据维护', '数据初始化') OR `permission` = 'system:reset');

-- ---------------------------------------------------------------------------
-- 4.3 注册数据管理七个权限码
--     module='dataManagement'，占用新号段 281-287（承接 fieldConfig 的 271-273）。
--     type=2 与全部功能权限一致。
-- ---------------------------------------------------------------------------
INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:data:view', '数据管理查看', 'dataManagement', 'view', 2, 281, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:data:view');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:data:backup', '手动备份', 'dataManagement', 'backup', 2, 282, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:data:backup');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:data:download', '备份下载', 'dataManagement', 'download', 2, 283, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:data:download');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:data:delete', '备份删除', 'dataManagement', 'delete', 2, 284, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:data:delete');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:data:upload', '备份上传', 'dataManagement', 'upload', 2, 285, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:data:upload');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:data:restore', '数据恢复', 'dataManagement', 'restore', 2, 286, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:data:restore');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:data:config', '数据管理配置', 'dataManagement', 'config', 2, 287, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:data:config');

-- ---------------------------------------------------------------------------
-- 4.4 全部授予 ADMIN（仅超管；role_permission 无 updated_at 列，勿加）
-- ---------------------------------------------------------------------------
-- 按 code 取 id，避免硬编码主键在不同环境漂移（沿用 V20260806b 的写法）
SET @dm_admin_role := (SELECT `id` FROM `role` WHERE `code` = 'ADMIN');

INSERT INTO `role_permission` (`role_id`, `permission_id`, `created_at`)
SELECT @dm_admin_role, p.`id`, NOW()
FROM `permission` p
WHERE p.`code` IN ('system:data:view','system:data:backup','system:data:download',
                   'system:data:delete','system:data:upload','system:data:restore',
                   'system:data:config')
  AND @dm_admin_role IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp
                  WHERE rp.`role_id` = @dm_admin_role AND rp.`permission_id` = p.`id`);

-- ---------------------------------------------------------------------------
-- 4.5 旧权限码兼容说明
--     system:backup:export（phase7 #669 注册）**保留不删**，
--     后端 BackupController 旧接口标注 @Deprecated 仍继续鉴权该码，
--     待下一版确认无外部调用后再随迁移脚本下线。此处仅记录，无 DDL/DML。
-- ---------------------------------------------------------------------------

-- ===========================================================================
-- §5 自检（只读，不改数据）
-- ===========================================================================
SELECT '【自检】backup_record 表' AS item,
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'backup_record') AS actual,
       1 AS expect;

SELECT '【自检】restore_record 表' AS item,
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'restore_record') AS actual,
       1 AS expect;

SELECT '【自检】field_config 预埋列' AS item,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'field_config'
          AND COLUMN_NAME IN ('unique','regex_rule','visible_roles','readonly_scope','remark')) AS actual,
       5 AS expect;

SELECT '【自检】sys_config 配置项' AS item,
       (SELECT COUNT(*) FROM `sys_config` WHERE `config_key` LIKE 'data.management.%') AS actual,
       6 AS expect;

SELECT '【自检】system:data:* 权限码' AS item,
       (SELECT COUNT(*) FROM `permission` WHERE `code` LIKE 'system:data:%' AND `deleted` = 0) AS actual,
       7 AS expect;

SELECT '【自检】ADMIN 授权数' AS item,
       (SELECT COUNT(*) FROM `role_permission` rp
        JOIN `role` r ON r.`id` = rp.`role_id`
        JOIN `permission` p ON p.`id` = rp.`permission_id`
        WHERE r.`code` = 'ADMIN' AND p.`code` LIKE 'system:data:%') AS actual,
       7 AS expect;

SELECT '【自检】数据管理菜单' AS item,
       (SELECT COUNT(*) FROM `menu`
        WHERE `path` = '/admin/system/data-management' AND `name` = '数据管理' AND `deleted` = 0) AS actual,
       1 AS expect;

SELECT '【自检】数据维护菜单已下线' AS item,
       (SELECT COUNT(*) FROM `menu`
        WHERE `deleted` = 0 AND (`name` IN ('数据维护','数据初始化') OR `permission` = 'system:reset')) AS actual,
       0 AS expect;
