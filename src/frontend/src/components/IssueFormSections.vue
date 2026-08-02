<template>
  <!-- Phase8 W2 #12：问题弹窗内容容器，由「4 分区折叠」改为「左侧竖形标签页」。
       业务页签（基本信息 / 详细描述 / 环境信息 …）由 schema.sections 动态 v-for 生成；
       系统页签（附件上传 / 关联信息 / 操作历史）固定追加，以后端 FieldSchemaVO.systemTabs 为准。
       - tab-position：全屏（drawerFullscreen）或窄屏（< 768px）时切 top 横排，否则 left 竖排（#3.5）；
       - #3.2 起标签间自由切换、不再离开校验（handleBeforeLeave 恒 true，全量校验仅在提交时执行）；
       - #3.1 各标签经 #label 插槽渲染「已填写」红点（由父级 filledTabs 驱动，按 section.code 索引）；
       - 切换时给内容区加一次性动画类，实现淡入 + 轻微位移的平滑过渡（不重挂子组件）。 -->
  <el-tabs
    v-model="active"
    :tab-position="tabPosition"
    :class="['if-issue-tabs', { 'is-switching': switching }]"
    :before-leave="handleBeforeLeave"
    @tab-change="onTabChange"
  >
    <!-- ===== 业务页签：由 schema.sections 动态生成 ===== -->
    <el-tab-pane
      v-for="section in businessSections"
      :key="section.code"
      :name="section.code"
      :label="sectionLabel(section)"
    >
      <template #label>
        <span class="if-tab-label">
          {{ sectionLabel(section) }}
          <span v-if="filledTabs[section.code]" class="if-tab-dot" />
        </span>
      </template>
      <div class="if-tab-panel"><slot :name="section.code" /></div>
    </el-tab-pane>

    <!-- ===== 系统页签：附件上传 ===== -->
    <el-tab-pane
      v-if="showAttachment && hasSystemTab('attachment')"
      name="attachment"
      :label="t('issue.tab.attachment')"
    >
      <template #label>
        <span class="if-tab-label">
          {{ t('issue.tab.attachment') }}
          <span v-if="filledTabs.attachment" class="if-tab-dot" />
        </span>
      </template>
      <div class="if-tab-panel"><slot name="attachment" /></div>
    </el-tab-pane>

    <!-- ===== 系统页签：关联信息 ===== -->
    <el-tab-pane
      v-if="showRelation && hasSystemTab('relation')"
      name="relation"
      :label="t('issue.tab.relation')"
    >
      <div class="if-tab-panel"><slot name="relation" /></div>
    </el-tab-pane>

    <!-- ===== 系统页签：操作历史 ===== -->
    <el-tab-pane
      v-if="showHistory && hasSystemTab('history')"
      name="history"
      :label="t('issue.tab.history')"
    >
      <div class="if-tab-panel"><slot name="history" /></div>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { ref, computed, inject, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  /** 字段渲染契约 FieldSchemaVO（含 sections 与 systemTabs），可空（加载前兜底） */
  schema: { type: Object, default: null },
  /** 是否渲染「附件上传」标签页 */
  showAttachment: { type: Boolean, default: true },
  /** 是否渲染「关联信息」标签页 */
  showRelation: { type: Boolean, default: true },
  /** 是否渲染「操作历史」标签页 */
  showHistory: { type: Boolean, default: true },
  /**
   * #3.2：离开当前标签前的校验钩子（历史 prop，保留仅为向后兼容）。
   * 自此版本起标签间自由切换，handleBeforeLeave 恒返回 true，本 prop 不再参与拦截。
   */
  beforeLeave: { type: Function, default: null },
  /**
   * #3.1：各标签「已填写」状态（按 section.code 索引的布尔对象，含 attachment），
   * 为真时该标签渲染「已填写」红点；由父级 IssueForm 传入，默认空对象。
   */
  filledTabs: { type: Object, default: () => ({}) },
  /** 窄屏阈值（px），低于该宽度标签页切换为水平（top） */
  narrowWidth: { type: Number, default: 768 }
})
const emit = defineEmits(['change'])

const { t, te } = useI18n()

/** 当前激活标签，默认第一个（schema 首个业务页签，否则 attachment） */
const active = ref('')

/** 业务区域列表（schema 未就绪时为空） */
const businessSections = computed(() => {
  const schema = props.schema
  if (!schema || !Array.isArray(schema.sections)) return []
  return schema.sections
})

/** 固定系统页签：优先取后端 systemTabs，未下发时兜底为三件套 */
const systemTabs = computed(() => {
  const st = props.schema && props.schema.systemTabs
  return Array.isArray(st) && st.length ? st : ['attachment', 'relation', 'history']
})
function hasSystemTab(name) {
  return systemTabs.value.includes(name)
}

/**
 * 区域展示名：i18nKey 命中翻译则用翻译，否则回退 name，再回退 code。
 *
 * @param {object} section 区域节点
 * @returns {string}
 */
function sectionLabel(section) {
  if (!section) return ''
  if (section.i18nKey && te(section.i18nKey)) return t(section.i18nKey)
  return section.name || section.code || ''
}

/* ---------------- 响应式：窄屏切水平标签 ---------------- */
const isNarrow = ref(false)
function updateNarrow() {
  isNarrow.value = typeof window !== 'undefined' && window.innerWidth < props.narrowWidth
}

/**
 * #3.5：抽屉全屏态（由 FormDrawer provide 的响应式 ref）。
 * 未被 provide 的调用点（如 IssueDetailDrawer）取默认 ref(false)，行为不变。
 */
const drawerFullscreen = inject('drawerFullscreen', ref(false))

/**
 * 标签排布：全屏（横向铺开）或窄屏时用顶部横排 top，否则左侧竖排 left。
 * 两种布局下「已填写」红点均随 #label 插槽渲染，保持可见。
 */
const tabPosition = computed(() =>
  drawerFullscreen.value || isNarrow.value ? 'top' : 'left'
)

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
 * el-tabs before-leave：#3.2 起标签间「自由切换」，不做任何校验拦截
 * （全量校验仅在点击「提交」时由 IssueForm.submit() 统一执行）。
 * 保留函数与 beforeLeave prop 仅为向后兼容，恒返回 true。
 * @returns {boolean} 恒 true，允许切换
 */
function handleBeforeLeave() {
  return true
}

/**
 * 激活指定标签（校验失败定位用）。
 * 保留 expand 作为方法名，兼容父级既有调用点。
 * @param {string} name section.code | attachment | relation | history
 */
function expand(name) {
  if (!name) return
  // 仅允许切到「实际存在」的页签，避免切到 schema 未包含的标签
  const exists =
    businessSections.value.some((s) => s.code === name) || hasSystemTab(name)
  if (exists && active.value !== name) {
    active.value = name
  }
}

/** schema 就绪后把默认激活页签设为第一个业务页签 */
function syncDefaultActive() {
  if (!active.value && businessSections.value.length) {
    active.value = businessSections.value[0].code
  } else if (!active.value) {
    active.value = 'attachment'
  }
}

defineExpose({ expand, activate: expand, active, syncDefaultActive })

// schema 异步就绪后，业务页签才出现：自动把激活页签切到首个业务页签
watch(
  () => businessSections.value.length,
  (len) => {
    if (len > 0 && (!active.value || !businessSections.value.some((s) => s.code === active.value))) {
      active.value = businessSections.value[0].code
    }
  }
)

onMounted(() => {
  updateNarrow()
  window.addEventListener('resize', updateNarrow)
  syncDefaultActive()
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

/* 竖形标签：左侧导航固定宽度 + 右侧内容留白（Phase9 T14：尺寸/字重改令牌） */
.if-issue-tabs :deep(.el-tabs__header.is-left) {
  margin-right: var(--if-space-md);
}
.if-issue-tabs :deep(.el-tabs__item.is-left) {
  justify-content: flex-start;
  min-width: 108px;
  height: 42px;
  font-weight: var(--if-weight-medium);
}
.if-issue-tabs :deep(.el-tabs__item.is-active) {
  font-weight: var(--if-weight-bold);
}
.if-issue-tabs :deep(.el-tabs__content) {
  padding-left: var(--if-space-xs);
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
  padding: 2px 0 var(--if-space-sm);
}

/* #3.1：标签文字 + 「已填写」红点。inline-flex 保证左排 / 顶排两种布局下均不溢出 */
.if-tab-label {
  display: inline-flex;
  align-items: center;
}
.if-tab-dot {
  display: inline-block;
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  margin-left: 6px;
  border-radius: 50%;
  /* Phase9 T14：红点走固定语义色，四主题一致 */
  background-color: var(--if-color-danger);
}

/* 窄屏：水平标签允许横向滚动，避免挤压 */
@media (max-width: 767px) {
  .if-issue-tabs :deep(.el-tabs__header.is-top) {
    margin-bottom: var(--if-space-sm);
  }
  .if-issue-tabs {
    min-height: 0;
  }
  /* 移动端标签热区 >= 44px */
  .if-issue-tabs :deep(.el-tabs__item) {
    height: var(--if-touch-size);
    line-height: var(--if-touch-size);
  }
}

/* 降低动效偏好时关闭过渡动画 */
@media (prefers-reduced-motion: reduce) {
  .if-issue-tabs.is-switching :deep(.el-tabs__content) {
    animation: none;
  }
}
</style>
