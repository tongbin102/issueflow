-- ============================================================
-- issueFlow Phase9 动态字段配置 增量脚本（T01）
-- 字符集 utf8mb4 / 存储引擎 InnoDB
-- 约定：逻辑删除 deleted / 条件唯一走 code_active 生成列
-- 执行入口：bash scripts/migrate.sh <this-file.sql>
-- 幂等：建表 IF NOT EXISTS；加列 information_schema 探测；种子 NOT EXISTS
-- ============================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- ① 建 field_section（字段区域 / 表单分区）
--    type_scope 本期恒为 'GLOBAL'（Q1 零成本预埋，P2-F16 放开写入即可）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `field_section` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(64)  NOT NULL            COMMENT '区域编码（大写下划线），程序依赖，创建后不可改',
  `name`        VARCHAR(50)  NOT NULL            COMMENT '区域名称（页签标题，i18n 缺失时的兜底文案）',
  `i18n_key`    VARCHAR(100) DEFAULT NULL        COMMENT 'i18n key，如 field.section.BASIC；为空则回退 name',
  `type_scope`  VARCHAR(64)  NOT NULL DEFAULT 'GLOBAL'
                COMMENT '生效范围：本期恒为 GLOBAL；P2-F16 存 issue 类型 code',
  `sort`        INT          NOT NULL DEFAULT 0  COMMENT '升序展示（页签左右顺序）',
  `enabled`     TINYINT      NOT NULL DEFAULT 1  COMMENT '1启用 0停用（停用则整个页签不渲染）',
  `is_system`   TINYINT      NOT NULL DEFAULT 0  COMMENT '1=系统预设区域，删除接口硬拦截，仅可改名/排序',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  `code_active` VARCHAR(64) GENERATED ALWAYS AS (IF(`deleted` = 0, `code`, NULL)) VIRTUAL
                COMMENT '条件唯一辅助列，仅供 uk_field_section_code 使用，Java 实体不映射',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_section_code` (`code_active`),
  KEY `idx_field_section_scope_sort` (`type_scope`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段区域（表单分区/页签）';

-- ---------------------------------------------------------------------------
-- ② 建 field_config（字段配置，本期核心表）
--    A3：只建全局 uk_field_config_code，不建 section_code_active
--    A4：列名 is_system，对外 JSON 仍输出 system（@JsonProperty("system")）
--    Q2：is_system=1 的字段其 code 与 Issue 实体属性名严格同名
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `field_config` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `section_id`     BIGINT       NOT NULL            COMMENT '所属区域 field_section.id（无外键）',
  `code`           VARCHAR(64)  NOT NULL            COMMENT '字段编码（小驼峰），全局唯一，创建后不可改；is_system=1 时须与 Issue 实体属性同名',
  `name`           VARCHAR(50)  NOT NULL            COMMENT '字段标签（i18n 缺失时兜底）',
  `i18n_key`       VARCHAR(100) DEFAULT NULL        COMMENT 'i18n key，如 field.label.title',
  `type`           VARCHAR(20)  NOT NULL            COMMENT '字段类型：TEXT/NUMBER/DATE/DATETIME/DICT/REF，创建后不可改（Q4）',
  `required`       TINYINT      NOT NULL DEFAULT 0  COMMENT '1必填 0选填',
  `placeholder`    VARCHAR(200) DEFAULT NULL        COMMENT '占位提示',
  `default_value`  VARCHAR(500) DEFAULT NULL        COMMENT '默认值（字符串形态，按 type 解析）',
  `span`           TINYINT      NOT NULL DEFAULT 12 COMMENT '栅格宽度 1~24（el-col），常用 12=半行 24=整行',
  `multiline`      TINYINT      NOT NULL DEFAULT 0  COMMENT 'TEXT 专用：1=textarea 0=input',
  `max_length`     INT          DEFAULT NULL        COMMENT 'TEXT 专用：最大字符数',
  `min_val`        DECIMAL(20,6) DEFAULT NULL       COMMENT 'NUMBER 专用：最小值',
  `max_val`        DECIMAL(20,6) DEFAULT NULL       COMMENT 'NUMBER 专用：最大值',
  `decimal_scale`  TINYINT      DEFAULT NULL        COMMENT 'NUMBER 专用：小数位数，NULL=整数',
  `dict_code`      VARCHAR(50)  DEFAULT NULL        COMMENT 'DICT 专用：dict.dict_code，候选走 /api/dicts/options',
  `ref_source`     VARCHAR(50)  DEFAULT NULL        COMMENT 'REF 专用：ref_source_registry.code 白名单编码（Q7，永不存表名）',
  `display_type`   VARCHAR(10)  DEFAULT NULL        COMMENT 'REF 专用：select 平铺 / tree 树形；为空按 registry.query_type 兜底',
  `multi_select`   TINYINT      NOT NULL DEFAULT 0  COMMENT '1多选 0单选（DICT/REF 有效；多选值逗号拼接存 value_text）',
  `depends_on`     VARCHAR(64)  DEFAULT NULL        COMMENT '依赖的上游字段 code（本期单级，Q6）',
  `depends_param`  VARCHAR(64)  DEFAULT NULL        COMMENT '传给 ref-options 的过滤参数名；为空则取 registry.filter_field',
  `is_system`      TINYINT      NOT NULL DEFAULT 0  COMMENT '1=内置字段（F12）：仅可改 name/i18n_key/required/sort/placeholder/span，code/type/删除均硬拦截',
  `visible_in_list` TINYINT     NOT NULL DEFAULT 0  COMMENT 'F14 元数据：是否可作为列表列（本期只落库，列表页消费留下期）',
  `searchable`     TINYINT      NOT NULL DEFAULT 0  COMMENT 'F14 元数据：是否可作为查询条件（本期只落库）',
  `perm_code`      VARCHAR(100) DEFAULT NULL        COMMENT 'P2-F19 预留：字段级权限标识，本期恒 NULL 且不参与鉴权',
  `type_scope`     VARCHAR(64)  NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：本期恒 GLOBAL（Q1）',
  `sort`           INT          NOT NULL DEFAULT 0  COMMENT '区域内升序展示',
  `enabled`        TINYINT      NOT NULL DEFAULT 1  COMMENT '1启用 0停用（停用：表单不可选，详情只读+灰 tag，Q5）',
  `created_at`     DATETIME     DEFAULT NULL,
  `updated_at`     DATETIME     DEFAULT NULL,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  `code_active`    VARCHAR(64) GENERATED ALWAYS AS (IF(`deleted` = 0, `code`, NULL)) VIRTUAL
                   COMMENT '条件唯一辅助列，仅供 uk_field_config_code 使用，Java 实体不映射',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_config_code` (`code_active`),
  KEY `idx_field_config_section_sort` (`section_id`, `sort`),
  KEY `idx_field_config_scope` (`type_scope`, `enabled`),
  KEY `idx_field_config_depends` (`depends_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段配置（动态表单元数据）';

-- ---------------------------------------------------------------------------
-- ③ 建 issue_field_value（自定义字段值，竖表，仅存 is_system=0 的自定义字段）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `issue_field_value` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `issue_id`    BIGINT        NOT NULL            COMMENT '所属问题 issue.id（无外键）',
  `field_code`  VARCHAR(64)   NOT NULL            COMMENT '字段编码 field_config.code（冗余存 code 而非 id，避免回显 JOIN）',
  `value_text`  TEXT          DEFAULT NULL        COMMENT 'TEXT/DICT/REF 值；多选为逗号拼接',
  `value_num`   DECIMAL(20,6) DEFAULT NULL        COMMENT 'NUMBER 值',
  `value_date`  DATETIME      DEFAULT NULL        COMMENT 'DATE/DATETIME 值（DATE 取 00:00:00）',
  `created_at`  DATETIME      DEFAULT NULL,
  `updated_at`  DATETIME      DEFAULT NULL,
  `deleted`     TINYINT       NOT NULL DEFAULT 0  COMMENT '字段被删除时值软删保留（Q5），不物理清理',
  `pair_active` VARCHAR(96) GENERATED ALWAYS AS
                (IF(`deleted` = 0, CONCAT(`issue_id`, '_', `field_code`), NULL)) VIRTUAL
                COMMENT '条件唯一辅助列，仅供 uk_ifv_pair 使用，Java 实体不映射',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifv_pair` (`pair_active`),
  KEY `idx_ifv_issue` (`issue_id`, `deleted`),
  KEY `idx_ifv_code_text` (`field_code`, `value_text`(64)),
  KEY `idx_ifv_code_num`  (`field_code`, `value_num`),
  KEY `idx_ifv_code_date` (`field_code`, `value_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题自定义字段值（竖表）';

-- ---------------------------------------------------------------------------
-- ④ 建 ref_source_registry（REF 字段引用源白名单，Q7 + A1）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ref_source_registry` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(50)  NOT NULL            COMMENT '引用源编码（大写），前端只传此值，永不传表名',
  `name`        VARCHAR(50)  NOT NULL            COMMENT '引用源名称（配置页下拉展示）',
  `table_name`  VARCHAR(64)  NOT NULL            COMMENT '目标表名，须过正则 + information_schema 校验',
  `label_field` VARCHAR(64)  NOT NULL            COMMENT '展示列（下拉 label）',
  `value_field` VARCHAR(64)  NOT NULL DEFAULT 'id' COMMENT '取值列（下拉 value）',
  `query_type`  VARCHAR(10)  NOT NULL DEFAULT 'flat' COMMENT 'flat 平铺列表 / tree 树形',
  `parent_field` VARCHAR(64) DEFAULT NULL        COMMENT '树形自关联父列，query_type=tree 时必填',
  `filter_field` VARCHAR(64) DEFAULT NULL        COMMENT '依赖过滤列：被 depends_on 触发时用于 WHERE 的列',
  `order_field` VARCHAR(64)  DEFAULT NULL        COMMENT '排序列，为空时按 value_field 升序',
  `enabled`     TINYINT      NOT NULL DEFAULT 1  COMMENT '1启用 0停用（停用后不出现在配置页下拉）',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  `code_active` VARCHAR(50) GENERATED ALWAYS AS (IF(`deleted` = 0, `code`, NULL)) VIRTUAL
                COMMENT '条件唯一辅助列，仅供 uk_ref_source_code 使用，Java 实体不映射',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ref_source_code` (`code_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='REF 字段引用源白名单';

-- ---------------------------------------------------------------------------
-- ② 种子：field_section 3 条（全部 is_system=1）
-- ---------------------------------------------------------------------------
INSERT INTO `field_section`
  (`code`,`name`,`i18n_key`,`type_scope`,`sort`,`enabled`,`is_system`,`created_at`,`updated_at`)
SELECT 'BASIC','基本信息','field.section.BASIC','GLOBAL',1,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_section` WHERE `code`='BASIC' AND `deleted`=0);

INSERT INTO `field_section`
  (`code`,`name`,`i18n_key`,`type_scope`,`sort`,`enabled`,`is_system`,`created_at`,`updated_at`)
SELECT 'DETAIL','详细描述','field.section.DETAIL','GLOBAL',2,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_section` WHERE `code`='DETAIL' AND `deleted`=0);

INSERT INTO `field_section`
  (`code`,`name`,`i18n_key`,`type_scope`,`sort`,`enabled`,`is_system`,`created_at`,`updated_at`)
SELECT 'ENV','环境信息','field.section.ENV','GLOBAL',3,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_section` WHERE `code`='ENV' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- ④ 种子：ref_source_registry 4 条
-- ---------------------------------------------------------------------------
INSERT INTO `ref_source_registry`
 (`code`,`name`,`table_name`,`label_field`,`value_field`,`query_type`,`parent_field`,`filter_field`,`order_field`,`enabled`,`created_at`,`updated_at`)
SELECT 'PROJECT','项目','project','name','id','flat',NULL,NULL,'sort',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `ref_source_registry` WHERE `code`='PROJECT' AND `deleted`=0);

INSERT INTO `ref_source_registry`
 (`code`,`name`,`table_name`,`label_field`,`value_field`,`query_type`,`parent_field`,`filter_field`,`order_field`,`enabled`,`created_at`,`updated_at`)
SELECT 'MODULE','模块','module','name','id','tree','parent_id','project_id','sort',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `ref_source_registry` WHERE `code`='MODULE' AND `deleted`=0);

INSERT INTO `ref_source_registry`
 (`code`,`name`,`table_name`,`label_field`,`value_field`,`query_type`,`parent_field`,`filter_field`,`order_field`,`enabled`,`created_at`,`updated_at`)
SELECT 'USER','用户','user','username','id','flat',NULL,NULL,'id',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `ref_source_registry` WHERE `code`='USER' AND `deleted`=0);

INSERT INTO `ref_source_registry`
 (`code`,`name`,`table_name`,`label_field`,`value_field`,`query_type`,`parent_field`,`filter_field`,`order_field`,`enabled`,`created_at`,`updated_at`)
SELECT 'ISSUE','问题','issue','title','id','flat',NULL,NULL,'id',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `ref_source_registry` WHERE `code`='ISSUE' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- ⑤ dict 新增 ISSUE_TYPE 系统字典（is_system=1，保护整类不被删除，A5）
-- ---------------------------------------------------------------------------
INSERT INTO `dict` (`dict_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`created_at`,`updated_at`)
SELECT 'ISSUE_TYPE','问题类型','问题的分类维度，原 issue_type 表迁入（Phase9）',5,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `dict` WHERE `dict_code`='ISSUE_TYPE' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- ⑤ issue_type → dict_item 数据迁移（仅迁移存活行；按 code 去重幂等）
--    extra 列存旧 issue_type.id，供灰度期按旧 type_id 回查比对
-- ---------------------------------------------------------------------------
INSERT INTO `dict_item`
  (`dict_code`,`item_code`,`name`,`description`,`sort`,`enabled`,`is_system`,`extra`,`created_at`,`updated_at`,`deleted`)
SELECT 'ISSUE_TYPE', t.`code`, t.`name`, t.`description`, t.`sort`, t.`enabled`, 0,
       CAST(t.`id` AS CHAR), NOW(), NOW(), 0
FROM `issue_type` t
WHERE t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `dict_item` d
    WHERE d.`dict_code` = 'ISSUE_TYPE' AND d.`item_code` = t.`code` AND d.`deleted` = 0
  );

-- ---------------------------------------------------------------------------
-- ⑥ issue 新增 type_code 列 + 回填 + 索引（引用口径由 id 改 code）
-- ---------------------------------------------------------------------------
SET @col_exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issue' AND COLUMN_NAME = 'type_code'
);
SET @ddl := IF(@col_exist = 0,
  'ALTER TABLE `issue` ADD COLUMN `type_code` VARCHAR(64) DEFAULT NULL COMMENT ''问题类型编码，引用 dict_item(ISSUE_TYPE).item_code（Phase9 起以此为准，type_id 仅灰度回查）'' AFTER `type_id`',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 从 issue_type 回填（墓碑表数据仍在，含已软删类型也一并回填，保证历史问题可读）
UPDATE `issue` i
JOIN `issue_type` t ON t.`id` = i.`type_id`
SET i.`type_code` = t.`code`
WHERE i.`type_code` IS NULL AND i.`type_id` IS NOT NULL;

SET @idx_exist := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issue' AND INDEX_NAME = 'idx_issue_type_code'
);
SET @ddl2 := IF(@idx_exist = 0,
  'ALTER TABLE `issue` ADD KEY `idx_issue_type_code` (`type_code`)', 'SELECT 1');
PREPARE s2 FROM @ddl2; EXECUTE s2; DEALLOCATE PREPARE s2;

-- ---------------------------------------------------------------------------
-- ⑤ field_config 内置字段种子（14 条，is_system=1，Q2 元数据层）
--    code 与 Issue 实体属性名严格同名，值仍读写 issue 主表原列
-- ---------------------------------------------------------------------------
SET @sec_basic  := (SELECT `id` FROM `field_section` WHERE `code`='BASIC'  AND `deleted`=0 LIMIT 1);
SET @sec_detail := (SELECT `id` FROM `field_section` WHERE `code`='DETAIL' AND `deleted`=0 LIMIT 1);
SET @sec_env    := (SELECT `id` FROM `field_section` WHERE `code`='ENV'    AND `deleted`=0 LIMIT 1);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`max_length`,`is_system`,`type_scope`,`sort`,`enabled`,`visible_in_list`,`searchable`,`created_at`,`updated_at`)
SELECT @sec_basic,'title','标题','field.label.title','TEXT',1,24,200,1,'GLOBAL',1,1,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='title' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`dict_code`,`is_system`,`type_scope`,`sort`,`enabled`,`visible_in_list`,`searchable`,`created_at`,`updated_at`)
SELECT @sec_basic,'typeCode','问题类型','field.label.typeCode','DICT',1,12,'ISSUE_TYPE',1,'GLOBAL',2,1,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='typeCode' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`dict_code`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_basic,'source','来源','field.label.source','DICT',0,12,'ISSUE_SOURCE',1,'GLOBAL',3,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='source' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`dict_code`,`is_system`,`type_scope`,`sort`,`enabled`,`visible_in_list`,`searchable`,`created_at`,`updated_at`)
SELECT @sec_basic,'severity','严重等级','field.label.severity','DICT',1,12,'ISSUE_SEVERITY',1,'GLOBAL',4,1,1,0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='severity' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`dict_code`,`is_system`,`type_scope`,`sort`,`enabled`,`visible_in_list`,`searchable`,`created_at`,`updated_at`)
SELECT @sec_basic,'priority','优先级','field.label.priority','DICT',1,12,'ISSUE_PRIORITY',1,'GLOBAL',5,1,1,0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='priority' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`ref_source`,`display_type`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_basic,'projectId','所属项目','field.label.projectId','REF',1,12,'PROJECT','select',1,'GLOBAL',6,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='projectId' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`ref_source`,`display_type`,`depends_on`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_basic,'moduleId','所属模块','field.label.moduleId','REF',0,12,'MODULE','tree','projectId',1,'GLOBAL',7,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='moduleId' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`max_length`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_basic,'tags','标签','field.label.tags','TEXT',0,24,255,1,'GLOBAL',8,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='tags' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`multiline`,`max_length`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_detail,'description','详细描述','field.label.description','TEXT',1,24,1,5000,1,'GLOBAL',1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='description' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`multiline`,`max_length`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_detail,'reproduceSteps','复现步骤','field.label.reproduceSteps','TEXT',0,24,1,5000,1,'GLOBAL',2,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='reproduceSteps' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`max_length`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_env,'envOs','操作系统','field.label.envOs','TEXT',0,12,100,1,'GLOBAL',1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='envOs' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`max_length`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_env,'envBrowser','浏览器','field.label.envBrowser','TEXT',0,12,100,1,'GLOBAL',2,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='envBrowser' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`max_length`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_env,'envAppVersion','应用版本','field.label.envAppVersion','TEXT',0,12,50,1,'GLOBAL',3,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='envAppVersion' AND `deleted`=0);

INSERT INTO `field_config`
 (`section_id`,`code`,`name`,`i18n_key`,`type`,`required`,`span`,`max_length`,`is_system`,`type_scope`,`sort`,`enabled`,`created_at`,`updated_at`)
SELECT @sec_env,'envDevice','设备型号','field.label.envDevice','TEXT',0,12,100,1,'GLOBAL',4,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `field_config` WHERE `code`='envDevice' AND `deleted`=0);

-- ---------------------------------------------------------------------------
-- ⑧ menu 变更：软删 /admin/issue-types；新增 /admin/field-configs 挂 business 下
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `deleted` = 1, `updated_at` = NOW()
WHERE `path` = '/admin/issue-types' AND `type` = 2 AND `deleted` = 0;

SET @business_id := (
  SELECT `id` FROM `menu` WHERE `path` = '/admin/business' AND `type` = 2 AND `deleted` = 0 LIMIT 1
);

INSERT INTO `menu` (`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`created_at`,`updated_at`,`deleted`)
SELECT '字段配置','/admin/field-configs',@business_id,2,'field:config:list','SetUp',2,NOW(),NOW(),0
WHERE @business_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `menu` WHERE `path`='/admin/field-configs' AND `type`=2 AND `deleted`=0
  );

UPDATE `menu` SET `sort`=3, `updated_at`=NOW()
 WHERE `parent_id`=@business_id AND `path`='/admin/projects' AND `deleted`=0;
UPDATE `menu` SET `sort`=4, `updated_at`=NOW()
 WHERE `parent_id`=@business_id AND `path`='/admin/modules'  AND `deleted`=0;
UPDATE `menu` SET `sort`=5, `updated_at`=NOW()
 WHERE `parent_id`=@business_id AND `path`='/admin/dicts'    AND `deleted`=0;

-- ---------------------------------------------------------------------------
-- 回执：迁移校验（非 0 需人工介入）
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS `unfilled_type_code`
FROM `issue` WHERE `type_id` IS NOT NULL AND `type_code` IS NULL AND `deleted` = 0;
