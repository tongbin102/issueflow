<template>
  <el-button
    class="if-button"
    :class="{ 'if-button--block': block }"
    :type="type"
    :size="size"
    :plain="plain"
    :round="round"
    :circle="circle"
    :text="text"
    :link="link"
    :loading="loading"
    :disabled="disabled"
    :native-type="nativeType"
    :icon="icon"
    @click="onClick"
  >
    <slot />
  </el-button>
</template>

<script setup>
/**
 * IfButton —— 按钮统一封装（Phase9 T5）。
 *
 * 为什么不直接用 el-button：
 *   1) 统一移动端触控热区（>= --if-touch-size 44px），满足可访问性要求；
 *   2) 统一 block（占满一行）能力，移动端表单/卡片操作区大量复用；
 *   3) 后续如需替换底层 UI 库，只改这一处。
 *
 * 透传规则：props 与 el-button 同名同义，避免团队记忆负担。
 */
const props = defineProps({
  /** 按钮类型：default/primary/success/warning/danger/info */
  type: { type: String, default: 'default' },
  /** 尺寸：large/default/small */
  size: { type: String, default: 'default' },
  /** 朴素按钮 */
  plain: { type: Boolean, default: false },
  /** 圆角按钮 */
  round: { type: Boolean, default: false },
  /** 圆形按钮 */
  circle: { type: Boolean, default: false },
  /** 文字按钮 */
  text: { type: Boolean, default: false },
  /** 链接按钮 */
  link: { type: Boolean, default: false },
  /** 加载态 */
  loading: { type: Boolean, default: false },
  /** 禁用态 */
  disabled: { type: Boolean, default: false },
  /** 原生 type：button/submit/reset */
  nativeType: { type: String, default: 'button' },
  /** 图标组件（Element Plus 图标） */
  icon: { type: [String, Object], default: undefined },
  /** 是否占满父容器宽度 */
  block: { type: Boolean, default: false }
})

const emit = defineEmits(['click'])

/**
 * 点击事件转发；禁用 / 加载态由 el-button 自身拦截，此处只做透传。
 * @param {MouseEvent} evt 原生事件
 */
function onClick(evt) {
  if (props.disabled || props.loading) return
  emit('click', evt)
}
</script>

<style scoped>
.if-button {
  transition: background-color var(--if-transition-fast), border-color var(--if-transition-fast),
    color var(--if-transition-fast);
}

.if-button--block {
  width: 100%;
  display: flex;
}

/* 移动端触控热区：PRD §2.6 要求 >= 44px */
@media (max-width: 767px) {
  .if-button {
    min-height: var(--if-touch-size);
  }

  .if-button.el-button--small {
    min-height: 36px;
  }
}
</style>
