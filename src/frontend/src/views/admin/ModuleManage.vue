<template>
  <div class="module-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>模块配置</span>
        </div>
      </template>

      <!-- 项目选择：记住上次选择（localStorage） -->
      <div v-if="projects.length" class="project-bar">
        <span class="project-bar__label">项目：</span>
        <el-select
          v-model="currentProjectId"
          filterable
          placeholder="请选择项目"
          style="width: 280px"
          @change="onProjectChange"
        >
          <el-option
            v-for="p in projects"
            :key="p.id"
            :label="p.name"
            :value="p.id"
          />
        </el-select>
      </div>

      <!-- 模块树面板 -->
      <div v-if="currentProjectId" class="panel-wrap">
        <ModuleTreePanel :project-id="currentProjectId" />
      </div>
      <el-empty
        v-else
        :description="projects.length ? '请选择一个项目以管理其模块' : '暂无可选项目，请先在「项目配置」中创建项目'"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listProjectOptions } from '@/api/project'
import ModuleTreePanel from '@/components/ModuleTreePanel.vue'

/** R6 模块配置页：项目下拉 + 复用模块树面板 */
const LAST_PROJECT_KEY = 'if_module_last_project'

const projects = ref([])
const currentProjectId = ref(null)

function onProjectChange(id) {
  try {
    localStorage.setItem(LAST_PROJECT_KEY, String(id))
  } catch (e) {
    /* 忽略持久化异常 */
  }
}

async function loadProjects() {
  try {
    const data = await listProjectOptions()
    projects.value = data || []
  } catch (e) {
    projects.value = []
  }
  if (!projects.value.length) return
  // 优先恢复上次选择的项目；不存在时默认取第一个
  let lastId = null
  try {
    const raw = localStorage.getItem(LAST_PROJECT_KEY)
    if (raw) lastId = Number(raw)
  } catch (e) {
    lastId = null
  }
  const hit = projects.value.find((p) => p.id === lastId)
  currentProjectId.value = hit ? hit.id : projects.value[0].id
  onProjectChange(currentProjectId.value)
}

onMounted(loadProjects)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.project-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.project-bar__label {
  margin-right: 8px;
  color: var(--el-text-color-regular);
}
.panel-wrap {
  min-height: 400px;
}
</style>
