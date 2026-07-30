<template>
  <!-- R3 统一表单抽屉：rtl + append-to-body；size sm/md/lg = 480/620/800；
       标题 {动作}{对象}；底部左取消右保存（带 loading）；@closed 由父级重置表单 -->
  <el-drawer
    :model-value="modelValue"
    :title="title"
    direction="rtl"
    append-to-body
    :size="drawerSize"
    :close-on-click-modal="false"
    class="if-form-drawer"
    @update:model-value="onVisibleChange"
    @closed="emit('closed')"
  >
    <div class="if-form-drawer__body">
      <slot />
    </div>
    <template #footer>
      <slot name="footer">
        <div class="if-form-drawer__footer">
          <el-button @click="onCancel">{{ cancelText }}</el-button>
          <el-button type="primary" :loading="loading" @click="emit('confirm')">
            {{ confirmText }}
          </el-button>
        </div>
      </slot>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** v-model 显隐 */
  modelValue: { type: Boolean, default: false },
  /** 标题，约定 {动作}{对象}，如「新增用户」「编辑组织」 */
  title: { type: String, default: '' },
  /** 尺寸档位：sm=480 / md=620 / lg=800 */
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md', 'lg'].includes(value)
  },
  /** 保存按钮 loading */
  loading: { type: Boolean, default: false },
  /** 保存按钮文案 */
  confirmText: { type: String, default: '保存' },
  /** 取消按钮文案 */
  cancelText: { type: String, default: '取消' }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel', 'closed'])

const SIZE_MAP = { sm: 480, md: 620, lg: 800 }

/** 移动端（<=768px）降级为满宽，桌面按档位取宽 */
const drawerSize = computed(() => {
  const width = SIZE_MAP[props.size] || SIZE_MAP.md
  if (typeof window !== 'undefined' && window.innerWidth <= 768) {
    return '100%'
  }
  return `${width}px`
})

function onVisibleChange(value) {
  emit('update:modelValue', value)
}

function onCancel() {
  emit('cancel')
  emit('update:modelValue', false)
}
</script>

<style scoped>
.if-form-drawer__body {
  padding: 0 4px;
}

.if-form-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
