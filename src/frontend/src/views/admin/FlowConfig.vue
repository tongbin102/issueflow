<template>
  <div class="flow-config">
    <!-- 旧流程开关（保留，兼容 Phase3 行为） -->
    <el-card class="page-card switch-card" shadow="never">
      <template #header><span>流程开关</span></template>
      <el-form label-width="160px" label-position="right">
        <el-form-item label="允许回退（待验证→处理中）">
          <el-switch v-model="rejectEnabled" :disabled="!canConfig" @change="saveSwitch" />
        </el-form-item>
        <el-form-item label="允许重开（已关闭→待处理）">
          <el-switch v-model="reopenEnabled" :disabled="!canConfig" @change="saveSwitch" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon>
        开关关闭时，图中对应流转以灰色虚线展示且不可触发。
      </el-alert>
    </el-card>

    <!-- 流程可视化（R2） -->
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>流程图</span>
          <div v-if="canConfig" class="head-right">
            <el-button type="primary" :icon="Plus" @click="openCreateNode">新增节点</el-button>
            <el-button :icon="Plus" @click="openCreateTransition">新增流转</el-button>
            <el-button :icon="Position" :disabled="!layoutDirty" :loading="layoutSaving" @click="saveLayout">
              保存布局
            </el-button>
            <el-button :icon="RefreshLeft" @click="onResetDefault">恢复默认</el-button>
          </div>
          <el-tag v-else type="info" effect="plain">只读模式（缺少 flow:config 权限）</el-tag>
        </div>
      </template>
      <div ref="chartRef" v-loading="graphLoading" class="flow-chart"></div>
      <div class="chart-tip" v-if="canConfig">
        提示：可直接拖拽节点调整位置，调整后点击「保存布局」持久化坐标。
      </div>
    </el-card>

    <!-- 节点 / 流转双列表 -->
    <el-row :gutter="12">
      <el-col :xs="24" :md="10">
        <el-card class="page-card" shadow="never">
          <template #header><span>流程节点</span></template>
          <el-table :data="nodes" border stripe size="small">
            <el-table-column label="名称" min-width="100">
              <template #default="{ row }">
                <span class="node-dot" :style="{ background: nodeColor(row) }"></span>
                {{ row.name }}
              </template>
            </el-table-column>
            <el-table-column prop="code" label="编码" width="130" show-overflow-tooltip />
            <el-table-column label="状态码" width="90" align="center">
              <template #default="{ row }">{{ row.statusCode }}（{{ row.statusDesc }}）</template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="60" align="center" />
            <el-table-column v-if="canConfig" label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditNode(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onDeleteNode(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="14">
        <el-card class="page-card" shadow="never">
          <template #header><span>流转规则</span></template>
          <el-table :data="transitions" border stripe size="small">
            <el-table-column label="流转" min-width="150">
              <template #default="{ row }">{{ row.fromName }} → {{ row.toName }}</template>
            </el-table-column>
            <el-table-column label="动作" width="110">
              <template #default="{ row }">{{ row.actionName || row.actionCode }}</template>
            </el-table-column>
            <el-table-column label="允许角色" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ roleNames(row.allowRoles) }}</template>
            </el-table-column>
            <el-table-column label="必填备注" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.remarkRequired === 1" type="warning" size="small">是</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="transitionDisabled(row) ? 'info' : 'success'" size="small" effect="light">
                  {{ transitionDisabled(row) ? '已禁用' : '生效中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="canConfig" label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditTransition(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onDeleteTransition(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 节点表单抽屉 -->
    <FormDrawer
      v-model="nodeDrawerVisible"
      :title="nodeForm.id ? '编辑节点' : '新增节点'"
      size="sm"
      :loading="saving"
      @confirm="submitNode"
      @closed="onNodeDrawerClosed"
    >
      <el-form ref="nodeFormRef" :model="nodeForm" :rules="nodeRules" label-width="90px">
        <el-form-item label="节点名称" prop="name">
          <el-input v-model="nodeForm.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="节点编码">
          <el-input v-model="nodeForm.code" maxlength="50" placeholder="如 OPEN（可空）" />
        </el-form-item>
        <el-form-item label="绑定状态" prop="statusCode">
          <el-select v-model="nodeForm.statusCode" placeholder="选择状态码（0-4，唯一）" style="width: 100%">
            <el-option
              v-for="s in STATUS_OPTIONS"
              :key="s.value"
              :label="`${s.value} - ${s.label}`"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="节点类型">
          <el-select v-model="nodeForm.nodeType" style="width: 100%">
            <el-option :value="1" label="开始节点" />
            <el-option :value="2" label="过程节点" />
            <el-option :value="3" label="结束节点" />
          </el-select>
        </el-form-item>
        <el-form-item label="节点颜色">
          <el-color-picker v-model="nodeForm.color" />
          <span class="color-tip">留空时按状态色标显示</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="nodeForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="nodeForm.description" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
    </FormDrawer>

    <!-- 流转表单抽屉 -->
    <FormDrawer
      v-model="transitionDrawerVisible"
      :title="transitionForm.id ? '编辑流转' : '新增流转'"
      size="sm"
      :loading="saving"
      @confirm="submitTransition"
      @closed="onTransitionDrawerClosed"
    >
      <el-form ref="transitionFormRef" :model="transitionForm" :rules="transitionRules" label-width="90px">
        <el-form-item label="源节点" prop="fromNodeId">
          <el-select v-model="transitionForm.fromNodeId" placeholder="选择源节点" style="width: 100%">
            <el-option v-for="n in nodes" :key="n.id" :label="n.name" :value="n.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标节点" prop="toNodeId">
          <el-select v-model="transitionForm.toNodeId" placeholder="选择目标节点" style="width: 100%">
            <el-option v-for="n in nodes" :key="n.id" :label="n.name" :value="n.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作码" prop="actionCode">
          <el-select
            v-model="transitionForm.actionCode"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入动作码"
            style="width: 100%"
          >
            <el-option
              v-for="(label, code) in ACTION_LABELS"
              :key="code"
              :label="`${code}（${label}）`"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="动作名称">
          <el-input v-model="transitionForm.actionName" maxlength="30" placeholder="如 认领 / 提交修复" />
        </el-form-item>
        <el-form-item label="允许角色" prop="allowRolesArray">
          <el-select v-model="transitionForm.allowRolesArray" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="(label, code) in ROLE_LABELS" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填备注">
          <el-switch v-model="transitionForm.remarkRequired" />
        </el-form-item>
        <el-form-item label="关联开关">
          <el-select v-model="transitionForm.configKey" clearable placeholder="无（始终生效）" style="width: 100%">
            <el-option label="回退开关（flow_reject_enabled）" value="flow_reject_enabled" />
            <el-option label="重开开关（flow_reopen_enabled）" value="flow_reopen_enabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="transitionForm.enabled" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="transitionForm.sort" :min="0" />
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Position, RefreshLeft } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getFlowConfig, setFlowConfig } from '@/api/sysConfig'
import {
  getFlowGraph,
  createFlowNode,
  updateFlowNode,
  deleteFlowNode,
  saveFlowNodePositions,
  createFlowTransition,
  updateFlowTransition,
  deleteFlowTransition,
  resetFlowDefault
} from '@/api/flow'
import { STATUS_OPTIONS, ROLE_LABELS, ACTION_LABELS, statusColor } from '@/utils/format'
import { useUserStore } from '@/store/user'
import FormDrawer from '@/components/FormDrawer.vue'

const userStore = useUserStore()
/** 有 flow:config 才允许编辑；否则整页只读 */
const canConfig = computed(() => userStore.hasPerm('flow:config'))

/* ---------------- 旧流程开关 ---------------- */
const rejectEnabled = ref(true)
const reopenEnabled = ref(true)
const switchSaving = ref(false)

async function loadSwitch() {
  try {
    const data = await getFlowConfig()
    if (data) {
      rejectEnabled.value = data.rejectEnabled !== undefined ? !!data.rejectEnabled : true
      reopenEnabled.value = data.reopenEnabled !== undefined ? !!data.reopenEnabled : true
    }
  } catch (e) {
    console.error('[FlowConfig] loadFlowConfig failed:', e)
  }
}
function saveSwitch() {
  if (switchSaving.value) return
  switchSaving.value = true
  setFlowConfig({ rejectEnabled: rejectEnabled.value, reopenEnabled: reopenEnabled.value })
    .then(() => {
      ElMessage.success('已保存')
      renderChart() // 开关影响流转灰化展示
    })
    .catch(() => {})
    .finally(() => {
      switchSaving.value = false
    })
}

/* ---------------- 流程图数据 ---------------- */
const graphLoading = ref(false)
const nodes = ref([])
const transitions = ref([])

async function loadGraph() {
  graphLoading.value = true
  try {
    const data = await getFlowGraph()
    nodes.value = (data && data.nodes) || []
    transitions.value = (data && data.transitions) || []
  } catch (e) {
    nodes.value = []
    transitions.value = []
  } finally {
    graphLoading.value = false
    layoutDirty.value = false
    await nextTick()
    renderChart()
  }
}

function nodeColor(node) {
  return node.color || statusColor(node.statusCode)
}

/** 流转是否被禁用（enabled=0 或关联开关关闭） */
function transitionDisabled(t) {
  if (t.enabled === 0) return true
  if (t.configKey === 'flow_reject_enabled' && !rejectEnabled.value) return true
  if (t.configKey === 'flow_reopen_enabled' && !reopenEnabled.value) return true
  return false
}

/** 角色码串 → 中文缩写串（开/测/管/提） */
function roleAbbr(allowRoles) {
  return (allowRoles || '')
    .split(',')
    .map((r) => (ROLE_LABELS[r.trim()] || r.trim()).charAt(0))
    .filter(Boolean)
    .join('/')
}
function roleNames(allowRoles) {
  const names = (allowRoles || '')
    .split(',')
    .map((r) => ROLE_LABELS[r.trim()] || r.trim())
    .filter(Boolean)
  return names.length ? names.join('、') : '-'
}

/* ---------------- echarts 渲染 ---------------- */
const chartRef = ref(null)
let chart = null
const layoutDirty = ref(false)
const layoutSaving = ref(false)

function buildOption() {
  const nodeData = nodes.value.map((n) => ({
    id: String(n.id),
    name: n.name,
    x: n.posX || 0,
    y: n.posY || 0,
    symbolSize: 64,
    itemStyle: { color: nodeColor(n) },
    label: { show: true, color: '#fff', fontSize: 12 }
  }))
  const pairSet = new Set(transitions.value.map((t) => `${t.fromNodeId}-${t.toNodeId}`))
  const links = transitions.value.map((t) => {
    const disabled = transitionDisabled(t)
    const hasReverse = pairSet.has(`${t.toNodeId}-${t.fromNodeId}`)
    return {
      source: String(t.fromNodeId),
      target: String(t.toNodeId),
      label: {
        show: true,
        fontSize: 11,
        color: disabled ? '#C0C4CC' : '#606266',
        formatter: `${t.actionName || t.actionCode}(${roleAbbr(t.allowRoles)})`
      },
      lineStyle: {
        type: disabled ? 'dashed' : 'solid',
        color: disabled ? '#C0C4CC' : '#909399',
        width: 2,
        curveness: hasReverse ? 0.25 : 0
      }
    }
  })
  return {
    tooltip: { show: false },
    series: [
      {
        type: 'graph',
        layout: 'none',
        roam: true,
        draggable: canConfig.value,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 8,
        data: nodeData,
        links,
        emphasis: { focus: 'adjacency' }
      }
    ]
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
    // 节点拖拽结束：像素坐标换算回图坐标并暂存，待「保存布局」持久化
    chart.on('mouseup', (params) => {
      if (!canConfig.value) return
      if (params.dataType !== 'node') return
      const pos = chart.convertFromPixel({ seriesIndex: 0 }, [
        params.event.offsetX,
        params.event.offsetY
      ])
      const node = nodes.value[params.dataIndex]
      if (node && Array.isArray(pos)) {
        node.posX = Math.round(pos[0])
        node.posY = Math.round(pos[1])
        layoutDirty.value = true
      }
    })
  }
  chart.setOption(buildOption(), true)
}

function onResize() {
  chart && chart.resize()
}

async function saveLayout() {
  if (!layoutDirty.value) return
  layoutSaving.value = true
  try {
    await saveFlowNodePositions(
      nodes.value.map((n) => ({ id: n.id, posX: n.posX || 0, posY: n.posY || 0 }))
    )
    ElMessage.success('布局已保存')
    layoutDirty.value = false
  } catch (e) {
  } finally {
    layoutSaving.value = false
  }
}

/* ---------------- 节点表单 ---------------- */
const nodeDrawerVisible = ref(false)
const nodeFormRef = ref(null)
const saving = ref(false)
const emptyNodeForm = () => ({
  id: null,
  name: '',
  code: '',
  statusCode: null,
  nodeType: 2,
  color: '',
  sort: 0,
  description: ''
})
const nodeForm = reactive(emptyNodeForm())
const nodeRules = {
  name: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
  statusCode: [{ required: true, message: '请选择绑定状态码', trigger: 'change' }]
}

function openCreateNode() {
  Object.assign(nodeForm, emptyNodeForm())
  nodeDrawerVisible.value = true
}
function openEditNode(row) {
  Object.assign(nodeForm, {
    id: row.id,
    name: row.name || '',
    code: row.code || '',
    statusCode: row.statusCode,
    nodeType: row.nodeType || 2,
    color: row.color || '',
    sort: row.sort || 0,
    description: row.description || ''
  })
  nodeDrawerVisible.value = true
}
function onNodeDrawerClosed() {
  nodeFormRef.value && nodeFormRef.value.clearValidate()
  Object.assign(nodeForm, emptyNodeForm())
}
function submitNode() {
  if (!nodeFormRef.value) return
  nodeFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    const editing = !!nodeForm.id
    const exist = editing ? nodes.value.find((n) => n.id === nodeForm.id) : null
    const payload = {
      name: nodeForm.name,
      code: nodeForm.code,
      statusCode: nodeForm.statusCode,
      nodeType: nodeForm.nodeType,
      color: nodeForm.color,
      // 新增节点放画布中部偏移位置，编辑保持原坐标
      posX: exist ? exist.posX : 200 + nodes.value.length * 40,
      posY: exist ? exist.posY : 220,
      sort: nodeForm.sort || 0,
      description: nodeForm.description,
      enabled: 1
    }
    try {
      if (editing) {
        await updateFlowNode(nodeForm.id, payload)
        ElMessage.success('已更新')
      } else {
        await createFlowNode(payload)
        ElMessage.success('已创建')
      }
      nodeDrawerVisible.value = false
      loadGraph()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}
function onDeleteNode(row) {
  ElMessageBox.confirm(
    `确认删除节点「${row.name}」？被流转规则引用或该状态下存在问题时将被拒绝。`,
    '提示',
    { type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteFlowNode(row.id)
        ElMessage.success('已删除')
        loadGraph()
      } catch (e) {
        console.error('[FlowConfig] deleteFlowNode failed:', e)
      }
    })
    .catch(() => {})
}

/* ---------------- 流转表单 ---------------- */
const transitionDrawerVisible = ref(false)
const transitionFormRef = ref(null)
const emptyTransitionForm = () => ({
  id: null,
  fromNodeId: null,
  toNodeId: null,
  actionCode: '',
  actionName: '',
  allowRolesArray: [],
  remarkRequired: false,
  configKey: '',
  enabled: true,
  sort: 0
})
const transitionForm = reactive(emptyTransitionForm())
const transitionRules = {
  fromNodeId: [{ required: true, message: '请选择源节点', trigger: 'change' }],
  toNodeId: [{ required: true, message: '请选择目标节点', trigger: 'change' }],
  actionCode: [{ required: true, message: '请选择或输入动作码', trigger: 'change' }],
  allowRolesArray: [{ required: true, type: 'array', min: 1, message: '请至少选择一个角色', trigger: 'change' }]
}

function openCreateTransition() {
  Object.assign(transitionForm, emptyTransitionForm())
  transitionDrawerVisible.value = true
}
function openEditTransition(row) {
  Object.assign(transitionForm, {
    id: row.id,
    fromNodeId: row.fromNodeId,
    toNodeId: row.toNodeId,
    actionCode: row.actionCode || '',
    actionName: row.actionName || '',
    allowRolesArray: (row.allowRoles || '').split(',').map((s) => s.trim()).filter(Boolean),
    remarkRequired: row.remarkRequired === 1,
    configKey: row.configKey || '',
    enabled: row.enabled !== 0,
    sort: row.sort || 0
  })
  transitionDrawerVisible.value = true
}
function onTransitionDrawerClosed() {
  transitionFormRef.value && transitionFormRef.value.clearValidate()
  Object.assign(transitionForm, emptyTransitionForm())
}
function submitTransition() {
  if (!transitionFormRef.value) return
  transitionFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (transitionForm.fromNodeId === transitionForm.toNodeId) {
      ElMessage.warning('源节点与目标节点不能相同')
      return
    }
    saving.value = true
    const payload = {
      fromNodeId: transitionForm.fromNodeId,
      toNodeId: transitionForm.toNodeId,
      actionCode: transitionForm.actionCode,
      actionName: transitionForm.actionName,
      allowRoles: transitionForm.allowRolesArray.join(','),
      remarkRequired: transitionForm.remarkRequired ? 1 : 0,
      configKey: transitionForm.configKey || '',
      enabled: transitionForm.enabled ? 1 : 0,
      sort: transitionForm.sort || 0
    }
    try {
      if (transitionForm.id) {
        await updateFlowTransition(transitionForm.id, payload)
        ElMessage.success('已更新')
      } else {
        await createFlowTransition(payload)
        ElMessage.success('已创建')
      }
      transitionDrawerVisible.value = false
      loadGraph()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}
function onDeleteTransition(row) {
  ElMessageBox.confirm(`确认删除流转「${row.fromName} → ${row.toName}」？`, '提示', {
    type: 'warning'
  })
    .then(async () => {
      try {
        await deleteFlowTransition(row.id)
        ElMessage.success('已删除')
        loadGraph()
      } catch (e) {
        console.error('[FlowConfig] deleteFlowTransition failed:', e)
      }
    })
    .catch(() => {})
}

/* ---------------- 恢复默认 ---------------- */
function onResetDefault() {
  ElMessageBox.confirm(
    '确认恢复默认流程？当前全部自定义节点与流转将被清空，并重建 5 个内置节点与 6 条默认流转。',
    '提示',
    { type: 'warning' }
  )
    .then(async () => {
      try {
        await resetFlowDefault()
        ElMessage.success('已恢复默认流程')
        loadGraph()
      } catch (e) {
        console.error('[FlowConfig] resetFlowDefault failed:', e)
      }
    })
    .catch(() => {})
}

onMounted(async () => {
  await loadSwitch()
  await loadGraph()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.flow-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.switch-card {
  max-width: none;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.head-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.flow-chart {
  width: 100%;
  height: 380px;
}
.chart-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.node-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.color-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
