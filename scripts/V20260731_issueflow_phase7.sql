-- ============================================================
-- issueFlow Phase 7 增量 DDL + 种子
-- 内容：
--   ①  dict / dict_item 建表（生成列条件唯一）+ 4 类字典 + 17 条预设项种子
--   ②  issue 加 source(VARCHAR 字典 item_code) / priority(TINYINT) + 索引 + 存量回填
--   ③  user 加 avatar / nickname / pwd_updated_at / bind_wechat / bind_dingtalk
--   ④  login_log 建表
--   ⑤  file_config 建表 + 单行默认配置；file_record 建表 + 存量 issue_attachment 回灌
--   ⑥  scheduled_task / scheduled_task_log 建表 + 2 条内置任务种子
--   ⑦  菜单重构（业务管理原地升级保留 id、项目/模块迁入、字典配置新增、
--       项目管理空分组逻辑删除、基础设施三层新增、一级 sort 重排、用户设置改名）
--   ⑧  21 个新权限码 + 授 ADMIN（ARCH §3.8 标称「22 个」，其代码块实际只列出 21 项，
--       且个人中心明确「不设权限码」，故本脚本按实际清单种 21 条）
--
-- 约定（与 Phase 1-6 完全一致）：
--   * 建表 CREATE TABLE IF NOT EXISTS
--   * 加列 / 加索引用 information_schema 计数 + PREPARE 动态 DDL
--   * 种子 INSERT ... SELECT ... WHERE NOT EXISTS
--   * 改存量用带「目标态不等于当前态」条件的 UPDATE
--   * 全部语句可重复执行（幂等）
--   * role_permission 表无 updated_at 列，种子不得携带
--   * 唯一性一律用「生成列 code_active + 单列唯一索引」，
--     禁止 (code, deleted) 复合唯一 —— 会导致 MyBatis-Plus 软删 UPDATE 撞唯一键、DELETE 接口 500
--
-- 前置：目标库须已执行 Phase 1-6 全部脚本（不可在空库首次运行）。
-- 部署顺序：先执行本脚本 → 再重启后端。
-- ⚠ 冲突提示：scripts/ 下另有一份 ARCH 字面版 V20260810_issueflow_phase7.sql
--   （dict_type + type_id + issue.source_id 口径）。两者结构互斥，**只能二选一执行**。
--   本文件为准（与 entity/Dict.java、DictItem.java、Issue.source 现状一致），
--   V20260810 应在确认后废弃，避免运维误灌两份。
-- 日期：2026-07-31
-- ============================================================
SET NAMES utf8mb4;

-- ===========================================================================
-- 第 1 节  字典表 dict / dict_item
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1.1 dict（字典类型）
--     dict_code 在 deleted=0 范围内唯一，用生成列 code_active 实现条件唯一。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `dict_code`   VARCHAR(50)  NOT NULL            COMMENT '字典编码（大写），程序依赖，创建后不可改',
  `name`        VARCHAR(50)  NOT NULL            COMMENT '字典名称',
  `description` VARCHAR(200) DEFAULT NULL        COMMENT '描述',
  `sort`        INT          NOT NULL DEFAULT 0  COMMENT '升序展示',
  `enabled`     TINYINT      NOT NULL DEFAULT 1  COMMENT '1启用 0停用',
  `is_system`   TINYINT      NOT NULL DEFAULT 0  COMMENT '1=系统预设，不可删除、编码不可改',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  -- 条件唯一辅助列：未删除行取 dict_code，软删行取 NULL（唯一索引忽略 NULL）
  `code_active` VARCHAR(50) GENERATED ALWAYS AS (IF(`deleted` = 0, `dict_code`, NULL)) VIRTUAL
                COMMENT '条件唯一辅助列，仅供 uk_dict_code_active 使用，Java 实体不映射',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code_active` (`code_active`),
  KEY `idx_dict_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

-- ---------------------------------------------------------------------------
-- 1.2 dict_item（字典项）
--     同一 dict_code 下 item_code 唯一：生成列拼接 CONCAT(dict_code,'_',item_code)。
--     采用「冗余 dict_code」而非 type_id，使 issue.source 直接存 item_code 后
--     下拉查询与回显均无需 JOIN dict 表。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_item` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `dict_code`   VARCHAR(50)  NOT NULL            COMMENT '所属字典编码，冗余存储避免 JOIN',
  `item_code`   VARCHAR(50)  NOT NULL            COMMENT '字典项编码（大写），同字典内唯一，预设项不可改',
  `name`        VARCHAR(50)  NOT NULL            COMMENT '字典项名称',
  `description` VARCHAR(200) DEFAULT NULL        COMMENT '描述',
  `sort`        INT          NOT NULL DEFAULT 0  COMMENT '升序展示',
  `enabled`     TINYINT      NOT NULL DEFAULT 1  COMMENT '1启用 0停用',
  `is_system`   TINYINT      NOT NULL DEFAULT 0  COMMENT '1=系统预设，删除接口硬拦截，仅可停用',
  `extra`       VARCHAR(200) DEFAULT NULL        COMMENT '扩展值：枚举镜像类字典存对应数值 code',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  -- 条件唯一辅助列：未删除行取 dict_code_item_code，软删行取 NULL
  `code_active` VARCHAR(120) GENERATED ALWAYS AS
                (IF(`deleted` = 0, CONCAT(`dict_code`, '_', `item_code`), NULL)) VIRTUAL
                COMMENT '条件唯一辅助列，仅供 uk_dict_item_active 使用，Java 实体不映射',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_item_active` (`code_active`),
  KEY `idx_dict_item_dict` (`dict_code`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项';

-- ---------------------------------------------------------------------------
-- 1.3 dict 种子（4 类，均 is_system=1）
--     ISSUE_PRIORITY / ISSUE_STATUS / ISSUE_SEVERITY 为「系统枚举镜像」：
--     页面可见可改名称，但业务取值仍走固定枚举，改名不影响后端逻辑。
-- ---------------------------------------------------------------------------
INSERT INTO `dict` (`dict_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`created_at`,`updated_at`)
SELECT 'ISSUE_SOURCE','问题来源','问题的录入渠道，可自由扩展',1,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict` WHERE `dict_code`='ISSUE_SOURCE' AND `deleted`=0);

INSERT INTO `dict` (`dict_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`created_at`,`updated_at`)
SELECT 'ISSUE_PRIORITY','优先级','系统枚举镜像：0高 1中 2低，改名不影响业务取值',2,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict` WHERE `dict_code`='ISSUE_PRIORITY' AND `deleted`=0);

INSERT INTO `dict` (`dict_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`created_at`,`updated_at`)
SELECT 'ISSUE_STATUS','问题状态','系统枚举镜像：0待处理~4已关闭，改名不影响流程引擎',3,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict` WHERE `dict_code`='ISSUE_STATUS' AND `deleted`=0);

INSERT INTO `dict` (`dict_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`created_at`,`updated_at`)
SELECT 'ISSUE_SEVERITY','严重等级','系统枚举镜像：0致命~3轻微，改名不影响业务取值',4,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict` WHERE `dict_code`='ISSUE_SEVERITY' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 1.4 dict_item 种子 —— ISSUE_SOURCE（5 项，均 is_system=1）
--     SYSTEM 为默认来源，存量 issue.source 回填该值。
-- ---------------------------------------------------------------------------
INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SOURCE','SYSTEM','系统录入','用户在系统页面手工提交（默认来源）',1,1,1,NULL,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SOURCE' AND `item_code`='SYSTEM' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SOURCE','API_IMPORT','接口导入','由外部系统通过开放接口写入',2,1,1,NULL,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SOURCE' AND `item_code`='API_IMPORT' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SOURCE','EXCEL_IMPORT','批量导入','通过 Excel 批量导入',3,1,1,NULL,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SOURCE' AND `item_code`='EXCEL_IMPORT' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SOURCE','EMAIL','邮件反馈','用户通过邮件反馈后录入',4,1,1,NULL,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SOURCE' AND `item_code`='EMAIL' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SOURCE','OTHER','其他','其他未分类来源',99,1,1,NULL,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SOURCE' AND `item_code`='OTHER' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 1.5 dict_item 种子 —— ISSUE_PRIORITY（3 项，extra 存数值 code）
-- ---------------------------------------------------------------------------
INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_PRIORITY','HIGH','高','需优先处理',1,1,1,'0',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_PRIORITY' AND `item_code`='HIGH' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_PRIORITY','MEDIUM','中','常规处理（默认）',2,1,1,'1',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_PRIORITY' AND `item_code`='MEDIUM' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_PRIORITY','LOW','低','可延后处理',3,1,1,'2',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_PRIORITY' AND `item_code`='LOW' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 1.6 dict_item 种子 —— ISSUE_STATUS（5 项，镜像 IssueStatusEnum 0-4）
--     item_code 必须与 IssueStatusEnum 常量名逐字一致
--     （OPEN / IN_PROGRESS / PENDING_VERIFY / VERIFIED / CLOSED），
--     flow_node.code 亦用同一套编码，改动会导致流程引擎与字典镜像对不上。
-- ---------------------------------------------------------------------------
INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_STATUS','OPEN','待处理','问题已提交，等待认领',1,1,1,'0',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_STATUS' AND `item_code`='OPEN' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_STATUS','IN_PROGRESS','处理中','已认领，开发处理中',2,1,1,'1',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_STATUS' AND `item_code`='IN_PROGRESS' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_STATUS','PENDING_VERIFY','待验证','已修复，等待测试验证',3,1,1,'2',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_STATUS' AND `item_code`='PENDING_VERIFY' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_STATUS','VERIFIED','验证通过','测试验证通过',4,1,1,'3',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_STATUS' AND `item_code`='VERIFIED' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_STATUS','CLOSED','已关闭','问题已闭环',5,1,1,'4',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_STATUS' AND `item_code`='CLOSED' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 1.7 dict_item 种子 —— ISSUE_SEVERITY（4 项，镜像 SeverityEnum 0-3）
-- ---------------------------------------------------------------------------
INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SEVERITY','FATAL','致命','系统不可用或数据丢失',1,1,1,'0',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SEVERITY' AND `item_code`='FATAL' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SEVERITY','SERIOUS','严重','主流程受阻，无变通方案',2,1,1,'1',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SEVERITY' AND `item_code`='SERIOUS' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SEVERITY','NORMAL','一般','功能异常但有变通方案',3,1,1,'2',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SEVERITY' AND `item_code`='NORMAL' AND `deleted`=0);

INSERT INTO `dict_item` (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`)
SELECT 'ISSUE_SEVERITY','MINOR','轻微','文案、样式等次要问题',4,1,1,'3',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `dict_code`='ISSUE_SEVERITY' AND `item_code`='MINOR' AND `deleted`=0);

-- ===========================================================================
-- 第 2 节  issue 表增量：source（来源，字典 item_code）+ priority（优先级）
-- ===========================================================================

-- 2.1 issue.source（VARCHAR，存 dict_item.item_code，dict_code='ISSUE_SOURCE'）
SET @c_src := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue' AND COLUMN_NAME='source');
SET @sql := IF(@c_src=0,
  'ALTER TABLE `issue` ADD COLUMN `source` VARCHAR(50) DEFAULT NULL COMMENT ''来源，存 dict_item.item_code（dict_code=ISSUE_SOURCE）'' AFTER `type_id`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2.2 issue.priority（TINYINT，0高 1中 2低，NOT NULL DEFAULT 1 → 加列瞬间存量即为「中」）
SET @c_pri := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue' AND COLUMN_NAME='priority');
SET @sql := IF(@c_pri=0,
  'ALTER TABLE `issue` ADD COLUMN `priority` TINYINT NOT NULL DEFAULT 1 COMMENT ''优先级：0高 1中 2低'' AFTER `severity`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2.3 索引 idx_issue_source
SET @i_src := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue' AND INDEX_NAME='idx_issue_source');
SET @sql := IF(@i_src=0, 'ALTER TABLE `issue` ADD KEY `idx_issue_source` (`source`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2.4 索引 idx_issue_priority
SET @i_pri := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue' AND INDEX_NAME='idx_issue_priority');
SET @sql := IF(@i_pri=0, 'ALTER TABLE `issue` ADD KEY `idx_issue_priority` (`priority`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2.5 存量回填：source 为空 → 'SYSTEM'（验收口径 COUNT(*) WHERE source IS NULL = 0）
UPDATE `issue` SET `source` = 'SYSTEM' WHERE `source` IS NULL OR `source` = '';

-- 2.6 存量回填：priority 为空 → 1（中）。NOT NULL DEFAULT 1 下通常无空行，此处纯兜底。
UPDATE `issue` SET `priority` = 1 WHERE `priority` IS NULL;

-- ===========================================================================
-- 第 3 节  user 表增量：头像 / 昵称 / 改密时间 / 第三方绑定
--          email、phone 沿用 Phase 1 既有列（不重复创建，仅做存在性兜底）。
--          本期不加 email/phone 唯一索引：存量可能有重复或空值，加索引会让脚本在生产库直接失败；
--          唯一性由 Service 层保证。
-- ===========================================================================
SET @c_avatar := (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='avatar');
SET @sql := IF(@c_avatar=0,
  'ALTER TABLE `user` ADD COLUMN `avatar` VARCHAR(255) DEFAULT NULL COMMENT ''头像相对路径，对应 file_record.relative_path''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c_nick := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='nickname');
SET @sql := IF(@c_nick=0,
  'ALTER TABLE `user` ADD COLUMN `nickname` VARCHAR(50) DEFAULT NULL COMMENT ''昵称，为空时展示 real_name''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c_pwdat := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='pwd_updated_at');
SET @sql := IF(@c_pwdat=0,
  'ALTER TABLE `user` ADD COLUMN `pwd_updated_at` DATETIME DEFAULT NULL COMMENT ''上次修改密码时间''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c_wx := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='bind_wechat');
SET @sql := IF(@c_wx=0,
  'ALTER TABLE `user` ADD COLUMN `bind_wechat` VARCHAR(64) DEFAULT NULL COMMENT ''微信绑定标识，NULL 表示未绑定''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c_dt := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='bind_dingtalk');
SET @sql := IF(@c_dt=0,
  'ALTER TABLE `user` ADD COLUMN `bind_dingtalk` VARCHAR(64) DEFAULT NULL COMMENT ''钉钉绑定标识，NULL 表示未绑定''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- email / phone 存在性兜底（Phase 1 已建，正常为 no-op）
SET @c_email := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='email');
SET @sql := IF(@c_email=0,
  'ALTER TABLE `user` ADD COLUMN `email` VARCHAR(100) DEFAULT NULL COMMENT ''邮箱''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c_phone := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='phone');
SET @sql := IF(@c_phone=0,
  'ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(20) DEFAULT NULL COMMENT ''手机号''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ===========================================================================
-- 第 4 节  login_log（登录日志）
--          无唯一索引；成功与失败均记录。
-- ===========================================================================
CREATE TABLE IF NOT EXISTS `login_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       DEFAULT NULL       COMMENT '用户 id，用户不存在时为 NULL',
  `username`    VARCHAR(50)  DEFAULT NULL       COMMENT '登录名，冗余存便于失败场景追溯',
  `ip`          VARCHAR(64)  DEFAULT NULL       COMMENT 'X-Forwarded-For 首段 → X-Real-IP → RemoteAddr',
  `user_agent`  VARCHAR(512) DEFAULT NULL       COMMENT '原始 UA（超长截断）',
  `browser`     VARCHAR(50)  DEFAULT NULL       COMMENT 'UA 解析结果：浏览器',
  `os`          VARCHAR(50)  DEFAULT NULL       COMMENT 'UA 解析结果：操作系统',
  `success`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  `fail_reason` VARCHAR(100) DEFAULT NULL       COMMENT '失败原因：密码错误/账号已禁用/用户不存在',
  `login_at`    DATETIME     NOT NULL           COMMENT '登录时间',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_login_log_user` (`user_id`, `login_at`),
  KEY `idx_login_log_time` (`login_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- ===========================================================================
-- 第 5 节  file_config（文件存储配置，单行）+ file_record（统一文件记录）
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 5.1 file_config：全局唯一一行配置。
--     采用独立表而非 sys_config 的 file.* 键，保证「文件配置」只有一个真源，
--     避免两处存储不一致（本期与 ARCH §3.7 的差异，见交付说明）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_config` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `storage_root`  VARCHAR(255) NOT NULL DEFAULT '/data/attachments' COMMENT '存储根目录，必须为绝对路径',
  `max_size_mb`   INT          NOT NULL DEFAULT 10  COMMENT '单文件大小上限(MB)，取值 1-100',
  `allowed_exts`  VARCHAR(500) NOT NULL DEFAULT 'jpg,jpeg,png,gif,pdf,zip,rar,doc,docx,xls,xlsx,txt,log'
                                                     COMMENT '允许的扩展名，逗号分隔小写',
  `storage_type`  VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式：LOCAL（预留 OSS）',
  `created_at`    DATETIME     DEFAULT NULL,
  `updated_at`    DATETIME     DEFAULT NULL,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存储配置（单行）';

-- 5.2 file_config 默认行（仅当表内无存活行时插入）
INSERT INTO `file_config` (`storage_root`,`max_size_mb`,`allowed_exts`,`storage_type`,`created_at`,`updated_at`)
SELECT '/data/attachments', 10,
       'jpg,jpeg,png,gif,pdf,zip,rar,doc,docx,xls,xlsx,txt,log', 'LOCAL', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `file_config` WHERE `deleted`=0 LIMIT 1) AS `_fc`);

-- ---------------------------------------------------------------------------
-- 5.3 file_record：统一文件视图（无唯一索引）
--     与 issue_attachment 并存：附件详情仍读 issue_attachment（零回归），
--     file_record 供「基础设施 > 文件列表」统一查看。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `file_name`     VARCHAR(128) NOT NULL          COMMENT '存储名 uuid.ext',
  `original_name` VARCHAR(255) DEFAULT NULL      COMMENT '原始文件名',
  `relative_path` VARCHAR(255) DEFAULT NULL      COMMENT '相对存储根的路径 yyyyMM/uuid.ext',
  `file_path`     VARCHAR(512) DEFAULT NULL      COMMENT '绝对路径（兼容存量 issue_attachment 回灌）',
  `file_size`     BIGINT       DEFAULT NULL      COMMENT '字节数',
  `content_type`  VARCHAR(100) DEFAULT NULL      COMMENT 'MIME 类型',
  `ext`           VARCHAR(20)  DEFAULT NULL      COMMENT '小写扩展名，供筛选',
  `biz_type`      VARCHAR(30)  NOT NULL DEFAULT 'MANUAL' COMMENT 'ISSUE / AVATAR / MANUAL',
  `biz_id`        BIGINT       DEFAULT NULL      COMMENT '关联业务 id',
  `uploader_id`   BIGINT       DEFAULT NULL      COMMENT '上传人 user.id',
  `storage_type`  VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL（预留 OSS）',
  `created_at`    DATETIME     DEFAULT NULL,
  `updated_at`    DATETIME     DEFAULT NULL,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_file_biz` (`biz_type`, `biz_id`),
  KEY `idx_file_ext` (`ext`),
  KEY `idx_file_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一文件记录';

-- 5.4 存量 issue_attachment 一次性回灌为 file_record（按 file_name 去重，幂等）
INSERT INTO `file_record`
  (`file_name`,`original_name`,`relative_path`,`file_path`,`file_size`,`content_type`,
   `ext`,`biz_type`,`biz_id`,`uploader_id`,`storage_type`,`created_at`,`updated_at`,`deleted`)
SELECT a.`file_name`,
       a.`original_name`,
       NULL,
       a.`file_path`,
       a.`file_size`,
       a.`content_type`,
       LOWER(SUBSTRING_INDEX(a.`file_name`, '.', -1)),
       'ISSUE',
       a.`issue_id`,
       a.`uploader_id`,
       'LOCAL',
       a.`created_at`,
       a.`created_at`,
       0
FROM `issue_attachment` a
WHERE a.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `file_record` fr WHERE fr.`file_name` = a.`file_name`);

-- ===========================================================================
-- 第 6 节  scheduled_task / scheduled_task_log（定时任务）
--          next_exec_time 不落库，由 CronUtils 实时计算，避免与调度器状态不一致。
-- ===========================================================================
CREATE TABLE IF NOT EXISTS `scheduled_task` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `task_name`        VARCHAR(100) NOT NULL             COMMENT '任务名称',
  `task_group`       VARCHAR(50)  NOT NULL DEFAULT 'default' COMMENT '任务分组',
  `job_key`          VARCHAR(100) NOT NULL             COMMENT '执行目标，必须命中后端 jobRegistry 白名单',
  `cron`             VARCHAR(100) NOT NULL             COMMENT 'Spring cron 表达式（6 位）',
  `params`           VARCHAR(500) DEFAULT NULL         COMMENT 'JSON 字符串参数',
  `status`           TINYINT      NOT NULL DEFAULT 1   COMMENT '1运行 0暂停',
  `description`      VARCHAR(200) DEFAULT NULL         COMMENT '描述',
  `last_exec_time`   DATETIME     DEFAULT NULL         COMMENT '上次执行时间',
  `last_exec_result` TINYINT      DEFAULT NULL         COMMENT '上次执行结果 1成功 0失败',
  `last_cost_ms`     BIGINT       DEFAULT NULL         COMMENT '上次执行耗时(ms)',
  `created_at`       DATETIME     DEFAULT NULL,
  `updated_at`       DATETIME     DEFAULT NULL,
  `deleted`          TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务';

CREATE TABLE IF NOT EXISTS `scheduled_task_log` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `task_id`      BIGINT        NOT NULL           COMMENT '所属任务 scheduled_task.id',
  `start_time`   DATETIME      NOT NULL           COMMENT '开始时间',
  `cost_ms`      BIGINT        DEFAULT NULL       COMMENT '耗时(ms)',
  `success`      TINYINT       NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  `message`      VARCHAR(2000) DEFAULT NULL       COMMENT '执行结果/异常摘要',
  `trigger_type` VARCHAR(20)   NOT NULL DEFAULT 'CRON' COMMENT 'CRON / MANUAL',
  `created_at`   DATETIME      DEFAULT NULL,
  `updated_at`   DATETIME      DEFAULT NULL,
  `deleted`      TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task_log` (`task_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行日志';

-- 6.1 内置任务种子 2 条（按 job_key 幂等；允许被管理员删除，重跑本脚本可恢复）
INSERT INTO `scheduled_task`
  (`task_name`,`task_group`,`job_key`,`cron`,`params`,`status`,`description`,`created_at`,`updated_at`)
SELECT '清理过期临时文件','default','CLEAN_EXPIRED_FILE','0 0 3 * * ?',NULL,1,
       '每日 03:00 清理逻辑删除超过 30 天的文件记录及物理文件',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `scheduled_task` WHERE `job_key`='CLEAN_EXPIRED_FILE' AND `deleted`=0);

INSERT INTO `scheduled_task`
  (`task_name`,`task_group`,`job_key`,`cron`,`params`,`status`,`description`,`created_at`,`updated_at`)
SELECT '清理过期登录日志','default','CLEAN_LOGIN_LOG','0 30 3 * * ?',NULL,1,
       '每日 03:30 清理 90 天前的登录日志',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `scheduled_task` WHERE `job_key`='CLEAN_LOGIN_LOG' AND `deleted`=0);

-- ===========================================================================
-- 第 7 节  后台菜单重构（type=2）
--
-- 目标结构：
--   概览(1)
--   业务管理(2)  ← 由原「问题管理」叶子菜单【原地升级】，保留同一行 menu.id
--     ├ 问题列表(1)  /admin/issues   【新增子行，旧路由原样可达】
--     ├ 项目配置(2)  /admin/projects 【迁入】
--     ├ 模块配置(3)  /admin/modules  【迁入】
--     └ 字典配置(4)  /admin/dicts    【新增】
--   问题类型(3)  /admin/issue-types  【决策 A：保持一级平铺，不动 parent_id】
--   流程管理(5)  ├ 流程配置 / 流程监控（Phase4 已归组，本期不动）
--   基础设施(6)  【新增三层】
--     ├ 文件管理(1) /admin/infra/file
--     │   ├ 文件配置(1) /admin/infra/file/config
--     │   └ 文件列表(2) /admin/infra/file/list
--     ├ 配置管理(2) /admin/infra/config
--     ├ Redis监控(3) /admin/infra/redis
--     └ 定时任务(4) /admin/infra/job
--   系统管理(7)
--
-- 幂等要点：第 7.1 条用 `parent_id=0` 作为哨兵。首次执行时顶级 /admin/issues 被改名为
-- /admin/business；第 7.2 条插入的子行 /admin/issues 其 parent_id ≠ 0，
-- 故二次执行时 7.1 不会误把子菜单再改名成「业务管理」。
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 7.1 「问题管理」原地升级为「业务管理」分组（保留 menu.id，避免 404 与授权丢失）
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `name`='业务管理',
    `path`='/admin/business',
    `permission`='business:view',
    `icon`='Management',
    `sort`=2,
    `updated_at`=NOW()
WHERE `path`='/admin/issues' AND `type`=2 AND `deleted`=0 AND `parent_id`=0;

SET @business_id := (SELECT `id` FROM `menu` WHERE `path`='/admin/business' AND `type`=2 AND `deleted`=0 LIMIT 1);

-- ---------------------------------------------------------------------------
-- 7.2 业务管理 > 问题列表（新增子行，沿用旧路由 /admin/issues → 旧书签不 404）
-- ---------------------------------------------------------------------------
INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '问题列表', '/admin/issues', @business_id, 1, 'issue:list', 'Tickets', 2, NOW(), NOW()
WHERE @business_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/issues' AND `type`=2 AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 7.3 「项目配置」「模块配置」迁入业务管理（仅在父级或排序未就位时更新）
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `parent_id`=@business_id, `sort`=2, `updated_at`=NOW()
WHERE `path`='/admin/projects' AND `type`=2 AND `deleted`=0
  AND @business_id IS NOT NULL
  AND (`parent_id` <> @business_id OR `sort` <> 2);

UPDATE `menu`
SET `parent_id`=@business_id, `sort`=3, `updated_at`=NOW()
WHERE `path`='/admin/modules' AND `type`=2 AND `deleted`=0
  AND @business_id IS NOT NULL
  AND (`parent_id` <> @business_id OR `sort` <> 3);

-- ---------------------------------------------------------------------------
-- 7.4 业务管理 > 字典配置（新增）
-- ---------------------------------------------------------------------------
INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '字典配置', '/admin/dicts', @business_id, 4, 'dict:list', 'Notebook', 2, NOW(), NOW()
WHERE @business_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/dicts' AND `type`=2 AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- 7.5 原「项目管理」一级分组迁空后逻辑删除（消除侧栏空分组 / 死菜单）
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `deleted`=1, `updated_at`=NOW()
WHERE `path`='/admin/project' AND `type`=2 AND `deleted`=0;

-- ---------------------------------------------------------------------------
-- 7.6 基础设施一级菜单
-- ---------------------------------------------------------------------------
INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '基础设施', '/admin/infra', 0, 6, 'infra:view', 'Tools', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/infra' AND `type`=2 AND `deleted`=0);

SET @infra_id := (SELECT `id` FROM `menu` WHERE `path`='/admin/infra' AND `type`=2 AND `deleted`=0 LIMIT 1);

-- 7.7 基础设施 > 文件管理（二级目录节点，其下再挂两个三级页面）
INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '文件管理', '/admin/infra/file', @infra_id, 1, 'file:list', 'Folder', 2, NOW(), NOW()
WHERE @infra_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/infra/file' AND `type`=2 AND `deleted`=0);

SET @infra_file_id := (SELECT `id` FROM `menu` WHERE `path`='/admin/infra/file' AND `type`=2 AND `deleted`=0 LIMIT 1);

-- 7.8 文件管理 > 文件配置 / 文件列表（三级）
INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '文件配置', '/admin/infra/file/config', @infra_file_id, 1, 'file:config', 'Setting', 2, NOW(), NOW()
WHERE @infra_file_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/infra/file/config' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '文件列表', '/admin/infra/file/list', @infra_file_id, 2, 'file:list', 'Document', 2, NOW(), NOW()
WHERE @infra_file_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/infra/file/list' AND `type`=2 AND `deleted`=0);

-- 7.9 基础设施 > 配置管理 / Redis 监控 / 定时任务（二级）
INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '配置管理', '/admin/infra/config', @infra_id, 2, 'config:list', 'Operation', 2, NOW(), NOW()
WHERE @infra_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/infra/config' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT 'Redis监控', '/admin/infra/redis', @infra_id, 3, 'redis:monitor', 'Odometer', 2, NOW(), NOW()
WHERE @infra_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/infra/redis' AND `type`=2 AND `deleted`=0);

INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`)
SELECT '定时任务', '/admin/infra/job', @infra_id, 4, 'job:list', 'Timer', 2, NOW(), NOW()
WHERE @infra_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `menu` WHERE `path`='/admin/infra/job' AND `type`=2 AND `deleted`=0);

-- 7.9.1 icon 自愈：Timer 已核验存在于 @element-plus/icons-vue（dist/index.js: name:"Timer"），
--       但【不在】Phase 6 脚本第 12 段的 icon 白名单里 —— 若运维在本脚本之后重跑 V20260803，
--       兜底语句会把 Timer 刷成 Grid。此处做自愈式纠正（幂等，命中才更新）。
--       根治办法：把 'Timer' 追加进 V20260803 第 12 段白名单（属另一文件，不在本次交付范围）。
UPDATE `menu` SET `icon`='Timer', `updated_at`=NOW()
WHERE `path`='/admin/infra/job' AND `type`=2 AND `deleted`=0 AND `icon`<>'Timer';

-- ---------------------------------------------------------------------------
-- 7.10 后台一级菜单排序重排（仅在未就位时更新）
--      概览1 / 业务管理2 / 问题类型3 /（4 位预留给流程监控）/ 流程管理5 / 基础设施6 / 系统管理7
--      注：流程监控自 Phase 4 起已挂在「流程管理」下，本期不改其归属，故一级序号 4 空缺。
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `sort`=1, `updated_at`=NOW()
WHERE `path`='/admin/index' AND `type`=2 AND `deleted`=0 AND `sort`<>1;

UPDATE `menu` SET `sort`=3, `updated_at`=NOW()
WHERE `path`='/admin/issue-types' AND `type`=2 AND `deleted`=0 AND `sort`<>3;

UPDATE `menu` SET `sort`=5, `updated_at`=NOW()
WHERE `path`='/admin/flow' AND `type`=2 AND `deleted`=0 AND `sort`<>5;

UPDATE `menu` SET `sort`=7, `updated_at`=NOW()
WHERE `path`='/admin/system' AND `type`=2 AND `deleted`=0 AND `sort`<>7;

-- ---------------------------------------------------------------------------
-- 7.11 「个人设置」→「用户设置」
--      当前该入口为后台顶栏头像下拉项，由前端 i18n（layout.topbar.profile）渲染，
--      menu 表中通常无对应行。此处做防御性改名，库中若存在同名菜单一并纠正；
--      前端文案改动在 T4 完成。
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `name`='用户设置', `updated_at`=NOW()
WHERE `name`='个人设置' AND `deleted`=0;

-- ===========================================================================
-- 第 8 节  权限种子：21 个新权限码 + 授 ADMIN
--          role_permission 表无 updated_at 列，种子不得携带该列。
-- ===========================================================================
INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'dict:list','字典查看','dict','list',2,201,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='dict:list');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'dict:create','字典新增','dict','create',2,202,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='dict:create');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'dict:update','字典编辑','dict','update',2,203,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='dict:update');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'dict:delete','字典删除','dict','delete',2,204,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='dict:delete');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'file:list','文件查看','file','list',2,211,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='file:list');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'file:upload','文件上传','file','upload',2,212,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='file:upload');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'file:delete','文件删除','file','delete',2,213,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='file:delete');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'file:config','文件配置维护','file','config',2,214,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='file:config');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'config:list','配置查看','config','list',2,221,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='config:list');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'config:create','配置新增','config','create',2,222,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='config:create');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'config:update','配置编辑','config','update',2,223,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='config:update');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'config:delete','配置删除','config','delete',2,224,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='config:delete');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'redis:monitor','Redis监控','redis','monitor',2,231,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='redis:monitor');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'job:list','定时任务查看','job','list',2,241,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='job:list');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'job:create','定时任务新增','job','create',2,242,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='job:create');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'job:update','定时任务编辑','job','update',2,243,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='job:update');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'job:delete','定时任务删除','job','delete',2,244,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='job:delete');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'job:run','定时任务立即执行','job','run',2,245,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='job:run');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'system:backup:export','数据备份导出','system','export',2,251,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='system:backup:export');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'infra:view','基础设施访问','infra','view',2,261,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='infra:view');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'business:view','业务管理访问','business','view',2,262,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='business:view');

-- 注：个人中心（/api/profile/**）按 ARCH §3.8 约定「登录即可」，不设权限码；
--     越权由「接口不收 userId 入参」结构性杜绝，故此处不再种 profile:view。

-- 8.1 全部新权限码授予 ADMIN
SET @admin_role := (SELECT `id` FROM `role` WHERE `code`='ADMIN');

INSERT INTO `role_permission` (`role_id`,`permission_id`,`created_at`)
SELECT @admin_role, p.`id`, NOW()
FROM `permission` p
WHERE @admin_role IS NOT NULL
  AND p.`code` IN (
    'dict:list','dict:create','dict:update','dict:delete',
    'file:list','file:upload','file:delete','file:config',
    'config:list','config:create','config:update','config:delete',
    'redis:monitor',
    'job:list','job:create','job:update','job:delete','job:run',
    'system:backup:export',
    'infra:view','business:view'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id`=@admin_role AND rp.`permission_id`=p.`id`
  );

-- ===========================================================================
-- 第 9 节  自检查询（执行后人工比对，不影响幂等）
-- ===========================================================================
-- SELECT COUNT(*) AS null_source FROM `issue` WHERE `source` IS NULL;                 -- 期望 0
-- SELECT COUNT(*) AS dict_cnt FROM `dict` WHERE `deleted`=0;                          -- 期望 4
-- SELECT COUNT(*) AS dict_item_cnt FROM `dict_item` WHERE `deleted`=0;                -- 期望 17
-- SELECT `name`,`path`,`parent_id`,`sort` FROM `menu` WHERE `type`=2 AND `deleted`=0 ORDER BY `parent_id`,`sort`;
-- SELECT COUNT(*) AS new_perm FROM `permission` WHERE `code` IN (
--   'dict:list','dict:create','dict:update','dict:delete',
--   'file:list','file:upload','file:delete','file:config',
--   'config:list','config:create','config:update','config:delete','redis:monitor',
--   'job:list','job:create','job:update','job:delete','job:run',
--   'system:backup:export','infra:view','business:view');                            -- 期望 21
-- 生成列条件唯一验证（铁律，必须人工跑一遍）：
--   UPDATE `dict_item` SET `deleted`=1 WHERE `dict_code`='ISSUE_SOURCE' AND `item_code`='TMP_T';
--   再以同 dict_code+item_code 插入一条 → 应插入成功且不报 1062。
