<template>
  <FormDrawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="xl"
    :loading="saving"
    :before-close="handleBeforeClose"
    @update:model-value="onVisibleChange"
    @closed="onClosed"
  >
    <div class="apd" :class="{ 'apd--narrow': isNarrow }">
      <!-- ============ A 区：只读 / 错误提示（固定） ============ -->
      <div v-if="isBuiltinRole || saveError || loadError" class="apd__notice">
        <el-alert
          v-if="isBuiltinRole"
          type="warning"
          :closable="false"
          show-icon
          :title="t('role.msg.builtinTip')"
        />
        <el-alert v-if="saveError" type="error" :closable="false" show-icon :title="saveError">
          <template #default>
            <div class="apd__notice-body">
              <span v-if="saveErrorDetail" class="apd__notice-detail">{{ saveErrorDetail }}</span>
              <el-button
                link
                type="primary"
                :loading="saving"
                :icon="RefreshRight"
                @click="onRetrySave"
              >
                {{ t('role.action.retry') }}
              </el-button>
            </div>
          </template>
        </el-alert>
      </div>

      <!-- ============ B 区：前后台切换 + 搜索 + 批量工具（固定） ============ -->
      <div class="apd__toolbar">
        <el-segmented v-model="activeTab" :options="tabOptions" class="apd__tabs">
          <template #default="{ item }">
            <span class="apd-tab">
              <span class="apd-tab__label">{{ item.label }}</span>
              <span class="apd-tab__badge">{{ item.count }}</span>
            </span>
          </template>
        </el-segmented>

        <el-input
          v-model="keyword"
          class="apd__search"
          clearable
          :prefix-icon="Search"
          :placeholder="t('role.placeholder.searchPerm')"
        />

        <div class="apd__tools">
          <el-button
            link
            type="primary"
            :disabled="batchDisabled"
            @click="onSelectAll"
          >{{ t('role.tree.selectAll') }}</el-button>
          <el-button
            v-if="activeQuery"
            link
            type="primary"
            :disabled="batchDisabled"
            @click="onSelectAllVisible"
          >{{ t('role.tree.selectAllVisible') }}</el-button>
          <el-button
            link
            type="primary"
            :disabled="batchDisabled"
            @click="onInvertSelect"
          >{{ t('role.tree.invertSelect') }}</el-button>
          <el-divider direction="vertical" />
          <el-button link @click="onExpandAll">{{ t('role.tree.expandAll') }}</el-button>
          <el-button link @click="onCollapseAll">{{ t('role.tree.collapseAll') }}</el-button>
        </div>
      </div>

      <!-- ============ C 区：统计条（固定） ============ -->
      <div class="apd__stats">
        <span class="apd__stats-main">{{ t('role.selected.count', { count: totalSelectedCount }) }}</span>
        <span class="apd__stats-sep">·</span>
        <span class="apd__stats-dist">
          {{ t('role.selected.distribution', { front: frontendCount, back: backendCount }) }}
        </span>
        <template v-if="orphanCount > 0">
          <span class="apd__stats-sep">·</span>
          <el-tooltip :content="t('role.selected.staleTip')" placement="top">
            <span class="apd__stats-stale" @click="focusStale">
              <el-icon><WarningFilled /></el-icon>
              {{ t('role.selected.staleCount', { count: orphanCount }) }}
            </span>
          </el-tooltip>
        </template>
        <span v-if="activeQuery" class="apd__stats-sep">·</span>
        <span v-if="activeQuery" class="apd__stats-match">
          {{ t('role.tree.matchCount', { count: matchedCurrentCodes.length }) }}
        </span>
        <span class="apd__stats-spacer" />
        <span v-if="showManyItemsTip" class="apd__stats-tip">
          <el-icon><InfoFilled /></el-icon>
          {{ t('role.tip.manyItems') }}
        </span>
      </div>

      <!-- ============ D 区：唯一滚动区（左树 / 右清单） ============ -->
      <div ref="mainRef" v-loading="loading" class="apd__main">
        <!-- 加载失败：整体错误态 -->
        <div v-if="loadError" class="apd__load-error">
          <el-empty :description="loadError">
            <el-button type="primary" :icon="RefreshRight" @click="initialize">
              {{ t('role.action.reload') }}
            </el-button>
          </el-empty>
        </div>

        <template v-else>
          <!-- ---- 左栏：权限树 ---- -->
          <section class="apd-panel apd-panel--tree">
            <header class="apd-panel__head">
              <span class="apd-panel__title">{{ currentTabLabel }}</span>
              <span class="apd-panel__sub">{{ currentTabSelectedCount }} / {{ currentTabTotal }}</span>
            </header>
            <div class="apd-panel__body">
              <!-- 空态覆盖（树保持挂载，避免勾选状态丢失） -->
              <div v-if="leftEmptyScene" class="apd-panel__empty">
                <el-empty
                  :image-size="72"
                  :description="leftEmptyDescription"
                >
                  <el-button
                    v-if="leftEmptyScene === 'search'"
                    @click="clearKeyword"
                  >{{ t('role.empty.clearSearch') }}</el-button>
                  <el-button
                    v-else-if="leftEmptyScene === 'tab'"
                    type="primary"
                    link
                    @click="switchSide"
                  >{{ otherTabLabel }}</el-button>
                </el-empty>
              </div>

              <div
                v-for="type in SIDE_TYPES"
                v-show="activeTab === type && !leftEmptyScene"
                :key="type"
                class="apd-tree-wrap"
              >
                <el-tree-v2
                  :ref="(el) => setTreeRef(type, el)"
                  :data="displayTrees[type]"
                  :props="TREE_FIELDS"
                  :height="treeHeight"
                  :item-size="30"
                  show-checkbox
                  :check-strictly="false"
                  :expand-on-click-node="false"
                  :check-on-click-node="!isBuiltinRole"
                  :check-on-click-leaf="!isBuiltinRole"
                  :default-expanded-keys="expandedKeys[type]"
                  :filter-method="filterMethod"
                  @check="() => syncFromTree(type)"
                  @node-expand="(data) => onNodeExpand(type, data)"
                  @node-collapse="(data) => onNodeCollapse(type, data)"
                >
                  <template #default="{ data }">
                    <span class="apd-node" :class="{ 'is-group': !data.isLeaf }">
                      <span class="apd-node__label" v-html="highlight(data.label)" />
                      <span
                        v-if="data.isLeaf"
                        class="apd-node__code"
                        v-html="highlight(data.code)"
                      />
                      <template v-else>
                        <span class="apd-node__count">
                          ({{ groupCheckedCount(data) }}/{{ groupTotalCount(data) }})
                        </span>
                        <el-tooltip
                          v-if="data.uncategorized"
                          :content="t('role.tree.uncategorizedTip')"
                          placement="top"
                        >
                          <el-icon class="apd-node__info"><InfoFilled /></el-icon>
                        </el-tooltip>
                        <span v-if="!isBuiltinRole" class="apd-node__ops">
                          <el-button
                            link
                            type="primary"
                            @click.stop="setGroupChecked(data, true)"
                          >{{ t('role.tree.selectGroup') }}</el-button>
                          <el-button
                            link
                            type="info"
                            @click.stop="setGroupChecked(data, false)"
                          >{{ t('role.tree.clearGroup') }}</el-button>
                        </span>
                      </template>
                    </span>
                  </template>
                </el-tree-v2>
              </div>
            </div>
          </section>

          <!-- ---- 右栏：已选清单 ---- -->
          <section class="apd-panel apd-panel--selected">
            <header class="apd-panel__head">
              <span class="apd-panel__title">{{ t('role.selected.title') }}</span>
              <span class="apd-panel__sub">{{ t('role.selected.count', { count: scopeCount }) }}</span>
              <span class="apd-panel__head-spacer" />
              <el-segmented
                v-model="selectedScope"
                size="small"
                :options="scopeOptions"
                class="apd-panel__scope"
              />
              <el-button
                link
                type="danger"
                :disabled="isBuiltinRole || scopeCount === 0"
                @click="onClearScope"
              >{{ t('role.selected.clear') }}</el-button>
            </header>

            <el-scrollbar
              ref="selectedScrollRef"
              class="apd-panel__body apd-selected"
              :height="listHeight + 'px'"
              @scroll="onSelectedScroll"
            >
              <el-empty
                v-if="selectedRows.length === 0"
                :image-size="60"
                :description="t('role.selected.count', { count: 0 })"
              />
              <template v-else>
                <div v-for="row in visibleRows" :key="row.key" class="apd-row-holder">
                  <!-- 分组头 -->
                  <div
                    v-if="row.kind === 'group'"
                    class="apd-sgroup"
                    :class="{ 'is-stale': row.stale }"
                    @click="toggleGroupCollapse(row.groupKey)"
                  >
                    <el-icon class="apd-sgroup__caret" :class="{ 'is-collapsed': row.collapsed }">
                      <CaretBottom />
                    </el-icon>
                    <el-icon v-if="row.stale" class="apd-sgroup__warn"><WarningFilled /></el-icon>
                    <span class="apd-sgroup__label">{{ row.label }}</span>
                    <span class="apd-sgroup__count">{{ row.count }}</span>
                  </div>
                  <!-- 权限项 -->
                  <div v-else class="apd-sitem" :class="{ 'is-stale': row.stale }">
                    <span class="apd-sitem__name">{{ row.name }}</span>
                    <span class="apd-sitem__code">{{ row.code }}</span>
                    <el-tooltip v-if="row.stale" :content="t('role.selected.staleTip')" placement="top">
                      <el-tag size="small" type="info" class="apd-sitem__tag">
                        {{ t('role.selected.stale') }}
                      </el-tag>
                    </el-tooltip>
                    <el-tooltip :content="t('role.selected.remove')" placement="top">
                      <el-button
                        class="apd-sitem__remove"
                        link
                        :disabled="isBuiltinRole"
                        :icon="Close"
                        @click="removeSelected(row)"
                      />
                    </el-tooltip>
                  </div>
                </div>
              </template>
            </el-scrollbar>
          </section>
        </template>
      </div>
    </div>

    <!-- ============ E 区：底部操作（固定） ============ -->
    <template #footer>
      <div class="apd__footer">
        <span class="apd__footer-hint">
          <template v-if="!isBuiltinRole && hasChanges">
            <span class="apd__delta apd__delta--add">+{{ addedCodes.length }}</span>
            <span class="apd__delta apd__delta--remove">-{{ removedCodes.length }}</span>
          </template>
        </span>
        <div class="apd__footer-actions">
          <el-button :disabled="saving" @click="requestClose">
            {{ t('common.action.cancel') }}
          </el-button>
          <el-button
            v-if="!isBuiltinRole"
            :disabled="!hasChanges || saving"
            @click="onReset"
          >{{ t('role.action.reset') }}</el-button>
          <el-tooltip
            v-if="!isBuiltinRole"
            :disabled="hasChanges"
            :content="t('role.msg.noChanges')"
            placement="top"
          >
            <span class="apd__save-wrap">
              <el-button
                type="primary"
                :loading="saving"
                :disabled="!hasChanges || saving || loading || !!loadError"
                @click="onSaveClick"
              >{{ t('common.action.save') }}</el-button>
            </span>
          </el-tooltip>
        </div>
      </div>
    </template>
  </FormDrawer>

  <!-- ============ 保存前的变更确认 ============ -->
  <el-dialog
    v-model="confirmVisible"
    append-to-body
    width="560px"
    :title="t('role.diff.title')"
    class="apd-diff"
  >
    <p class="apd-diff__subtitle">{{ t('role.diff.subtitle', { role: roleName }) }}</p>

    <div v-if="addedCodes.length" class="apd-diff__block">
      <div class="apd-diff__head apd-diff__head--add">
        {{ t('role.diff.added', { count: addedCodes.length }) }}
      </div>
      <ul class="apd-diff__list">
        <li v-for="code in visibleAdded" :key="'a-' + code">
          <span class="apd-diff__name">{{ displayName(code) }}</span>
          <span class="apd-diff__code">{{ code }}</span>
        </li>
      </ul>
    </div>

    <div v-if="removedCodes.length" class="apd-diff__block">
      <div class="apd-diff__head apd-diff__head--remove">
        {{ t('role.diff.removed', { count: removedCodes.length }) }}
      </div>
      <ul class="apd-diff__list">
        <li v-for="code in visibleRemoved" :key="'r-' + code">
          <el-icon v-if="!permIndexByCode.has(code)" class="apd-diff__warn"><WarningFilled /></el-icon>
          <span class="apd-diff__name">{{ displayName(code) }}</span>
          <span class="apd-diff__code">{{ code }}</span>
        </li>
      </ul>
    </div>

    <div class="apd-diff__footline">
      <el-button v-if="diffTruncated" link type="primary" @click="diffExpanded = true">
        {{ t('role.diff.viewAll') }}
      </el-button>
      <span class="apd-diff__total">{{ t('role.diff.total', { count: finalCodes.length }) }}</span>
    </div>

    <template #footer>
      <el-button @click="confirmVisible = false">{{ t('role.diff.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="doSave">
        {{ t('role.diff.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 角色「分配权限」抽屉。
 *
 * 设计要点：
 * 1. 前台端(type=1) / 后台端(type=2) 两端状态完全独立（已选集合 / 展开态 / 滚动位置各自保存），
 *    切换 Tab 不发请求、不保存；提交时三个集合合并为单一 permissionCodes 数组。
 * 2. 权限树由 code 的 `module:resource:action` 分段在前端派生；只有叶子是真实权限，
 *    一二级为虚拟聚合节点，不参与提交。
 * 3. 目录中不存在的角色权限码（陈旧码）单独存放于 orphanSelected，默认保留，绝不隐式丢弃。
 *
 * 接口 / PermissionVO 契约保持不变。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CaretBottom,
  Close,
  InfoFilled,
  RefreshRight,
  Search,
  WarningFilled
} from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { getRolePermissions, assignRolePermissions } from '@/api/role'
import { listPermissions } from '@/api/permission'
import FormDrawer from '@/components/FormDrawer.vue'

const props = defineProps({
  /** v-model 显隐 */
  modelValue: { type: Boolean, default: false },
  /** 当前角色（至少包含 id / name / builtin） */
  role: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const { t, te } = useI18n()

/* ============================== 常量 ============================== */

/** 端维度：1=前台端，2=后台端 */
const FRONTEND = 1
const BACKEND = 2
const SIDE_TYPES = [FRONTEND, BACKEND]
/** type 非 1/2 时归入后台端的「未分类」虚拟分组 */
const UNCAT_KEY = '__uncategorized__'
/** module 缺失时的兜底分组（复用既有 role.permModule.other 词条） */
const FALLBACK_MODULE = 'other'
/** 右清单「已失效」分组 key */
const STALE_GROUP_KEY = '__stale__'
/** 右清单单批渲染条数（>100 条时分批渲染，避免一次性挂载过多 DOM） */
const RENDER_CHUNK = 120
/** 面板头部高度，用于换算树 / 清单的可视高度 */
const PANEL_HEAD_HEIGHT = 44
/** 搜索防抖 */
const SEARCH_DEBOUNCE = 250
/** 双栏断点 */
const TWO_COLUMN_BREAKPOINT = 992
/** 「权限项较多」提示阈值 */
const MANY_ITEMS_THRESHOLD = 200
/** diff 明细折叠阈值 */
const DIFF_PREVIEW_LIMIT = 10

const SCOPE_CURRENT = 'current'
const SCOPE_ALL = 'all'

/* ============================== 状态 ============================== */

const allPermissions = ref([])
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const saveError = ref('')
const saveErrorDetail = ref('')
const failCount = ref(0)

const activeTab = ref(BACKEND)
/** 核心：两端各自的已选权限码集合（始终整体替换，保证响应式可靠） */
const selectedByType = reactive({ [FRONTEND]: new Set(), [BACKEND]: new Set() })
/** 角色拥有但目录中已不存在的陈旧权限码 */
const orphanSelected = ref(new Set())
/** 打开时的完整快照，用于 diff 与重置 */
const originalSelected = ref(new Set())

const keyword = ref('')
const debouncedKeyword = ref('')

/** 下发给树的展开态（仅整体替换，替换即触发树重新展开） */
const expandedKeys = reactive({ [FRONTEND]: [], [BACKEND]: [] })
/** 非响应式：跟踪用户实时展开态，用于搜索前快照 / 搜索后恢复 */
const liveExpanded = { [FRONTEND]: new Set(), [BACKEND]: new Set() }
/** 进入搜索前的展开态快照 */
const expandedSnapshot = { [FRONTEND]: null, [BACKEND]: null }

const selectedScope = ref(SCOPE_CURRENT)
const collapsedGroups = reactive({})
const renderLimit = ref(RENDER_CHUNK)

const confirmVisible = ref(false)
const diffExpanded = ref(false)

/** 树实例（非响应式，仅在事件/方法中读取） */
const treeRefs = {}
const mainRef = ref(null)
const selectedScrollRef = ref(null)
const mainHeight = ref(0)
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1280)

/** el-tree-v2 字段映射（对象身份必须稳定） */
const TREE_FIELDS = {
  value: 'key',
  label: 'label',
  children: 'children',
  disabled: 'disabled',
  class: (data) => (data && data.isLeaf && isCodeSelected(data) ? 'apd-row is-selected' : 'apd-row')
}

/* ========================= 基础派生 / 工具 ========================= */

const isBuiltinRole = computed(() => !!(props.role && props.role.builtin))
const roleName = computed(() => (props.role && (props.role.name || props.role.code)) || '')
const drawerTitle = computed(() =>
  roleName.value ? `${t('role.action.assignPerm')} · ${roleName.value}` : t('role.action.assignPerm')
)
const isNarrow = computed(() => viewportWidth.value < TWO_COLUMN_BREAKPOINT)

/** 归一化端维度：1→1，2→2，其余（null/0/未知）→2 */
function normalizeType(value) {
  return Number(value) === FRONTEND ? FRONTEND : BACKEND
}

/** 是否被后端明确标注了端维度 */
function isTaggedType(value) {
  const n = Number(value)
  return n === FRONTEND || n === BACKEND
}

/** 权限模块名 → 文案：优先 role.permModule.{module}，缺失时回退原始 module 英文值 */
function moduleLabel(module) {
  const key = `role.permModule.${module}`
  return te(key) ? t(key) : module
}

function sideLabel(type) {
  return type === FRONTEND ? t('role.tab.frontend') : t('role.tab.backend')
}

function groupLabelOf(moduleKey) {
  return moduleKey === UNCAT_KEY ? t('role.tree.uncategorized') : moduleLabel(moduleKey)
}

function normalizeQuery(value) {
  return String(value == null ? '' : value).trim().toLowerCase()
}

/** 排序：order（后端 sort）升序 → key 字典序 */
function byOrderThenKey(a, b) {
  const diff = (a.order || 0) - (b.order || 0)
  return diff !== 0 ? diff : String(a.key).localeCompare(String(b.key))
}

const permIndexByCode = computed(() => {
  const map = new Map()
  ;(allPermissions.value || []).forEach((vo) => {
    if (vo && vo.code !== undefined && vo.code !== null && vo.code !== '') {
      map.set(String(vo.code), vo)
    }
  })
  return map
})

function displayName(code) {
  const vo = permIndexByCode.value.get(code)
  return (vo && vo.name) || code
}

/* ============================ 权限树派生 ============================ */

/**
 * 由权限目录派生两棵树（前台 / 后台）。
 * 层级来源于 code 的 `:` 分段：≥3 段 → module/resource/action；
 * 2 段 → module/action；≤1 段 → 直接挂在 module 下（按实际段数收敛）。
 */
const treeModel = computed(() => {
  const list = allPermissions.value || []
  const codeMeta = new Map()
  const buckets = { [FRONTEND]: new Map(), [BACKEND]: new Map() }

  list.forEach((vo) => {
    if (!vo || vo.code === undefined || vo.code === null || vo.code === '') return
    const code = String(vo.code)
    if (codeMeta.has(code)) return

    const tagged = isTaggedType(vo.type)
    const type = normalizeType(vo.type)
    const segs = code
      .split(':')
      .map((s) => s.trim())
      .filter(Boolean)
    const moduleKey = tagged ? segs[0] || vo.module || FALLBACK_MODULE : UNCAT_KEY
    const resourceKey = tagged && segs.length >= 3 ? segs[1] : ''
    const order = Number.isFinite(Number(vo.sort)) ? Number(vo.sort) : 0

    codeMeta.set(code, { vo, type, tagged, moduleKey, resourceKey, order })

    const mods = buckets[type]
    let mod = mods.get(moduleKey)
    if (!mod) {
      mod = {
        key: `mod:${type}:${moduleKey}`,
        label: groupLabelOf(moduleKey),
        type,
        uncategorized: moduleKey === UNCAT_KEY,
        order: moduleKey === UNCAT_KEY ? Number.MAX_SAFE_INTEGER : order,
        subs: new Map(),
        leaves: []
      }
      mods.set(moduleKey, mod)
    } else if (moduleKey !== UNCAT_KEY) {
      mod.order = Math.min(mod.order, order)
    }

    const leaf = {
      key: code,
      code,
      label: vo.name || code,
      type,
      isLeaf: true,
      uncategorized: !tagged,
      order,
      searchText: [
        vo.name,
        code,
        mod.label,
        tagged ? moduleKey : '',
        vo.module,
        vo.module ? moduleLabel(vo.module) : '',
        resourceKey,
        vo.action
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
    }

    if (resourceKey) {
      let res = mod.subs.get(resourceKey)
      if (!res) {
        res = {
          key: `res:${type}:${moduleKey}:${resourceKey}`,
          label: resourceKey,
          type,
          uncategorized: false,
          order,
          leaves: []
        }
        mod.subs.set(resourceKey, res)
      } else {
        res.order = Math.min(res.order, order)
      }
      res.leaves.push(leaf)
    } else {
      mod.leaves.push(leaf)
    }
  })

  const trees = { [FRONTEND]: [], [BACKEND]: [] }
  const leavesByType = { [FRONTEND]: [], [BACKEND]: [] }
  const groupKeysByType = { [FRONTEND]: [], [BACKEND]: [] }
  const rootKeysByType = { [FRONTEND]: [], [BACKEND]: [] }
  const groupLeafCodes = new Map()
  const ancestorsByCode = new Map()

  SIDE_TYPES.forEach((type) => {
    Array.from(buckets[type].values())
      .sort(byOrderThenKey)
      .forEach((mod) => {
        const children = []
        const modCodes = []

        Array.from(mod.subs.values())
          .sort(byOrderThenKey)
          .forEach((res) => {
            const resLeaves = res.leaves.slice().sort(byOrderThenKey)
            const resCodes = resLeaves.map((leaf) => leaf.code)
            resLeaves.forEach((leaf) => {
              ancestorsByCode.set(leaf.code, [mod.key, res.key])
              leavesByType[type].push(leaf)
            })
            groupLeafCodes.set(res.key, resCodes)
            groupKeysByType[type].push(res.key)
            modCodes.push(...resCodes)
            children.push({
              key: res.key,
              label: res.label,
              type,
              isLeaf: false,
              uncategorized: false,
              order: res.order,
              children: resLeaves
            })
          })

        mod.leaves
          .slice()
          .sort(byOrderThenKey)
          .forEach((leaf) => {
            ancestorsByCode.set(leaf.code, [mod.key])
            leavesByType[type].push(leaf)
            modCodes.push(leaf.code)
            children.push(leaf)
          })

        if (!children.length) return
        children.sort(byOrderThenKey)
        groupLeafCodes.set(mod.key, modCodes)
        groupKeysByType[type].push(mod.key)
        rootKeysByType[type].push(mod.key)
        trees[type].push({
          key: mod.key,
          label: mod.label,
          type,
          isLeaf: false,
          uncategorized: mod.uncategorized,
          order: mod.order,
          children
        })
      })
  })

  return { trees, leavesByType, groupKeysByType, rootKeysByType, groupLeafCodes, ancestorsByCode, codeMeta }
})

/** 内置角色：整棵树只读（克隆一份并打 disabled，非内置时零开销直接复用） */
const displayTrees = computed(() => {
  const base = treeModel.value.trees
  if (!isBuiltinRole.value) return base
  return { [FRONTEND]: cloneDisabled(base[FRONTEND]), [BACKEND]: cloneDisabled(base[BACKEND]) }
})

function cloneDisabled(nodes) {
  return (nodes || []).map((node) =>
    node.children
      ? { ...node, disabled: true, children: cloneDisabled(node.children) }
      : { ...node, disabled: true }
  )
}

/* ============================ 计数 / 统计 ============================ */

const frontendCount = computed(() => selectedByType[FRONTEND].size)
const backendCount = computed(() => selectedByType[BACKEND].size)
const orphanCount = computed(() => orphanSelected.value.size)
const totalSelectedCount = computed(
  () => frontendCount.value + backendCount.value + orphanCount.value
)

const currentTabTotal = computed(
  () => (treeModel.value.leavesByType[activeTab.value] || []).length
)
const currentTabSelectedCount = computed(() => selectedByType[activeTab.value].size)
const currentTabLabel = computed(() => sideLabel(activeTab.value))
const otherSide = computed(() => (activeTab.value === FRONTEND ? BACKEND : FRONTEND))
const otherTabLabel = computed(() => sideLabel(otherSide.value))

const tabOptions = computed(() => [
  { label: t('role.tab.frontend'), value: FRONTEND, count: frontendCount.value },
  { label: t('role.tab.backend'), value: BACKEND, count: backendCount.value }
])

const scopeOptions = computed(() => [
  { label: t('role.selected.scopeCurrent'), value: SCOPE_CURRENT },
  { label: t('role.selected.scopeAll'), value: SCOPE_ALL }
])

const showManyItemsTip = computed(() => currentTabTotal.value > MANY_ITEMS_THRESHOLD)

/* ============================== 搜索 ============================== */

const activeQuery = computed(() => normalizeQuery(debouncedKeyword.value))

/** 当前端命中的权限码（无关键字时即全部） */
const matchedCurrentCodes = computed(() => {
  const leaves = treeModel.value.leavesByType[activeTab.value] || []
  const q = activeQuery.value
  if (!q) return leaves.map((leaf) => leaf.code)
  return leaves.filter((leaf) => leaf.searchText.includes(q)).map((leaf) => leaf.code)
})

/** 只让「命中的叶子」通过过滤：分组节点由 el-tree-v2 依据后代命中情况自动保留 */
function filterMethod(query, data) {
  const q = normalizeQuery(query)
  if (!q) return true
  return !!(data && data.isLeaf && data.searchText && data.searchText.includes(q))
}

function escapeHtml(value) {
  return String(value == null ? '' : value).replace(
    /[&<>"']/g,
    (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]
  )
}

/** 命中片段高亮（先转义再插入 <mark>，杜绝注入） */
function highlight(text) {
  const raw = String(text == null ? '' : text)
  const q = activeQuery.value
  if (!q) return escapeHtml(raw)
  const lower = raw.toLowerCase()
  let out = ''
  let from = 0
  let idx = lower.indexOf(q)
  while (idx !== -1) {
    out += escapeHtml(raw.slice(from, idx))
    out += `<mark class="apd-mark">${escapeHtml(raw.slice(idx, idx + q.length))}</mark>`
    from = idx + q.length
    idx = lower.indexOf(q, from)
  }
  return out + escapeHtml(raw.slice(from))
}

function clearKeyword() {
  keyword.value = ''
  debouncedKeyword.value = ''
  applyFilterAll('')
}

let searchTimer = null
watch(keyword, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    debouncedKeyword.value = value
  }, SEARCH_DEBOUNCE)
})

watch(debouncedKeyword, (value) => applyFilterAll(value))

/** 两端同时应用过滤：进入搜索时快照展开态，退出搜索时恢复 */
function applyFilterAll(rawKeyword) {
  const q = normalizeQuery(rawKeyword)
  SIDE_TYPES.forEach((type) => {
    const tree = treeRefs[type]
    if (!tree) return
    if (q && expandedSnapshot[type] === null) {
      expandedSnapshot[type] = new Set(liveExpanded[type])
    }
    tree.filter(q ? rawKeyword : '')
    if (q) {
      setExpanded(type, ancestorKeysOfMatched(type, q))
    } else {
      const restored = expandedSnapshot[type] || new Set(treeModel.value.rootKeysByType[type] || [])
      expandedSnapshot[type] = null
      setExpanded(type, Array.from(restored))
    }
  })
}

/** 命中叶子的全部祖先 key（用于自动展开） */
function ancestorKeysOfMatched(type, q) {
  const { leavesByType, ancestorsByCode } = treeModel.value
  const keys = new Set()
  ;(leavesByType[type] || []).forEach((leaf) => {
    if (!leaf.searchText.includes(q)) return
    ;(ancestorsByCode.get(leaf.code) || []).forEach((key) => keys.add(key))
  })
  return Array.from(keys)
}

/* ============================ 树交互 ============================ */

function setTreeRef(type, el) {
  treeRefs[type] = el || null
}

/** 整体替换展开态（触发 el-tree-v2 的 default-expanded-keys 监听，并同步下发保证无闪烁） */
function setExpanded(type, keys) {
  const list = Array.from(new Set(keys || []))
  expandedKeys[type] = list
  liveExpanded[type] = new Set(list)
  const tree = treeRefs[type]
  if (tree) tree.setExpandedKeys(list)
}

function onNodeExpand(type, data) {
  if (data && data.key !== undefined) liveExpanded[type].add(data.key)
}

function onNodeCollapse(type, data) {
  if (data && data.key !== undefined) liveExpanded[type].delete(data.key)
}

function isCodeSelected(data) {
  return !!(data && data.code && selectedByType[data.type] && selectedByType[data.type].has(data.code))
}

function groupTotalCount(data) {
  return (treeModel.value.groupLeafCodes.get(data.key) || []).length
}

function groupCheckedCount(data) {
  const codes = treeModel.value.groupLeafCodes.get(data.key) || []
  const picked = selectedByType[data.type]
  let count = 0
  codes.forEach((code) => {
    if (picked.has(code)) count += 1
  })
  return count
}

/** 树 → 状态：以树内部的叶子勾选结果为准（父子联动 / 半选由 el-tree-v2 负责） */
function syncFromTree(type) {
  const tree = treeRefs[type]
  if (!tree) return
  selectedByType[type] = new Set(tree.getCheckedKeys(true))
}

/** 状态 → 树：整体重设勾选（内部会先清空再按父子联动重算半选） */
function applySelectionToTree(type) {
  const tree = treeRefs[type]
  if (!tree) return
  tree.setCheckedKeys(Array.from(selectedByType[type]))
}

/** 分组行的「全选本组 / 清空本组」 */
function setGroupChecked(data, checked) {
  if (isBuiltinRole.value) return
  const tree = treeRefs[data.type]
  if (!tree) return
  tree.setChecked(data.key, checked)
  syncFromTree(data.type)
}

function replaceSelection(type, codes) {
  selectedByType[type] = new Set(codes)
  applySelectionToTree(type)
}

function onSelectAll() {
  if (batchDisabled.value) return
  const type = activeTab.value
  replaceSelection(type, (treeModel.value.leavesByType[type] || []).map((leaf) => leaf.code))
}

function onSelectAllVisible() {
  if (batchDisabled.value) return
  const type = activeTab.value
  const next = new Set(selectedByType[type])
  matchedCurrentCodes.value.forEach((code) => next.add(code))
  replaceSelection(type, next)
}

function onInvertSelect() {
  if (batchDisabled.value) return
  const type = activeTab.value
  const next = new Set(selectedByType[type])
  matchedCurrentCodes.value.forEach((code) => {
    if (next.has(code)) next.delete(code)
    else next.add(code)
  })
  replaceSelection(type, next)
}

function onExpandAll() {
  const type = activeTab.value
  setExpanded(type, treeModel.value.groupKeysByType[type] || [])
}

function onCollapseAll() {
  setExpanded(activeTab.value, [])
}

function switchSide() {
  activeTab.value = otherSide.value
}

const batchDisabled = computed(() => isBuiltinRole.value || matchedCurrentCodes.value.length === 0)

/** 左栏空态场景：catalog（目录为空）/ tab（当前端无权限）/ search（搜索无结果） */
const leftEmptyScene = computed(() => {
  if (loading.value || loadError.value) return ''
  if (!(allPermissions.value || []).length) return 'catalog'
  if (currentTabTotal.value === 0) return 'tab'
  if (activeQuery.value && matchedCurrentCodes.value.length === 0) return 'search'
  return ''
})

const leftEmptyDescription = computed(() => {
  if (leftEmptyScene.value === 'catalog') return t('role.empty.noPermission')
  if (leftEmptyScene.value === 'tab') return t('role.empty.noPermissionInTab')
  if (leftEmptyScene.value === 'search') {
    return t('role.empty.noSearchResult', { keyword: debouncedKeyword.value })
  }
  return ''
})

/* ========================== 右侧已选清单 ========================== */

/**
 * 按 module 分组的已选清单。
 * 「已失效」分组仅在范围=全部时出现（陈旧码不属于任何一端），
 * 统计条上的 ⚠ 徽标点击即可切到全部范围定位。
 */
const selectedGroups = computed(() => {
  const { codeMeta } = treeModel.value
  const scopeTypes = selectedScope.value === SCOPE_ALL ? SIDE_TYPES : [activeTab.value]
  const withSide = selectedScope.value === SCOPE_ALL
  const map = new Map()

  scopeTypes.forEach((type) => {
    Array.from(selectedByType[type]).forEach((code) => {
      const meta = codeMeta.get(code)
      const moduleKey = meta ? meta.moduleKey : FALLBACK_MODULE
      const groupKey = `g:${type}:${moduleKey}`
      let group = map.get(groupKey)
      if (!group) {
        const base = groupLabelOf(moduleKey)
        group = {
          groupKey,
          label: withSide ? `${sideLabel(type)} / ${base}` : base,
          type,
          stale: false,
          order: meta ? meta.order : 0,
          items: []
        }
        map.set(groupKey, group)
      } else if (meta) {
        group.order = Math.min(group.order, meta.order)
      }
      group.items.push({
        key: code,
        code,
        name: (meta && meta.vo && meta.vo.name) || code,
        type,
        stale: false,
        order: meta ? meta.order : 0
      })
    })
  })

  const groups = Array.from(map.values()).sort(
    (a, b) => (a.order - b.order) || a.groupKey.localeCompare(b.groupKey)
  )
  groups.forEach((group) => group.items.sort(byOrderThenKey))

  if (selectedScope.value === SCOPE_ALL && orphanSelected.value.size) {
    groups.push({
      groupKey: STALE_GROUP_KEY,
      label: t('role.selected.stale'),
      type: null,
      stale: true,
      order: Number.MAX_SAFE_INTEGER,
      items: Array.from(orphanSelected.value)
        .sort((a, b) => a.localeCompare(b))
        .map((code) => ({ key: code, code, name: code, type: null, stale: true, order: 0 }))
    })
  }
  return groups
})

const scopeCount = computed(() =>
  selectedGroups.value.reduce((total, group) => total + group.items.length, 0)
)

/** 拍平成行（分组头 + 权限项），便于分批渲染 */
const selectedRows = computed(() => {
  const rows = []
  selectedGroups.value.forEach((group) => {
    const collapsed = !!collapsedGroups[group.groupKey]
    rows.push({
      kind: 'group',
      key: `g#${group.groupKey}`,
      groupKey: group.groupKey,
      label: group.label,
      count: group.items.length,
      stale: group.stale,
      collapsed
    })
    if (collapsed) return
    group.items.forEach((item) => {
      rows.push({
        kind: 'item',
        key: `i#${group.groupKey}#${item.code}`,
        code: item.code,
        name: item.name,
        type: item.type,
        stale: item.stale
      })
    })
  })
  return rows
})

/** >RENDER_CHUNK 条时分批渲染，滚动到底部自动追加 */
const visibleRows = computed(() => {
  const rows = selectedRows.value
  return rows.length <= RENDER_CHUNK ? rows : rows.slice(0, renderLimit.value)
})

function onSelectedScroll() {
  const wrap = selectedScrollRef.value && selectedScrollRef.value.wrapRef
  if (!wrap) return
  if (renderLimit.value >= selectedRows.value.length) return
  if (wrap.scrollTop + wrap.clientHeight >= wrap.scrollHeight - 120) {
    renderLimit.value = Math.min(renderLimit.value + RENDER_CHUNK, selectedRows.value.length)
  }
}

function toggleGroupCollapse(groupKey) {
  collapsedGroups[groupKey] = !collapsedGroups[groupKey]
}

function removeSelected(row) {
  if (isBuiltinRole.value) return
  if (row.stale) {
    const next = new Set(orphanSelected.value)
    next.delete(row.code)
    orphanSelected.value = next
    return
  }
  const next = new Set(selectedByType[row.type])
  next.delete(row.code)
  replaceSelection(row.type, next)
}

function onClearScope() {
  if (isBuiltinRole.value) return
  const count = scopeCount.value
  if (!count) return
  const isAll = selectedScope.value === SCOPE_ALL
  const scope = isAll ? t('role.selected.scopeAll') : t('role.selected.scopeCurrent')
  ElMessageBox.confirm(t('role.selected.clearConfirm', { scope, count }), t('common.msg.tip'), {
    type: 'warning'
  })
    .then(() => {
      if (isAll) {
        SIDE_TYPES.forEach((type) => replaceSelection(type, []))
        orphanSelected.value = new Set()
      } else {
        replaceSelection(activeTab.value, [])
      }
    })
    .catch(() => {})
}

/** 统计条 ⚠ 徽标：切到「全部」范围以便定位失效项 */
function focusStale() {
  selectedScope.value = SCOPE_ALL
  collapsedGroups[STALE_GROUP_KEY] = false
}

/* ============================ 提交与 diff ============================ */

/**
 * 提交出参：两端已选 + 陈旧码去重合并成单一数组。
 * 目录内的码按 sort 升序 → code 字典序；陈旧码保序追加末尾（绝不隐式丢弃）。
 */
const finalCodes = computed(() => {
  const merged = new Set([
    ...selectedByType[FRONTEND],
    ...selectedByType[BACKEND],
    ...orphanSelected.value
  ])
  const index = permIndexByCode.value
  const known = []
  const unknown = []
  merged.forEach((code) => (index.has(code) ? known : unknown).push(code))
  known.sort((a, b) => {
    const sa = Number(index.get(a) && index.get(a).sort) || 0
    const sb = Number(index.get(b) && index.get(b).sort) || 0
    return sa - sb || a.localeCompare(b)
  })
  unknown.sort((a, b) => a.localeCompare(b))
  return [...known, ...unknown]
})

const finalCodeSet = computed(() => new Set(finalCodes.value))
const addedCodes = computed(() => finalCodes.value.filter((code) => !originalSelected.value.has(code)))
const removedCodes = computed(() =>
  Array.from(originalSelected.value).filter((code) => !finalCodeSet.value.has(code))
)
const hasChanges = computed(() => addedCodes.value.length > 0 || removedCodes.value.length > 0)

const visibleAdded = computed(() =>
  diffExpanded.value ? addedCodes.value : addedCodes.value.slice(0, DIFF_PREVIEW_LIMIT)
)
const visibleRemoved = computed(() =>
  diffExpanded.value ? removedCodes.value : removedCodes.value.slice(0, DIFF_PREVIEW_LIMIT)
)
const diffTruncated = computed(
  () =>
    !diffExpanded.value &&
    (addedCodes.value.length > DIFF_PREVIEW_LIMIT || removedCodes.value.length > DIFF_PREVIEW_LIMIT)
)

function onSaveClick() {
  if (isBuiltinRole.value || !hasChanges.value || saving.value) return
  diffExpanded.value = false
  confirmVisible.value = true
}

/** 重试：复用当前 finalCodes 直接重发，不再弹 diff */
function onRetrySave() {
  if (saving.value) return
  doSave()
}

async function doSave() {
  if (!props.role || props.role.id === undefined || props.role.id === null) return
  confirmVisible.value = false
  saving.value = true
  saveError.value = ''
  saveErrorDetail.value = ''
  try {
    await assignRolePermissions(props.role.id, finalCodes.value)
    failCount.value = 0
    ElMessage.success(t('role.msg.permSaved'))
    emit('saved')
    emit('update:modelValue', false)
  } catch (e) {
    failCount.value += 1
    const isTimeout =
      (e && e.code === 'ECONNABORTED') || /timeout/i.test((e && e.message) || '')
    const base = isTimeout ? t('role.msg.permSaveTimeout') : t('role.msg.permSaveFailed')
    saveError.value = base
    saveErrorDetail.value =
      failCount.value >= 3 ? t('role.msg.permSaveFailedMulti') : (e && e.message) || ''
    ElMessage.error(failCount.value >= 3 ? t('role.msg.permSaveFailedMulti') : base)
  } finally {
    saving.value = false
  }
}

/** 重置到打开时的快照 */
function onReset() {
  if (isBuiltinRole.value || saving.value) return
  applySnapshot(originalSelected.value)
}

function applySnapshot(codes) {
  const index = permIndexByCode.value
  const next = { [FRONTEND]: new Set(), [BACKEND]: new Set() }
  const orphans = new Set()
  Array.from(codes || []).forEach((code) => {
    const vo = index.get(code)
    if (!vo) {
      orphans.add(code)
      return
    }
    next[normalizeType(vo.type)].add(code)
  })
  SIDE_TYPES.forEach((type) => replaceSelection(type, next[type]))
  orphanSelected.value = orphans
}

/* ============================ 打开 / 关闭 ============================ */

async function initialize() {
  const role = props.role
  if (!role || role.id === undefined || role.id === null) return
  loading.value = true
  loadError.value = ''
  saveError.value = ''
  saveErrorDetail.value = ''
  failCount.value = 0
  keyword.value = ''
  debouncedKeyword.value = ''
  selectedScope.value = SCOPE_CURRENT
  renderLimit.value = RENDER_CHUNK
  diffExpanded.value = false
  confirmVisible.value = false
  Object.keys(collapsedGroups).forEach((key) => delete collapsedGroups[key])
  SIDE_TYPES.forEach((type) => {
    expandedSnapshot[type] = null
  })

  try {
    const [catalog, roleCodes] = await Promise.all([
      listPermissions(),
      getRolePermissions(role.id)
    ])
    allPermissions.value = catalog || []
    const codes = (roleCodes || []).map((code) => String(code))
    applySnapshotToState(codes)
    originalSelected.value = new Set(codes)

    // 默认后台端；若后台端无权限项而前台端有，则自动落到前台端
    await nextTick()
    const backendTotal = (treeModel.value.leavesByType[BACKEND] || []).length
    const frontendTotal = (treeModel.value.leavesByType[FRONTEND] || []).length
    activeTab.value = backendTotal === 0 && frontendTotal > 0 ? FRONTEND : BACKEND

    await nextTick()
    SIDE_TYPES.forEach((type) => {
      setExpanded(type, treeModel.value.rootKeysByType[type] || [])
      applySelectionToTree(type)
    })
  } catch (e) {
    allPermissions.value = []
    SIDE_TYPES.forEach((type) => {
      selectedByType[type] = new Set()
    })
    orphanSelected.value = new Set()
    originalSelected.value = new Set()
    loadError.value = (e && e.message) || t('role.msg.loadPermFailed')
    if (!loadError.value) loadError.value = t('role.msg.loadPermFailed')
  } finally {
    loading.value = false
  }
}

/** 初始化写入状态（不触树，树在 nextTick 后统一下发） */
function applySnapshotToState(codes) {
  const index = permIndexByCode.value
  const next = { [FRONTEND]: new Set(), [BACKEND]: new Set() }
  const orphans = new Set()
  codes.forEach((code) => {
    const vo = index.get(code)
    if (!vo) {
      orphans.add(code)
      return
    }
    next[normalizeType(vo.type)].add(code)
  })
  SIDE_TYPES.forEach((type) => {
    selectedByType[type] = next[type]
  })
  orphanSelected.value = orphans
}

function confirmDiscard(done) {
  if (isBuiltinRole.value || !hasChanges.value) {
    done()
    return
  }
  ElMessageBox.confirm(t('role.msg.discardConfirm'), t('common.msg.tip'), { type: 'warning' })
    .then(done)
    .catch(() => {})
}

/** el-drawer 关闭前拦截（遮罩 / ESC / 右上角 ×） */
function handleBeforeClose(done) {
  if (saving.value) return
  confirmDiscard(done)
}

/** 底部「取消」 */
function requestClose() {
  if (saving.value) return
  confirmDiscard(() => emit('update:modelValue', false))
}

function onVisibleChange(value) {
  emit('update:modelValue', value)
}

/** 抽屉完全关闭后清理瞬时态，保证下次打开干净 */
function onClosed() {
  keyword.value = ''
  debouncedKeyword.value = ''
  confirmVisible.value = false
  diffExpanded.value = false
  saveError.value = ''
  saveErrorDetail.value = ''
  failCount.value = 0
}

/* ============================ 尺寸 / 生命周期 ============================ */

const treeHeight = computed(() =>
  isNarrow.value ? 300 : Math.max(220, Math.round(mainHeight.value) - PANEL_HEAD_HEIGHT)
)
const listHeight = computed(() =>
  isNarrow.value ? 260 : Math.max(220, Math.round(mainHeight.value) - PANEL_HEAD_HEIGHT)
)

let resizeObserver = null

function onWindowResize() {
  if (typeof window !== 'undefined') viewportWidth.value = window.innerWidth
}

onMounted(() => {
  if (typeof window !== 'undefined') window.addEventListener('resize', onWindowResize)
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      const rect = entries && entries[0] && entries[0].contentRect
      if (rect) mainHeight.value = rect.height
    })
  }
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
  if (typeof window !== 'undefined') window.removeEventListener('resize', onWindowResize)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})

watch(mainRef, (el, prev) => {
  if (!resizeObserver) return
  if (prev) resizeObserver.unobserve(prev)
  if (el) {
    resizeObserver.observe(el)
    mainHeight.value = el.clientHeight
  }
})

/** 打开（或打开状态下切换角色）时初始化 */
watch(
  () => [props.modelValue, props.role && props.role.id],
  ([visible], old) => {
    if (!visible) return
    const prevVisible = old ? old[0] : false
    const prevId = old ? old[1] : undefined
    const nextId = props.role && props.role.id
    if (!prevVisible || prevId !== nextId) initialize()
  },
  { immediate: true }
)

/** 切换范围 / 端时重置右清单的分批渲染游标 */
watch([selectedScope, activeTab], () => {
  renderLimit.value = RENDER_CHUNK
  const wrap = selectedScrollRef.value && selectedScrollRef.value.wrapRef
  if (wrap) wrap.scrollTop = 0
})
</script>

<style scoped>
/* ============ 布局骨架：A/B/C/E 固定，仅 D 区滚动 ============ */
.apd {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: calc(100vh - 190px);
  min-height: 420px;
}
.apd--narrow {
  height: auto;
  min-height: 0;
}

.apd__notice {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: none;
}
.apd__notice-body {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.apd__notice-detail {
  color: var(--el-color-error);
  font-size: 12px;
  word-break: break-all;
}

/* ---- B 区：Tab + 搜索 + 工具 ---- */
.apd__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  flex: none;
}
.apd__search {
  width: 240px;
  flex: 0 1 240px;
}
.apd__tools {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  flex-wrap: wrap;
}
.apd-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.apd-tab__badge {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--el-fill-color-darker);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

/* ---- C 区：统计条 ---- */
.apd__stats {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
  padding: 6px 10px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
}
.apd__stats-main {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.apd__stats-sep {
  color: var(--el-text-color-placeholder);
}
.apd__stats-stale {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-warning);
  cursor: pointer;
}
.apd__stats-match {
  color: var(--el-color-primary);
}
.apd__stats-spacer {
  flex: 1;
}
.apd__stats-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-placeholder);
}

/* ---- D 区：双栏主体（唯一滚动区） ---- */
.apd__main {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.apd--narrow .apd__main {
  flex-direction: column;
  overflow-y: auto;
  max-height: calc(100vh - 300px);
}
.apd__load-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.apd-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}
.apd-panel--tree {
  flex: 7;
}
.apd-panel--selected {
  flex: 3;
}
.apd--narrow .apd-panel--tree,
.apd--narrow .apd-panel--selected {
  flex: none;
}
.apd-panel__head {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 10px;
  flex: none;
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}
.apd-panel__title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.apd-panel__sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.apd-panel__head-spacer {
  flex: 1;
}
.apd-panel__body {
  position: relative;
  flex: 1;
  min-height: 0;
}
.apd-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 220px;
}
.apd-tree-wrap {
  height: 100%;
}

/* ---- 树节点 ---- */
.apd-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}
.apd-node__label {
  flex: none;
  max-width: 55%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.apd-node.is-group .apd-node__label {
  font-weight: 600;
}
.apd-node__code {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.apd-node__count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.apd-node__info {
  color: var(--el-color-warning);
  font-size: 13px;
}
.apd-node__ops {
  display: none;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  padding-right: 8px;
}
.apd-node__ops :deep(.el-button) {
  font-size: 12px;
}

/* ---- 已选清单 ---- */
.apd-selected {
  background: var(--el-bg-color);
}
.apd-sgroup {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-weight: 600;
  user-select: none;
}
.apd-sgroup.is-stale {
  color: var(--el-color-warning);
}
.apd-sgroup__caret {
  transition: transform 0.2s;
}
.apd-sgroup__caret.is-collapsed {
  transform: rotate(-90deg);
}
.apd-sgroup__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.apd-sgroup__count {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.apd-sitem {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 30px;
  padding: 0 10px 0 22px;
  font-size: 13px;
}
.apd-sitem:hover {
  background: var(--el-fill-color-light);
}
.apd-sitem.is-stale {
  color: var(--el-text-color-placeholder);
}
.apd-sitem__name {
  flex: none;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.apd-sitem__code {
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.apd-sitem__tag {
  flex: none;
}
.apd-sitem__remove {
  flex: none;
  visibility: hidden;
}
.apd-sitem:hover .apd-sitem__remove {
  visibility: visible;
}

/* ---- 底部 ---- */
.apd__footer {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.apd__footer-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.apd__delta {
  font-weight: 600;
}
.apd__delta--add {
  color: var(--el-color-success);
}
.apd__delta--remove {
  color: var(--el-color-danger);
}
.apd__footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.apd__save-wrap {
  display: inline-flex;
}

/* ---- diff 弹窗 ---- */
.apd-diff__subtitle {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.apd-diff__block {
  margin-bottom: 12px;
}
.apd-diff__head {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
}
.apd-diff__head--add {
  color: var(--el-color-success);
}
.apd-diff__head--remove {
  color: var(--el-color-danger);
}
.apd-diff__list {
  margin: 0;
  padding: 0;
  max-height: 200px;
  overflow-y: auto;
  list-style: none;
}
.apd-diff__list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
  font-size: 13px;
}
.apd-diff__warn {
  color: var(--el-color-warning);
}
.apd-diff__code {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.apd-diff__footline {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 6px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.apd-diff__total {
  margin-left: auto;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}
</style>

<style>
/* el-tree-v2 的节点由虚拟列表渲染，行级样式需作用于非 scoped 层 */
.el-tree-node.apd-row.is-selected {
  background: var(--el-color-primary-light-9);
}
.el-tree-node.apd-row:hover .apd-node__ops {
  display: inline-flex;
}
.apd-mark {
  padding: 0 1px;
  border-radius: 2px;
  background: var(--el-color-warning-light-7);
  color: var(--el-color-warning-dark-2);
}
</style>
