<template>
  <!-- 顶层：渲染 el-menu 容器，遍历根节点
       Phase8 W5 #2：前台（defaultExpandAll）需等菜单树加载完、openedMenus 算好后再渲染，
       故 v-if 加 (!defaultExpandAll || menuReady) 门闸；后台恒为 true，渲染时机不变。
       topMenuBindings 仅在 defaultExpandAll 时注入 default-openeds/@open/@close，后台为空对象。 -->
  <el-menu
    v-if="!nested && (!defaultExpandAll || menuReady)"
    class="if-menu"
    :default-active="activeMenu"
    :collapse="collapsed"
    :collapse-transition="false"
    router
    background-color="transparent"
    v-bind="topMenuBindings"
  >
    <SideMenu
      v-for="node in visibleMenus"
      :key="node.id"
      :node="node"
      nested
    />
  </el-menu>

  <!-- 嵌套层：作为父级 el-sub-menu / el-menu 的直接子节点递归 -->
  <template v-else>
    <el-sub-menu v-if="hasChildren(node)" :index="resolveIndex(node)">
      <template #title>
        <el-icon v-if="node.icon"><component :is="resolveIcon(node.icon)" /></el-icon>
        <span>{{ menuLabelI18n(node) }}</span>
      </template>
      <SideMenu
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        nested
      />
    </el-sub-menu>
    <el-menu-item v-else :index="resolveIndex(node)">
      <el-icon v-if="node.icon"><component :is="resolveIcon(node.icon)" /></el-icon>
      <template #title>{{ menuLabelI18n(node) }}</template>
    </el-menu-item>
  </template>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { getSidebarMenus } from '@/api/menu'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { menuLabelI18n } from '@/utils/i18nEnum'

// 允许组件在模板中递归调用自身
defineOptions({ name: 'SideMenu' })

const props = defineProps({
  /** 端维度：1=前台端 / 2=后台端（仅顶层用于拉取菜单树） */
  type: { type: Number, default: 2 },
  /** 递归节点（嵌套层传入单个节点） */
  node: { type: Object, default: null },
  /** 是否为嵌套递归节点（隐藏顶层 el-menu 包裹） */
  nested: { type: Boolean, default: false },
  /**
   * Phase8 W5 #2：仅前台顶层（<SideMenu :type="1" :default-expand-all="true" />）传 true。
   * 为 true 时启用「所有层级父菜单默认展开 + 手动折叠状态持久化（刷新保持）」；
   * 为 false（后台 type=2 / 递归子节点）时完全走 el-menu 原生默认收起行为，零行为变更。
   */
  defaultExpandAll: { type: Boolean, default: false }
})

const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const collapsed = computed(() => appStore.sidebarCollapsed && !appStore.isMobile)
const activeMenu = computed(() => route.path)

// 顶层拉取菜单树；嵌套层直接使用父节点 children
const fetched = ref([])
const tree = computed(() =>
  props.nested ? (props.node ? props.node.children || [] : []) : fetched.value
)

// ===== Phase8 W5 #2：前台菜单默认展开 + 手动折叠持久化（仅 defaultExpandAll 生效）=====
// 「已手动折叠」父菜单 index 集合的 localStorage 键（前台 type=1 专用）。
const MENU_CLOSED_STORAGE_KEY = 'if-menu-closed-type1'
// 顶层 el-menu 初始展开的父菜单 index 集合（= 全部父级 index - 已手动折叠集合）。
const openedMenus = ref([])
// 就绪门闸：本项目 element-plus@2.14.3 的 el-menu 仅有 `default-openeds`（无 `openeds`），
// 且该 prop 只在组件创建时读取一次、后续非响应式；而菜单树是异步加载的。
// 故前台必须等 load() 完成、openedMenus 算好后再渲染顶层 el-menu，才能拿到正确初始展开态。
const menuReady = ref(false)

const visibleMenus = computed(() => filterByPermission(tree.value))

function hasChildren(n) {
  return !!(n && n.children && n.children.length)
}

function resolveIndex(n) {
  return n && n.path ? n.path : `menu-${n && n.id}`
}

/**
 * 图标兜底（T7）：数据库存的 icon 必须是 Element Plus 真实图标名；
 * 无效 / 拼错时回退 Grid，避免 <component :is> 渲染警告或空白。
 */
function resolveIcon(iconName) {
  if (iconName && ElementPlusIconsVue[iconName]) return iconName
  return 'Grid'
}

// 按权限码过滤：未加载权限时（避免误隐藏）默认全部可见
function filterByPermission(nodes) {
  const perms = userStore.permissions
  const loaded = perms && perms.length
  return (nodes || [])
    .map((n) => {
      const visible = !loaded || !n.permission || userStore.hasPerm(n.permission)
      return {
        ...n,
        hidden: !visible,
        children: hasChildren(n) ? filterByPermission(n.children) : n.children
      }
    })
    .filter((n) => !n.hidden)
}

/**
 * Phase8 W5 #2：递归收集「所有层级」有子节点的父菜单 index（= resolveIndex(n)）。
 * 命中父节点后继续深入其 children，确保多级嵌套的每一层父菜单都被覆盖。
 * @param {Array} nodes 菜单节点数组
 * @returns {string[]} 各层父菜单的 el-menu index 字符串（path 或 'menu-<id>'）
 */
function collectParentIndices(nodes) {
  const indices = []
  ;(nodes || []).forEach((n) => {
    if (hasChildren(n)) {
      indices.push(resolveIndex(n))
      indices.push(...collectParentIndices(n.children))
    }
  })
  return indices
}

/** 读「已手动折叠」集合（解析失败或非数组时回退空数组，容错） */
function readClosedSet() {
  try {
    const raw = JSON.parse(localStorage.getItem(MENU_CLOSED_STORAGE_KEY) || '[]')
    return Array.isArray(raw) ? raw : []
  } catch (e) {
    return []
  }
}

/** 写「已手动折叠」集合 */
function writeClosedSet(list) {
  localStorage.setItem(MENU_CLOSED_STORAGE_KEY, JSON.stringify(list))
}

/**
 * 初始化默认展开项：全部父级 index 减去用户「已手动折叠」的集合。
 * 仅 defaultExpandAll 生效；基于 fetched.value（原始树，覆盖所有层级）。
 * 注：即便某父级因权限被过滤不渲染，其 index 出现在 default-openeds 也仅是无害的存在性比对。
 */
function initOpenedMenus() {
  if (!props.defaultExpandAll) return
  const allParent = collectParentIndices(fetched.value)
  const closedSet = readClosedSet()
  openedMenus.value = allParent.filter((i) => !closedSet.includes(i))
}

/** 用户手动展开某父菜单：从「已折叠集合」移除并持久化（刷新后仍展开） */
function onOpen(index) {
  const s = new Set(readClosedSet())
  s.delete(index)
  writeClosedSet([...s])
}

/** 用户手动折叠某父菜单：加入「已折叠集合」并持久化（刷新后仍收起） */
function onClose(index) {
  const s = new Set(readClosedSet())
  s.add(index)
  writeClosedSet([...s])
}

/**
 * 顶层 el-menu 的条件绑定：
 * - defaultExpandAll（前台 type=1）：注入 default-openeds 初值 + open/close 持久化监听；
 * - 否则（后台 type=2 / 递归节点）：返回空对象，完全不绑，保持 el-menu 原生默认行为。
 * 因 el-menu 的 default-openeds 非响应式（仅创建时读一次），配合 menuReady 门闸延迟渲染以拿到正确初值。
 */
const topMenuBindings = computed(() => {
  if (!props.defaultExpandAll) return {}
  return {
    defaultOpeneds: openedMenus.value,
    onOpen,
    onClose
  }
})

async function load() {
  if (props.nested) return
  // 前台重新加载时先落门闸，触发顶层 el-menu 重建，使 default-openeds 能按最新数据重新生效
  if (props.defaultExpandAll) menuReady.value = false
  try {
    fetched.value = (await getSidebarMenus(props.type)) || []
  } catch (e) {
    fetched.value = []
  } finally {
    // 菜单树就绪后计算默认展开项，再放行顶层 el-menu 渲染（后台不受门闸影响，渲染时机不变）
    initOpenedMenus()
    menuReady.value = true
  }
}

onMounted(load)
// 端切换或登录态变化后重新拉取
watch(() => props.type, load)
watch(
  () => userStore.isLoggedIn,
  (v) => {
    if (v) load()
  }
)
</script>
