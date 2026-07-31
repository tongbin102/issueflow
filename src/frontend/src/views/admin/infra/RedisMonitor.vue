<template>
  <!-- Phase7 T7：基础设施 > Redis 监控（GET /api/admin/redis/info，只读）
       降级契约：后端 Redis 不可用时仍返回 HTTP 200 + available=false + errorMessage，
       本页据此渲染错误卡片 + 重试按钮，绝不白屏、不长时间转圈。 -->
  <div class="redis-monitor">
    <el-card class="page-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="head">
          <div class="head__title">
            <span>{{ t('infra.redis.title') }}</span>
            <el-tag
              :type="info.available ? 'success' : 'danger'"
              size="small"
              effect="light"
              class="head__state"
            >
              {{ info.available ? t('common.status.enabled') : t('infra.redis.unavailableTitle') }}
            </el-tag>
          </div>
          <div class="head__actions">
            <span v-if="lastUpdate" class="head__time text-muted">
              {{ t('infra.redis.lastUpdate', { time: lastUpdate }) }}
            </span>
            <el-tooltip :content="t('infra.redis.autoRefreshTip')" placement="top">
              <el-switch
                v-model="autoRefresh"
                :active-text="t('infra.redis.autoRefresh')"
                @change="onAutoRefreshChange"
              />
            </el-tooltip>
            <el-button :icon="Refresh" @click="fetchData">{{ t('common.action.refresh') }}</el-button>
          </div>
        </div>
      </template>

      <!-- 降级态：整页错误卡片 -->
      <template v-if="!info.available">
        <el-result icon="warning" :title="t('infra.redis.unavailableTitle')">
          <template #sub-title>
            <div class="err-reason">
              {{
                t('infra.redis.unavailableDesc', {
                  reason: info.errorMessage || t('infra.redis.unavailableUnknown')
                })
              }}
            </div>
            <div class="err-hint text-muted">{{ t('infra.redis.unavailableHint') }}</div>
          </template>
          <template #extra>
            <el-button type="primary" :icon="Refresh" :loading="loading" @click="fetchData">
              {{ t('infra.redis.retry') }}
            </el-button>
          </template>
        </el-result>
      </template>

      <!-- 正常态：服务器 / 内存 / 运行统计 三卡片 + 键空间表格 -->
      <template v-else>
        <el-alert
          class="tip"
          type="info"
          :closable="false"
          show-icon
          :title="t('infra.redis.readonlyTip')"
        />

        <el-row :gutter="12" class="card-row">
          <!-- 服务器 -->
          <el-col :xs="24" :md="8">
            <div class="info-card">
              <div class="info-card__title">
                <el-icon><Monitor /></el-icon>
                <span>{{ t('infra.redis.server.title') }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.server.version') }}</span>
                <span class="v">{{ serverInfo.version }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.server.mode') }}</span>
                <span class="v">{{ serverInfo.mode }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.server.os') }}</span>
                <span class="v" :title="serverInfo.os">{{ serverInfo.os }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.server.uptime') }}</span>
                <span class="v">{{ serverInfo.uptime }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.server.clients') }}</span>
                <span class="v">{{ serverInfo.clients }}</span>
              </div>
            </div>
          </el-col>

          <!-- 内存 -->
          <el-col :xs="24" :md="8">
            <div class="info-card">
              <div class="info-card__title">
                <el-icon><Coin /></el-icon>
                <span>{{ t('infra.redis.memory.title') }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.memory.used') }}</span>
                <span class="v">{{ memoryInfo.used }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.memory.peak') }}</span>
                <span class="v">{{ memoryInfo.peak }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.memory.max') }}</span>
                <span class="v">{{ memoryInfo.max }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.memory.fragmentation') }}</span>
                <span class="v">{{ memoryInfo.fragmentation }}</span>
              </div>
              <!-- 仅在设置了 maxmemory 时展示占用进度条（ARCH：本期用 el-progress，不引图表） -->
              <div v-if="memoryInfo.usagePercent !== null" class="info-card__progress">
                <div class="k">{{ t('infra.redis.memory.usage') }}</div>
                <el-progress
                  :percentage="memoryInfo.usagePercent"
                  :status="memoryProgressStatus"
                  :stroke-width="10"
                />
              </div>
            </div>
          </el-col>

          <!-- 运行统计 -->
          <el-col :xs="24" :md="8">
            <div class="info-card">
              <div class="info-card__title">
                <el-icon><Odometer /></el-icon>
                <span>{{ t('infra.redis.stats.title') }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.stats.dbSize') }}</span>
                <span class="v">{{ statsInfo.dbSize }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.stats.hitRate') }}</span>
                <span class="v">{{ statsInfo.hitRate }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.stats.hits') }}</span>
                <span class="v">{{ statsInfo.hits }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.stats.misses') }}</span>
                <span class="v">{{ statsInfo.misses }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.stats.expired') }}</span>
                <span class="v">{{ statsInfo.expired }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.stats.evicted') }}</span>
                <span class="v">{{ statsInfo.evicted }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.stats.totalConnections') }}</span>
                <span class="v">{{ statsInfo.totalConnections }}</span>
              </div>
              <div class="info-card__row">
                <span class="k">{{ t('infra.redis.stats.totalCommands') }}</span>
                <span class="v">{{ statsInfo.totalCommands }}</span>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 键空间分布 -->
        <div class="section-title">{{ t('infra.redis.keyspace.title') }}</div>
        <div class="table-wrap">
          <el-table :data="info.keyspace" border stripe size="small" style="width: 100%">
            <el-table-column prop="db" :label="t('infra.redis.keyspace.db')" min-width="120" />
            <el-table-column
              prop="keys"
              :label="t('infra.redis.keyspace.keys')"
              min-width="120"
              align="right"
            />
            <el-table-column
              prop="expires"
              :label="t('infra.redis.keyspace.expires')"
              min-width="120"
              align="right"
            />
            <el-table-column
              prop="avgTtl"
              :label="t('infra.redis.keyspace.avgTtl')"
              min-width="140"
              align="right"
            />
            <template #empty>
              <el-empty :description="t('infra.redis.keyspace.empty')" :image-size="60" />
            </template>
          </el-table>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Monitor, Coin, Odometer } from '@element-plus/icons-vue'
import { getRedisInfo } from '@/api/redisMonitor'
import { formatDate, formatFileSize } from '@/utils/format'

const { t } = useI18n()

/** 自动刷新周期（ARCH Q3：默认关闭，避免无人值守时持续打 INFO） */
const AUTO_REFRESH_MS = 10000

const loading = ref(false)
const autoRefresh = ref(false)
const lastUpdate = ref('')
let timer = null

/** 监控数据；available 默认 false，接口失败时同样走降级态 */
const info = reactive({
  available: false,
  errorMessage: '',
  server: {},
  memory: {},
  stats: {},
  keyspace: [],
  dbSize: 0
})

const DASH = '-'

/**
 * 安全取值：Map 缺 key 时回退占位符。
 * @param {Object} source 来源 Map
 * @param {string} key 键名
 * @returns {string}
 */
function pick(source, key) {
  const value = source && source[key]
  return value === null || value === undefined || value === '' ? DASH : String(value)
}

/**
 * 数字取值：解析失败返回 null。
 * @param {Object} source 来源 Map
 * @param {string} key 键名
 * @returns {number|null}
 */
function pickNumber(source, key) {
  const value = source && source[key]
  if (value === null || value === undefined || value === '') return null
  const num = Number(value)
  return Number.isNaN(num) ? null : num
}

/** 千分位展示 */
function formatCount(value) {
  if (value === null || value === undefined) return DASH
  return Number(value).toLocaleString()
}

const serverInfo = computed(() => {
  const src = info.server || {}
  const uptimeDays = pickNumber(src, 'uptime_in_days')
  return {
    version: pick(src, 'redis_version'),
    mode: pick(src, 'redis_mode'),
    os: pick(src, 'os'),
    uptime:
      uptimeDays === null ? DASH : t('infra.redis.server.uptimeDays', { days: uptimeDays }),
    clients: formatCount(pickNumber(src, 'connected_clients'))
  }
})

const memoryInfo = computed(() => {
  const src = info.memory || {}
  const used = pickNumber(src, 'used_memory')
  const peak = pickNumber(src, 'used_memory_peak')
  const max = pickNumber(src, 'maxmemory')
  const ratio = pickNumber(src, 'mem_fragmentation_ratio')
  let usagePercent = null
  if (max !== null && max > 0 && used !== null) {
    usagePercent = Math.min(100, Math.round((used / max) * 100))
  }
  return {
    used: used === null ? pick(src, 'used_memory_human') : formatFileSize(used),
    peak: peak === null ? pick(src, 'used_memory_peak_human') : formatFileSize(peak),
    max: max === null || max === 0 ? t('infra.redis.memory.maxUnlimited') : formatFileSize(max),
    fragmentation: ratio === null ? DASH : ratio.toFixed(2),
    usagePercent
  }
})

/** 内存占用 >= 90% 告警色，>= 70% 警示色 */
const memoryProgressStatus = computed(() => {
  const percent = memoryInfo.value.usagePercent
  if (percent === null) return ''
  if (percent >= 90) return 'exception'
  if (percent >= 70) return 'warning'
  return 'success'
})

const statsInfo = computed(() => {
  const src = info.stats || {}
  const hits = pickNumber(src, 'keyspace_hits')
  const misses = pickNumber(src, 'keyspace_misses')
  // 命中率 = hits / (hits + misses)；分母为 0 时按 ARCH 约定显示 '-'
  let hitRate = DASH
  if (hits !== null && misses !== null && hits + misses > 0) {
    hitRate = `${((hits / (hits + misses)) * 100).toFixed(2)}%`
  }
  return {
    dbSize: formatCount(info.dbSize ?? 0),
    hits: formatCount(hits),
    misses: formatCount(misses),
    hitRate,
    expired: formatCount(pickNumber(src, 'expired_keys')),
    evicted: formatCount(pickNumber(src, 'evicted_keys')),
    totalConnections: formatCount(pickNumber(src, 'total_connections_received')),
    totalCommands: formatCount(pickNumber(src, 'total_commands_processed'))
  }
})

async function fetchData() {
  loading.value = true
  try {
    const data = (await getRedisInfo()) || {}
    info.available = data.available === true
    info.errorMessage = data.errorMessage || ''
    info.server = data.server || {}
    info.memory = data.memory || {}
    info.stats = data.stats || {}
    info.keyspace = data.keyspace || []
    info.dbSize = data.dbSize ?? 0
    lastUpdate.value = formatDate(new Date())
  } catch (e) {
    // 网络层异常（后端未启动 / 超时）也统一降级为错误卡片，不抛给用户看白屏
    info.available = false
    info.errorMessage = (e && e.message) || ''
    info.server = {}
    info.memory = {}
    info.stats = {}
    info.keyspace = []
    info.dbSize = 0
    lastUpdate.value = formatDate(new Date())
  } finally {
    loading.value = false
  }
}

function startTimer() {
  stopTimer()
  timer = window.setInterval(fetchData, AUTO_REFRESH_MS)
}

function stopTimer() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

function onAutoRefreshChange(value) {
  if (value) {
    startTimer()
  } else {
    stopTimer()
  }
}

onMounted(fetchData)
// 离开页面必须清定时器，否则后台会持续打 INFO
onBeforeUnmount(stopTimer)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.head__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.head__state {
  font-weight: normal;
}

.head__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.head__time {
  font-size: 12px;
}

.tip {
  margin-bottom: 12px;
}

.card-row {
  row-gap: 12px;
}

.info-card {
  height: 100%;
  background: var(--if-stat-card-bg, var(--el-fill-color-lighter));
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--if-radius, 4px);
  padding: 16px;
}

.info-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.info-card__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 5px 0;
  font-size: 13px;
}

.info-card__row .k {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.info-card__row .v {
  color: var(--text-primary);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-card__progress {
  margin-top: 10px;
}

.info-card__progress .k {
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 6px;
}

.section-title {
  font-weight: 600;
  color: var(--text-primary);
  margin: 20px 0 12px;
}

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

.err-reason {
  color: var(--text-regular);
  word-break: break-all;
  max-width: 640px;
  margin: 0 auto;
}

.err-hint {
  font-size: 12px;
  margin-top: 8px;
  max-width: 640px;
}

@media (max-width: 768px) {
  .head__actions {
    width: 100%;
    justify-content: space-between;
  }
  .head__time {
    width: 100%;
  }
}
</style>
