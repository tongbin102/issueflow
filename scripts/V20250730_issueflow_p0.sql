-- ============================================================
-- issueFlow P0 增量 DDL
-- 内容：项目管理(project) / 系统管理(organization, menu) + issue 关联项目
-- 执行顺序：建表 -> issue 加列 -> 初始化默认项目 -> 历史 issue 归并默认项目
-- 说明：issue.project_id 不加外键，避免与逻辑删除(deleted)冲突。
-- ============================================================

SET NAMES utf8mb4;

-- 1. project 项目表
CREATE TABLE IF NOT EXISTS `project` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(100) NOT NULL COMMENT '项目名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '项目描述',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `created_at`  DATETIME     DEFAULT NULL,
  `updated_at`  DATETIME     DEFAULT NULL,
  `deleted`     INT          DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_name` (`name`),
  KEY `idx_project_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 2. organization 组织表
CREATE TABLE IF NOT EXISTS `organization` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(100) NOT NULL COMMENT '组织名称',
  `parent_id`  BIGINT   NOT NULL DEFAULT 0 COMMENT '父级id，0表示根',
  `sort`       INT      NOT NULL DEFAULT 0 COMMENT '排序号，升序展示',
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted`    INT      DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_org_parent` (`parent_id`),
  KEY `idx_org_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织表';

-- 3. menu 菜单表
CREATE TABLE IF NOT EXISTS `menu` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(50)  NOT NULL COMMENT '菜单名称',
  `path`       VARCHAR(200) DEFAULT NULL COMMENT '路由路径',
  `parent_id`  BIGINT   NOT NULL DEFAULT 0 COMMENT '父级id，0表示根',
  `sort`       INT      NOT NULL DEFAULT 0 COMMENT '排序号，升序展示',
  `permission` VARCHAR(100) DEFAULT NULL COMMENT '权限标识 module:resource:action',
  `icon`       VARCHAR(50)  DEFAULT NULL COMMENT '图标名（Element Plus icon 名）',
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted`    INT      DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_menu_parent` (`parent_id`),
  KEY `idx_menu_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- 4. issue 表新增 project_id（无外键，避免逻辑删除冲突）
SET @p0_exist := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'issue'
    AND COLUMN_NAME = 'project_id'
);
SET @p0_sql := IF(
  @p0_exist = 0,
  'ALTER TABLE `issue` ADD COLUMN `project_id` BIGINT DEFAULT NULL COMMENT \'关联项目id\' AFTER `assignee_id`, ADD KEY `idx_issue_project` (`project_id`)',
  'SELECT 1'
);
PREPARE p0_stmt FROM @p0_sql;
EXECUTE p0_stmt;
DEALLOCATE PREPARE p0_stmt;

-- 5. 初始化默认项目（幂等：已存在则不重复插入）
INSERT INTO `project` (`name`, `description`, `status`, `created_at`, `updated_at`)
SELECT '默认项目', '系统默认项目', 1, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `project` WHERE `name` = '默认项目' AND `deleted` = 0
);

-- 6. 历史 issue 关联默认项目（仅回填未关联的）
UPDATE `issue`
SET `project_id` = (
  SELECT `id` FROM `project` WHERE `name` = '默认项目' AND `deleted` = 0 LIMIT 1
)
WHERE `project_id` IS NULL;
