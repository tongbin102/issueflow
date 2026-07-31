<template>
  <!-- T2：字典配置（业务管理 > 字典配置）
       桌面端左类型列表（220px）+ 右选项表格；≤768px 左树折叠为顶部下拉 -->
  <div class="dict-manage">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="head">
          <span>{{ t('dict.page.title') }}</span>
        </div>
      </template>

      <!-- 移动端：类型选择器（替代左侧列表） -->
      <div v-if="isMobile" class="mobile-type-bar">
        <el-select
          v-model="activeTypeId"
          :placeholder="t('dict.placeholder.selectType')"
          style="width: 100%"
          @change="onTypeChange"
        >
          <el-option
            v-for="item in types"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </div>

      <div class="dict-body" :class="{ 'is-mobile': isMobile }">
        <!-- 左：类型列表（桌面端） -->
        <aside v-if="!isMobile" class="type-pane">
          <div class="pane-head">
            <span class="pane-title">{{ t('dict.page.typePanel') }}</span>
            <el-button link type="primary" :icon="Plus" @click="openTypeCreate">
              {{ t('common.action.create') }}
            </el-button>
          </div>
          <el-scrollbar class="type-scroll">
            <ul v-loading="typeLoading" class="type-list">
              <li
                v-for="item in types"
                :key="item.id"
                class="type-item"
                :class="{ 'is-active': item.id === activeTypeId }"
                @click="selectType(item)"
              >
                <div class="type-main">
                  <span class="type-name" :title="item.name">{{ item.name }}</span>
                  <el-tag size="small" effect="plain" type="info">{{ item.itemCount ?? 0 }}</el-tag>
                </div>
                <div class="type-sub">
                  <span class="type-code">{{ item.code }}</span>
                  <span class="type-ops">
                    <el-button link type="primary" size="small" @click.stop="openTypeEdit(item)">
                      {{ t('common.action.edit') }}
                    </el-button>
                    <el-tooltip
                      v-if="item.isSystem"
                      :content="t('dict.tip.systemTypeDelete')"
                      placement="top"
                    >
                      <span class="op-disabled-wrap">
                        <el-button link type="danger" size="small" disabled>
                          {{ t('common.action.delete') }}
                        </el-button>
                      </span>
                    </el-tooltip>
                    <el-button
                      v-else
                      link
                      type="danger"
                      size="small"
                      @click.stop="onDeleteType(item)"
                    >
                      {{ t('common.action.delete') }}
                    </el-button>
                  </span>
                </div>
              </li>
            </ul>
          </el-scrollbar>
        </aside>

        <!-- 右：选项表格 -->
        <section class="item-pane">
          <el-alert
            v-if="activeType && activeType.mirror"
            class="mirror-alert"
            type="info"
            :closable="false"
            show-icon
            :title="t('dict.tip.mirrorType')"
          />

          <el-form :inline="true" class="filter-form" @submit.prevent>
            <el-form-item :label="t('common.field.keyword')">
              <el-input
                v-model="itemKeyword"
                :placeholder="t('common.placeholder.search')"
                clearable
                :style="isMobile ? 'width: 100%' : 'width: 200px'"
                @keyup.enter="fetchItems"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="fetchItems">
                {{ t('common.action.search') }}
              </el-button>
              <el-button @click="onResetItemFilter">{{ t('common.action.reset') }}</el-button>
            </el-form-item>
            <el-form-item v-if="canCreateItem" class="filter-right">
              <el-button type="primary" :icon="Plus" @click="openItemCreate">
                {{ t('dict.action.createItem') }}
              </el-button>
            </el-form-item>
          </el-form>

          <el-table
            v-loading="itemLoading"
            :data="items"
            border
            stripe
            style="width: 100%"
            :empty-text="activeTypeId ? t('common.msg.noData') : t('dict.page.emptyType')"
          >
            <el-table-column
              prop="name"
              :label="t('dict.col.name')"
              min-width="140"
              show-overflow-tooltip
            />
            <el-table-column prop="code" :label="t('dict.col.code')" width="140" />
            <el-table-column
              prop="description"
              :label="t('dict.col.description')"
              min-width="160"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
            <el-table-column prop="sort" :label="t('dict.col.sort')" width="80" align="center" />
            <el-table-column :label="t('dict.col.status')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" effect="light">
                  {{ row.enabled ? t('common.status.enabled') : t('common.status.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('dict.col.refCount')" width="90" align="center">
              <template #default="{ row }">{{ row.refCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column prop="updatedAt" :label="t('dict.col.updatedAt')" width="170" />
            <el-table-column :label="t('dict.col.actions')" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openItemEdit(row)">
                  {{ t('common.action.edit') }}
                </el-button>
                <el-button
                  link
                  :type="row.enabled ? 'warning' : 'success'"
                  size="small"
                  @click="onToggleItem(row)"
                >
                  {{ row.enabled ? t('common.action.disable') : t('common.action.enable') }}
                </el-button>
                <el-tooltip
                  v-if="row.isSystem"
                  :content="t('dict.tip.systemItemDelete')"
                  placement="top"
                >
                  <span class="op-disabled-wrap">
                    <el-button link type="danger" size="small" disabled>
                      {{ t('common.action.delete') }}
                    </el-button>
                  </span>
                </el-tooltip>
                <el-button v-else link type="danger" size="small" @click="onDeleteItem(row)">
                  {{ t('common.action.delete') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </el-card>

    <!-- 类型新增 / 编辑抽屉 -->
    <FormDrawer
      v-model="typeDrawerVisible"
      :title="typeEditing ? t('dict.drawer.editType') : t('dict.drawer.createType')"
      size="sm"
      :loading="typeSaving"
      @confirm="onSaveType"
      @closed="resetTypeForm"
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="96px">
        <el-form-item :label="t('dict.form.typeName')" prop="name">
          <el-input
            v-model="typeForm.name"
            :placeholder="t('dict.placeholder.typeName')"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('dict.form.typeCode')" prop="code">
          <el-input
            v-model="typeForm.code"
            :placeholder="t('dict.placeholder.typeCode')"
            maxlength="50"
            :disabled="!!typeEditing"
          />
          <div v-if="typeEditing" class="form-tip">{{ t('dict.tip.codeReadonly') }}</div>
        </el-form-item>
        <el-form-item :label="t('dict.form.description')" prop="description">
          <el-input
            v-model="typeForm.description"
            type="textarea"
            :rows="3"
            :placeholder="t('dict.placeholder.description')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('dict.form.sort')" prop="sort">
          <el-input-number v-model="typeForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('dict.form.status')" prop="enabled">
          <el-switch
            v-model="typeForm.enabled"
            :active-text="t('common.status.enabled')"
            :inactive-text="t('common.status.disabled')"
          />
        </el-form-item>
      </el-form>
    </FormDrawer>

    <!-- 选项新增 / 编辑抽屉 -->
    <FormDrawer
      v-model="itemDrawerVisible"
      :title="itemEditing ? t('dict.drawer.editItem') : t('dict.drawer.createItem')"
      size="sm"
      :loading="itemSaving"
      @confirm="onSaveItem"
      @closed="resetItemForm"
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="96px">
        <el-form-item :label="t('dict.form.belongType')">
          <el-input :model-value="activeType ? activeType.name : ''" disabled />
        </el-form-item>
        <el-form-item :label="t('dict.form.itemName')" prop="name">
          <el-input
            v-model="itemForm.name"
            :placeholder="t('dict.placeholder.itemName')"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('dict.form.itemCode')" prop="code">
          <!-- 预设项 code 只读（后端亦静默忽略入参 code） -->
          <el-input
            v-model="itemForm.code"
            :placeholder="t('dict.placeholder.itemCode')"
            maxlength="50"
            :disabled="isSystemItemEditing"
          />
          <div v-if="isSystemItemEditing" class="form-tip">{{ t('dict.tip.codeReadonly') }}</div>
        </el-form-item>
        <el-form-item :label="t('dict.form.description')" prop="description">
          <el-input
            v-model="itemForm.description"
            type="textarea"
            :rows="3"
            :placeholder="t('dict.placeholder.description')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('dict.form.sort')" prop="sort">
          <el-input-number v-model="itemForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('dict.form.status')" prop="enabled">
          <el-switch
            v-model="itemForm.enabled"
            :active-text="t('common.status.enabled')"
            :inactive-text="t('common.status.disabled')"
          />
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import FormDrawer from '@/components/FormDrawer.vue'
import {
  listDictTypes,
  createDictType,
  updateDictType,
  deleteDictType,
  listDictItems,
  createDictItem,
  updateDictItem,
  toggleDictItemStatus,
  deleteDictItem
} from '@/api/dict'
import { useDictStore } from '@/store/dict'
import { useAppStore } from '@/store/app'

const { t } = useI18n()
const dictStore = useDictStore()
const appStore = useAppStore()

const isMobile = computed(() => appStore.isMobile)

/* ------------------------------------------------------------------ 类型 */
const typeLoading = ref(false)
const types = ref([])
const activeTypeId = ref(null)
const activeType = computed(() => types.value.find((x) => x.id === activeTypeId.value) || null)

/** 枚举镜像类型隐藏「新增选项」（后端亦硬拦截） */
const canCreateItem = computed(() => !!activeType.value && !activeType.value.mirror)

const typeDrawerVisible = ref(false)
const typeSaving = ref(false)
const typeFormRef = ref(null)
const typeEditing = ref(null)
const typeForm = reactive({ name: '', code: '', description: '', sort: 0, enabled: true })

const typeRules = computed(() => ({
  name: [{ required: true, message: t('dict.rules.nameRequired'), trigger: 'blur' }],
  code: [
    { required: true, message: t('dict.rules.codeRequired'), trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: t('dict.rules.codePattern'), trigger: 'blur' }
  ]
}))

/**
 * 拉取类型列表；首次加载自动选中第一个类型。
 * @param {number} [keepId] 刷新后需保持选中的类型 id
 */
async function fetchTypes(keepId) {
  typeLoading.value = true
  try {
    types.value = (await listDictTypes({})) || []
    const target = keepId ?? activeTypeId.value
    const hit = types.value.find((x) => x.id === target)
    activeTypeId.value = hit ? hit.id : types.value.length ? types.value[0].id : null
    await fetchItems()
  } catch (e) {
    types.value = []
    activeTypeId.value = null
  } finally {
    typeLoading.value = false
  }
}

function selectType(item) {
  if (activeTypeId.value === item.id) return
  activeTypeId.value = item.id
  itemKeyword.value = ''
  fetchItems()
}

function onTypeChange() {
  itemKeyword.value = ''
  fetchItems()
}

function openTypeCreate() {
  typeEditing.value = null
  typeDrawerVisible.value = true
}

function openTypeEdit(row) {
  typeEditing.value = row
  typeForm.name = row.name || ''
  typeForm.code = row.code || ''
  typeForm.description = row.description || ''
  typeForm.sort = row.sort ?? 0
  typeForm.enabled = row.enabled !== false
  typeDrawerVisible.value = true
}

function resetTypeForm() {
  typeEditing.value = null
  Object.assign(typeForm, { name: '', code: '', description: '', sort: 0, enabled: true })
  if (typeFormRef.value) typeFormRef.value.clearValidate()
}

function onSaveType() {
  if (!typeFormRef.value) return
  typeFormRef.value.validate(async (valid) => {
    if (!valid) return
    typeSaving.value = true
    const editing = typeEditing.value
    try {
      const payload = {
        name: typeForm.name,
        code: typeForm.code,
        description: typeForm.description,
        sort: typeForm.sort,
        enabled: typeForm.enabled
      }
      if (editing) {
        await updateDictType(editing.id, payload)
        ElMessage.success(t('dict.msg.updateTypeSuccess'))
        dictStore.invalidate(editing.code)
      } else {
        await createDictType(payload)
        ElMessage.success(t('dict.msg.createTypeSuccess'))
        dictStore.invalidate(payload.code)
      }
      typeDrawerVisible.value = false
      await fetchTypes(editing ? editing.id : activeTypeId.value)
    } catch (e) {
      // 业务错误（编码重复等）由 request 拦截器统一提示
    } finally {
      typeSaving.value = false
    }
  })
}

function onDeleteType(row) {
  ElMessageBox.confirm(
    t('dict.msg.deleteTypeConfirm', { name: row.name }),
    t('common.msg.warning'),
    { type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteDictType(row.id)
        ElMessage.success(t('dict.msg.deleteTypeSuccess'))
        dictStore.invalidate(row.code)
        if (activeTypeId.value === row.id) activeTypeId.value = null
        await fetchTypes()
      } catch (e) {
        // 预设类型 / 仍有选项等阻断由拦截器提示
      }
    })
    .catch(() => {})
}

/* ------------------------------------------------------------------ 选项 */
const itemLoading = ref(false)
const items = ref([])
const itemKeyword = ref('')

const itemDrawerVisible = ref(false)
const itemSaving = ref(false)
const itemFormRef = ref(null)
const itemEditing = ref(null)
const itemForm = reactive({ name: '', code: '', description: '', sort: 0, enabled: true })

const isSystemItemEditing = computed(() => !!(itemEditing.value && itemEditing.value.isSystem))

const itemRules = computed(() => ({
  name: [{ required: true, message: t('dict.rules.nameRequired'), trigger: 'blur' }],
  code: [
    { required: true, message: t('dict.rules.codeRequired'), trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: t('dict.rules.codePattern'), trigger: 'blur' }
  ]
}))

async function fetchItems() {
  if (!activeType.value) {
    items.value = []
    return
  }
  itemLoading.value = true
  try {
    const params = { typeCode: activeType.value?.code }
    if (itemKeyword.value) params.keyword = itemKeyword.value
    items.value = (await listDictItems(params)) || []
  } catch (e) {
    items.value = []
  } finally {
    itemLoading.value = false
  }
}

function onResetItemFilter() {
  itemKeyword.value = ''
  fetchItems()
}

function openItemCreate() {
  itemEditing.value = null
  itemDrawerVisible.value = true
}

function openItemEdit(row) {
  itemEditing.value = row
  itemForm.name = row.name || ''
  itemForm.code = row.code || ''
  itemForm.description = row.description || ''
  itemForm.sort = row.sort ?? 0
  itemForm.enabled = row.enabled !== false
  itemDrawerVisible.value = true
}

function resetItemForm() {
  itemEditing.value = null
  Object.assign(itemForm, { name: '', code: '', description: '', sort: 0, enabled: true })
  if (itemFormRef.value) itemFormRef.value.clearValidate()
}

function onSaveItem() {
  if (!itemFormRef.value) return
  itemFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!activeType.value) return
    itemSaving.value = true
    try {
      const payload = {
        typeCode: activeType.value?.code,
        name: itemForm.name,
        code: itemForm.code,
        description: itemForm.description,
        sort: itemForm.sort,
        enabled: itemForm.enabled
      }
      if (itemEditing.value) {
        await updateDictItem(itemEditing.value.id, payload)
        ElMessage.success(t('dict.msg.updateItemSuccess'))
      } else {
        await createDictItem(payload)
        ElMessage.success(t('dict.msg.createItemSuccess'))
      }
      itemDrawerVisible.value = false
      afterItemMutate()
    } catch (e) {
      // 编码重复 / 镜像类型禁新增等由拦截器提示
    } finally {
      itemSaving.value = false
    }
  })
}

async function onToggleItem(row) {
  const next = !row.enabled
  try {
    await toggleDictItemStatus(row.id, next)
    ElMessage.success(next ? t('dict.msg.switchToEnabled') : t('dict.msg.switchToDisabled'))
    afterItemMutate()
  } catch (e) {
    // 拦截器统一提示
  }
}

function onDeleteItem(row) {
  ElMessageBox.confirm(
    t('dict.msg.deleteItemConfirm', { name: row.name }),
    t('common.msg.warning'),
    { type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteDictItem(row.id)
        ElMessage.success(t('dict.msg.deleteItemSuccess'))
        afterItemMutate()
      } catch (e) {
        // 预设项 / 被引用阻断由拦截器提示
      }
    })
    .catch(() => {})
}

/** 选项写操作后：失效全站下拉缓存 + 刷新表格与类型计数 */
function afterItemMutate() {
  if (activeType.value) dictStore.invalidate(activeType.value.code)
  fetchItems()
  refreshTypeCounts()
}

/** 仅刷新左侧类型计数，不改变当前选中 */
async function refreshTypeCounts() {
  try {
    const rows = (await listDictTypes({})) || []
    types.value = rows
  } catch (e) {
    // 静默：计数刷新失败不影响主流程
  }
}

fetchTypes()
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mobile-type-bar {
  margin-bottom: 12px;
}

.dict-body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.dict-body.is-mobile {
  display: block;
}

.type-pane {
  flex: 0 0 220px;
  width: 220px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.pane-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.type-scroll {
  max-height: 560px;
}

.type-list {
  margin: 0;
  padding: 4px;
  list-style: none;
}

.type-item {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.type-item + .type-item {
  margin-top: 2px;
}

.type-item:hover {
  background: var(--el-fill-color-light);
}

.type-item.is-active {
  background: var(--el-color-primary-light-9);
}

.type-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.type-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.type-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
}

.type-code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-ops {
  flex-shrink: 0;
}

.item-pane {
  flex: 1;
  min-width: 0;
}

.mirror-alert {
  margin-bottom: 12px;
}

.filter-form {
  margin-bottom: 4px;
}

.filter-right {
  float: right;
}

.op-disabled-wrap {
  display: inline-block;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

@media (max-width: 768px) {
  .filter-right {
    float: none;
  }
}
</style>
