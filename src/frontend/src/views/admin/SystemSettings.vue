<template>
  <div class="system-settings">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>系统设置</span>
        </div>
      </template>

      <!-- 数据初始化入口（R7） -->
      <el-card shadow="never" class="setting-item">
        <div class="setting-item__body">
          <div class="setting-item__info">
            <div class="setting-item__title">数据初始化</div>
            <div class="setting-item__desc">
              清空所有业务数据（问题、项目、模块、组织、非 admin 用户等），保留角色、权限、菜单、
              系统配置与流程定义。适用于试运行结束后正式上线前的一次性清库。该操作不可撤销，请谨慎执行。
            </div>
          </div>
          <el-button
            v-perm="'system:reset'"
            v-permission="'ADMIN'"
            type="danger"
            @click="resetVisible = true"
          >初始化数据</el-button>
        </div>

        <!-- 初始化结果：各表清理条数 -->
        <div v-if="resetCounts" class="reset-result">
          <el-alert type="success" :closable="false" show-icon title="数据初始化已完成，各表清理条数如下：" />
          <el-descriptions :column="2" border size="small" class="reset-result__table">
            <el-descriptions-item
              v-for="(count, table) in resetCounts"
              :key="table"
              :label="TABLE_LABELS[table] || table"
            >{{ count }} 条</el-descriptions-item>
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
import DataResetDrawer from '@/components/DataResetDrawer.vue'

/** R7 系统设置页：目前仅数据初始化入口，后续设置项可平级追加 el-card */
const resetVisible = ref(false)
/** 最近一次初始化结果（Map<表名, 条数>），null 表示未执行过 */
const resetCounts = ref(null)

const TABLE_LABELS = {
  issue_attachment: '问题附件',
  issue_history: '问题历史',
  issue_relation: '问题关联',
  issue: '问题',
  tag: '标签',
  module_dependency: '模块依赖',
  module: '模块',
  project: '项目',
  organization: '组织',
  user: '用户（除 admin）'
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
