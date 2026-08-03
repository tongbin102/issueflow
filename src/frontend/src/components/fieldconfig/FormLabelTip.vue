<template>
  <!-- 【需求二】带问号提示的表单标签。
       用法：<el-form-item><template #label><FormLabelTip label="..." tip="..." /></template>
       - 标签文字与问号图标基线对齐，图标不参与 label-width 的宽度计算（绝对不撑行）；
       - tip 为空时只渲染文字，不留空白占位，保证没有说明的字段视觉上完全一致。 -->
  <span class="form-label-tip">
    <span class="form-label-tip__text">{{ label }}</span>
    <el-tooltip
      v-if="tip"
      :content="tip"
      :placement="placement"
      effect="dark"
      :show-after="120"
      popper-class="form-label-tip__popper"
    >
      <el-icon class="form-label-tip__icon" :aria-label="tip">
        <QuestionFilled />
      </el-icon>
    </el-tooltip>
  </span>
</template>

<script setup>
import { ElIcon, ElTooltip } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'

defineProps({
  /** 标签文字（已翻译） */
  label: { type: String, default: '' },
  /** 问号提示内容（已翻译）；空串/未传则不渲染问号 */
  tip: { type: String, default: '' },
  /** tooltip 弹出方向 */
  placement: { type: String, default: 'top' }
})
</script>

<style scoped>
.form-label-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  line-height: inherit;
}

.form-label-tip__text {
  /* 标签文字本身不换行，超长由 el-form-item 的 label-width 兜底 */
  white-space: nowrap;
}

.form-label-tip__icon {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--el-text-color-placeholder);
  cursor: help;
  transition: color 0.2s;
}

.form-label-tip__icon:hover {
  color: var(--el-color-primary);
}
</style>

<style>
/* tooltip 内容较长时限宽换行（非 scoped：popper 挂在 body 上） */
.form-label-tip__popper {
  max-width: 280px;
  line-height: 1.6;
}
</style>
