-- ===========================================================================
-- issueFlow Phase8 Wave 2 迁移脚本
-- 生成日期：2026-08-02
-- 覆盖需求：
--   #7  新增用户移除「密码」字段，自动取默认密码   —— 纯代码，无 DDL/DML
--                                                    （依赖 W1 已建的 site.default_password）
--   #9  用户增改新增「所属组织」(org_id)          —— 本脚本 §1
--   #6  issue「所属项目」必填 + 存量数据回填       —— 本脚本 §2 / §3 / §4
--   #12 issue 弹窗改左侧竖形标签页                —— 纯前端，无 DDL/DML
--
-- 幂等性说明（全部语句可重复执行）：
--   §1 MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，故先查 information_schema.COLUMNS
--      计数，再用 PREPARE/EXECUTE 动态执行——已存在则退化为 SELECT 1。
--      （与 V20250730_issueflow_p0.sql §4 同款写法，保持仓库内风格一致）
--   §2 回填 UPDATE 带 WHERE project_id IS NULL，天然幂等；
--      并用 (SELECT COUNT(*) FROM project WHERE deleted=0) > 0 保护——
--      project 表为空时整条 UPDATE 影响 0 行，绝不把 project_id 写成 NULL。
--   §3 改 NOT NULL 前双重前置判断：① 当前 IS_NULLABLE='YES'；② 已无 NULL 残留。
--      任一不满足即跳过，重复执行安全，且不会因残留 NULL 报 1138/1265 而中断。
--   §4 仅 SELECT 自检，不改数据。
--
-- 执行环境：24 号机 MySQL8，issueflow 库
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. user 表新增 org_id（需求 #9）
--    可空：并非所有用户都归属组织；不加外键，与 issue.project_id 一致的口径
--    （organization 走逻辑删除，外键会与 deleted 冲突）。
--    同时建普通索引 idx_user_org，便于按组织筛人。
-- ---------------------------------------------------------------------------
SET @w2_user_org_exist := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND COLUMN_NAME = 'org_id'
);
SET @w2_user_org_sql := IF(
  @w2_user_org_exist = 0,
  'ALTER TABLE `user` ADD COLUMN `org_id` BIGINT DEFAULT NULL COMMENT \'所属组织 id（organization.id，可空，无外键）\' AFTER `role_id`, ADD KEY `idx_user_org` (`org_id`)',
  'SELECT 1'
);
PREPARE w2_user_org_stmt FROM @w2_user_org_sql;
EXECUTE w2_user_org_stmt;
DEALLOCATE PREPARE w2_user_org_stmt;

-- ---------------------------------------------------------------------------
-- 2. issue.project_id 存量回填（需求 #6）
--    规则：未关联项目的历史问题统一挂到 id 最小的有效项目（MIN(id) WHERE deleted=0）。
--    保护：project 表无有效行时条件不成立，UPDATE 影响 0 行 —— 此时 §3 也会因
--          仍有 NULL 而自动跳过，不会破坏库结构（见交付说明的「遗留项」）。
-- ---------------------------------------------------------------------------
UPDATE `issue`
SET `project_id` = (SELECT MIN(`id`) FROM `project` WHERE `deleted` = 0)
WHERE `project_id` IS NULL
  AND (SELECT COUNT(*) FROM `project` WHERE `deleted` = 0) > 0;

-- ---------------------------------------------------------------------------
-- 3. issue.project_id 改为 NOT NULL（需求 #6）
--    仅当「当前可空」且「已无 NULL 残留」时才执行，避免 project 表为空场景下报错。
--    列类型沿用 V20250730 建列时的 BIGINT，仅收紧空值约束，不改类型与索引。
-- ---------------------------------------------------------------------------
SET @w2_proj_nullable := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'issue'
    AND COLUMN_NAME = 'project_id'
    AND IS_NULLABLE = 'YES'
);
SET @w2_proj_null_rows := (SELECT COUNT(*) FROM `issue` WHERE `project_id` IS NULL);
SET @w2_proj_sql := IF(
  @w2_proj_nullable = 1 AND @w2_proj_null_rows = 0,
  'ALTER TABLE `issue` MODIFY COLUMN `project_id` BIGINT NOT NULL COMMENT \'所属项目id（Phase8 W2 起必填）\'',
  'SELECT 1'
);
PREPARE w2_proj_stmt FROM @w2_proj_sql;
EXECUTE w2_proj_stmt;
DEALLOCATE PREPARE w2_proj_stmt;

-- ---------------------------------------------------------------------------
-- 4. 执行结果自检（仅查询，不改数据）
--    预期：
--      user.org_id      存在，IS_NULLABLE = YES
--      issue.project_id IS_NULLABLE = NO，且 null_rows = 0
--    若 null_rows > 0 或 project_cnt = 0，说明 project 表为空导致 #6 未完全落地，
--    需先在「项目配置」建至少 1 个项目后重跑本脚本。
-- ---------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM `project` WHERE `deleted` = 0)          AS project_cnt,
  (SELECT COUNT(*) FROM `issue` WHERE `project_id` IS NULL)     AS issue_null_project_rows,
  (SELECT `IS_NULLABLE` FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issue'
      AND COLUMN_NAME = 'project_id')                           AS issue_project_nullable,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user'
      AND COLUMN_NAME = 'org_id')                               AS user_org_col_exists;

-- ===========================================================================
-- 回滚参考（如需）：
--   ALTER TABLE `issue` MODIFY COLUMN `project_id` BIGINT DEFAULT NULL COMMENT '关联项目id';
--   ALTER TABLE `user` DROP INDEX `idx_user_org`, DROP COLUMN `org_id`;
--   （§2 回填为数据变更，如需回滚请依据备份还原，脚本不提供反向 UPDATE）
-- ===========================================================================
