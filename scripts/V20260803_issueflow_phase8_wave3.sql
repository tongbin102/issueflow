-- ===========================================================================
-- issueFlow Phase8 Wave 3 迁移脚本
-- 生成日期：2026-08-03
-- 覆盖需求：
--   #11 用户多角色（单角色 role_id -> 多角色 user_role + user.roles）
--       §1 新建 user_role 关系表
--       §2 user 表新增 roles 列（JSON 数组角色码）
--       §3 存量回填 user_role（按 user.role_id 对应的 role.code）
--       §4 存量回填 user.roles（与 §3 同源，JSON 数组文本）
--       §5 执行结果自检（仅 SELECT）
--
-- 幂等性说明（全部语句可重复执行）：
--   §1 CREATE TABLE IF NOT EXISTS + UNIQUE KEY uk_user_role(user_id, role_code) 防重复行。
--   §2 MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，故先查 information_schema.COLUMNS
--      计数，再用 PREPARE/EXECUTE 动态执行——已存在则退化为 SELECT 1。
--      （与 V20260802_issueflow_phase8_wave2.sql §1 同款写法，保持仓库内风格一致）
--   §3 INSERT ... SELECT 带 NOT EXISTS 反连接，且有唯一键兜底，重复执行插 0 行。
--   §4 UPDATE 带 WHERE (roles IS NULL OR roles = '' OR roles = 'null')，
--      只补空值，不覆盖管理员后续在页面上分配的多角色结果 —— 重复执行安全。
--   §5 仅 SELECT 自检，不改数据。
--
-- 执行环境：24 号机 MySQL8，issueflow 库
-- ===========================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 新建 user_role 用户-角色关系表（需求 #11）
--    存角色「码」而非角色 id：JWT / SecurityContext 直接消费角色码，
--    鉴权链路无需 id->code 反查。不加外键，与 user.org_id 同口径
--    （user 走逻辑删除，外键会与 deleted 冲突；role 支持自定义角色增删）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户 id（user.id）',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色码（role.code）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_code`),
  KEY `idx_user_role_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关系(多角色)';

-- ---------------------------------------------------------------------------
-- 2. user 表新增 roles 列（需求 #11）
--    JSON 数组角色码文本，如 ["ADMIN"]；是 user_role 的冗余读缓存，
--    用于列表/登录场景免 N+1 查询。为 NULL 时后端自动回落 user_role 关系表。
--    列类型用 VARCHAR(500) 而非 JSON：与仓库既有列风格一致，
--    且 MyBatis-Plus JacksonTypeHandler 按字符串读写，无需 JSON 函数。
-- ---------------------------------------------------------------------------
SET @w3_user_roles_exist := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND COLUMN_NAME = 'roles'
);
SET @w3_user_roles_sql := IF(
  @w3_user_roles_exist = 0,
  'ALTER TABLE `user` ADD COLUMN `roles` VARCHAR(500) DEFAULT NULL COMMENT \'JSON 数组角色码，如 ["ADMIN"]（Phase8 W3 #11）\' AFTER `role_id`',
  'SELECT 1'
);
PREPARE w3_user_roles_stmt FROM @w3_user_roles_sql;
EXECUTE w3_user_roles_stmt;
DEALLOCATE PREPARE w3_user_roles_stmt;

-- ---------------------------------------------------------------------------
-- 3. 存量回填 user_role（需求 #11）
--    规则：每个未删除用户，按其 role_id 对应的 role.code 建一条关系行。
--    NOT EXISTS 反连接 + uk_user_role 唯一键双保险，重复执行插 0 行；
--    也不会覆盖管理员后续在「用户管理」里分配的额外角色。
-- ---------------------------------------------------------------------------
INSERT INTO `user_role` (`user_id`, `role_code`)
SELECT u.`id`, r.`code`
FROM `user` u
JOIN `role` r ON r.`id` = u.`role_id`
WHERE u.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `user_role` ur
    WHERE ur.`user_id` = u.`id` AND ur.`role_code` = r.`code`
  );

-- ---------------------------------------------------------------------------
-- 4. 存量回填 user.roles（需求 #11）
--    以 user_role 关系表为准聚合成 JSON 数组文本，例如 admin 用户 -> ["ADMIN"]。
--    只补空值（NULL / 空串 / 字面量 'null'），已有值不动 —— 幂等且不覆盖业务数据。
--    用 CONCAT + GROUP_CONCAT 手工拼装而非 JSON_ARRAYAGG：兼容性更稳，
--    且 role_code 为受控字典值（字母/下划线），无需转义。
-- ---------------------------------------------------------------------------
UPDATE `user` u
SET u.`roles` = (
  SELECT CONCAT('["', GROUP_CONCAT(ur.`role_code` ORDER BY ur.`id` SEPARATOR '","'), '"]')
  FROM `user_role` ur
  WHERE ur.`user_id` = u.`id`
)
WHERE u.`deleted` = 0
  AND (u.`roles` IS NULL OR u.`roles` = '' OR u.`roles` = 'null')
  AND EXISTS (SELECT 1 FROM `user_role` ur2 WHERE ur2.`user_id` = u.`id`);

-- ---------------------------------------------------------------------------
-- 5. 执行结果自检（仅查询，不改数据）
--    预期：
--      user_role_table_exists   = 1
--      user_roles_col_exists    = 1
--      users_without_role_row   = 0   （每个有效用户至少一条角色关系）
--      users_with_null_roles    = 0   （roles 冗余列已全部回填）
--      admin_roles              = ["ADMIN"]
-- ---------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_role')      AS user_role_table_exists,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user'
      AND COLUMN_NAME = 'roles')                                        AS user_roles_col_exists,
  (SELECT COUNT(*) FROM `user` u WHERE u.`deleted` = 0
     AND NOT EXISTS (SELECT 1 FROM `user_role` ur
                     WHERE ur.`user_id` = u.`id`))                      AS users_without_role_row,
  (SELECT COUNT(*) FROM `user` WHERE `deleted` = 0
     AND (`roles` IS NULL OR `roles` = '' OR `roles` = 'null'))         AS users_with_null_roles,
  (SELECT COUNT(*) FROM `user_role`)                                    AS user_role_total_rows,
  (SELECT `roles` FROM `user` WHERE `username` = 'admin' LIMIT 1)       AS admin_roles;

-- ===========================================================================
-- 回滚参考（如需）：
--   ALTER TABLE `user` DROP COLUMN `roles`;
--   DROP TABLE IF EXISTS `user_role`;
--   （user.role_id 全程未被本脚本修改，回滚后即恢复升级前的单角色模型）
-- ===========================================================================
