-- ===========================================================================
-- issueFlow Phase8 Wave 1 迁移脚本
-- 生成日期：2026-08-01
-- 覆盖需求：
--   #10 后台左上角标题跟随「网站名称」配置        —— 纯前端，无 DDL/DML
--   #2  菜单重命名 + 新增「新增用户默认密码」配置  —— 本脚本 §1 / §2
--   #8  移除「模块配置」页面及菜单入口            —— 本脚本 §3
--   #4  前台底部页脚（版权 / 备案号）             —— 纯前端，无 DDL/DML
--
-- 幂等性说明：
--   全部语句可重复执行。
--   §1 使用 INSERT ... ON DUPLICATE KEY UPDATE（sys_config.uk_cfg_key 唯一键），
--      重复执行仅刷新 description，【不覆盖 config_value】——避免二次执行把
--      管理员已在「系统设置」页改过的默认密码重置回 123456。
--   §2 / §3 的 UPDATE 均带状态前置条件，重复执行影响 0 行。
--
-- 执行环境：24 号机 MySQL8，issueflow 库
-- ===========================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. sys_config：新增「新增用户默认密码」配置键 site.default_password（需求 #2）
--    默认值 123456；前端「系统设置」页以密码框（可切换明文）维护，校验长度 6~32。
--    敏感项：公开接口 GET /api/site/config 不下发，仅管理端
--            GET /api/admin/site/config（site:config:update 权限）返回。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `updated_at`)
VALUES ('site.default_password', '123456', '后台新增用户时的初始密码（6~32 字符）', NOW())
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`),
  `updated_at`  = NOW();

-- ---------------------------------------------------------------------------
-- 2. menu：菜单文案重命名（需求 #2）
--    仅改 name，path / component / parent_id / sort / permission 一律不动，
--    前端 i18n key（MENU_KEY_BY_PATH 按 path 映射）也保持不变，只改词条取值。
--
--    2.1 /admin/system/site     「网站设置」→「系统设置」（SiteSettings.vue，站点配置）
--    2.2 /admin/system/settings 「系统设置」→「备份设置」（SystemSettings.vue，数据维护）
--
--    注意执行顺序：先把 /admin/system/settings 改名，再改 /admin/system/site，
--    否则中间态会出现两条同名「系统设置」记录（不影响功能，仅为可读性）。
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `name` = '备份设置', `updated_at` = NOW()
WHERE `path` = '/admin/system/settings'
  AND `type` = 2
  AND `deleted` = 0
  AND `name` <> '备份设置';

UPDATE `menu`
SET `name` = '系统设置', `updated_at` = NOW()
WHERE `path` = '/admin/system/site'
  AND `type` = 2
  AND `deleted` = 0
  AND `name` <> '系统设置';

-- ---------------------------------------------------------------------------
-- 3. menu：下线「模块配置」菜单入口（需求 #8）
--    软删除（deleted=1），与 BaseEntity 逻辑删除约定一致，可回滚。
--    按 path 精确匹配，不涉及「项目配置」/admin/projects 等兄弟节点。
--    模块维护能力保留：ProjectManage.vue 仍通过 ModuleTreeDrawer → ModuleTreePanel 使用。
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `deleted` = 1, `updated_at` = NOW()
WHERE `path` = '/admin/modules'
  AND `type` = 2
  AND `deleted` = 0;

-- ---------------------------------------------------------------------------
-- 4. 执行结果自检（可选，仅查询不改数据）
-- ---------------------------------------------------------------------------
-- SELECT `config_key`, `config_value`, `description`
--   FROM `sys_config` WHERE `config_key` = 'site.default_password';
-- SELECT `id`, `name`, `path`, `type`, `deleted`
--   FROM `menu`
--  WHERE `path` IN ('/admin/system/site', '/admin/system/settings', '/admin/modules')
--  ORDER BY `path`;

-- ===========================================================================
-- 回滚参考（如需）：
--   UPDATE `menu` SET `deleted`=0 WHERE `path`='/admin/modules' AND `type`=2;
--   UPDATE `menu` SET `name`='网站设置' WHERE `path`='/admin/system/site' AND `type`=2;
--   UPDATE `menu` SET `name`='系统设置' WHERE `path`='/admin/system/settings' AND `type`=2;
--   DELETE FROM `sys_config` WHERE `config_key`='site.default_password';
-- ===========================================================================
