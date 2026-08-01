#!/usr/bin/env bash
# ===========================================================================
# issueFlow 每日自动备份 —— 只备 issueflow_db 一个库
#
# 【背景】2026-08-01 11:13，24 号 MySQL 容器 mysql-gihtg 被外部（domainHub 的
#         reset 脚本）重建，issueflow_db 整库丢失。而 /home/jsadmin/db-backups/
#         里只有 quiz_test / weekly_report 的备份，从未包含 issueflow_db，
#         导致 7/30 上线以来的业务数据永久丢失。本脚本即为杜绝此类事故而生。
#
# 【运行位置】24 号服务器（10.55.3.24），以 jsadmin 身份运行（sudo 免密）。
#
# 【⚠ 红线一：多项目共用实例】
#   mysql-gihtg 同时承载 quiz_test / weekly_report / mysql / sys 等库。
#   本脚本 mysqldump 只传 --databases issueflow_db，绝不使用 --all-databases。
#
# 【⚠ 红线二：清理旧备份只认 issueflow_db- 前缀】
#   /home/jsadmin/db-backups/ 下还躺着其他项目「仅存」的备份：
#     pre-reinstall-*.sql.gz / business-only-*.sql.gz / restore-users.sql
#   删掉就是二次事故。因此保留策略采用「白名单 + 严格文件名正则」双保险：
#     ① 只遍历 issueflow_db-*.sql.gz
#     ② 再用 ^issueflow_db-[0-9]{8}-[0-9]{6}\.sql\.gz$ 精确校验，不匹配一律跳过
#     ③ 时间取自「文件名」而非 mtime（mtime 会被 cp/rsync 改写，不可信）
#     ④ 永远至少保留最新的 1 份，即使它已超过保留期
#
# 【⚠ 红线三：禁止密码明文落地 / 禁止出现在 ps】
#   密码不写进本文件（本文件要进 git）。运行期通过 --defaults-extra-file
#   临时配置文件传给 mysqldump，用完即删（trap 兜底）。
#   命令行上只出现临时文件路径，`ps aux` 看不到密码。
#
# 【用法】
#   手工：  MYSQL_ROOT_PASSWORD='xxx' ./backup-issueflow-db.sh
#   cron ： 读取 /home/jsadmin/.issueflow-backup.env（chmod 600, 属主 jsadmin）
#   自检：  DRY_RUN=1 BACKUP_DIR=/tmp/xxx ./backup-issueflow-db.sh --prune-only
#
# 【退出码】0=成功；非 0=失败（cron 会把 stderr 邮件/日志留痕，且已写 backup.log）
# ===========================================================================
set -uo pipefail

# cron 的 PATH 极简，显式补全，避免 sudo/docker/gzip 找不到
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

# ---------------------------------------------------------------------------
# 可配置项（均可用环境变量覆盖，便于 dry-run 自检）
# ---------------------------------------------------------------------------
CONTAINER="${CONTAINER:-mysql-gihtg}"
DB="${DB:-issueflow_db}"
BACKUP_DIR="${BACKUP_DIR:-/home/jsadmin/db-backups}"
LOG_FILE="${LOG_FILE:-$BACKUP_DIR/backup.log}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
ENV_FILE="${ENV_FILE:-/home/jsadmin/.issueflow-backup.env}"
MIN_SIZE_BYTES="${MIN_SIZE_BYTES:-10240}"   # 小于 10KB 认定为异常产物
DRY_RUN="${DRY_RUN:-0}"                     # 1=只打印不删除/不备份
PRUNE_ONLY=0
[ "${1:-}" = "--prune-only" ] && PRUNE_ONLY=1

# 前缀写死为常量，保留策略的所有匹配都基于它，杜绝拼错误删
PREFIX="issueflow_db-"

TS="$(date '+%Y%m%d-%H%M%S')"
TARGET="$BACKUP_DIR/${PREFIX}${TS}.sql.gz"
TMP_OUT="$TARGET.partial"

# ---------------------------------------------------------------------------
# 日志：同时输出到 stdout 与 backup.log（追加，带时间戳）
# ---------------------------------------------------------------------------
log() {
  local level="$1"; shift
  local line
  line="$(date '+%F %T') [$level] $*"
  echo "$line"
  # 日志目录可能尚不存在（dry-run 场景），写不进去也不能让主流程崩
  [ -d "$(dirname "$LOG_FILE")" ] && echo "$line" >> "$LOG_FILE" 2>/dev/null
  return 0
}

die() {
  log ERROR "$*"
  log ERROR "备份失败，退出码 1"
  exit 1
}

# ---------------------------------------------------------------------------
# 清理：临时 cnf（宿主机 + 容器内）与半成品文件，任何退出路径都要执行
# ---------------------------------------------------------------------------
HOST_CNF=""
CNT_CNF=""
ERR_FILE=""
cleanup() {
  local rc=$?
  [ -n "$HOST_CNF" ] && [ -f "$HOST_CNF" ] && rm -f "$HOST_CNF"
  [ -n "$ERR_FILE" ] && [ -f "$ERR_FILE" ] && rm -f "$ERR_FILE"
  if [ -n "$CNT_CNF" ]; then
    sudo docker exec "$CONTAINER" rm -f "$CNT_CNF" >/dev/null 2>&1 || true
  fi
  # 半成品不能留在备份目录里冒充可用备份
  [ -f "$TMP_OUT" ] && rm -f "$TMP_OUT"
  return $rc
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------------------
# 保留策略：只清理 issueflow_db-YYYYMMDD-HHMMSS.sql.gz，按文件名时间判定
# ---------------------------------------------------------------------------
prune_old_backups() {
  local cutoff now kept=0 removed=0 skipped=0
  now="$(date '+%s')"
  cutoff=$((now - RETENTION_DAYS * 86400))

  log INFO "开始清理超过 ${RETENTION_DAYS} 天的旧备份（只匹配 ${PREFIX}* ）"

  # 先收集「合法命名」的备份，按文件名升序（= 时间升序）
  local candidates=()
  local f base
  for f in "$BACKUP_DIR/${PREFIX}"*.sql.gz; do
    [ -e "$f" ] || continue          # glob 无匹配时跳过字面量
    base="$(basename "$f")"

    # 第二道闸：严格正则。任何不完全符合命名规范的文件一律不删。
    if ! [[ "$base" =~ ^issueflow_db-[0-9]{8}-[0-9]{6}\.sql\.gz$ ]]; then
      log WARN  "  跳过（文件名不符合规范，不删）: $base"
      skipped=$((skipped + 1))
      continue
    fi
    candidates+=("$f")
  done

  if [ "${#candidates[@]}" -eq 0 ]; then
    log INFO "  无历史备份可清理"
    return 0
  fi

  # 按文件名排序，最后一个即最新的一份
  local sorted=()
  while IFS= read -r line; do sorted+=("$line"); done < <(printf '%s\n' "${candidates[@]}" | sort)
  local newest="${sorted[-1]}"

  local d t epoch
  for f in "${sorted[@]}"; do
    base="$(basename "$f")"

    # 第三道闸：最新的一份永远保留，避免「全过期」时清空备份目录
    if [ "$f" = "$newest" ]; then
      log INFO  "  保留（最新一份，无条件）: $base"
      kept=$((kept + 1))
      continue
    fi

    # 从文件名解析时间：issueflow_db-YYYYMMDD-HHMMSS.sql.gz
    d="${base:13:8}"
    t="${base:22:6}"
    epoch="$(date -d "${d:0:4}-${d:4:2}-${d:6:2} ${t:0:2}:${t:2:2}:${t:4:2}" '+%s' 2>/dev/null)"
    if [ -z "$epoch" ]; then
      log WARN  "  跳过（时间戳无法解析，不删）: $base"
      skipped=$((skipped + 1))
      continue
    fi

    if [ "$epoch" -lt "$cutoff" ]; then
      if [ "$DRY_RUN" = "1" ]; then
        log INFO  "  [DRY-RUN] 将删除: $base"
      else
        rm -f -- "$f" && log INFO "  已删除（超期）: $base" || log WARN "  删除失败: $base"
      fi
      removed=$((removed + 1))
    else
      log INFO  "  保留（未超期）: $base"
      kept=$((kept + 1))
    fi
  done

  log INFO "清理完成：删除 $removed 份，保留 $kept 份，跳过 $skipped 份"
  return 0
}

# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
main() {
  mkdir -p "$BACKUP_DIR" || die "无法创建备份目录: $BACKUP_DIR"

  # --prune-only：仅跑保留策略（用于 dry-run 自检），不连数据库
  if [ "$PRUNE_ONLY" = "1" ]; then
    log INFO "===== 保留策略自检模式（--prune-only, DRY_RUN=$DRY_RUN） ====="
    prune_old_backups
    log INFO "===== 自检结束 ====="
    exit 0
  fi

  log INFO "===== issueFlow 备份开始（库: $DB, 容器: $CONTAINER） ====="

  # --- 取密码：环境变量优先，其次受保护的 env 文件 ---
  if [ -z "${MYSQL_ROOT_PASSWORD:-}" ] && [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    . "$ENV_FILE"
  fi
  [ -n "${MYSQL_ROOT_PASSWORD:-}" ] || \
    die "未取到 root 密码：请设置环境变量 MYSQL_ROOT_PASSWORD，或提供 $ENV_FILE（chmod 600）"

  command -v sudo  >/dev/null 2>&1 || die "找不到 sudo"
  command -v gzip  >/dev/null 2>&1 || die "找不到 gzip"

  local running
  running="$(sudo docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null || true)"
  [ "$running" = "true" ] || die "容器 $CONTAINER 未在运行"

  # --- 构造临时 defaults 文件：密码只存在于 600 权限的临时文件里 ---
  HOST_CNF="$(mktemp /tmp/.ifbk-XXXXXXXX.cnf)" || die "mktemp 失败"
  chmod 600 "$HOST_CNF"
  {
    echo "[mysqldump]"
    echo "user=root"
    echo "password=\"${MYSQL_ROOT_PASSWORD}\""
  } > "$HOST_CNF"

  CNT_CNF="/tmp/$(basename "$HOST_CNF")"
  sudo docker cp "$HOST_CNF" "$CONTAINER:$CNT_CNF" >/dev/null 2>&1 \
    || die "无法将临时配置复制进容器"
  sudo docker exec "$CONTAINER" chmod 600 "$CNT_CNF" >/dev/null 2>&1 || true

  # --- 导出 ---
  # 注意：--defaults-extra-file 必须是 mysqldump 的第一个参数。
  #       只导 $DB 单库；--databases 会带上 CREATE DATABASE/USE，便于灾难恢复直灌。
  log INFO "执行 mysqldump -> $TARGET"
  ERR_FILE="$(mktemp /tmp/.ifbk-err-XXXXXXXX)" || die "mktemp 失败"

  # stderr 落临时文件而非进程替换：避免 exit 时 grep 尚未 flush 造成的竞态，
  # 同时保证 PIPESTATUS 只反映 mysqldump|gzip 这两环。
  sudo docker exec "$CONTAINER" \
    mysqldump "--defaults-extra-file=$CNT_CNF" \
      --default-character-set=utf8mb4 \
      --single-transaction \
      --routines \
      --triggers \
      --events \
      --databases "$DB" \
    2>"$ERR_FILE" \
    | gzip -9 > "$TMP_OUT"

  local st=("${PIPESTATUS[@]}")
  local dump_rc="${st[0]}" gzip_rc="${st[1]}"

  # 过滤 mysqldump 固有的无害告警，只保留真实错误
  local errs
  errs="$(grep -v 'Using a password on the command line interface can be insecure' "$ERR_FILE" \
          | grep -v '^[[:space:]]*$' || true)"

  if [ "$dump_rc" -ne 0 ]; then
    [ -n "$errs" ] && log ERROR "mysqldump stderr: $errs"
    die "mysqldump 失败（exit=$dump_rc），已丢弃半成品"
  fi
  [ "$gzip_rc" -eq 0 ] || die "gzip 压缩失败（exit=$gzip_rc），已丢弃半成品"
  [ -n "$errs" ] && log WARN "mysqldump 有告警输出: $errs"

  # --- 校验产物 ---
  [ -s "$TMP_OUT" ] || die "备份产物为空文件，已丢弃"

  local size
  size="$(stat -c '%s' "$TMP_OUT")"
  [ "$size" -ge "$MIN_SIZE_BYTES" ] \
    || die "备份产物过小（${size} 字节 < ${MIN_SIZE_BYTES}），疑似导出异常，已丢弃"

  gzip -t "$TMP_OUT" 2>/dev/null || die "gzip -t 校验失败，压缩包损坏，已丢弃"

  # 内容抽检：必须能看到目标库的建表语句，防止「导出了个空壳」
  #
  # 【坑｜勿改回 pipefail 下的裸管道】
  #   `zcat big.gz | head -200 | grep -q X` 中，head 取够 200 行就退出，
  #   zcat 继续写管道被 SIGPIPE 打死 → zcat 退出码 141。
  #   脚本开头的 `set -o pipefail` 会把整条管道判成失败（rc=141），
  #   哪怕 grep 明明匹配成功（实测 zcat=141 head=0 grep=0）。
  #   首次联调就栽在这：dump 完全正常，却被误判「未发现 CREATE TABLE」而丢弃。
  #   故此处放进子 shell 显式关掉 pipefail，只取 grep 的结果。
  if ! ( set +o pipefail; gzip -cd "$TMP_OUT" 2>/dev/null | head -200 | grep -q "CREATE TABLE" ); then
    die "备份内容中未发现 CREATE TABLE 语句，疑似导出异常，已丢弃"
  fi

  # 全部校验通过后才落到正式文件名（避免半成品被误当作可用备份）
  mv -f "$TMP_OUT" "$TARGET" || die "重命名到正式文件名失败"
  chmod 640 "$TARGET"

  log INFO "备份成功: $(basename "$TARGET")  大小: $(du -h "$TARGET" | cut -f1) (${size} 字节)  gzip -t: OK"

  # --- 清理旧备份 ---
  prune_old_backups

  log INFO "===== issueFlow 备份结束（成功） ====="
  return 0
}

main "$@"
