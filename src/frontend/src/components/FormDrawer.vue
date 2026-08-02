<template>
  <!-- R3 统一表单抽屉：rtl + append-to-body；size sm/md/lg = 480/620/800；
       标题 {动作}{对象}；底部左取消右保存（带 loading）；@closed 由父级重置表单。
       Phase6：新增 fullscreenable（默认 false），为 true 时头部渲染纯图标全屏切换按钮 -->
  <el-drawer
    :model-value="modelValue"
    direction="rtl"
    append-to-body
    :size="drawerSize"
    :close-on-click-modal="false"
    :before-close="beforeClose || undefined"
    class="if-form-drawer"
    :class="{ 'is-fullscreen': isFullscreen }"
    @update:model-value="onVisibleChange"
    @closed="onClosed"
  >
    <template #header="{ titleId, titleClass }">
      <div class="if-form-drawer__header">
        <span :id="titleId" :class="titleClass" class="if-form-drawer__title">{{ title }}</span>
        <!-- 全屏切换：纯图标按钮（无文字，title 提示）；#3.6：移动端隐藏（已强制全屏） -->
        <el-button
          v-if="fullscreenable && !isMobile"
          link
          class="if-form-drawer__fullscreen-btn"
          :title="isFullscreen ? t('common.action.exitFullscreen') : t('common.action.fullscreen')"
          @click="toggleFullscreen"
        >
          <el-icon :size="16">
            <Aim v-if="isFullscreen" />
            <FullScreen v-else />
          </el-icon>
        </el-button>
      </div>
    </template>
    <div class="if-form-drawer__body">
      <slot />
    </div>
    <template #footer>
      <slot name="footer">
        <div class="if-form-drawer__footer">
          <el-button @click="onCancel">{{ cancelLabel }}</el-button>
          <el-button type="primary" :loading="loading" @click="emit('confirm')">
            {{ confirmLabel }}
          </el-button>
        </div>
      </slot>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, ref, provide, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { FullScreen, Aim } from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'

const props = defineProps({
  /** v-model 显隐 */
  modelValue: { type: Boolean, default: false },
  /** 标题，约定 {动作}{对象}，如「新增用户」「编辑组织」 */
  title: { type: String, default: '' },
  /** 尺寸档位：sm=480 / md=620 / lg=800 / xl=min(1080px, 92vw) 双栏场景专用 */
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md', 'lg', 'xl'].includes(value)
  },
  /** 保存按钮 loading */
  loading: { type: Boolean, default: false },
  /** 保存按钮文案（不传时走 i18n：common.action.save） */
  confirmText: { type: String, default: '' },
  /** 取消按钮文案（不传时走 i18n：common.action.cancel） */
  cancelText: { type: String, default: '' },
  /** Phase6：是否允许全屏切换（默认 false，不渲染图标按钮，存量调用零影响） */
  fullscreenable: { type: Boolean, default: false },
  /**
   * 关闭前拦截钩子 `(done) => void`，用于「有未保存变更时二次确认」等场景。
   * 仅作用于遮罩/ESC/右上角关闭；不传时行为与之前完全一致（存量调用零影响）。
   */
  beforeClose: { type: Function, default: null }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel', 'closed'])

const { t } = useI18n()
const appStore = useAppStore()

const SIZE_MAP = { sm: 480, md: 620, lg: 800, xl: 'min(1080px, 92vw)' }

/** 全屏态：仅 fullscreenable=true 时可进入；抽屉完全关闭后自动复位 */
const isFullscreen = ref(false)

/**
 * #3.5：把全屏态以响应式 ref 下发给子组件（如 IssueFormSections），
 * 供其在全屏时把左侧竖形标签切为顶部横排。无 provide 的调用点（如 IssueDetailDrawer）
 * inject 默认 ref(false)，行为不变。
 */
provide('drawerFullscreen', isFullscreen)

/** #3.6：移动端判定（Pinia device=mobile 或视口 <=768px），用于强制全屏 + 隐藏全屏按钮 */
const isMobile = computed(
  () => appStore.isMobile || (typeof window !== 'undefined' && window.innerWidth <= 768)
)

/** #3.6：移动端打开弹窗时强制全屏（满宽 + 标签横排）；关闭后由 onClosed 复位 */
watch(
  () => props.modelValue,
  (visible) => {
    if (visible && isMobile.value) {
      isFullscreen.value = true
    }
  }
)

/** 默认按钮文案走 i18n，显式传入 confirmText/cancelText 时优先 */
const confirmLabel = computed(() => props.confirmText || t('common.action.save'))
const cancelLabel = computed(() => props.cancelText || t('common.action.cancel'))

/** 全屏 100%；移动端（<=768px）降级为满宽；桌面按档位取宽 */
const drawerSize = computed(() => {
  if (props.fullscreenable && isFullscreen.value) {
    return '100%'
  }
  if (typeof window !== 'undefined' && window.innerWidth <= 768) {
    return '100%'
  }
  const width = SIZE_MAP[props.size] || SIZE_MAP.md
  // xl 档位直接返回 CSS 表达式（自适应窄屏），其余档位为像素数值
  return typeof width === 'number' ? `${width}px` : width
})

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

function onVisibleChange(value) {
  emit('update:modelValue', value)
}

function onCancel() {
  emit('cancel')
  emit('update:modelValue', false)
}

function onClosed() {
  // 关闭动画结束后复位全屏态，下次打开恢复常规宽度
  isFullscreen.value = false
  emit('closed')
}
</script>

<style scoped>
.if-form-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}

.if-form-drawer__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.if-form-drawer__fullscreen-btn {
  flex-shrink: 0;
  margin-left: 8px;
  padding: 4px;
}

.if-form-drawer__body {
  padding: 0 4px;
}

.if-form-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
