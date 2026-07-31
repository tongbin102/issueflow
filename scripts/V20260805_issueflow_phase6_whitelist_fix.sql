-- ===========================================================================
-- issueFlow Phase8 Wave 5 迁移脚本 —— Phase6 图标白名单缺口补齐
-- 生成日期：2026-08-05
-- 基线：Wave4 commit 4df9d41
--
-- 【用途】
--   补 V20260803_issueflow_phase6.sql §12.2（L151-159）的白名单缺口：
--   该段有一条「白名单式 icon 自愈」——任何 deleted=0 且不在白名单内的 icon 会被强制刷成 Grid。
--   Wave4 新引入的 4 个图标 FolderOpened / Share / Files / SetUp（以及 Phase7 的 Timer）
--   原本不在该白名单内，一旦运维「单独重跑 Phase6」，这些图标就会被回刷为 Grid。
--
--   本次已同步在 Phase6 §12.2 白名单里追加上述取值（根治），本脚本再做两件事以确保线上一致：
--     §1 对受影响菜单按 id 守卫式「重断言」为正确图标（若被外部刷成 Grid 则纠正回来）；
--     §2 复制 Phase6 §12.2 的自愈 UPDATE，但白名单已含新值，保证幂等（正确值不动、仅纠 Grid）；
--     §3 自检 SELECT：确认这 5 个 id 的 icon 均为新值，且全盘（deleted=0）无 Grid 占位 / 无空 icon。
--
-- 【幂等性说明（全部语句可重复执行）】
--   §1 每条 UPDATE 带 `icon` <> '目标值' 守卫，已是正确值时影响 0 行；仅当被刷成 Grid 时纠正。
--   §2 白名单自愈 UPDATE：新值已在白名单内，不会被刷；正确值不变，天然幂等。
--   §3 仅 SELECT 自检，不改数据。
--
-- 【执行环境】24 号机 MySQL8，issueflow_db 库（部署阶段灌库；本脚本不 SSH 改运行库）。
-- 【依赖顺序】需在 V20260804_issueflow_phase8_wave4.sql 之后执行。
--
-- 【受影响菜单（重断言目标值，与 Wave4 §2 一致）】
--   id=17 项目管理  -> FolderOpened   （软删行 deleted=1，重断言属无害留存操作）
--   id=16 流程管理  -> Share
--   id=6  流程配置  -> Share
--   id=26 文件管理  -> Files
--   id=29 配置管理  -> SetUp
--   （Timer 为 id=31 计划任务，Phase7 引入，本次仅补进白名单，无需重断言）
-- ===========================================================================
SET NAMES utf8mb4;


-- ---------------------------------------------------------------------------
-- 1. 受影响菜单图标「守卫式重断言」（idempotent：已正确则跳过，被刷 Grid 则纠正）
--
--    守卫用 `icon` <> '目标值'：这些菜单行 icon 恒为非空字符串（被外部刷坏时为 'Grid'，
--    非 NULL），故无需额外 NULL 分支；已是目标值时 `'X' <> 'X'` 为 false，影响 0 行。
--    id=17 为 deleted=1 软删行，不参与侧边栏渲染，重断言仅为墓碑行日后恢复即带正确图标（无害）。
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `icon` = 'FolderOpened', `updated_at` = NOW()
WHERE `id` = 17 AND `icon` <> 'FolderOpened';

UPDATE `menu` SET `icon` = 'Share', `updated_at` = NOW()
WHERE `id` = 16 AND `icon` <> 'Share';

UPDATE `menu` SET `icon` = 'Share', `updated_at` = NOW()
WHERE `id` = 6 AND `icon` <> 'Share';

UPDATE `menu` SET `icon` = 'Files', `updated_at` = NOW()
WHERE `id` = 26 AND `icon` <> 'Files';

UPDATE `menu` SET `icon` = 'SetUp', `updated_at` = NOW()
WHERE `id` = 29 AND `icon` <> 'SetUp';


-- ---------------------------------------------------------------------------
-- 2. 白名单式 icon 自愈（复制 Phase6 §12.2，白名单已含 W4/W7 新值，保证幂等）
--
--    与 Phase6 §12.2 同构：deleted=0 且 icon 非空、且不在白名单内的行 -> Grid。
--    关键差异：白名单新增 FolderOpened / Share / Files / SetUp / Timer，
--    因此这些新值不会被回刷；其余合法图标同样在名单内、保持不变；仅当出现真正非法图标才纠为 Grid。
-- ---------------------------------------------------------------------------
UPDATE `menu` SET `icon`='Grid'
WHERE `deleted`=0 AND `icon` IS NOT NULL AND `icon`<>''
  AND `icon` NOT IN (
    'HomeFilled','Tickets','EditPen','DataLine','Management','Folder','Grid','Switch',
    'Operation','Tools','Setting','User','UserFilled','OfficeBuilding','Document',
    'CollectionTag','Monitor','Menu','List','Brush','FullScreen','Aim','Star','Bell',
    'Search','Plus','Delete','Edit','Refresh','Download','Upload','Link','Filter',
    'Histogram','PieChart','TrendCharts','DataBoard','DataAnalysis','Odometer','Notebook',
    'FolderOpened','Share','Files','SetUp','Timer'
  );


-- ---------------------------------------------------------------------------
-- 3. 执行结果自检（仅查询，不改数据）
-- ---------------------------------------------------------------------------

-- 3.1 汇总校验，预期：
--     icon_17_project = 'FolderOpened'
--     icon_16_flow    = 'Share'
--     icon_6_flowcfg  = 'Share'
--     icon_26_file    = 'Files'
--     icon_29_config  = 'SetUp'
--     correct_icon_rows     = 5   （5 个受影响 id 均为期望新值）
--     placeholder_icon_rows = 0   （deleted=0 全盘无空 icon / 无 Grid 占位）
SELECT
  (SELECT `icon` FROM `menu` WHERE `id`=17) AS icon_17_project,
  (SELECT `icon` FROM `menu` WHERE `id`=16) AS icon_16_flow,
  (SELECT `icon` FROM `menu` WHERE `id`=6)  AS icon_6_flowcfg,
  (SELECT `icon` FROM `menu` WHERE `id`=26) AS icon_26_file,
  (SELECT `icon` FROM `menu` WHERE `id`=29) AS icon_29_config,
  (SELECT COUNT(*) FROM `menu`
     WHERE (`id`=17 AND `icon`='FolderOpened')
        OR (`id`=16 AND `icon`='Share')
        OR (`id`=6  AND `icon`='Share')
        OR (`id`=26 AND `icon`='Files')
        OR (`id`=29 AND `icon`='SetUp'))            AS correct_icon_rows,
  (SELECT COUNT(*) FROM `menu`
     WHERE `deleted`=0
       AND (`icon` IS NULL OR `icon`='' OR `icon`='Grid')) AS placeholder_icon_rows;

-- 3.2 受影响 5 行明细核对（含 deleted 标记，便于确认 id=17 为软删行）
SELECT `id`, `parent_id`, `type`, `deleted`, `icon`, `name`, `path`
FROM `menu`
WHERE `id` IN (17, 16, 6, 26, 29)
ORDER BY `id`;

-- 3.3 兜底：列出任何 deleted=0 的 Grid 占位 / 空 icon 行（预期 0 行）
SELECT `id`, `parent_id`, `type`, `icon`, `name`, `path`
FROM `menu`
WHERE `deleted`=0 AND (`icon` IS NULL OR `icon`='' OR `icon`='Grid')
ORDER BY `id`;


-- ===========================================================================
-- 回滚参考（如需）：
--   -- §1 图标重断言还原（还原为 Wave4 之前的旧值）
--   UPDATE `menu` SET `icon`='Management' WHERE `id`=17;
--   UPDATE `menu` SET `icon`='Operation'  WHERE `id`=16;
--   UPDATE `menu` SET `icon`='Tools'      WHERE `id`=6;
--   UPDATE `menu` SET `icon`='Folder'     WHERE `id`=26;
--   UPDATE `menu` SET `icon`='Operation'  WHERE `id`=29;
--   -- §2 白名单自愈无独立回滚（幂等纠错语句，正确数据下为空操作）。
--   -- 注：Phase6 §12.2 白名单的追加项（本次根治）如需回退，删除该行新增的
--   --     'FolderOpened','Share','Files','SetUp','Timer' 即可。
-- ===========================================================================
