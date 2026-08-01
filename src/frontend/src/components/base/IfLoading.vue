<template>
  <!-- 加载中：骨架屏 / 圆环两种形态 -->
  <div v-if="loading" class="if-loading" :style="{ minHeight: minHeight }" role="status" aria-live="polite">
    <!-- 骨架屏：更适合列表 / 卡片区域，避免布局跳动 -->
    <div v-if="type === 'skeleton'" class="if-loading__skeleton">
      <div
        v-for="row in normalizedRows"
        :key="row"
        class="if-loading__bar"
        :style="{ width: barWidth(row) }"
      />
    </div>

    <!-- 圆环：适合小面积 / 内联占位 -->
    <div v-else class="if-loading__spinner-wrap">
      <span class="if-loading__spinner" aria-hidden="true" />
      <span v-if="showText" class="if-loading__text">{{ displayText }}</span>
    </div>
  </div>

  <!-- 加载完成：渲染真实内容 -->
  <template v-else>
    <slot />
  </template>
</template>

<script setup>
/**
 * IfLoading —— 统一加载态占位组件（Phase9 T4）。
 *
 * 用法：
 *   <IfLoading :loading="loading" type="skeleton" :rows="4">
 *     <RealContent />
 *   </IfLoading>
 *
 * 设计约定：
 *   - 所有尺寸 / 颜色一律消费 --if-* 令牌，四套主题自动适配；
 *   - 不使用 v-loading 遮罩，避免深色主题下白色蒙层突兀；
 *   - 尊重 prefers-reduced-motion（在 styles/index.css 全局兜底）。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  /** 是否处于加载中 */
  loading: { type: Boolean, default: false },
  /** 形态：skeleton 骨架屏 | spinner 圆环 */
  type: {
    type: String,
    default: 'skeleton',
    validator: (v) => ['skeleton', 'spinner'].includes(v)
  },
  /** 骨架屏行数（type=skeleton 时生效） */
  rows: { type: Number, default: 3 },
  /** 自定义提示文案，缺省读 i18n common.msg.loading */
  text: { type: String, default: '' },
  /** 是否展示提示文案（type=spinner 时生效） */
  showText: { type: Boolean, default: true },
  /** 占位区最小高度 */
  minHeight: { type: String, default: '120px' }
})

const { t } = useI18n()

/** 行数下限 1、上限 10，避免异常入参导致渲染爆炸。 */
const normalizedRows = computed(() => {
  const n = Number(props.rows)
  const safe = Number.isFinite(n) ? Math.floor(n) : 3
  return Math.min(Math.max(safe, 1), 10)
})

const displayText = computed(() => props.text || t('common.msg.loading'))

/**
 * 骨架条宽度：最后一行收窄，视觉上更像真实文本段落。
 * @param {number} row 当前行序号（从 1 开始）
 * @returns {string} CSS 宽度
 */
function barWidth(row) {
  if (row === normalizedRows.value && normalizedRows.value > 1) return '60%'
  if (row === 1) return '40%'
  return '100%'
}
</script>

<style scoped>
.if-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: var(--if-space-md);
}

.if-loading__skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--if-space-sm);
  width: 100%;
}

.if-loading__bar {
  height: 14px;
  border-radius: var(--if-radius-sm);
  background: var(--if-skeleton-bg);
  animation: if-skeleton-pulse 1.4s ease-in-out infinite;
}

@keyframes if-skeleton-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}

.if-loading__spinner-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--if-space-sm);
}

.if-loading__spinner {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid var(--border-color);
  border-top-color: var(--theme-color);
  animation: if-spin 0.8s linear infinite;
}

@keyframes if-spin {
  to {
    transform: rotate(360deg);
  }
}

.if-loading__text {
  font-size: var(--if-font-sm);
  color: var(--text-secondary);
}
</style>
