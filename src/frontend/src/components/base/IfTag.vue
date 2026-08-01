<template>
  <span
    class="if-tag"
    :class="[`if-tag--${semantic}`, `if-tag--${size}`, { 'if-tag--plain': plain, 'if-tag--pill': round }]"
  >
    <span v-if="dot" class="if-tag__dot" aria-hidden="true" />
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup>
/**
 * IfTag —— 语义标签（Phase9 T6）。
 *
 * 与 el-tag 的分工：
 *   - IfTag 用于「业务语义」标签（状态 / 严重等级 / 优先级），
 *     色值来自固定语义令牌 --if-color-*，不随 light/dark/blue/green 主题变化，
 *     保证「红=危险、绿=通过」的认知在任何主题下都稳定（ARCH §七.6）；
 *   - el-tag 仍用于中性标签（如自定义 tags 关键词）。
 *
 * 用法：
 *   <IfTag :semantic="statusSemantic(row.status)" :label="statusLabelI18n(row.status)" dot />
 */
defineProps({
  /** 语义：success | warning | danger | info | processing */
  semantic: {
    type: String,
    default: 'info',
    validator: (v) => ['success', 'warning', 'danger', 'info', 'processing'].includes(v)
  },
  /** 标签文案（也可用默认插槽） */
  label: { type: [String, Number], default: '' },
  /** 尺寸：small | default */
  size: {
    type: String,
    default: 'default',
    validator: (v) => ['small', 'default'].includes(v)
  },
  /** 朴素样式：透明底 + 语义描边 */
  plain: { type: Boolean, default: false },
  /** 胶囊圆角 */
  round: { type: Boolean, default: true },
  /** 是否显示前置圆点 */
  dot: { type: Boolean, default: false }
})
</script>

<style scoped>
.if-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--if-space-xs);
  padding: 2px var(--if-space-sm);
  border: 1px solid transparent;
  border-radius: var(--if-radius-sm);
  font-size: var(--if-font-xs);
  font-weight: var(--if-weight-medium);
  line-height: 18px;
  white-space: nowrap;
}

.if-tag--pill {
  border-radius: var(--if-radius-pill);
}

.if-tag--small {
  padding: 0 var(--if-space-xs);
  font-size: 11px;
  line-height: 16px;
}

.if-tag__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

/* --- 语义配色：底色用 soft 令牌（暗色主题已加高不透明度），文字/描边用纯语义色 --- */
.if-tag--success {
  color: var(--if-color-success);
  background: var(--if-color-success-soft);
  border-color: var(--if-color-success-soft);
}

.if-tag--warning {
  color: var(--if-color-warning);
  background: var(--if-color-warning-soft);
  border-color: var(--if-color-warning-soft);
}

.if-tag--danger {
  color: var(--if-color-danger);
  background: var(--if-color-danger-soft);
  border-color: var(--if-color-danger-soft);
}

.if-tag--info {
  color: var(--if-color-info);
  background: var(--if-color-info-soft);
  border-color: var(--if-color-info-soft);
}

.if-tag--processing {
  color: var(--if-color-processing);
  background: var(--if-color-processing-soft);
  border-color: var(--if-color-processing-soft);
}

.if-tag--plain {
  background: transparent;
}

.if-tag--plain.if-tag--success {
  border-color: var(--if-color-success);
}

.if-tag--plain.if-tag--warning {
  border-color: var(--if-color-warning);
}

.if-tag--plain.if-tag--danger {
  border-color: var(--if-color-danger);
}

.if-tag--plain.if-tag--info {
  border-color: var(--if-color-info);
}

.if-tag--plain.if-tag--processing {
  border-color: var(--if-color-processing);
}
</style>
