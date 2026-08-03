import { ref, computed, onBeforeUnmount } from 'vue'
import { fetchTaskProgress } from '@/api/dataManagement'

/** 默认轮询间隔（毫秒） */
const DEFAULT_INTERVAL = 1500

/** 连续失败多少次后放弃轮询 */
const MAX_CONSECUTIVE_ERRORS = 5

/**
 * 备份 / 恢复任务进度轮询 composable（Phase10）。
 *
 * 设计要点：
 * 1. **只在需要时轮询**：任务进入终态（finished=true）立即停表，
 *    不做「反正每秒问一次也不贵」这种对后端不负责任的事；
 * 2. **失败容错**：恢复过程中后端会短暂重启连接池，个别请求失败是正常现象，
 *    连续失败 5 次才判定断线，避免一次网络抖动就把进度条判死；
 * 3. **组件卸载即停**：onBeforeUnmount 清定时器，杜绝路由切走后仍在后台空转。
 *
 * @param {{interval?: number, onSuccess?: Function, onFail?: Function, onFinish?: Function}} [options] 配置项
 * @returns {Object} 轮询状态与控制方法
 */
export function useTaskProgress(options = {}) {
  const interval = options.interval || DEFAULT_INTERVAL

  /** 当前任务号，空串表示未启动 */
  const taskId = ref('')
  /** 最近一次拉到的进度对象 */
  const progress = ref(null)
  /** 是否正在轮询 */
  const polling = ref(false)
  /** 断线提示（连续失败达到阈值后置位） */
  const lostConnection = ref(false)

  let timer = null
  let errorCount = 0

  /** 进度百分比（0-100），无数据时为 0 */
  const percent = computed(() => {
    const value = progress.value && progress.value.progress
    return typeof value === 'number' ? Math.max(0, Math.min(100, value)) : 0
  })

  /** 当前阶段码 */
  const phase = computed(() => (progress.value && progress.value.phase) || '')

  /** 当前状态码 */
  const status = computed(() => (progress.value && progress.value.status) || '')

  /** 是否已终态 */
  const finished = computed(() => Boolean(progress.value && progress.value.finished))

  /** 是否成功 */
  const succeeded = computed(() => status.value === 'SUCCESS')

  /** 是否失败（含取消） */
  const failed = computed(() => status.value === 'FAILED' || status.value === 'CANCELED')

  /** 后端下发的提示文案（已脱敏） */
  const message = computed(() => (progress.value && progress.value.message) || '')

  /** 后端下发的失败原因（已脱敏） */
  const errorMsg = computed(() => (progress.value && progress.value.errorMsg) || '')

  /**
   * 清除定时器。
   */
  function clearTimer() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  /**
   * 停止轮询（不清空已有进度，便于停在最终画面上）。
   */
  function stop() {
    clearTimer()
    polling.value = false
  }

  /**
   * 重置全部状态。
   */
  function reset() {
    stop()
    taskId.value = ''
    progress.value = null
    lostConnection.value = false
    errorCount = 0
  }

  /**
   * 拉取一次进度，并按需安排下一次。
   */
  async function tick() {
    if (!taskId.value || !polling.value) return
    try {
      const data = await fetchTaskProgress(taskId.value)
      errorCount = 0
      lostConnection.value = false
      progress.value = data || null

      if (data && data.finished) {
        stop()
        if (data.status === 'SUCCESS' && typeof options.onSuccess === 'function') {
          options.onSuccess(data)
        }
        if ((data.status === 'FAILED' || data.status === 'CANCELED')
          && typeof options.onFail === 'function') {
          options.onFail(data)
        }
        if (typeof options.onFinish === 'function') {
          options.onFinish(data)
        }
        return
      }
    } catch (e) {
      errorCount += 1
      // 任务号在 Redis 过期（TTL 2h）时后端返回 40111，没有继续轮询的意义
      if (e && e.code === 40111) {
        lostConnection.value = true
        stop()
        if (typeof options.onFinish === 'function') options.onFinish(null)
        return
      }
      if (errorCount >= MAX_CONSECUTIVE_ERRORS) {
        lostConnection.value = true
        stop()
        if (typeof options.onFinish === 'function') options.onFinish(null)
        return
      }
    }
    clearTimer()
    if (polling.value) {
      timer = setTimeout(tick, interval)
    }
  }

  /**
   * 开始轮询指定任务。
   * @param {string} id 任务号
   * @param {Object} [initial] 后端受理时返回的初始进度，先渲染避免白屏
   */
  function start(id, initial) {
    if (!id) return
    reset()
    taskId.value = id
    progress.value = initial || null
    polling.value = true
    // 首次立即拉一发，别让用户对着 0% 干等一个轮询周期
    tick()
  }

  onBeforeUnmount(() => {
    clearTimer()
    polling.value = false
  })

  return {
    taskId,
    progress,
    polling,
    lostConnection,
    percent,
    phase,
    status,
    finished,
    succeeded,
    failed,
    message,
    errorMsg,
    start,
    stop,
    reset
  }
}

export default useTaskProgress
