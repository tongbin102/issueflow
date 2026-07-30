<template>
  <el-drawer
    :model-value="props.visible"
    :title="`模块管理 · ${projectName}`"
    size="620px"
    append-to-body
    @update:model-value="(v) => emit('update:visible', v)"
    @closed="onClosed"
  >
    <div class="module-tree">
      <!-- 工具条 -->
      <div class="mt-toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索模块名称"
          clearable
          :prefix-icon="Search"
          style="width: 220px"
        />
        <el-button-group>
          <el-button :icon="ArrowDown" @click="expandAll">展开</el-button>
          <el-button :icon="ArrowUp" @click="collapseAll">折叠</el-button>
        </el-button-group>
        <div class="mt-toolbar__right">
          <el-button type="primary" :icon="Plus" @click="openCreateRoot">新建根模块</el-button>
        </div>
      </div>

      <!-- 树 -->
      <el-tree
        v-loading="loading"
        ref="treeRef"
        class="mt-tree"
        :data="moduleTree"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        draggable
        show-checkbox
        :expand-on-click-node="false"
        :allow-drop="allowDrop"
        :filter-node-method="filterNode"
        @node-click="onNodeClick"
        @node-drop="onNodeDrop"
      >
        <template #default="{ data }">
          <span class="mt-node">
            <span class="mt-node__label" v-html="highlight(data.name)"></span>
            <el-tag
              v-if="data.dependencyCount"
              size="small"
              type="info"
              effect="plain"
              class="mt-node__dep"
            >依赖{{ data.dependencyCount }}</el-tag>
          </span>
        </template>
      </el-tree>

      <!-- 选中节点的单节点操作 -->
      <div class="mt-actions" v-if="currentNode">
        <span class="mt-actions__hint">
          已选：<b>{{ currentNode.pathLabel }}</b>
        </span>
        <el-button size="small" :icon="Plus" @click="openCreateChild">子模块</el-button>
        <el-button size="small" :icon="Edit" @click="openEdit">编辑</el-button>
        <el-button size="small" :icon="Link" @click="openDep">设依赖</el-button>
        <el-button size="small" type="danger" :icon="Delete" @click="onDeleteNode">删除</el-button>
      </div>
      <div class="mt-actions mt-actions--batch" v-else>
        <span class="mt-actions__hint">未选择节点（勾选复选框可做批量操作）</span>
      </div>

      <!-- 批量操作 -->
      <div class="mt-batch">
        <el-button :icon="Delete" @click="onBatchDelete">批量删除</el-button>
        <el-button :icon="Rank" @click="onBatchMove">批量移动</el-button>
      </div>
    </div>

    <!-- 新建 / 编辑 弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="formTitle"
      width="420px"
      append-to-body
    >
      <el-form label-width="72px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="模块名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="可选"
          />
        </el-form-item>
        <el-form-item label="父级">
          <span class="form-parent">{{ parentLabel }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量移动目标选择 -->
    <el-dialog v-model="moveVisible" title="批量移动到" width="420px" append-to-body>
      <el-form label-width="72px">
        <el-form-item label="目标父级">
          <el-select v-model="moveTarget" filterable placeholder="选择目标父模块" style="width: 100%">
            <el-option :value="0" label="（根级）" />
            <el-option
              v-for="m in moveTargets"
              :key="m.id"
              :value="m.id"
              :label="m.pathLabel"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBatchMove">确定移动</el-button>
      </template>
    </el-dialog>

    <!-- 依赖设置 -->
    <el-dialog v-model="depVisible" title="设置模块依赖" width="460px" append-to-body>
      <p class="dep-tip">当前模块「{{ currentNode && currentNode.name }}」所依赖的模块（单向，仅展示）。</p>
      <el-tree-select
        v-model="depIds"
        :data="moduleTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        multiple
        :render-after-expand="false"
        filterable
        clearable
        placeholder="选择依赖的模块"
        style="width: 100%"
      />
      <template #footer>
        <el-button @click="depVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDep">保存依赖</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  ArrowDown,
  ArrowUp,
  Plus,
  Edit,
  Delete,
  Link,
  Rank
} from '@element-plus/icons-vue'
import {
  listModuleTree,
  createModule,
  updateModule,
  deleteModule,
  moveModule,
  batchDeleteModule,
  batchMoveModule,
  setModuleDependencies
} from '@/api/module'

const props = defineProps({
  projectId: { type: Number, required: true },
  projectName: { type: String, default: '' },
  visible: { type: Boolean, default: false }
})
const emit = defineEmits(['update:visible', 'saved'])

const treeRef = ref(null)
const loading = ref(false)
const keyword = ref('')
const moduleTree = ref([])
const currentNode = ref(null)

/* ---------------- 工具函数 ---------------- */
function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
function highlight(name) {
  const kw = keyword.value.trim()
  if (!kw) return escapeHtml(name)
  const lower = name.toLowerCase()
  const k = kw.toLowerCase()
  const idx = lower.indexOf(k)
  if (idx < 0) return escapeHtml(name)
  return (
    escapeHtml(name.slice(0, idx)) +
    '<span class="hl">' +
    escapeHtml(name.slice(idx, idx + k.length)) +
    '</span>' +
    escapeHtml(name.slice(idx + k.length))
  )
}

/** 递归给每个节点标注全路径 pathLabel（父 > 子 > 孙）。 */
function annotate(nodes, parentPath) {
  ;(nodes || []).forEach((n) => {
    n.pathLabel = parentPath ? `${parentPath} > ${n.name}` : n.name
    annotate(n.children, n.pathLabel)
  })
}

/** 扁平化（含 pathLabel）。 */
function flatten(nodes, out = []) {
  ;(nodes || []).forEach((n) => {
    out.push({ id: n.id, name: n.name, pathLabel: n.pathLabel, parentId: n.parentId })
    flatten(n.children, out)
  })
  return out
}

/** 统计某节点子孙数量（含自身以外）。 */
function countDescendants(node) {
  let c = 0
  ;(node.children || []).forEach((ch) => {
    c += 1 + countDescendants(ch)
  })
  return c
}

/** 在树中查找节点，返回 {node, parent, siblings}。 */
function findNode(nodes, id, parent = null) {
  for (const n of nodes || []) {
    if (n.id === id) return { node: n, parent, siblings: nodes }
    const r = findNode(n.children, id, n)
    if (r) return r
  }
  return null
}

/** 收集某节点下所有子孙 id。 */
function collectDescendantIds(node, out = []) {
  ;(node.children || []).forEach((ch) => {
    out.push(ch.id)
    collectDescendantIds(ch, out)
  })
  return out
}

/* ---------------- 加载 ---------------- */
async function loadTree() {
  if (!props.projectId) return
  loading.value = true
  try {
    const data = await listModuleTree(props.projectId)
    const tree = Array.isArray(data) ? data : []
    annotate(tree, '')
    moduleTree.value = tree
    currentNode.value = null
    keyword.value = ''
    await nextTick()
    treeRef.value && treeRef.value.filter('')
  } catch (e) {
    moduleTree.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) loadTree()
  }
)
function onClosed() {
  currentNode.value = null
}

/* ---------------- 展开 / 折叠 / 过滤 ---------------- */
function expandAll() {
  const store = treeRef.value && treeRef.value.store
  if (store && store.nodesMap) Object.values(store.nodesMap).forEach((n) => (n.expanded = true))
}
function collapseAll() {
  const store = treeRef.value && treeRef.value.store
  if (store && store.nodesMap) Object.values(store.nodesMap).forEach((n) => (n.expanded = false))
}
function filterNode(value, data) {
  if (!value) return true
  return data.name.toLowerCase().includes(value.toLowerCase())
}
// 关键字变化触发 el-tree 过滤（自动展开命中路径的祖先）
watch(keyword, (v) => {
  treeRef.value && treeRef.value.filter(v)
})

/* ---------------- 选择 / 拖拽 ---------------- */
function onNodeClick(node) {
  currentNode.value = node
}
function allowDrop(draggingNode, dropNode, type) {
  if (type !== 'inner') return true
  // 禁止拖入自身子孙，避免形成环
  let p = dropNode
  while (p) {
    if (p.data && p.data.id === draggingNode.data.id) return false
    p = p.parent
  }
  return true
}
async function onNodeDrop(draggingNode) {
  const draggedId = draggingNode.data.id
  const found = findNode(moduleTree.value, draggedId)
  if (!found) return
  const parentId = found.parent ? found.parent.id : 0
  const orderedSiblingIds = found.siblings.map((s) => s.id)
  try {
    await moveModule(draggedId, { targetParentId: parentId, orderedSiblingIds })
    ElMessage.success('已移动')
  } catch (e) {
    loadTree() // 失败回滚 UI
  }
}

/* ---------------- 新建 / 编辑 ---------------- */
const formVisible = ref(false)
const formMode = ref('create') // create | edit
const formTitle = computed(() => (formMode.value === 'edit' ? '编辑模块' : '新建模块'))
const form = reactive({ id: null, parentId: 0, name: '', description: '' })
const parentLabel = computed(() => {
  if (formMode.value === 'edit') return '（保持原层级）'
  if (!form.parentId) return '（根级）'
  const f = findNode(moduleTree.value, form.parentId)
  return f ? f.node.pathLabel : '（根级）'
})

function openCreateRoot() {
  formMode.value = 'create'
  Object.assign(form, { id: null, parentId: 0, name: '', description: '' })
  formVisible.value = true
}
function openCreateChild() {
  if (!currentNode.value) {
    ElMessage.warning('请先在树上选择一个父模块')
    return
  }
  formMode.value = 'create'
  Object.assign(form, { id: null, parentId: currentNode.value.id, name: '', description: '' })
  formVisible.value = true
}
function openEdit() {
  if (!currentNode.value) {
    ElMessage.warning('请先在树上选择一个模块')
    return
  }
  formMode.value = 'edit'
  Object.assign(form, {
    id: currentNode.value.id,
    parentId: currentNode.value.parentId,
    name: currentNode.value.name,
    description: currentNode.value.description || ''
  })
  formVisible.value = true
}
const saving = ref(false)
async function submitForm() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入模块名称')
    return
  }
  saving.value = true
  try {
    if (formMode.value === 'edit') {
      await updateModule(form.id, { name: form.name, description: form.description })
    } else {
      await createModule({
        projectId: props.projectId,
        parentId: form.parentId || 0,
        name: form.name,
        description: form.description
      })
    }
    ElMessage.success('已保存')
    formVisible.value = false
    await loadTree()
    emit('saved')
  } catch (e) {
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除（单） ---------------- */
async function onDeleteNode() {
  if (!currentNode.value) {
    ElMessage.warning('请先在树上选择一个模块')
    return
  }
  const cnt = countDescendants(currentNode.value)
  const extra = cnt > 0 ? `，并将级联删除其下 ${cnt} 个子模块` : ''
  try {
    await ElMessageBox.confirm(
      `确认删除模块「${currentNode.value.name}」吗？${extra}`,
      '提示',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteModule(currentNode.value.id)
    ElMessage.success('已删除')
    await loadTree()
    emit('saved')
  } catch (e) {}
}

/* ---------------- 批量 ---------------- */
async function onBatchDelete() {
  if (!treeRef.value) return
  const ids = treeRef.value.getCheckedKeys()
  if (!ids.length) {
    ElMessage.warning('请勾选要删除的模块')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${ids.length} 个模块吗？若任一模块（含子孙）存在关联问题，将整体拒绝。`,
      '提示',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await batchDeleteModule({ projectId: props.projectId, ids })
    ElMessage.success('已删除')
    await loadTree()
    emit('saved')
  } catch (e) {}
}

const moveVisible = ref(false)
const moveTarget = ref(0)
const moveTargets = computed(() => {
  const checked = (treeRef.value ? treeRef.value.getCheckedKeys() : []) || []
  const forbidden = new Set()
  checked.forEach((id) => {
    const f = findNode(moduleTree.value, id)
    if (f) collectDescendantIds(f.node).forEach((d) => forbidden.add(d))
  })
  return flatten(moduleTree.value).filter((m) => !forbidden.has(m.id))
})
async function onBatchMove() {
  if (!treeRef.value) return
  const ids = treeRef.value.getCheckedKeys()
  if (!ids.length) {
    ElMessage.warning('请勾选要移动的模块')
    return
  }
  moveTarget.value = 0
  moveVisible.value = true
}
async function submitBatchMove() {
  if (!treeRef.value) return
  const ids = treeRef.value.getCheckedKeys()
  saving.value = true
  try {
    await batchMoveModule({
      projectId: props.projectId,
      ids,
      targetParentId: moveTarget.value || 0
    })
    ElMessage.success('已移动')
    moveVisible.value = false
    await loadTree()
    emit('saved')
  } catch (e) {
  } finally {
    saving.value = false
  }
}

/* ---------------- 依赖设置 ---------------- */
const depVisible = ref(false)
const depIds = ref([])
function openDep() {
  if (!currentNode.value) {
    ElMessage.warning('请先在树上选择一个模块')
    return
  }
  depIds.value = (currentNode.value.dependencies || []).map((d) => d.id)
  depVisible.value = true
}
async function submitDep() {
  if (!currentNode.value) return
  let ids = (depIds.value || []).slice()
  if (ids.includes(currentNode.value.id)) {
    ids = ids.filter((x) => x !== currentNode.value.id)
    ElMessage.warning('已移除对自身的依赖')
  }
  saving.value = true
  try {
    await setModuleDependencies(currentNode.value.id, ids)
    ElMessage.success('依赖已更新')
    depVisible.value = false
    await loadTree()
    emit('saved')
  } catch (e) {
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.module-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.mt-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.mt-toolbar__right {
  margin-left: auto;
}
.mt-tree {
  flex: 1;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
  min-height: 200px;
}
.mt-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.mt-node__dep {
  transform: scale(0.85);
}
.mt-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.mt-actions--batch {
  opacity: 0.7;
}
.mt-actions__hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.mt-batch {
  margin-top: 10px;
}
.dep-tip {
  margin-top: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
:deep(.hl) {
  background: #fff3a0;
  color: #b06a00;
  border-radius: 2px;
  padding: 0 1px;
}
</style>
