<template>
  <!-- T4：问题表单 4 分区折叠容器（基本信息 / 详细描述 / 环境信息 / 附件）。
       默认全部展开；校验失败时父级通过 expand(name) 强制展开目标分区再滚动定位。 -->
  <el-collapse v-model="active" class="if-issue-sections">
    <el-collapse-item name="basic" :title="t('issue.section.basic')">
      <slot name="basic" />
    </el-collapse-item>
    <el-collapse-item name="detail" :title="t('issue.section.detail')">
      <slot name="detail" />
    </el-collapse-item>
    <el-collapse-item name="env" :title="t('issue.form.section.env')">
      <slot name="env" />
    </el-collapse-item>
    <el-collapse-item
      v-if="showAttachment"
      name="attachment"
      :title="t('issue.section.attachment')"
    >
      <slot name="attachment" />
    </el-collapse-item>
  </el-collapse>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  /** 是否渲染附件分区（编辑态不支持随表单传附件时隐藏） */
  showAttachment: { type: Boolean, default: true },
  /** 表单模式：create 仅展开基本信息；edit 全部展开（R2） */
  mode: { type: String, default: 'create' }
})

const { t } = useI18n()

/** 展开的分区集合：create 仅 basic；edit 全部展开（R2 默认展开偏差修复） */
const active = ref(props.mode === 'edit' ? ['basic', 'detail', 'env', 'attachment'] : ['basic'])

/**
 * 强制展开指定分区（校验定位用）。
 * @param {string} name basic | detail | env | attachment
 */
function expand(name) {
  if (name && !active.value.includes(name)) {
    active.value = [...active.value, name]
  }
}

defineExpose({ expand })
</script>

<style scoped>
.if-issue-sections {
  border-top: none;
}

.if-issue-sections :deep(.el-collapse-item__header) {
  font-weight: 600;
}

.if-issue-sections :deep(.el-collapse-item__content) {
  padding-top: 12px;
  padding-bottom: 8px;
}
</style>
