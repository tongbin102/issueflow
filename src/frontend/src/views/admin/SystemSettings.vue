<template>
  <div class="system-settings">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('system.title') }}</span>
        </div>
      </template>

      <!-- 数据初始化入口（R7） -->
      <el-card shadow="never" class="setting-item">
        <div class="setting-item__body">
          <div class="setting-item__info">
            <div class="setting-item__title">{{ t('system.reset.title') }}</div>
            <div class="setting-item__desc">{{ t('system.reset.desc') }}</div>
          </div>
          <el-button
            v-perm="'system:reset'"
            v-permission="'ADMIN'"
            type="danger"
            @click="resetVisible = true"
          >{{ t('system.reset.button') }}</el-button>
        </div>

        <!-- 初始化结果：各表清理条数 -->
        <div v-if="resetCounts" class="reset-result">
          <el-alert type="success" :closable="false" show-icon :title="t('system.reset.doneTitle')" />
          <el-descriptions :column="2" border size="small" class="reset-result__table">
            <el-descriptions-item
              v-for="(count, table) in resetCounts"
              :key="table"
              :label="tableLabel(table)"
            >{{ t('system.reset.countUnit', { count }) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-card>
    </el-card>

    <!-- 数据初始化抽屉 -->
    <DataResetDrawer v-model="resetVisible" @success="onResetSuccess" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import DataResetDrawer from '@/components/DataResetDrawer.vue'

const { t, te } = useI18n()

/** R7 系统设置页：目前仅数据初始化入口，后续设置项可平级追加 el-card */
const resetVisible = ref(false)
/** 最近一次初始化结果（Map<表名, 条数>），null 表示未执行过 */
const resetCounts = ref(null)

/**
 * 表名 → 文案：优先取 system.reset.table.{table} key，缺失时回退原始表名
 */
function tableLabel(table) {
  const key = `system.reset.table.${table}`
  return te(key) ? t(key) : table
}

function onResetSuccess(counts) {
  resetCounts.value = counts && Object.keys(counts).length ? counts : null
}
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.setting-item {
  border: 1px solid var(--el-border-color-lighter);
}
.setting-item__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.setting-item__title {
  font-weight: 600;
  margin-bottom: 6px;
}
.setting-item__desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
  max-width: 720px;
}
.reset-result {
  margin-top: 16px;
}
.reset-result__table {
  margin-top: 12px;
  max-width: 640px;
}
</style>
