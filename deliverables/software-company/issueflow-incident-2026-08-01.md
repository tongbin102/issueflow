# issueFlow 生产事故报告：issueflow_db 整库丢失

| 项目 | 内容 |
|------|------|
| 事故等级 | P0（线上服务完全不可用 + 数据永久丢失） |
| 发生时间 | 2026-08-01 11:13（北京时间） |
| 发现时间 | 2026-08-01 11:25 |
| 恢复时间 | 2026-08-01 11:48（服务可用） |
| 加固完成 | 2026-08-01 12:10 |
| 服务中断时长 | 约 35 分钟 |
| 数据损失 | **7/30 上线至 8/1 的全部业务数据，不可恢复** |
| 关联提交 | `3a7f472` |

---

## 一、事故发现经过

用户告知「24 号 MySQL root 密码变为新值」。在验证新密码时，发现 `SHOW DATABASES` 结果中 **`issueflow_db` 已不存在**，随即展开排查。

若未做这次顺带核验，故障可能持续更久——因为 23 号 `issueflow-backend` 容器状态显示为 `Up 8 hours`，**从容器状态完全看不出异常**，只有日志里在持续刷 `Access denied`。

## 二、影响面

| 对象 | 状态 |
|------|------|
| `issueflow_db` 数据库 | 整库消失 |
| `issueflow` MySQL 账号 | 消失（现存仅 quiz_app / root / weekly） |
| 23 号 `issueflow-backend` | 容器 Up，但 HikariCP 无法建连，累计 20 次 `Access denied for user 'issueflow'@'10.55.3.23'` |
| 用户前台 / 管理后台 | 页面可打开，但所有数据接口失败 |
| `quiz_test`（ss-assess） | ✅ 未受影响 |
| `weekly_report` | ✅ 未受影响 |

## 三、根因分析

24 号 MySQL 容器 `mysql-gihtg` 于 `2026-08-01T03:13:31Z`（UTC）= 北京时间 11:13 **被重新创建**，bind 挂载目录 `/home/jsadmin/mysql-gihtg` 全新初始化——目录内所有系统文件（`auto.cnf`、`binlog.000001`、`ibdata1`）时间戳均为 11:13。

同期在 24 号落地了 `/home/jsadmin/domainhub-deploy-db/`（11:15），内含 `99-reset-DANGER.sh` 与 `setup-mysql-24.sh`。

**根因**：24 号 MySQL 是**多项目共用实例**，而 domainHub 的部署脚本作用域覆盖整个实例。执行重置后，运维方按记忆恢复了 `quiz_test` 与 `weekly_report`，但**遗漏了 issueflow_db**（该库上线时间晚，不在既有备份清单内）。root 密码也在此过程中被一并重置。

**放大因素**：issueFlow 从未纳入任何备份机制。`/home/jsadmin/db-backups/` 下的两份备份（7/30 10:19 与 10:31）产生于 issueFlow 上线（7/30 下午）**之前**，`grep -ci issueflow` 命中数为 **0**。

## 四、数据可恢复性判定：不可恢复

已穷尽以下渠道，全部落空：

| 渠道 | 结论 |
|------|------|
| `pre-reinstall-20260730-101904.sql.gz`（895KB） | 仅含 quiz_test + weekly_report |
| `business-only-20260730-103128.sql.gz` | 同上 |
| 24 号旧数据目录残留 | 无 `.bak` 类目录，原地清空重建 |
| Docker volume | `docker volume ls` 为空 |
| 旧实例 binlog | 随数据目录一并销毁（新 binlog 时间戳 11:13） |
| 23 号全盘搜索 | 无任何 issueflow dump |

## 五、恢复处置

### 5.1 关键发现：仓库 14 个 SQL 不足以重建

`scripts/` 下 14 个迁移脚本**没有任何一个创建基础表**。基线表（user / role / issue / sys_config / tag / issue_attachment / issue_history）位于 `src/backend/src/main/resources/db/schema.sql` + `data.sql`，由 Spring `sql.init` 在应用启动时加载。仅跑 14 个脚本，第一条 `ALTER TABLE issue` 就会失败。实际恢复序列为 **16 个脚本**。

### 5.2 关键发现：文件名字母序 ≠ 执行顺序

`V20260731_issueflow_phase7.sql` 文件名日期最早，但其 `ADD source AFTER type_id` **强依赖 phase6 建的 `issue.type_id`**。按字母序执行会报 `Unknown column 'type_id'`。

正确顺序（已固化为 `scripts/restore-run-order.sh`）：

```
schema.sql → data.sql → migrate-add-updated-at → p0 → phase2 → phase3
→ phase4 → phase5 → phase6 → V20260803b_fix_issuetype_unique → phase7
→ wave1 → wave2 → wave3 → wave4 → V20260805_phase6_whitelist_fix
```

### 5.3 关键发现：中文双重编码污染

首轮灌库全绿后，通过 HEX 抽查发现角色名存成 `C3A7C2AEC2A1...`（双重编码），正确值应为 `E7AEA1E79086E59198`。

**原因**：容器内 mysql 客户端 `character_set_client = latin1`，而 16 个脚本中有 7 个缺少 `SET NAMES utf8mb4`。若放过，线上角色下拉框与菜单将全是乱码。

**处置**：因当时库内零业务数据，采取全量重建并强制 `--default-character-set=utf8mb4`，事后修复仓库 7 个 SQL。

### 5.4 恢复结果

| 校验项 | 结果 |
|--------|------|
| `issueflow_db` 表数 | 26 |
| menu / permission / user / issue | 29 / 59 / 7 / 13 |
| 中文编码 | 提交者 / 开发人员 / 测试人员 / 管理员 ✅ |
| 前端 `http://10.55.3.23:18081` | HTTP 200 |
| `POST /api/auth/login`（admin/admin123） | HTTP 200，返回 JWT |
| 无 token `/api/menus/sidebar?type=2` | HTTP 401（非 500，证明 DB 链路正常） |
| quiz_test / weekly_report | 14 表 / 10 表，md5 未变 ✅ |

> 菜单硬编码 ID（wave4 的 id=25、id=17/16/6/26/29）全部命中，说明重建后 AUTO_INCREMENT 序列与原始部署完全一致，反证执行顺序推导无误。

## 六、加固措施（已上线）

| 措施 | 说明 |
|------|------|
| 每日自动备份 | `scripts/backup-issueflow-db.sh`，24 号 cron `0 2 * * *`，保留 7 天 |
| 密码安全 | 走 `/home/jsadmin/.issueflow-backup.env`(600)，不入 git、不进 `ps`（实测抓 60 次命中 0） |
| 防误删 | 四道闸：严格正则 + 只匹配 `issueflow_db-*` + 按文件名取时间 + 无条件保留最新 1 份 |
| 产物校验 | 非空 → ≥10KB → `gzip -t` → 含 `CREATE TABLE`，先写 `.partial` 再 `mv` |
| 恢复顺序固化 | `scripts/restore-run-order.sh` |
| 编码缺陷修复 | 仓库 16 个 SQL 现 100% 覆盖 `SET NAMES utf8mb4` |
| 文档 | README 新增 4.11「数据库备份与恢复」，含顺序表与字母序警告 |

首份备份已产出：`issueflow_db-20260801-120328.sql.gz`（12210 字节，gzip 校验通过，含 26 条 CREATE TABLE）。

## 七、遗留风险

1. **根因未消除（高）**：domainHub 的 reset 脚本作用域仍覆盖整个共用实例。备份只是止损，无法阻止再次被重置。**建议推动 domainHub 侧收敛脚本作用域，或将 issueFlow 迁移至独立 MySQL 实例。**
2. **备份未做异地**：备份与数据库在同一台机器（24 号）。若整机故障，备份一并丢失。建议增加异地/跨机同步。
3. **无监控告警**：本次容器状态显示 `Up` 但服务实际不可用，纯靠人工偶然发现。建议增加数据库连通性健康检查与告警。
4. **其他项目异常（非本次导致）**：23 号 `weekly-frontend` 处于 `Restarting`、`weekly-backend` 已 `Exited(127)`，早于本次事故，属独立问题。

## 八、经验教训

1. 共用数据库实例上的任何重置操作，必须先对**全部**库做 dump，而非凭记忆列举。
2. 新项目上线时应同步纳入备份清单——本次正是因为 issueFlow 上线晚于最后一次备份而成为盲区。
3. 容器 `Up` 状态不等于服务健康，健康检查必须探到数据库连通性。
4. 迁移脚本的**依赖顺序必须显式固化**，不能依赖文件名排序这种隐式约定。
5. SQL 脚本应强制声明字符集，不能依赖客户端默认值。
