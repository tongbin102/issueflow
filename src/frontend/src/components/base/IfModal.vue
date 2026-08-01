<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="computedWidth"
    :align-center="true"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="true"
    :destroy-on-close="destroyOnClose"
    :append-to-body="true"
    class="if-modal"
    @closed="onClosed"
  >
    <div class="if-modal__body">
      <slot />
    </div>

    <template #footer>
      <div class="if-modal__footer">
        <slot name="footer">
          <IfButton v-if="showCancel" @click="onCancel">
            {{ cancelText || t('common.action.cancel') }}
          </IfButton>
          <IfButton :type="confirmType" :loading="confirmLoading" @click="onConfirm">
            {{ confirmText || t('common.action.confirm') }}
          </IfButton>
        </slot>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * IfModal —— 轻量对话框（Phase9 T7）。
 *
 * 边界约定（ARCH §七.5，必须遵守）：
 *   - IfModal 只用于「轻量确认 / 简短信息提示」，例如删除确认、操作结果说明；
 *   - 任何表单编辑、多字段录入、详情浏览，一律继续使用 FormDrawer / IssueDetailDrawer，
 *     不得用 IfModal 承载，避免出现两套表单容器。
 *
 * 双向绑定：modelValue / update:modelValue。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/store/app'
import IfButton from './IfButton.vue'

const props = defineProps({
  /** 显示状态（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 标题 */
  title: { type: String, default: '' },
  /** 桌面宽度 */
  width: { type: String, default: '460px' },
  /** 确认按钮文案，缺省 common.action.confirm */
  confirmText: { type: String, default: '' },
  /** 取消按钮文案，缺省 common.action.cancel */
  cancelText: { type: String, default: '' },
  /** 确认按钮类型（危险操作传 danger） */
  confirmType: { type: String, default: 'primary' },
  /** 确认按钮加载态 */
  confirmLoading: { type: Boolean, default: false },
  /** 是否显示取消按钮 */
  showCancel: { type: Boolean, default: true },
  /** 点击遮罩是否关闭 */
  closeOnClickModal: { type: Boolean, default: false },
  /** 关闭后是否销毁内容 */
  destroyOnClose: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel', 'closed'])

const { t } = useI18n()
const appStore = useAppStore()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 移动端强制近全屏，避免 460px 在小屏被裁切。 */
const computedWidth = computed(() => (appStore.isMobile ? '92%' : props.width))

/** 确认：仅抛事件，是否关闭由调用方决定（可能需要等待异步提交）。 */
function onConfirm() {
  emit('confirm')
}

/** 取消：关闭弹窗并抛事件。 */
function onCancel() {
  visible.value = false
  emit('cancel')
}

/** 动画结束后回调，便于调用方清理临时状态。 */
function onClosed() {
  emit('closed')
}
</script>

<style scoped>
.if-modal__body {
  font-size: var(--if-font-base);
  line-height: var(--if-line-base);
  color: var(--text-regular);
}

.if-modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--if-space-sm);
}

@media (max-width: 767px) {
  .if-modal__footer {
    flex-direction: column-reverse;
  }

  .if-modal__footer :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
