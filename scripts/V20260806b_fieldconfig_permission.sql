-- ============================================================
-- issueFlow Phase9 补丁：字段配置权限码注册 + ADMIN 授权
--
-- 【背景 / 缺陷】
--   Phase9「问题字段可配置」于 2026-08-23 部署、08-24 灌库。
--   V20260806_dynamic_field.sql 只插入了 menu 记录
--   （id=32 / path=/admin/field-configs / parent_id=2「业务管理」/
--     permission='field:config:list' / type=2 / deleted=0），
--   但**漏了向 permission 表注册权限码、也漏了给 ADMIN 授权**。
--   前端 SideMenu.vue 按 hasPerm('field:config:list') 过滤菜单，
--   admin 权限集里没有该码 → 「字段配置」菜单被隐藏 → 功能上线但无入口。
--
-- 【依赖】
--   必须先执行 V20260806_dynamic_field.sql
--   （field_section / field_config / issue_field_value / ref_source_registry
--     等表与 menu id=32 记录已存在）。
--   本脚本**不建表、不改表结构、不动 menu**，仅补「权限注册 + 角色授权」两件事。
--
-- 【权限码来源】后端 requirePermission 实际调用（已 grep 核对）：
--   field:config:list   FieldConfigService#61,94  FieldSectionService#37,44
--                       → 列表 / 详情 / 树形表格 / 引用源下拉
--   field:config:save   FieldConfigService#122,143,274  FieldSectionService#50,67,125
--                       → 新增 / 编辑 / 启停切换 / section 保存
--   field:config:delete FieldConfigService#262  FieldSectionService#109
--                       → 删除字段 / 删除 section
--   注：FieldConfigController 的 /schema、/ref-options 为「登录即可」，无权限码。
--
-- 【表结构说明 —— 勿臆造 parent_id】
--   permission 表实测无 parent_id / 无分组外键，字段为：
--     id, code, name, module, action, type, sort, created_at, updated_at, deleted
--   分组靠 `module` 字符串 + `sort` 号段体现（见既有：issueType 101-105 /
--   dict 201-204 / file 211-214 / config 221-224 / redis 231 / job 241-245 /
--   system 251 / infra 261 / business 262）。
--   故本脚本沿用同一约定：module='fieldConfig'，占用新号段 271-273。
--   type=2 与全部功能权限一致（1 仅 dashboard:view 使用）。
--   role_permission 表**无 updated_at 列**，种子不得携带（Phase6 血泪教训）。
--
-- 【幂等】三条 INSERT 均带 WHERE NOT EXISTS；授权段按 code 关联并排重，
--         可重复执行，二次运行影响 0 行。
-- 【字符集】中文字面量，必须 utf8mb4，见下方 SET NAMES。
-- 日期：2026-08-06
-- ============================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 注册三个权限码（沿用 Phase6 §13 的写法）
-- ---------------------------------------------------------------------------
INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'field:config:list', '字段配置查看', 'fieldConfig', 'list', 2, 271, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='field:config:list');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'field:config:save', '字段配置保存', 'fieldConfig', 'save', 2, 272, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='field:config:save');

INSERT INTO `permission` (`code`,`name`,`module`,`action`,`type`,`sort`,`created_at`,`updated_at`)
SELECT 'field:config:delete', '字段配置删除', 'fieldConfig', 'delete', 2, 273, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `code`='field:config:delete');

-- ---------------------------------------------------------------------------
-- 2. 授权给 ADMIN 角色（实测 role.code='ADMIN' 即 id=4；
--    此处按 code 取 id，避免硬编码主键在不同环境漂移）
-- ---------------------------------------------------------------------------
SET @admin_role := (SELECT `id` FROM `role` WHERE `code`='ADMIN');

INSERT INTO `role_permission` (`role_id`,`permission_id`,`created_at`)
SELECT @admin_role, p.`id`, NOW()
FROM `permission` p
WHERE p.`code` IN ('field:config:list','field:config:save','field:config:delete')
  AND @admin_role IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp
                  WHERE rp.`role_id`=@admin_role AND rp.`permission_id`=p.`id`);

-- ---------------------------------------------------------------------------
-- 3. 自检（执行后肉眼核对：两条均应返回 3 行）
-- ---------------------------------------------------------------------------
SELECT `id`,`code`,`name`,`module`,`action`,`sort`
FROM `permission`
WHERE `code` LIKE 'field:config%'
ORDER BY `sort`;

SELECT rp.`role_id`, p.`code`
FROM `permission` p
JOIN `role_permission` rp ON rp.`permission_id` = p.`id`
WHERE rp.`role_id` = @admin_role AND p.`code` LIKE 'field:config%'
ORDER BY p.`sort`;
