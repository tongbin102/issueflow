<template>
  <!-- R3 统一表单抽屉：rtl + append-to-body；size sm/md/lg = 480/620/800；
       标题 {动作}{对象}；底部左取消右保存（带 loading）；@closed 由父级重置表单。
       Phase6：新增 fullscreenable（默认 false），为 true 时头部渲染纯图标全屏切换按钮。
       Phase9 UI 精修：新增 width（自定义宽度）/ subtitle（副标题）/ #header-extra（头部徽标槽），
       头部加主题色竖条 + 分隔线，内容区 24px 内边距并独立滚动，底部粘性栏改用 IfButton。
       —— 以上均为「加法」，未改动任何既有 prop / event / slot 的名称与语义。 -->
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
        <div class="if-form-drawer__heading">
          <!-- 左侧主题色竖条：纯装饰，读屏忽略 -->
          <span class="if-form-drawer__accent" aria-hidden="true"></span>
          <div class="if-form-drawer__titles">
            <div class="if-form-drawer__title-row">
              <span :id="titleId" :class="titleClass" class="if-form-drawer__title" :title="title">
                {{ title }}
              </span>
              <!-- 头部徽标槽：供调用方塞 el-tag / 状态标记（新增插槽，存量调用零影响） -->
              <span v-if="$slots['header-extra']" class="if-form-drawer__header-extra">
                <slot name="header-extra" />
              </span>
            </div>
            <span v-if="subtitle" class="if-form-drawer__subtitle" :title="subtitle">
              {{ subtitle }}
            </span>
          </div>
        </div>
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
          <IfButton @click="onCancel">{{ cancelLabel }}</IfButton>
          <IfButton type="primary" :loading="loading" @click="emit('confirm')">
            {{ confirmLabel }}
          </IfButton>
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
import IfButton from '@/components/base/IfButton.vue'

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
  /**
   * Phase9 UI 精修：自定义宽度（CSS 长度串，如 '560px' / 'min(560px, 92vw)'）。
   * 传入时直接覆盖 size 档位；为空（默认）时仍走 size → SIZE_MAP，存量调用零影响。
   * 全屏态与移动端仍强制 100%，优先级高于本 prop。
   */
  width: { type: String, default: '' },
  /**
   * Phase9 UI 精修：标题下方的一行弱化说明（如「类型编码：ISSUE_SOURCE」）。
   * 为空（默认）时不渲染，存量调用零影响。
   */
  subtitle: { type: String, default: '' },
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

/**
 * 宽度解析优先级：全屏 100% > 移动端 100% > 自定义 width > size 档位。
 * @returns {string} el-drawer 的 size 值（CSS 长度串）
 */
const drawerSize = computed(() => {
  if (props.fullscreenable && isFullscreen.value) {
    return '100%'
  }
  if (typeof window !== 'undefined' && window.innerWidth <= 768) {
    return '100%'
  }
  if (props.width) {
    return props.width
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
/* ==========================================================================
   头部（渲染在 el-drawer 的 #header 插槽内，属于本组件模板，走 scoped）
   ========================================================================== */
.if-form-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--if-space-sm);
  width: 100%;
  /* 与 el-drawer 右上角关闭按钮留出间距 */
  padding-right: var(--if-space-sm);
}

.if-form-drawer__heading {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: var(--if-space-sm);
  min-width: 0;
}

/* 主题色竖条：3px 宽为装饰性发丝线，无对应间距令牌；色值/圆角走令牌 */
.if-form-drawer__accent {
  flex: 0 0 auto;
  width: 3px;
  height: var(--if-font-h3);
  /* 3px 为与标题首行居中对齐的光学微调（(16*1.35-16)/2 ≈ 2.8） */
  margin-top: 3px;
  border-radius: var(--if-radius-pill);
  background: var(--theme-color, var(--if-color-processing));
}

.if-form-drawer__titles {
  flex: 1;
  min-width: 0;
}

.if-form-drawer__title-row {
  display: flex;
  align-items: center;
  gap: var(--if-space-sm);
  min-width: 0;
}

/* 覆盖 el-drawer__title 的 flex:1 / font-size:16px，避免徽标被推到最右 */
.if-form-drawer__title {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--if-font-h3);
  font-weight: var(--if-weight-bold);
  line-height: var(--if-line-tight);
  color: var(--text-primary);
}

.if-form-drawer__header-extra {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--if-space-xs);
}

.if-form-drawer__subtitle {
  display: block;
  /* 2px 为标题/副标题之间的紧凑光学间距，小于最小间距令牌（4px） */
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--if-font-xs);
  font-weight: var(--if-weight-regular);
  line-height: var(--if-line-tight);
  color: var(--text-secondary);
}

.if-form-drawer__fullscreen-btn {
  flex-shrink: 0;
  margin-left: var(--if-space-sm);
  padding: var(--if-space-xs);
}

/* ==========================================================================
   内容区（本组件模板内的包裹层）
   ========================================================================== */
.if-form-drawer__body {
  /* 作为 el-drawer__body（column flex）的唯一子项，占满剩余高度并独立滚动 */
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: var(--if-space-lg);
  font-size: var(--if-font-base);
  line-height: var(--if-line-base);
  color: var(--text-regular);
}

/* 校验提示统一：呼应系统语义色，所有抽屉内 el-form 一致 */
.if-form-drawer__body :deep(.el-form-item__error) {
  color: var(--if-color-danger);
  font-size: var(--if-font-xs);
  line-height: 1.4;
  margin-top: var(--if-space-xs);
}

.if-form-drawer__body :deep(.el-form-item.is-required > .el-form-item__label:before) {
  color: var(--if-color-danger);
}

/* ==========================================================================
   底部操作区（默认 footer 插槽内容）
   ========================================================================== */
.if-form-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--if-space-sm);
}

/* 移动端：按钮堆叠且满宽，与 IfModal 保持一致 */
@media (max-width: 767px) {
  .if-form-drawer__footer {
    flex-direction: column-reverse;
  }

  .if-form-drawer__footer :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>

<style>
/* ==========================================================================
   el-drawer 内部结构样式（header / body / footer / 面板本体）
   为什么不用 scoped + :deep：el-drawer 通过 Teleport 挂到 body，其根节点 .el-drawer
   并非本组件 subTree 的根元素，Vue 不会给它打上 data-v-* 作用域标记，
   因此 `:deep(.el-drawer__header)` 编译出的 `[data-v-x] .el-drawer__header`
   无法命中（header 是本组件插槽内容的「祖先」而非后代）。
   这里改用组件私有类名 `.if-form-drawer` 作为命名空间做限定，
   仅影响本组件渲染出的抽屉，不会污染 AdminStyleDrawer / IssueDetailDrawer 等其他 el-drawer。
   ========================================================================== */

/* 面板本体：更柔和的阴影 + 更顺滑的滑入滑出 */
.el-drawer.if-form-drawer {
  box-shadow: var(--if-shadow-lg);
  /* 注意：--if-transition-* 令牌自带缓动（0.25s ease），
     故缓动函数用独立的 transition-timing-function 覆盖，避免 shorthand 出现两个 timing-function 而整条失效 */
  transition: transform var(--if-transition-base), width var(--if-transition-base),
    box-shadow var(--if-transition-base);
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
}

/* 头部：底部分隔线；收敛 el-drawer 默认的 padding-bottom:0 + margin-bottom:32px */
.if-form-drawer .el-drawer__header {
  align-items: center;
  margin-bottom: 0;
  padding: var(--if-space-md) var(--if-space-lg);
  border-bottom: 1px solid var(--border-color);
  color: var(--text-primary);
}

/* 内容区：容器只做布局，内边距与滚动交给 .if-form-drawer__body */
.if-form-drawer .el-drawer__body {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

/* 底部：粘性操作栏（body 内部滚动，footer 始终可见） */
.if-form-drawer .el-drawer__footer {
  flex-shrink: 0;
  padding: var(--if-space-md) var(--if-space-lg);
  border-top: 1px solid var(--border-color);
  background: var(--bg-container);
}
</style>
