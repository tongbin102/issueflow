<template>
  <header class="if-page-header">
    <div class="if-page-header__main">
      <IfButton
        v-if="showBack"
        class="if-page-header__back"
        text
        :icon="ArrowLeft"
        @click="$emit('back')"
      >
        {{ t('common.action.back') }}
      </IfButton>

      <div class="if-page-header__text">
        <h1 class="if-page-header__title">
          <slot name="title">{{ title }}</slot>
        </h1>
        <p v-if="subtitle || $slots.subtitle" class="if-page-header__subtitle">
          <slot name="subtitle">{{ subtitle }}</slot>
        </p>
      </div>
    </div>

    <div v-if="$slots.actions" class="if-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
/**
 * IfPageHeader —— 页面级页头（Phase9 T7）。
 *
 * 统一「标题 + 副标题 + 右侧操作区」结构，替换各页手写的 div + h2。
 * 响应式：
 *   - 桌面 / 平板：标题与操作区左右分布；
 *   - 移动（<768px）：纵向堆叠，操作区占满一行，便于拇指点按。
 */
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import IfButton from './IfButton.vue'

defineProps({
  /** 主标题 */
  title: { type: String, default: '' },
  /** 副标题 / 说明文字 */
  subtitle: { type: String, default: '' },
  /** 是否显示返回按钮 */
  showBack: { type: Boolean, default: false }
})

defineEmits(['back'])

const { t } = useI18n()
</script>

<style scoped>
.if-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--if-space-md);
  margin-bottom: var(--if-space-md);
}

.if-page-header__main {
  display: flex;
  align-items: center;
  gap: var(--if-space-sm);
  min-width: 0;
}

.if-page-header__back {
  flex-shrink: 0;
}

.if-page-header__text {
  min-width: 0;
}

.if-page-header__title {
  margin: 0;
  font-size: var(--if-font-h1);
  font-weight: var(--if-weight-bold);
  line-height: var(--if-line-tight);
  color: var(--text-primary);
}

.if-page-header__subtitle {
  margin: var(--if-space-xs) 0 0;
  font-size: var(--if-font-sm);
  line-height: var(--if-line-base);
  color: var(--text-secondary);
}

.if-page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--if-space-sm);
  flex-shrink: 0;
}

/* 平板：标题降级为 h2（与 theme.css 断点保持一致） */
@media (max-width: 1279px) {
  .if-page-header__title {
    font-size: var(--if-font-h2);
  }
}

/* 移动端：纵向堆叠，操作区整行 */
@media (max-width: 767px) {
  .if-page-header {
    flex-direction: column;
    align-items: stretch;
    gap: var(--if-space-sm);
  }

  .if-page-header__actions {
    width: 100%;
  }

  .if-page-header__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
