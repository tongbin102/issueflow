#!/usr/bin/env bash
# ===========================================================================
# issueFlow 事故恢复 —— 按推导出的 phase 逻辑顺序灌库
#
# 【背景】24 号 MySQL 容器 mysql-gihtg 于 2026-08-01 11:13 被重建，
#         issueflow_db 整库丢失且无可用备份，经授权从零重建结构 + 种子。
#
# 【关键点】scripts/ 下文件名日期前缀与 phase 逻辑序不一致，
#         照字母序执行会因「表不存在 / 字段缺失」失败。本脚本固化正确顺序。
#
# 【安全约束】所有 SQL 一律通过 `mysql ... issueflow_db` 指定库执行，
#         绝不触碰 quiz_test / weekly_report / mysql / sys 等其他库。
#
# 【失败即停】任一脚本非 0 退出立即中断，不盲目继续。
#
# 【⚠ 字符集硬约束 —— 血泪教训，勿删】
#   mysql-gihtg 容器内 mysql 客户端 default-character-set=auto，实测解析为 latin1。
#   脚本里的中文字面量（角色名、菜单名、配置描述、列注释）会被按 latin1
#   解释后再转存 utf8mb4，产生「双重编码」乱码：
#     '管理员' 正确应为 E7AEA1E79086E59198，实测被写成 C3A7C2AEC2A1...
#
#   【2026-08-01 已修复】当时有 7 个 SQL 未声明 `SET NAMES utf8mb4`：
#     schema.sql / data.sql / migrate-add-updated-at.sql /
#     phase8_wave1 / wave2 / wave3 / wave4
#   现已逐个补齐，仓库 19 个 SQL 全部覆盖（详见 CHANGELOG 2026-08-01；
#   2026-08-01 新增 Phase9 的 V20260806_dynamic_field.sql，已含 SET NAMES utf8mb4；
#   2026-08-06 新增补丁 V20260806b_fieldconfig_permission.sql，已含 SET NAMES utf8mb4；
#   2026-08-06 新增补丁 V20260806c_fieldconfig_grant_developer.sql，已含 SET NAMES utf8mb4；
#   2026-08-06 新增补丁 V20260806d_fix_project_ref_order.sql，已含 SET NAMES utf8mb4；
#   2026-08-03 新增 Phase10 的 V20260803_data_management.sql，已含 SET NAMES utf8mb4）。
#
#   即便如此，本脚本**仍然保留**对每次 mysql 调用强制
#   --default-character-set=utf8mb4 —— 作为客户端层的第二道防线，
#   兜住「将来新增脚本又忘了写 SET NAMES」的情况。勿以「SQL 里已有」为由删除。
# ===========================================================================
set -uo pipefail

CONTAINER="mysql-gihtg"
DB="issueflow_db"
ROOT_PASS="${MYSQL_ROOT_PASS:?需通过环境变量 MYSQL_ROOT_PASS 提供 root 密码}"
DIR="/home/jsadmin/issueflow-restore"
LOG="$DIR/restore.log"

# 推导出的执行顺序（理由见交付报告）
FILES=(
  "schema.sql"                                  # Phase1 基线建表（role/user/user_role/issue/issue_attachment/issue_history/tag/sys_config）
  "data.sql"                                    # Phase1 种子：4 条角色字典
  "migrate-add-updated-at.sql"                  # 补 updated_at（基线已含，此处幂等空跑；须在基线表建好之后）
  "V20250730_issueflow_p0.sql"                  # P0：project/organization/menu 建表 + issue.project_id
  "V20250801_issueflow_phase2.sql"              # Phase2：issue_relation/permission/role_permission + menu.type + 全量菜单/权限/配置种子
  "V20260730_issueflow_phase3.sql"              # Phase3：project.leader_id/member_ids + 菜单调整（依赖 phase2 的 menu.type）
  "V20260801_issueflow_phase4.sql"              # Phase4：module/module_dependency + issue.module_id + 流程管理菜单
  "V20260802_issueflow_phase5.sql"              # Phase5：flow_node/flow_transition + organization 扩列 + user.leader_id
  "V20260803_issueflow_phase6.sql"              # Phase6：issue_type + issue.type_id + 菜单/权限/site.* 配置
  "V20260803b_fix_issuetype_unique.sql"         # Phase6 补丁：issue_type 条件唯一索引（须紧跟 phase6）
  "V20260731_issueflow_phase7.sql"              # Phase7：dict/dict_item/login_log/file_*/scheduled_* + issue.source/priority（依赖 phase6 的 type_id）
  "V20260801_issueflow_phase8_wave1.sql"        # W1：site.default_password + 菜单改名 + 下线模块配置
  "V20260802_issueflow_phase8_wave2.sql"        # W2：user.org_id + issue.project_id 收紧 NOT NULL
  "V20260803_issueflow_phase8_wave3.sql"        # W3：user_role + user.roles + 存量回填
  "V20260804_issueflow_phase8_wave4.sql"        # W4：菜单 sort/icon 订正 + 清理墓碑行
  "V20260805_issueflow_phase6_whitelist_fix.sql" # W5：Phase6 图标白名单缺口补齐（须在 W4 之后）
  "V20260806_dynamic_field.sql"                 # Phase9：动态字段配置（field_section/field_config/issue_field_value/ref_source_registry
                                                #        + ISSUE_TYPE 字典化迁移 + issue.type_code + 菜单 field-configs）
                                                #        依赖 phase6 的 issue_type、phase7 的 dict/dict_item、W4/W5 的 menu 终态
  "V20260806b_fieldconfig_permission.sql"       # Phase9 补丁：补注册 field:config:list/save/delete 三个权限码 + 授 ADMIN。
                                                #        必须最后执行且紧跟上一条 —— dynamic_field 只插了 menu 记录、
                                                #        漏了 permission 注册，导致前端按 hasPerm('field:config:list')
                                                #        过滤后「字段配置」菜单不可见（2026-08-24 线上缺陷）。
                                                #        依赖序而非字母序：b 后缀是补丁标记，不可提前。
  "V20260806c_fieldconfig_grant_developer.sql"  # Phase9 补丁2：给 DEVELOPER(role_id=2) 授权 field:config:list（只读）。
                                                #        须在 b 补丁之后 —— 依赖 permission 表已注册 field:config:list 码。
  "V20260806d_fix_project_ref_order.sql"        # Phase9 补丁3：订正 ref_source_registry 中 PROJECT 的 order_field。
                                                #        dynamic_field 种子写成了 project 表并不存在的 sort 列，
                                                #        导致 GET /api/field-configs/ref-options?refSource=PROJECT 报
                                                #        Unknown column 'sort' in 'order clause'（500），
                                                #        前台「提交新问题」表单直接打不开（2026-08-06 线上缺陷）。
                                                #        依赖 #17 已建 ref_source_registry 并灌入 PROJECT 种子。
  "V20260803_data_management.sql"               # Phase10：数据管理（备份 / 恢复）。
                                                #        backup_record / restore_record 建表 +
                                                #        field_config 预埋 5 列（unique / regex_rule / visible_roles /
                                                #        readonly_scope / remark）+ sys_config 6 条 data.management.* 配置 +
                                                #        菜单「备份设置」→「数据管理」更名改路由、下线「数据维护」+
                                                #        注册 system:data:view|backup|download|delete|upload|restore|config
                                                #        七个权限码并授 ADMIN。
                                                #        ⚠ 须最后执行：§2 依赖 #17 的 field_config 表，
                                                #        §4.1 依赖 #12 W1 的菜单改名与 #15/#16 的 menu 终态。
                                                #        旧码 system:backup:export 保留兼容一版，本脚本不删。
)

echo "==================================================================" | tee "$LOG"
echo " issueFlow 灌库开始 $(date '+%F %T')  目标库: $DB" | tee -a "$LOG"
echo "==================================================================" | tee -a "$LOG"

# ---------------------------------------------------------------------------
# 可选：全量重建（REBUILD=1）。
# 硬编码 issueflow_db，绝不接受外部传入库名 —— 防止误 DROP 其他项目库。
# 该实例为多项目共用（quiz_test / weekly_report / domainHub 等），此处是红线。
# ---------------------------------------------------------------------------
if [ "${REBUILD:-0}" = "1" ]; then
  echo ">>> REBUILD=1：重建 issueflow_db（仅此一个库）" | tee -a "$LOG"
  sudo docker exec -i "$CONTAINER" \
    mysql -uroot -p"$ROOT_PASS" --default-character-set=utf8mb4 <<'REBUILD_SQL' 2>&1 \
    | grep -v 'Using a password on the command line interface can be insecure' | tee -a "$LOG"
DROP DATABASE IF EXISTS `issueflow_db`;
CREATE DATABASE `issueflow_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `issueflow_db`.* TO 'issueflow'@'%';
FLUSH PRIVILEGES;
REBUILD_SQL
  echo ">>> 重建完成，其他库未受影响" | tee -a "$LOG"
fi

idx=0
for f in "${FILES[@]}"; do
  idx=$((idx + 1))
  printf '\n[%02d/%02d] >>> %s\n' "$idx" "${#FILES[@]}" "$f" | tee -a "$LOG"

  if [ ! -f "$DIR/$f" ]; then
    echo "❌ 文件不存在: $DIR/$f —— 中止" | tee -a "$LOG"
    exit 1
  fi

  # 关键：① 显式指定 $DB，SQL 只在 issueflow_db 上下文内生效
  #       ② 强制 utf8mb4，兜住未声明 SET NAMES 的脚本，避免中文双重编码
  out="$(sudo docker exec -i "$CONTAINER" \
          mysql -uroot -p"$ROOT_PASS" --default-character-set=utf8mb4 \
          --show-warnings "$DB" < "$DIR/$f" 2>&1)"
  rc=$?

  # 过滤 mysql 客户端固有的密码告警，保留真实输出
  clean="$(echo "$out" | grep -v 'Using a password on the command line interface can be insecure')"

  if [ $rc -ne 0 ]; then
    echo "❌ 失败 (exit=$rc)" | tee -a "$LOG"
    echo "$clean" | tee -a "$LOG"
    echo "—— 已在第 $idx 个脚本处中止，后续脚本未执行 ——" | tee -a "$LOG"
    exit $rc
  fi

  # 即使 rc=0 也要显式排查 ERROR 关键字（防止客户端吞错）
  if echo "$clean" | grep -qiE '^ERROR |ERROR [0-9]+ \('; then
    echo "❌ 输出中检测到 ERROR，视为失败" | tee -a "$LOG"
    echo "$clean" | tee -a "$LOG"
    exit 1
  fi

  echo "✅ 成功 (exit=0)" | tee -a "$LOG"
  [ -n "$clean" ] && echo "$clean" | head -20 | sed 's/^/     | /' | tee -a "$LOG"
done

echo "" | tee -a "$LOG"
echo "==================================================================" | tee -a "$LOG"
echo " 全部 ${#FILES[@]} 个脚本执行完毕 $(date '+%F %T')" | tee -a "$LOG"
echo "==================================================================" | tee -a "$LOG"
