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
      :width="DRAWER_WIDTH"
      :subtitle="typeDrawerSubtitle"
      :loading="typeSaving"
      @confirm="onSaveType"
      @closed="resetTypeForm"
    >
      <!-- 头部徽标：系统预设 / 枚举镜像，避免误改预设类型 -->
      <template v-if="typeDrawerTag" #header-extra>
        <el-tag :type="typeDrawerTag.type" size="small" effect="light">
          {{ typeDrawerTag.label }}
        </el-tag>
      </template>

      <!-- 左内容 + 右侧垂直导航条：二者共用 typeActiveTab，天然双向同步 -->
      <div class="dict-drawer-layout">
        <div class="dict-drawer-main">
          <el-form
            ref="typeFormRef"
            :model="typeForm"
            :rules="typeRules"
            label-width="96px"
            class="dict-drawer-form"
          >
            <el-tabs v-model="typeActiveTab" class="dict-drawer-tabs">
              <el-tab-pane :label="t('dict.tab.basic')" name="basic">
                <el-form-item :label="t('dict.form.typeName')" prop="name">
                  <el-input
                    v-model="typeForm.name"
                    :placeholder="t('dict.placeholder.typeName')"
                    maxlength="50"
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
              </el-tab-pane>
              <el-tab-pane :label="t('dict.tab.desc')" name="desc">
                <el-form-item :label="t('dict.form.description')" prop="description">
                  <el-input
                    v-model="typeForm.description"
                    type="textarea"
                    :rows="4"
                    :placeholder="t('dict.placeholder.description')"
                    maxlength="200"
                    show-word-limit
                  />
                </el-form-item>
              </el-tab-pane>
              <el-tab-pane :label="t('dict.tab.config')" name="config">
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
              </el-tab-pane>
            </el-tabs>
          </el-form>
        </div>

        <nav class="dict-drawer-rail" :aria-label="t('dict.tab.nav')">
          <button
            v-for="g in typeGroups"
            :key="g.name"
            type="button"
            class="rail-item"
            :class="{ 'is-active': typeActiveTab === g.name, 'is-done': g.done }"
            :aria-current="typeActiveTab === g.name ? 'true' : undefined"
            @click="typeActiveTab = g.name"
          >
            <span class="rail-dot" aria-hidden="true"></span>
            <span class="rail-label">{{ g.label }}</span>
          </button>
        </nav>
      </div>
    </FormDrawer>

    <!-- 选项新增 / 编辑抽屉 -->
    <FormDrawer
      v-model="itemDrawerVisible"
      :title="itemEditing ? t('dict.drawer.editItem') : t('dict.drawer.createItem')"
      size="sm"
      :width="DRAWER_WIDTH"
      :subtitle="itemDrawerSubtitle"
      :loading="itemSaving"
      @confirm="onSaveItem"
      @closed="resetItemForm"
    >
      <!-- 头部徽标：所属类型是系统预设 / 枚举镜像 / 自定义 -->
      <template v-if="itemDrawerTag" #header-extra>
        <el-tag :type="itemDrawerTag.type" size="small" effect="light">
          {{ itemDrawerTag.label }}
        </el-tag>
      </template>

      <!-- 左内容 + 右侧垂直导航条：二者共用 itemActiveTab，天然双向同步 -->
      <div class="dict-drawer-layout">
        <div class="dict-drawer-main">
          <el-form
            ref="itemFormRef"
            :model="itemForm"
            :rules="itemRules"
            label-width="96px"
            class="dict-drawer-form"
          >
            <el-tabs v-model="itemActiveTab" class="dict-drawer-tabs">
              <el-tab-pane :label="t('dict.tab.basic')" name="basic">
                <!-- 「所属类型」已上移至抽屉头部 subtitle + 徽标，此处不再重复占位 -->
                <el-form-item :label="t('dict.form.itemName')" prop="name">
                  <el-input
                    v-model="itemForm.name"
                    :placeholder="t('dict.placeholder.itemName')"
                    maxlength="50"
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
                  <div v-if="isSystemItemEditing" class="form-tip">
                    {{ t('dict.tip.codeReadonly') }}
                  </div>
                </el-form-item>
              </el-tab-pane>
              <el-tab-pane :label="t('dict.tab.desc')" name="desc">
                <el-form-item :label="t('dict.form.description')" prop="description">
                  <el-input
                    v-model="itemForm.description"
                    type="textarea"
                    :rows="4"
                    :placeholder="t('dict.placeholder.description')"
                    maxlength="200"
                    show-word-limit
                  />
                </el-form-item>
              </el-tab-pane>
              <el-tab-pane :label="t('dict.tab.config')" name="config">
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
              </el-tab-pane>
            </el-tabs>
          </el-form>
        </div>

        <nav class="dict-drawer-rail" :aria-label="t('dict.tab.nav')">
          <button
            v-for="g in itemGroups"
            :key="g.name"
            type="button"
            class="rail-item"
            :class="{ 'is-active': itemActiveTab === g.name, 'is-done': g.done }"
            :aria-current="itemActiveTab === g.name ? 'true' : undefined"
            @click="itemActiveTab = g.name"
          >
            <span class="rail-dot" aria-hidden="true"></span>
            <span class="rail-label">{{ g.label }}</span>
          </button>
        </nav>
      </div>
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

const { t, locale } = useI18n()
const dictStore = useDictStore()
const appStore = useAppStore()

const isMobile = computed(() => appStore.isMobile)

/* --------------------------------------------------------------- 抽屉外观 */
/**
 * 字典抽屉统一宽度：比 sm 档（480px）更舒适，窄屏自适应收敛。
 * 640px = 表单主区（含 96px 标签列）+ 右侧 116px 分组导航条 + 间距，两栏均不拥挤。
 * 通过 FormDrawer 的 width prop 覆盖 size 档位；移动端仍由组件内部强制满宽。
 */
const DRAWER_WIDTH = 'min(640px, 94vw)'

/**
 * 抽屉内分组字段归属：右侧导航条、Tab、校验联动三者共用同一份映射，避免各写各的。
 * basic=核心标识 / desc=长描述 / config=排序与状态。
 */
const GROUP_FIELDS = {
  basic: ['name', 'code'],
  desc: ['description'],
  config: ['sort', 'enabled']
}

/**
 * 按出错字段解析其所属分组，供校验失败时自动跳转 Tab。
 * @param {string} field 表单字段名（el-form-item 的 prop）
 * @returns {string} 分组名，未命中时回退 'config'
 */
function resolveGroupByField(field) {
  if (GROUP_FIELDS.basic.includes(field)) return 'basic'
  if (GROUP_FIELDS.desc.includes(field)) return 'desc'
  return 'config'
}

/**
 * 构造右侧导航条的分组数据。
 * @param {object} form 抽屉表单对象（typeForm / itemForm）
 * @returns {Array<{name: string, label: string, done: boolean}>} 分组列表
 */
function buildGroups(form) {
  return [
    {
      name: 'basic',
      label: t('dict.tab.basic'),
      done: !!(form.name && form.code)
    },
    {
      name: 'desc',
      label: t('dict.tab.desc'),
      done: !!form.description
    },
    {
      name: 'config',
      label: t('dict.tab.config'),
      done: form.sort !== null && form.sort !== undefined
    }
  ]
}

/** 「标签 + 取值」分隔符：中文用全角冒号，其余语言用半角冒号 + 空格 */
const labelSep = computed(() => (String(locale.value).startsWith('zh') ? '：' : ': '))

/**
 * 解析字典类型的属性徽标：枚举镜像 > 系统预设 > 自定义。
 * @param {object|null} type 字典类型行数据
 * @returns {{label: string, type: string}|null} el-tag 的文案与色型；无类型时返回 null
 */
function resolveTypeTag(type) {
  if (!type) return null
  if (type.mirror) return { label: t('dict.tag.mirror'), type: 'warning' }
  if (type.isSystem) return { label: t('dict.tag.system'), type: 'info' }
  return { label: t('dict.tag.custom'), type: 'success' }
}

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
const typeActiveTab = ref('basic')
const typeEditing = ref(null)
const typeForm = reactive({ name: '', code: '', description: '', sort: 0, enabled: true })

/** 类型抽屉右侧导航条分组（label 随语言切换、done 随填写状态实时更新） */
const typeGroups = computed(() => buildGroups(typeForm))

/** 类型抽屉副标题：编辑态展示不可变更的类型编码；新增态为空（不渲染） */
const typeDrawerSubtitle = computed(() =>
  typeEditing.value && typeEditing.value.code
    ? `${t('dict.form.typeCode')}${labelSep.value}${typeEditing.value.code}`
    : ''
)

/** 类型抽屉头部徽标：仅编辑态渲染，明示系统预设 / 枚举镜像，避免误操作 */
const typeDrawerTag = computed(() => resolveTypeTag(typeEditing.value))

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
  typeActiveTab.value = 'basic'
  if (typeFormRef.value) typeFormRef.value.clearValidate()
}

function onSaveType() {
  if (!typeFormRef.value) return
  typeFormRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 切到第一个出错字段所在的分组（右侧导航条随 typeActiveTab 自动高亮同一组）
      if (invalidFields && invalidFields.length > 0) {
        typeActiveTab.value = resolveGroupByField(invalidFields[0].field)
      }
      return
    }
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
const itemActiveTab = ref('basic')
const itemEditing = ref(null)
const itemForm = reactive({ name: '', code: '', description: '', sort: 0, enabled: true })

/** 选项抽屉右侧导航条分组（结构与类型抽屉一致，保证两个抽屉观感统一） */
const itemGroups = computed(() => buildGroups(itemForm))

const isSystemItemEditing = computed(() => !!(itemEditing.value && itemEditing.value.isSystem))

/** 选项抽屉副标题：选项必属某类型，头部固定展示所属类型名 */
const itemDrawerSubtitle = computed(() =>
  activeType.value ? `${t('dict.form.belongType')}${labelSep.value}${activeType.value.name}` : ''
)

/** 选项抽屉头部徽标：展示所属类型的属性（系统预设 / 枚举镜像 / 自定义） */
const itemDrawerTag = computed(() => resolveTypeTag(activeType.value))

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
  itemActiveTab.value = 'basic'
  if (itemFormRef.value) itemFormRef.value.clearValidate()
}

function onSaveItem() {
  if (!itemFormRef.value) return
  itemFormRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      // 切到第一个出错字段所在的分组（右侧导航条随 itemActiveTab 自动高亮同一组）
      if (invalidFields && invalidFields.length > 0) {
        itemActiveTab.value = resolveGroupByField(invalidFields[0].field)
      }
      return
    }
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
  margin-top: var(--if-space-xs);
  font-size: var(--if-font-xs);
  line-height: 1.4;
  color: var(--text-secondary);
}

/* ==========================================================================
   抽屉内部：左表单主区 + 右侧垂直分组导航条
   —— 仅在 FormDrawer 默认插槽内布局，容器组件本身不改动
   ========================================================================== */
.dict-drawer-layout {
  display: flex;
  align-items: flex-start;
  gap: var(--if-space-lg);
}

.dict-drawer-main {
  flex: 1;
  /* 允许内部输入框在窄抽屉下正常收缩，避免撑破布局 */
  min-width: 0;
}

/* 右侧导航条：随内容滚动吸顶，始终可见 */
.dict-drawer-rail {
  position: sticky;
  top: 0;
  display: flex;
  flex: 0 0 116px;
  flex-direction: column;
  gap: var(--if-space-sm);
  width: 116px;
  /* 与 Tab 头部基线对齐的光学微调 */
  padding-top: var(--if-space-xs);
  padding-left: var(--if-space-md);
  border-left: 1px solid var(--el-border-color-lighter);
}

.rail-item {
  display: flex;
  align-items: center;
  gap: var(--if-space-sm);
  width: 100%;
  padding: var(--if-space-xs) var(--if-space-sm);
  border: none;
  border-radius: var(--if-radius-sm);
  background: none;
  font-family: inherit;
  font-size: var(--if-font-base);
  line-height: var(--if-line-base);
  text-align: left;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: all var(--if-transition-base);
}

.rail-item:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.rail-item:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}

/* 已填写：文字回到常规色，弱提示「这一组有内容」 */
.rail-item.is-done {
  color: var(--el-text-color-regular);
}

.rail-item.is-active {
  background: var(--el-color-primary-light-9);
  font-weight: var(--if-weight-bold);
  color: var(--el-color-primary);
}

.rail-dot {
  flex-shrink: 0;
  /* 8px 圆点为装饰性指示器，非布局间距，故不套间距令牌 */
  width: 8px;
  height: 8px;
  border-radius: var(--if-radius-pill);
  background: var(--el-border-color);
  transition: all var(--if-transition-base);
}

.rail-item.is-done .rail-dot {
  background: var(--el-text-color-placeholder);
}

.rail-item.is-active .rail-dot {
  background: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
}

.rail-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 抽屉内表单：字段间距更匀，最后一项贴底不留白 ---- */
.dict-drawer-form :deep(.el-form-item) {
  margin-bottom: var(--if-space-lg);
}

.dict-drawer-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

/* 标签层级：常规色 + 中等字重，与输入值拉开对比但不喧宾夺主 */
.dict-drawer-form :deep(.el-form-item__label) {
  font-size: var(--if-font-base);
  font-weight: var(--if-weight-medium);
  color: var(--el-text-color-regular);
}

/* 控件字号统一为基准字号，避免输入区与标签层级错位 */
.dict-drawer-form :deep(.el-input__inner),
.dict-drawer-form :deep(.el-textarea__inner) {
  font-size: var(--if-font-base);
}

/* 数字/开关类控件保持左对齐紧凑排布 */
.dict-drawer-form :deep(.el-input-number) {
  width: 160px;
}

/* ---- 抽屉内 Tab：紧凑、无多余边框，与抽屉 body 融为一体 ---- */
.dict-drawer-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--if-space-md);
}

.dict-drawer-tabs :deep(.el-tabs__item) {
  font-size: var(--if-font-base);
}

/* Tab 内容区不加额外 padding（抽屉 body 已有 24px） */
.dict-drawer-tabs :deep(.el-tab-pane) {
  padding: 0;
}

@media (max-width: 768px) {
  .filter-right {
    float: none;
  }

  /* 窄屏抽屉强制满宽：导航条改为纵向堆叠会显拥挤，直接隐藏，仅保留 Tab 切换 */
  .dict-drawer-layout {
    flex-direction: column;
    gap: 0;
  }

  .dict-drawer-rail {
    display: none;
  }
}
</style>
