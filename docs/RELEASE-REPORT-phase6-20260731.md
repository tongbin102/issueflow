# issueFlow Phase 6 变更发布报告

- **发布日期**：2026-07-31
- **发布分支**：main（GitHub: tongbin102/issueflow）
- **发布环境**：23 号应用服务器（10.55.3.23）+ 24 号数据库服务器（10.55.3.24）
- **发布状态**：✅ 成功，含两处部署后热修复，冒烟全通过

---

## 一、代码变更

| Commit | 说明 | 变更规模 |
|--------|------|---------|
| `641a7cd` | feat: Phase6 十项需求全量实现（提交弹窗分区化/问题管理菜单/问题类型管理/右滑面板统一/全站 i18n/后台导航优化/前台多主题/网站设置） | 112 文件，+8058 / −636 |
| `a48a1c1` | fix(sql): V20260803 迁移脚本 sys_config 无 created_at 列修复 | 1 文件 |
| `9f9d541` | fix(issue): 问题编号生成基于最大序号，修复软删后序号回退撞唯一索引导致创建 500 | 5 文件，+31 / −18 |
| （本次补丁） | fix(db): 修复 issue_type 软删撞复合唯一索引导致删除 500（生成列方案），同步 ARCH 文档 | 2 SQL + 1 doc |

### Phase6 功能清单（对应用户 10 条需求）

1. 前台导航移除「提交问题」菜单项
2. 「我的问题」→「提交新问题」弹窗化：右上角纯图标全屏按钮、分区折叠布局（基本信息默认展开，补充材料默认折叠）
3. 前台新增「问题管理」父菜单，「我的问题」为其子菜单
4. 后台新增「问题类型」管理（增删改查+启停），Issue 新增类型字段，全链路（表单/列表/筛选/详情）贯通
5. 全部弹窗（不含提示框）统一为右侧滑出面板（FormDrawer）
6. 全站多语言（vue-i18n@9，zh-CN / en-US 各 21 模块资源文件）
7. 后台导航栏撑满全屏，「返回前台」固定底部
8. 模块配置菜单补图标（含全量菜单图标兜底）
9. 前台 4 种主题切换（light / dark / blue / green，CSS 变量隔离）
10. 「系统管理 → 网站设置」：网站名称、前台默认主题等 7 项参数（sys_config site.* 键）

### 热修复（部署冒烟发现的存量 Bug，本次一并修复）

**问题编号软删回退 Bug**（Phase1~5 遗留，冒烟暴露）：
- 现象：同日「建问题 → 删问题 → 再建问题」必 500（`Duplicate entry 'IS-YYYYMMDD-0001' for uk_issue_no`）
- 根因：`IssueMapper.countByIssueNoPrefix` 用 `COUNT(*) WHERE deleted=0` 推序号，软删行仍占唯一索引导致序号回退；且插入冲突重试取到相同编号，重试无效
- 修复：改为 `MAX(序号)` 且不过滤 deleted（`maxSeqByIssueNoPrefix`）；插入冲突循环重试最多 3 次（每次重新取号），3 次失败抛受控业务异常
- 涉及文件：`IssueMapper.java`、`IssueNoGenerator.java`、`IssueService.java` + 2 处 README 同步

**问题类型删除 500 Bug**（部署冒烟第 2 轮暴露，本次补丁修复）：
- 现象：`DELETE /api/issue-types/{id}` 返回 500（`Duplicate entry 'SMOKE_TMP2-1' for key 'issue_type.uk_issue_type_code'`）
- 根因：`issue_type.uk_issue_type_code` 为复合唯一索引 `(code, deleted)`；MyBatis-Plus 逻辑删除把 `deleteById` 翻译成 `UPDATE SET deleted=1`，索引元组由 `(code,0)` 变 `(code,1)`，与既有软删墓碑 `(code,1)` 撞键
- 修复：改为**生成列方案**——新增 `code_active = IF(deleted=0, code, NULL) VIRTUAL` + `UNIQUE KEY uk_issue_type_code (code_active)`。唯一索引忽略 NULL，故「同一 code 可有多条墓碑 + 至多一条存活行」，语义与 Java `assertCodeUnique()`（仅校验 deleted=0）逐字对齐
- ⚠️ 否决方案：`(code, deleted)` 复合（原设计，即本次故障源，ARCH 文档曾错误推荐，已划掉）；单列 `(code)` 会把 500 从 delete 迁到「软删后同名 code 新建」，且需改 Java 才兜得住
- 涉及文件：`scripts/V20260803_issueflow_phase6.sql`（改建表段）、`scripts/V20260803b_fix_issuetype_unique.sql`（新增，增量修复已部署库，幂等）、`docs/ARCH_phase6.md`（改索引设计 + 划掉错误方案）

---

## 二、数据/结构变更（24 号 MySQL：issueflow_db）

脚本：`scripts/V20260803_issueflow_phase6.sql`（幂等，已执行成功）、`scripts/V20260803b_fix_issuetype_unique.sql`（本次补丁增量修复，幂等，已执行成功）

| 类别 | 内容 |
|------|------|
| 新表 | `issue_type`（问题类型），含 6 条种子：缺陷/新功能/性能问题/界面样式/咨询/其他 |
| 表结构 | `issue` 新增 `type_id` 列，存量数据回填「其他」（回填后 type_id 无空值） |
| 菜单 | 新增后台「问题类型」「网站设置」及前台「问题管理」父菜单；模块配置图标 Tree→Grid + 全量菜单图标兜底；清理僵尸菜单 /admin/settings |
| 权限 | 新增 5 个权限码（issue:type:* 增删改查 + site:config:update），已挂 ADMIN 角色 |
| 配置 | `sys_config` 新增 site.* 七键（name/short_name/subtitle/default_theme/default_locale/copyright/icp） |

执行注意：脚本修正过一次 —— `sys_config` 表无 `created_at` 列，INSERT 改为仅 `updated_at`（commit `a48a1c1`）。

---

## 三、配置/依赖变更

| 项 | 变更 |
|----|------|
| 前端依赖 | 新增 `vue-i18n@^9.13.1`（本机 npm 沙箱限制，经 /tmp 安装 + cp -rn 注入 node_modules，构建通过） |
| 静态资源 | 新增 `locales/`（42 个语言文件）、`styles/themes.css`（4 主题变量包） |
| 部署配置 | 无端口/环境变量变更（沿用 18081/18082，MYSQL_HOST=10.55.3.24，REDIS_HOST=10.55.3.23） |

---

## 四、部署动作记录

1. 本地源码 scp → 23 号 `/opt/issueflow`（github.com 在 23 号被封，不可 git clone）
2. 24 号灌 `V20260803_issueflow_phase6.sql`（先于后端重启，StateMachine @PostConstruct 依赖）
3. 23 号 `docker compose -f docker-compose.23.yml up -d --build` 重建前后端容器
4. 热修复（9f9d541）：3 个 Java 文件经 /tmp 中转上传（/opt/issueflow 需 sudo），`--no-deps backend` 定向重建
   - ⚠️ 踩坑记录：直接用根目录 `docker-compose.yml` 重建会丢 MYSQL_HOST 环境变量导致崩溃循环，**必须用 `docker-compose.23.yml`**（deploy-23.sh 生成，env 已固化）
5. 补丁（生成列方案）：本地 `V20260803b_fix_issuetype_unique.sql` 经管道灌入 24 号 `mysql-gihtg`（纯 DDL/DML，后端无需重启）；校验索引变为 `uk_issue_type_code(code_active)`、残留墓碑清零、6 种子完好

---

## 五、冒烟验证结论

| 用例 | 结果 |
|------|------|
| 前台 / 管理后台页面 200 | ✅ |
| 网站公开配置 GET /api/site/config 返回 7 键 | ✅ |
| 后台侧栏含问题类型/网站设置/问题管理/模块配置图标 | ✅ |
| 创建问题（typeId=1）→ typeName「缺陷」回显、列表按类型过滤 | ✅ |
| typeId 缺失 → 400「问题类型不能为空」 | ✅ |
| 问题类型增删改查、options 仅返启用项 | ✅ |
| 类型停用 → 表单不可选（40063 业务拒绝），列表仍可见 | ✅ |
| 删除被引用类型 → 业务阻断 | ✅ |
| **回归：同日建→删→再建，编号 0002→0003→0004 递增不复用、零 500** | ✅ |
| **回归（判别性）：问题类型新建→停用→删除，连跑两遍全绿（编号 0011→0013 递增、删除不再 500）** | ✅ |

回归脚本：`tests/smoke-issueno-regression.sh`（可在 23 号直接执行复测）

---

## 六、访问地址

- **用户前台**：http://10.55.3.23:18081 （入口路由 `/user`，提交者/开发/测试/管理员可见）
- **管理后台**：http://10.55.3.23:18081 （入口路由 `/admin`，仅 ADMIN）
- **API**：http://10.55.3.23:18082/api

## 七、已知遗留问题

- Knife4j `/v3/api-docs` 未挂载，Swagger UI 暂不可用（不影响核心 REST）
- 单 JWT 无 refreshToken；附件无病毒扫描/扩展名白名单（历史遗留，非本期范围）
