-- ============================================================
-- issueFlow V20260803b — 修复 issue_type 唯一索引 + 清理冒烟测试残留
-- ============================================================
-- 【用途】
--   修复线上 bug：DELETE /api/issue-types/{id} 返回 500
--   Duplicate entry 'SMOKE_TMP2-1' for key 'issue_type.uk_issue_type_code'
--
-- 【根因】
--   V20260803 建表时 uk_issue_type_code 是复合唯一索引 (code, deleted)。
--   MyBatis-Plus 全局逻辑删除（application.yml: logic-delete-field=deleted /
--   logic-delete-value=1）把 issueTypeMapper.deleteById(id) 翻译成
--   UPDATE issue_type SET deleted=1 WHERE id=? AND deleted=0。
--   该 UPDATE 使索引元组从 (code,0) 变为 (code,1)；若同 code 已存在墓碑行 (code,1)，
--   则与之冲突 → DuplicateKeyException → GlobalExceptionHandler 兜底 → 500。
--
-- 【方案】生成列 + 条件唯一索引（仅对 deleted=0 的行唯一）
--   新增虚拟生成列 code_active = IF(deleted=0, code, NULL)，
--   唯一索引改建在 code_active 上。唯一索引忽略 NULL，因此：
--     · 存活行至多一条同 code   → 保持业务唯一语义（与 Service.assertCodeUnique 完全一致）
--     · 墓碑行 code_active=NULL → 任意多条互不冲突，软删永不撞键（本 bug 根除）
--     · 软删后同 code 可重新新建 → 无「墓碑永久占用 code」副作用
--   备选方案「单列唯一 (code)」被否决：Service.assertCodeUnique 只统计 deleted=0，
--   墓碑行对 Java 不可见但对 DB 唯一索引可见，会把 500 从 delete 迁移到 create，
--   且需改 Java 才能兜住（当前环境无 JDK17 不能编译）。取舍见回报说明。
--
-- 【依赖】
--   · 目标库：24 号服务器 MySQL 8.0 容器 mysql-gihtg 内的 issueflow 库
--     （生成列 + 虚拟列索引需 MySQL 5.7+，8.0 满足）
--   · 前置：必须已执行 V20260803_issueflow_phase6.sql（issue_type 表须已存在）
--
-- 【执行顺序】
--   1. Phase 1-5 脚本 → 2. V20260803_issueflow_phase6.sql
--   → 3. 【本脚本】V20260803b_fix_issuetype_unique.sql
--   → 4. 重启后端（可选：本次纯 DDL/DML，无 Java 变更，不重启亦可生效）
--
-- 【执行方式】
--   sudo docker exec -i mysql-gihtg mysql -uroot -p'***' issueflow \
--     < scripts/V20260803b_fix_issuetype_unique.sql
--
-- 【幂等性】
--   全部语句可重复执行：DDL 走 information_schema 探测 + 动态 PREPARE，
--   DML 用带条件的 DELETE（第二次执行影响 0 行）。重跑无副作用。
--
-- 日期：2026-08-03
-- ============================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0. 执行前快照（人工核对用，不改数据）
-- ---------------------------------------------------------------------------
SELECT '=== [0] BEFORE: 当前 uk_issue_type_code 索引构成 ===' AS `step`;
SELECT `INDEX_NAME`, `SEQ_IN_INDEX`, `COLUMN_NAME`, `NON_UNIQUE`
FROM information_schema.STATISTICS
WHERE `TABLE_SCHEMA` = DATABASE()
  AND `TABLE_NAME` = 'issue_type'
  AND `INDEX_NAME` = 'uk_issue_type_code'
ORDER BY `SEQ_IN_INDEX`;

SELECT '=== [0] BEFORE: issue_type 全量行（含软删） ===' AS `step`;
SELECT `id`, `name`, `code`, `sort`, `enabled`, `deleted` FROM `issue_type` ORDER BY `id`;

-- ---------------------------------------------------------------------------
-- 1. 物理清除冒烟测试残留行（id 8/9/10：SMOKE_TMP / SMOKE_TMP2）
--    这些是 Phase 6 冒烟产生的测试垃圾，非生产数据。
--    双重保险：id 白名单 IN (8,9,10) 且 code 必须是 SMOKE_ 前缀，
--    正式种子 id 1-6（BUG/FEATURE/PERFORMANCE/UI/QUESTION/OTHER）绝无可能被命中。
--    幂等：重跑时行已不存在，影响 0 行。
--    顺序说明：必须在建唯一索引之前执行，避免残留行造成 ADD UNIQUE 失败。
-- ---------------------------------------------------------------------------
DELETE FROM `issue_type`
WHERE `id` IN (8, 9, 10)
  AND `code` IN ('SMOKE_TMP', 'SMOKE_TMP2');

-- ---------------------------------------------------------------------------
-- 2. 新增虚拟生成列 code_active（幂等）
--    VIRTUAL 不占行存储，ADD COLUMN 为 in-place 操作，对 10 行小表零风险。
-- ---------------------------------------------------------------------------
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'issue_type'
    AND `COLUMN_NAME` = 'code_active'
);
SET @sql := IF(@has_col = 0,
  'ALTER TABLE `issue_type` ADD COLUMN `code_active` VARCHAR(50) GENERATED ALWAYS AS (IF(`deleted` = 0, `code`, NULL)) VIRTUAL COMMENT ''条件唯一辅助列（deleted=0 时为 code，否则 NULL），仅供 uk_issue_type_code 使用''',
  'SELECT ''[2] code_active 已存在，跳过'' AS `skip`');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 3. 删除旧的 uk_issue_type_code（幂等）
--    仅当该索引存在且包含任何非 code_active 的列时才 DROP：
--      · (code, deleted) 复合    → 命中 2 列 → DROP
--      · (code) 单列              → 命中 1 列 → DROP
--      · (code_active) 目标形态   → 命中 0 列 → 跳过（重跑安全）
--      · 索引不存在               → 命中 0 列 → 跳过
-- ---------------------------------------------------------------------------
SET @need_drop := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'issue_type'
    AND `INDEX_NAME` = 'uk_issue_type_code'
    AND `COLUMN_NAME` <> 'code_active'
);
SET @sql := IF(@need_drop > 0,
  'ALTER TABLE `issue_type` DROP INDEX `uk_issue_type_code`',
  'SELECT ''[3] 无旧索引需删除，跳过'' AS `skip`');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 4. 建索引前置校验：存活行内不得有重复 code
--    正常应返回 0 行；若返回非空，请先人工去重再重跑本脚本，
--    否则下一步 ADD UNIQUE 会以 Duplicate entry 报错中止（不会破坏数据）。
-- ---------------------------------------------------------------------------
SELECT '=== [4] PRECHECK: deleted=0 内重复 code（应为空） ===' AS `step`;
SELECT `code`, COUNT(*) AS `cnt`, GROUP_CONCAT(`id` ORDER BY `id`) AS `ids`
FROM `issue_type`
WHERE `deleted` = 0
GROUP BY `code`
HAVING COUNT(*) > 1;

-- ---------------------------------------------------------------------------
-- 5. 重建条件唯一索引 uk_issue_type_code (code_active)（幂等）
-- ---------------------------------------------------------------------------
SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'issue_type'
    AND `INDEX_NAME` = 'uk_issue_type_code'
);
SET @sql := IF(@has_idx = 0,
  'ALTER TABLE `issue_type` ADD UNIQUE KEY `uk_issue_type_code` (`code_active`)',
  'SELECT ''[5] uk_issue_type_code 已是目标形态，跳过'' AS `skip`');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 6. 校验（人工核对下列 4 项全部通过才算修复成功）
-- ---------------------------------------------------------------------------
-- 6.1 索引形态：应恰好 1 行，COLUMN_NAME=code_active、NON_UNIQUE=0
SELECT '=== [6.1] AFTER: uk_issue_type_code 索引构成（期望 1 行 code_active / NON_UNIQUE=0） ===' AS `step`;
SELECT `INDEX_NAME`, `SEQ_IN_INDEX`, `COLUMN_NAME`, `NON_UNIQUE`
FROM information_schema.STATISTICS
WHERE `TABLE_SCHEMA` = DATABASE()
  AND `TABLE_NAME` = 'issue_type'
  AND `INDEX_NAME` = 'uk_issue_type_code'
ORDER BY `SEQ_IN_INDEX`;

-- 6.2 类型总数：total_rows=6、alive=6、tombstone=0
SELECT '=== [6.2] AFTER: 行数统计（期望 total=6 / alive=6 / tombstone=0） ===' AS `step`;
SELECT COUNT(*)                                        AS `total_rows`,
       SUM(CASE WHEN `deleted` = 0 THEN 1 ELSE 0 END)  AS `alive`,
       SUM(CASE WHEN `deleted` = 1 THEN 1 ELSE 0 END)  AS `tombstone`
FROM `issue_type`;

-- 6.3 种子完好性：应返回 id 1-6 共 6 行
SELECT '=== [6.3] AFTER: 6 个正式种子（期望 BUG/FEATURE/PERFORMANCE/UI/QUESTION/OTHER 各 1 行） ===' AS `step`;
SELECT `id`, `name`, `code`, `sort`, `enabled`, `deleted`, `code_active`
FROM `issue_type`
ORDER BY `id`;

-- 6.4 残留清除确认：应返回 0
SELECT '=== [6.4] AFTER: 冒烟残留剩余数（期望 0） ===' AS `step`;
SELECT COUNT(*) AS `smoke_leftover`
FROM `issue_type`
WHERE `code` LIKE 'SMOKE\_%';

-- ---------------------------------------------------------------------------
-- 7. 回滚预案（仅在需要退回 Phase 6 原状时手工执行，正常流程不要跑）
--    注意：回滚会让 DELETE 接口 500 的 bug 复现，且需先清空所有墓碑行。
-- ---------------------------------------------------------------------------
-- DELETE FROM `issue_type` WHERE `deleted` = 1;
-- ALTER TABLE `issue_type` DROP INDEX `uk_issue_type_code`;
-- ALTER TABLE `issue_type` DROP COLUMN `code_active`;
-- ALTER TABLE `issue_type` ADD UNIQUE KEY `uk_issue_type_code` (`code`, `deleted`);
