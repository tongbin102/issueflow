<template>
  <div class="dashboard-filters">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="true"
        />
      </el-form-item>
      <el-form-item label="版本">
        <el-select
          v-model="version"
          placeholder="全部版本"
          clearable
          filterable
          allow-create
          default-first-option
          style="width: 180px"
        >
          <el-option
            v-for="v in versions"
            :key="v"
            :label="v"
            :value="v"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps({
  // 版本候选（来自 overview 或独立接口；为空则退化为可输入）
  versions: { type: Array, default: () => [] },
  modelValue: {
    type: Object,
    default: () => ({ start: '', end: '', version: '' })
  }
})
const emit = defineEmits(['update:modelValue', 'search'])

const range = ref(
  props.modelValue && props.modelValue.start
    ? [props.modelValue.start, props.modelValue.end]
    : []
)
const version = ref(props.modelValue ? props.modelValue.version || '' : '')

const current = computed(() => ({
  start: range.value && range.value[0] ? range.value[0] : '',
  end: range.value && range.value[1] ? range.value[1] : '',
  version: version.value || ''
}))

function onSearch() {
  emit('update:modelValue', current.value)
  emit('search', current.value)
}

function onReset() {
  range.value = []
  version.value = ''
  emit('update:modelValue', { start: '', end: '', version: '' })
  emit('search', { start: '', end: '', version: '' })
}
</script>

<style scoped>
.dashboard-filters {
  margin-bottom: 12px;
}
</style>
