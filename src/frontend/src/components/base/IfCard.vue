<template>
  <section
    class="if-card"
    :class="{
      'if-card--hoverable': hoverable,
      'if-card--clickable': clickable,
      'if-card--borderless': !bordered,
      'if-card--flat': flat
    }"
    :tabindex="clickable ? 0 : undefined"
    :role="clickable ? 'button' : undefined"
    @click="onClick"
    @keydown.enter.prevent="onClick"
    @keydown.space.prevent="onClick"
  >
    <!-- 头部：标题 / 副标题 / 右侧扩展区 -->
    <header v-if="hasHeader" class="if-card__header">
      <div class="if-card__title-wrap">
        <slot name="title">
          <h3 v-if="title" class="if-card__title">{{ title }}</h3>
        </slot>
        <p v-if="subtitle" class="if-card__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.extra" class="if-card__extra">
        <slot name="extra" />
      </div>
    </header>

    <!-- 主体：加载中显示骨架，加载完成显示内容 -->
    <div class="if-card__body" :style="bodyStyle">
      <IfLoading :loading="loading" :rows="skeletonRows">
        <slot />
      </IfLoading>
    </div>

    <!-- 底部 -->
    <footer v-if="$slots.footer" class="if-card__footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<script setup>
/**
 * IfCard —— 统一卡片容器（Phase9 T5）。
 *
 * 替代散落各处的 el-card / .page-card 手写样式，
 * 统一圆角（--if-radius）、阴影（--if-shadow-sm）、内边距（--if-space-*）。
 *
 * 用法：
 *   <IfCard :title="t('dashboard.section.quickEntry')" hoverable clickable @click="go">
 *     ...
 *   </IfCard>
 */
import { computed, useSlots } from 'vue'
import IfLoading from './IfLoading.vue'

const props = defineProps({
  /** 卡片标题（也可用 #title 插槽自定义） */
  title: { type: String, default: '' },
  /** 卡片副标题 */
  subtitle: { type: String, default: '' },
  /** 是否显示边框 */
  bordered: { type: Boolean, default: true },
  /** 是否启用 hover 提升效果 */
  hoverable: { type: Boolean, default: false },
  /** 是否可点击（渲染 role=button 并支持键盘触发） */
  clickable: { type: Boolean, default: false },
  /** 是否去掉阴影（嵌套在其他卡片内时使用） */
  flat: { type: Boolean, default: false },
  /** 加载态：主体区域显示骨架屏 */
  loading: { type: Boolean, default: false },
  /** 骨架屏行数 */
  skeletonRows: { type: Number, default: 3 },
  /** 主体内边距，缺省使用 --if-space-md */
  bodyPadding: { type: String, default: '' }
})

const emit = defineEmits(['click'])
const slots = useSlots()

const hasHeader = computed(() => Boolean(props.title || slots.title || slots.extra))

const bodyStyle = computed(() => (props.bodyPadding ? { padding: props.bodyPadding } : {}))

/**
 * 点击回调：仅在 clickable 为真时向外抛出，避免误触发。
 * @param {MouseEvent|KeyboardEvent} evt 原生事件
 */
function onClick(evt) {
  if (!props.clickable) return
  emit('click', evt)
}
</script>

<style scoped>
.if-card {
  display: flex;
  flex-direction: column;
  background: var(--bg-container);
  border: 1px solid var(--border-color);
  border-radius: var(--if-radius, var(--if-radius-sm));
  box-shadow: var(--if-shadow-sm);
  transition: box-shadow var(--if-transition-base), transform var(--if-transition-base),
    border-color var(--if-transition-base);
  overflow: hidden;
}

.if-card--borderless {
  border-color: transparent;
}

.if-card--flat {
  box-shadow: none;
}

.if-card--hoverable:hover {
  box-shadow: var(--if-shadow-md);
  transform: translateY(-2px);
}

.if-card--clickable {
  cursor: pointer;
}

.if-card--clickable:focus-visible {
  outline: 2px solid var(--theme-color);
  outline-offset: 2px;
}

.if-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--if-space-sm);
  padding: var(--if-space-md) var(--if-space-md) 0;
}

.if-card__title-wrap {
  min-width: 0;
}

.if-card__title {
  margin: 0;
  font-size: var(--if-font-h3);
  font-weight: var(--if-weight-bold);
  line-height: var(--if-line-tight);
  color: var(--text-primary);
}

.if-card__subtitle {
  margin: var(--if-space-xs) 0 0;
  font-size: var(--if-font-xs);
  color: var(--text-secondary);
  line-height: var(--if-line-base);
}

.if-card__extra {
  flex-shrink: 0;
}

.if-card__body {
  flex: 1;
  min-width: 0;
  padding: var(--if-space-md);
}

.if-card__footer {
  padding: 0 var(--if-space-md) var(--if-space-md);
  border-top: 1px solid var(--border-color);
  padding-top: var(--if-space-sm);
}

/* 移动端压缩内边距，争取内容宽度 */
@media (max-width: 767px) {
  .if-card__header {
    padding: var(--if-space-sm) var(--if-space-sm) 0;
  }

  .if-card__body {
    padding: var(--if-space-sm);
  }

  .if-card__footer {
    padding: var(--if-space-sm);
  }
}
</style>
