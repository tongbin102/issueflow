<template>
  <div class="if-empty" :style="{ minHeight: minHeight }" role="status">
    <div class="if-empty__icon" aria-hidden="true">
      <slot name="icon">
        <el-icon :size="iconSize"><component :is="resolvedIcon" /></el-icon>
      </slot>
    </div>

    <p class="if-empty__title">{{ displayTitle }}</p>
    <p v-if="displayDescription" class="if-empty__desc">{{ displayDescription }}</p>

    <div v-if="actionText || $slots.action" class="if-empty__action">
      <slot name="action">
        <IfButton type="primary" @click="$emit('action')">{{ actionText }}</IfButton>
      </slot>
    </div>
  </div>
</template>

<script setup>
/**
 * IfEmptyState —— 统一空状态（Phase9 T6）。
 *
 * 覆盖三类场景（PRD §3.4）：
 *   1) empty    ：确实没有数据（首次进入）
 *   2) noResult ：有数据但筛选后无匹配 → 引导「重置筛选」
 *   3) error    ：请求失败 → 引导「重试」
 *
 * 文案默认走 i18n（common.empty.*），调用方可用 title/description 覆盖。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Box, Search, WarningFilled } from '@element-plus/icons-vue'
import IfButton from './IfButton.vue'

const props = defineProps({
  /** 场景：empty | noResult | error */
  scene: {
    type: String,
    default: 'empty',
    validator: (v) => ['empty', 'noResult', 'error'].includes(v)
  },
  /** 自定义主文案，缺省按 scene 读 i18n */
  title: { type: String, default: '' },
  /** 自定义描述文案，缺省按 scene 读 i18n */
  description: { type: String, default: '' },
  /** 操作按钮文案，为空则不渲染按钮 */
  actionText: { type: String, default: '' },
  /** 图标尺寸 */
  iconSize: { type: Number, default: 44 },
  /** 容器最小高度 */
  minHeight: { type: String, default: '160px' }
})

defineEmits(['action'])

const { t } = useI18n()

/** 场景 → 图标映射。 */
const ICON_MAP = {
  empty: Box,
  noResult: Search,
  error: WarningFilled
}

const resolvedIcon = computed(() => ICON_MAP[props.scene] || Box)

const displayTitle = computed(() => props.title || t(`common.empty.${props.scene}Title`))

const displayDescription = computed(() => {
  if (props.description) return props.description
  const key = `common.empty.${props.scene}Desc`
  const text = t(key)
  // i18n 缺 key 时 vue-i18n 会回显 key 本身，此处兜底为空，避免把 key 展示给用户
  return text === key ? '' : text
})
</script>

<style scoped>
.if-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--if-space-xs);
  padding: var(--if-space-lg) var(--if-space-md);
  text-align: center;
}

.if-empty__icon {
  color: var(--text-secondary);
  opacity: 0.55;
  margin-bottom: var(--if-space-xs);
}

.if-empty__title {
  margin: 0;
  font-size: var(--if-font-base);
  font-weight: var(--if-weight-medium);
  color: var(--text-regular);
}

.if-empty__desc {
  margin: 0;
  max-width: 420px;
  font-size: var(--if-font-xs);
  line-height: var(--if-line-base);
  color: var(--text-secondary);
}

.if-empty__action {
  margin-top: var(--if-space-sm);
}
</style>
