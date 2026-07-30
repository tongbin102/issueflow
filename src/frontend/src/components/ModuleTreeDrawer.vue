<template>
  <!-- 模块管理抽屉：仅保留 el-drawer 外壳，树交互全部复用 ModuleTreePanel -->
  <el-drawer
    :model-value="props.visible"
    :title="`模块管理 · ${projectName}`"
    size="620px"
    append-to-body
    @update:model-value="(v) => emit('update:visible', v)"
  >
    <!-- v-if 保证每次打开都重新挂载并加载最新树 -->
    <ModuleTreePanel
      v-if="props.visible && props.projectId"
      :project-id="props.projectId"
      @saved="emit('saved')"
    />
  </el-drawer>
</template>

<script setup>
import ModuleTreePanel from '@/components/ModuleTreePanel.vue'

/**
 * 模块管理抽屉（对外契约保持不变）：
 * props: projectId / projectName / visible；emits: update:visible / saved。
 */
const props = defineProps({
  projectId: { type: Number, required: true },
  projectName: { type: String, default: '' },
  visible: { type: Boolean, default: false }
})
const emit = defineEmits(['update:visible', 'saved'])
</script>
