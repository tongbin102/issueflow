-- ============================================================
-- V20260806d_fix_project_ref_order.sql
-- 修复 ref_source_registry 中 PROJECT 引用源的 order_field 配置错误
--
-- 【故障现象】前台「提交新问题」打开表单即 500：
--   GET /api/field-configs/ref-options?refSource=PROJECT
--   java.sql.SQLSyntaxErrorException: Unknown column 'sort' in 'order clause'
--
-- 【根因】V20260806_dynamic_field.sql 的 ref_source_registry 种子把 PROJECT 的
--   order_field 写成了 'sort'，但 project 表并无 sort 列（实际列：
--   id / name / description / status / leader_id / member_ids /
--   created_at / updated_at / deleted）。MODULE 引用源指向 module 表，
--   module 表确有 sort 列，故不受影响，本脚本只订正 PROJECT 一行。
--
-- 【修法】order_field 改为 'id'：
--   与 value_field 一致，主键必然存在且唯一稳定，排序结果可预期；
--   若改用 'name' 则受 collation 影响且中文排序无业务含义，故选 id。
--
-- 【幂等】UPDATE ... WHERE order_field <> 'id'，重复执行不再产生变更。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 订正 PROJECT 引用源的排序列（project 表无 sort 列，回落到主键 id）
UPDATE `ref_source_registry`
SET `order_field` = 'id',
    `updated_at`  = NOW()
WHERE `code` = 'PROJECT'
  AND `deleted` = 0
  AND (`order_field` IS NULL OR `order_field` <> 'id');

-- 2. 验证：PROJECT 的 order_field 应为 id；其余行保持原样
SELECT `id`,
       `code`,
       `name`,
       `table_name`,
       `label_field`,
       `value_field`,
       `order_field`,
       `filter_field`,
       `parent_field`,
       `query_type`,
       `enabled`
FROM `ref_source_registry`
WHERE `deleted` = 0
ORDER BY `id`;
