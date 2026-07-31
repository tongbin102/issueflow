<template>
  <!-- Phase8 W2 #12：问题弹窗内容容器，由「4 分区折叠」改为「左侧竖形标签页」。
       5 个标签：基本信息 / 问题描述 / 附件上传 / 关联信息 / 操作历史。
       - tab-position 默认 left，窄屏（< 768px）自动切 top，保证移动端可用；
       - before-leave 钩子把「离开基本信息」的校验交给父级（校验不过则阻止切换）；
       - 切换时给内容区加一次性动画类，实现淡入 + 轻微位移的平滑过渡（不重挂子组件）。 -->
  <el-tabs
    v-model="active"
    :tab-position="tabPosition"
    :class="['if-issue-tabs', { 'is-switching': switching }]"
    :before-leave="handleBeforeLeave"
    @tab-change="onTabChange"
  >
    <el-tab-pane name="basic" :label="t('issue.tab.basic')">
      <div class="if-tab-panel"><slot name="basic" /></div>
    </el-tab-pane>

    <el-tab-pane name="detail" :label="t('issue.tab.description')">
      <div class="if-tab-panel"><slot name="detail" /></div>
    </el-tab-pane>

    <el-tab-pane v-if="showAttachment" name="attachment" :label="t('issue.tab.attachment')">
      <div class="if-tab-panel"><slot name="attachment" /></div>
    </el-tab-pane>

    <el-tab-pane v-if="showRelation" name="relation" :label="t('issue.tab.relation')">
      <div class="if-tab-panel"><slot name="relation" /></div>
    </el-tab-pane>

    <el-tab-pane v-if="showHistory" name="history" :label="t('issue.tab.history')">
      <div class="if-tab-panel"><slot name="history" /></div>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  /** 是否渲染「附件上传」标签页 */
  showAttachment: { type: Boolean, default: true },
  /** 是否渲染「关联信息」标签页 */
  showRelation: { type: Boolean, default: true },
  /** 是否渲染「操作历史」标签页 */
  showHistory: { type: Boolean, default: true },
  /**
   * 离开当前标签前的校验钩子，由父级注入。
   * 签名 (nextName, oldName) => boolean | Promise<boolean>，返回 false 阻止切换。
   * 仅在「离开 basic」时调用，其余标签自由切换。
   */
  beforeLeave: { type: Function, default: null },
  /** 窄屏阈值（px），低于该宽度标签页切换为水平（top） */
  narrowWidth: { type: Number, default: 768 }
})
const emit = defineEmits(['change'])

const { t } = useI18n()

/** 当前激活标签，默认第一个（基本信息） */
const active = ref('basic')

/* ---------------- 响应式：窄屏切水平标签 ---------------- */
const isNarrow = ref(false)
function updateNarrow() {
  isNarrow.value = typeof window !== 'undefined' && window.innerWidth < props.narrowWidth
}
const tabPosition = computed(() => (isNarrow.value ? 'top' : 'left'))

/* ---------------- 切换过渡动画（一次性类，避免重挂内容） ---------------- */
const switching = ref(false)
let switchTimer = null
function onTabChange(name) {
  switching.value = false
  nextTick(() => {
    switching.value = true
    if (switchTimer) clearTimeout(switchTimer)
    switchTimer = setTimeout(() => {
      switching.value = false
    }, 260)
  })
  emit('change', name)
}

/**
 * el-tabs before-leave：仅拦截「离开基本信息」，交由父级校验。
 * @param {string} nextName 目标标签
 * @param {string} oldName 当前标签
 * @returns {Promise<boolean>} false 阻止切换
 */
async function handleBeforeLeave(nextName, oldName) {
  if (oldName !== 'basic') return true
  if (typeof props.beforeLeave !== 'function') return true
  try {
    const result = await props.beforeLeave(nextName, oldName)
    return result !== false
  } catch (e) {
    return false
  }
}

/**
 * 激活指定标签（校验失败定位用）。
 * 保留 expand 作为方法名，兼容父级既有调用点。
 * @param {string} name basic | detail | attachment | relation | history
 */
function expand(name) {
  if (name && active.value !== name) {
    active.value = name
  }
}

defineExpose({ expand, activate: expand, active })

onMounted(() => {
  updateNarrow()
  window.addEventListener('resize', updateNarrow)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateNarrow)
  if (switchTimer) clearTimeout(switchTimer)
})
</script>

<style scoped>
.if-issue-tabs {
  min-height: 320px;
}

/* 竖形标签：左侧导航固定宽度 + 右侧内容留白 */
.if-issue-tabs :deep(.el-tabs__header.is-left) {
  margin-right: 16px;
}
.if-issue-tabs :deep(.el-tabs__item.is-left) {
  justify-content: flex-start;
  min-width: 108px;
  height: 42px;
  font-weight: 500;
}
.if-issue-tabs :deep(.el-tabs__item.is-active) {
  font-weight: 600;
}
.if-issue-tabs :deep(.el-tabs__content) {
  padding-left: 4px;
  overflow: visible;
}

/* 切换时的平滑过渡：淡入 + 轻微上移 */
.if-issue-tabs.is-switching :deep(.el-tabs__content) {
  animation: ifTabPanelIn 0.24s ease;
}
@keyframes ifTabPanelIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.if-tab-panel {
  padding: 2px 0 8px;
}

/* 窄屏：水平标签允许横向滚动，避免挤压 */
@media (max-width: 767px) {
  .if-issue-tabs :deep(.el-tabs__header.is-top) {
    margin-bottom: 12px;
  }
  .if-issue-tabs {
    min-height: 0;
  }
}

/* 降低动效偏好时关闭过渡动画 */
@media (prefers-reduced-motion: reduce) {
  .if-issue-tabs.is-switching :deep(.el-tabs__content) {
    animation: none;
  }
}
</style>
