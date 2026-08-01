-- ===========================================================================
-- issueFlow Phase8 Wave 4 迁移脚本
-- 生成日期：2026-08-04
-- 基线：Wave3 commit 98c6b67
-- 覆盖需求：
--   #1 「基础设施」作为「系统管理」的兄弟节点，且显示在其下方（不嵌套）
--      §1 menu.sort 调整（id=25：6 -> 8）
--   #3 统一全部菜单图标（语义贴切、风格统一，消除 Grid 占位与无意义重复）
--      §2 menu.icon 批量订正（后台 8 项；前台 4 项可见 [id=14 提交问题已软删] 已全部贴切，无需改动）
--   #5 清理两行历史残留菜单墓碑行（均已是 deleted=1 软删行，线上不渲染）
--      §3 物理删除 id=7（/admin/settings）与 id=18（/admin/modules）
--   §4 执行结果自检（仅 SELECT）
--
-- 本次为纯数据订正脚本：不改表结构、不动业务数据，仅订正 menu 表的展示层字段。
--
-- 【用户可见性说明】以 24 号库 menu 全表实机核查为准：
--   #1（基础设施排序）与 #3（图标统一）是本次唯二有界面可见变化的改动；
--   #5 删除的 id=7 / id=18 均为 deleted=1 软删行，从未渲染到侧边栏，
--   部署后界面无任何可见变化（不存在「点击必 404」的活跃入口，请勿按此验收）。
--
-- 幂等性说明（全部语句可重复执行）：
--   §1 UPDATE 带 `sort` <> 8 守卫，已生效时影响 0 行。
--   §2 每条 UPDATE 带 (`icon` IS NULL OR `icon` <> '目标值') 守卫，已生效时影响 0 行。
--      用 IS NULL OR <> 而非单纯 <>：icon 为 NULL 时 `NULL <> 'X'` 结果为 NULL（不匹配），
--      会导致空图标行永远漏更新，故显式补 NULL 分支。
--   §3 DELETE ... WHERE id IN (...) 天然幂等（第二次执行影响 0 行）。
--   §4 仅 SELECT 自检，不改数据。
--
-- 执行环境：24 号机 MySQL8，issueflow_db 库
-- 依赖顺序：需在 V20260803_issueflow_phase8_wave3.sql 之后执行
--
-- 【WARN｜Phase6 图标白名单会回刷本 Wave 的新图标值】
--   V20260803_issueflow_phase6.sql §12.2（L151-159）有一条白名单式 icon 自愈：
--     UPDATE `menu` SET `icon`='Grid'
--     WHERE `deleted`=0 AND `icon` IS NOT NULL AND `icon`<>'' AND `icon` NOT IN (<白名单>);
--   本 Wave 的 4 个新取值 FolderOpened / Share / Files / SetUp 均不在该白名单内
--   （Menu / Monitor / Setting 在白名单内，不受影响）。
--
--   影响面：按正常版本序执行（Phase6 早于 W4）无任何风险，W4 是最后一次写入，不会被覆盖。
--           仅当「W4 执行后又单独重跑 Phase6」时，上述 4 个图标会被强制刷回 Grid。
--
--   为何本脚本不追加自愈语句：该风险场景中 Phase6 在 W4 之后执行，任何写在 W4 末尾的
--   语句都先于它运行，起不到防护作用（属无效加固），故刻意不加，避免虚增 DML。
--   §2 的 8 条 UPDATE 本身即幂等自愈：重跑本脚本即可把 8 个图标全部复原。
--
--   运维约定：任何时候单独重跑 Phase6，必须紧接着重跑一次本脚本（W4）。
--   根治方案：将 FolderOpened / Share / Files / SetUp 补进 Phase6 §12.2 的白名单，
--             需改动 Phase6 脚本，超出本次「仅文档订正、零 DML 变更」的范围，
--             已作为遗留项上报，留待后续 Wave 处理。
--
-- 【执行前建议】保留待删行快照，便于回滚（DELETE 不可逆）：
--   SELECT * FROM `menu` WHERE `id` IN (7, 18);
-- ===========================================================================
SET NAMES utf8mb4;


-- ---------------------------------------------------------------------------
-- 1. 「基础设施」排到「系统管理」下方（需求 #1）
--
--    现状：基础设施(id=25) parent_id=0 sort=6；系统管理(id=5) parent_id=0 sort=7
--          -> 二者已是根级兄弟（本就未嵌套），但基础设施排在系统管理「之前」。
--    目标：保持 parent_id=0 不变（不做任何父子挂载），仅把 sort 调大到 8，
--          使其紧邻排在系统管理之后。
--
--    取值 8 的理由：可见根级节点（deleted=0）的 sort 占用为
--      1 概览 / 2 业务管理 / 3 问题类型 / 5 流程管理 / 7 系统管理，
--    8 是「大于系统管理(7) 且不与任何现存根节点冲突」的最小值，
--    既满足「位于系统管理下方」，又不越过任何业务菜单。
--
--    注：id=7（系统设置，sort=7）与 id=17（项目管理，sort=4）虽为根级行，
--    但均已是 deleted=1 软删行，不参与侧边栏渲染，故不占用可见排序位。
-- ---------------------------------------------------------------------------
UPDATE `menu`
SET `sort` = 8, `updated_at` = NOW()
WHERE `id` = 25
  AND `sort` <> 8;


-- ---------------------------------------------------------------------------
-- 2. 统一菜单图标（需求 #3）
--
--    约定：icon 存 Element Plus 图标组件名（PascalCase）。前端 main.js 已全量
--    全局注册 @element-plus/icons-vue，SideMenu.vue 的 resolveIcon() 对不存在
--    的名字兜底为 Grid。下列取值均已核对为 @element-plus/icons-vue 真实导出名。
--
--    本节只列「与现状不同」的行，避免无效 UPDATE：
--      id=17 项目管理  Management -> FolderOpened
--            （id=17 为软删行 deleted=1、不渲染，改图标属无害的留存操作；
--             可见菜单中 Management 本就唯一，即 id=2 业务管理，
--             原注释「与 id=2 撞名」不成立。保留本次更新是为了让墓碑行日后
--             一旦被恢复也直接带上正确图标，且 UPDATE 带守卫，无副作用。）
--      id=16 流程管理  Operation  -> Share         （原与 id=29 配置管理 撞名）
--      id=10 菜单管理  Grid       -> Menu          （消除 Grid 占位，语义精确命中）
--      id=22 系统设置  Monitor    -> Setting       （Monitor 语义为监控，与设置页不符）
--      id=6  流程配置  Tools      -> Share         （原与 id=25 基础设施 撞名；改为呼应父级流程管理）
--      id=4  流程监控  Switch     -> Monitor       （Monitor 语义精确命中「监控」）
--      id=26 文件管理  Folder     -> Files         （原与 id=3 项目配置 撞名）
--      id=29 配置管理  Operation  -> SetUp         （Operation 语义空泛；SetUp 表参数调节，
--                                                    且与 Setting 齿轮区分，避免设置类图标堆叠）
--
--    保持不变（现状已贴切，不产生 UPDATE）：
--      后台：1 DataLine / 2 Management / 21 CollectionTag / 25 Tools / 5 Setting /
--            23 Tickets / 3 Folder / 24 Notebook / 8 User / 9 OfficeBuilding /
--            11 UserFilled / 19 Setting / 30 Odometer / 31 Timer / 27 Setting / 28 Document
--      前台：12 HomeFilled 工作台 / 20 Tickets 问题管理 / 15 DataLine 个人看板 /
--            13 Document 我的问题                （以上 4 项为前台可见节点 deleted=0）
--            14 EditPen 提交问题：deleted=1 软删行，不渲染，图标同样保持不变
--
--    说明：id=2 业务管理 保留 Management——在可见菜单（deleted=0）范围内
--    Management 本就唯一（id=17 项目管理为软删行，不渲染），语义上也正是
--    「业务管理」的最佳匹配，因此无需改动
--    （比改成 Menu 更优：Menu 让位给 id=10 菜单管理，避免新的撞名）。
--
--    仍存在的同名图标均为「父子/同族呼应」，非无意义重复：
--      Share  : 16 流程管理 / 6 流程配置        （父子呼应）
--      Setting: 5 系统管理 / 22 系统设置 / 19 备份设置 / 27 文件配置
--               （设置语义族，分属不同子树，符合后台通用惯例）
-- ---------------------------------------------------------------------------

-- 2.1 根级菜单
UPDATE `menu`
SET `icon` = 'FolderOpened', `updated_at` = NOW()
WHERE `id` = 17
  AND (`icon` IS NULL OR `icon` <> 'FolderOpened');

UPDATE `menu`
SET `icon` = 'Share', `updated_at` = NOW()
WHERE `id` = 16
  AND (`icon` IS NULL OR `icon` <> 'Share');

-- 2.2 「系统管理」子菜单
UPDATE `menu`
SET `icon` = 'Menu', `updated_at` = NOW()
WHERE `id` = 10
  AND (`icon` IS NULL OR `icon` <> 'Menu');

UPDATE `menu`
SET `icon` = 'Setting', `updated_at` = NOW()
WHERE `id` = 22
  AND (`icon` IS NULL OR `icon` <> 'Setting');

-- 2.3 「流程管理」子菜单
UPDATE `menu`
SET `icon` = 'Share', `updated_at` = NOW()
WHERE `id` = 6
  AND (`icon` IS NULL OR `icon` <> 'Share');

UPDATE `menu`
SET `icon` = 'Monitor', `updated_at` = NOW()
WHERE `id` = 4
  AND (`icon` IS NULL OR `icon` <> 'Monitor');

-- 2.4 「基础设施」子菜单
UPDATE `menu`
SET `icon` = 'Files', `updated_at` = NOW()
WHERE `id` = 26
  AND (`icon` IS NULL OR `icon` <> 'Files');

UPDATE `menu`
SET `icon` = 'SetUp', `updated_at` = NOW()
WHERE `id` = 29
  AND (`icon` IS NULL OR `icon` <> 'SetUp');


-- ---------------------------------------------------------------------------
-- 3. 物理清理两行历史残留墓碑记录（需求 #5）
--
--    实机核查（24 号库 menu 全表）结论：id=7 与 id=18 实际均为 deleted=1，
--    二者都不会渲染到侧边栏。本节是纯粹的历史残留清理，不是活跃故障修复，
--    执行后界面无任何用户可见变化。
--
--    id=7  「系统设置」path=/admin/settings  icon=Brush  parent_id=0 sort=7
--          Phase2 遗留的根级入口，routes.js 无对应路由。已被两次软删：
--            V20260730_issueflow_phase3.sql L43
--            V20260803_issueflow_phase6.sql L161（§12.3）
--          id=7 实际 deleted=1，线上不存在这个 404 入口，从未渲染到侧边栏；
--          本次为物理清理残留墓碑，非活跃故障入口。
--          （真实设置页另有菜单指向，与本次删除无关：
--             /admin/system/site     -> id=22「系统设置」（站点基础配置）
--             /admin/system/settings -> id=19「备份设置」（数据初始化 / 维护））
--
--    id=18 「模块配置」path=/admin/modules  parent_id=2 sort=3
--          Wave1（#8）已下线 ModuleManage.vue 与 /admin/modules 路由，并已
--          软删除该菜单（V20260801_issueflow_phase8_wave1.sql §3 置 deleted=1），
--          实际 deleted=1，同样不渲染；本次做物理清理，去掉残留脏行。
--
--    安全性：
--      - 无 role_menu 之类的菜单关联表（菜单可见性由 menu.permission + 用户权限码驱动），
--        删除后不会产生孤儿关联行；软删行的删除无任何副作用。
--      - 两条记录均无子节点（无 parent_id IN (7,18) 的行），不会造成子树悬挂；
--        §4 自检会显式校验这一点。
--      - DELETE 刻意不加 `AND deleted = 0`：id=7 与 id=18 均已是 deleted=1，
--        否则这两行（尤其 id=18）永远清不掉。
-- ---------------------------------------------------------------------------
DELETE FROM `menu`
WHERE `id` IN (7, 18);


-- ---------------------------------------------------------------------------
-- 4. 执行结果自检（仅查询，不改数据）
-- ---------------------------------------------------------------------------

-- 4.1 汇总校验，预期：
--     stale_entry_rows      = 0   残留墓碑行 id=7 / id=18 已物理清理
--     orphaned_child_rows   = 0   无子节点悬挂在已删父级下
--     infra_sort            = 8   基础设施 sort
--     system_sort           = 7   系统管理 sort（未改动）
--     infra_below_system    = 1   基础设施确实排在系统管理下方
--     infra_parent_id       = 0   基础设施仍为根级（与系统管理平级，未嵌套）
--     placeholder_icon_rows = 0   无空图标 / 无 Grid 占位
SELECT
  (SELECT COUNT(*) FROM `menu` WHERE `id` IN (7, 18))                      AS stale_entry_rows,
  (SELECT COUNT(*) FROM `menu` WHERE `parent_id` IN (7, 18))               AS orphaned_child_rows,
  (SELECT `sort` FROM `menu` WHERE `id` = 25)                              AS infra_sort,
  (SELECT `sort` FROM `menu` WHERE `id` = 5)                               AS system_sort,
  (SELECT CASE WHEN (SELECT `sort` FROM `menu` WHERE `id` = 25)
                  > (SELECT `sort` FROM `menu` WHERE `id` = 5)
               THEN 1 ELSE 0 END)                                          AS infra_below_system,
  (SELECT `parent_id` FROM `menu` WHERE `id` = 25)                         AS infra_parent_id,
  (SELECT COUNT(*) FROM `menu`
    WHERE `deleted` = 0
      AND (`icon` IS NULL OR `icon` = '' OR `icon` = 'Grid'))              AS placeholder_icon_rows;

-- 4.2 后台菜单树全量核对（type=2），按父级 + 排序展示
SELECT `id`, `parent_id`, `sort`, `icon`, `name`, `path`
FROM `menu`
WHERE `type` = 2 AND `deleted` = 0
ORDER BY `parent_id`, `sort`, `id`;

-- 4.3 前台菜单树全量核对（type=1），本次未改动，用于回归比对
SELECT `id`, `parent_id`, `sort`, `icon`, `name`, `path`
FROM `menu`
WHERE `type` = 1 AND `deleted` = 0
ORDER BY `parent_id`, `sort`, `id`;

-- 4.4 图标复用情况核对：确认剩余重复均为「父子/同族呼应」，无 Grid 占位
SELECT `icon`, COUNT(*) AS used_count, GROUP_CONCAT(`name` ORDER BY `id`) AS used_by
FROM `menu`
WHERE `deleted` = 0
GROUP BY `icon`
HAVING COUNT(*) > 1
ORDER BY used_count DESC, `icon`;


-- ===========================================================================
-- 回滚参考（如需）：
--
--   -- #1 基础设施排序还原
--   UPDATE `menu` SET `sort` = 6 WHERE `id` = 25;
--
--   -- #3 图标还原
--   UPDATE `menu` SET `icon` = 'Management' WHERE `id` = 17;
--   UPDATE `menu` SET `icon` = 'Operation'  WHERE `id` = 16;
--   UPDATE `menu` SET `icon` = 'Grid'       WHERE `id` = 10;
--   UPDATE `menu` SET `icon` = 'Monitor'    WHERE `id` = 22;
--   UPDATE `menu` SET `icon` = 'Tools'      WHERE `id` = 6;
--   UPDATE `menu` SET `icon` = 'Switch'     WHERE `id` = 4;
--   UPDATE `menu` SET `icon` = 'Folder'     WHERE `id` = 26;
--   UPDATE `menu` SET `icon` = 'Operation'  WHERE `id` = 29;
--
--   -- #5 墓碑行还原（DELETE 不可逆，需按执行前快照补回；
--   --    permission 列取值以快照为准，下列仅为结构参考。
--   --    注意：两行原本就是 deleted=1 软删行，还原后依旧不渲染、界面无变化，
--   --    仅用于紧急回退比对）
--   -- INSERT INTO `menu` (`id`,`name`,`path`,`parent_id`,`sort`,`permission`,`icon`,`type`,`deleted`)
--   -- VALUES (7, '系统设置','/admin/settings',0,7,<snapshot>,'Brush',2,1),
--   --        (18,'模块配置','/admin/modules', 2,3,<snapshot>,'Grid', 2,1);
-- ===========================================================================
