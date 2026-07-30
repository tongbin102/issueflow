<template>
  <!-- 顶层：渲染 el-menu 容器，遍历根节点 -->
  <el-menu
    v-if="!nested"
    class="if-menu"
    :default-active="activeMenu"
    :collapse="collapsed"
    :collapse-transition="false"
    router
    background-color="transparent"
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
        <el-icon v-if="node.icon"><component :is="node.icon" /></el-icon>
        <span>{{ node.name }}</span>
      </template>
      <SideMenu
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        nested
      />
    </el-sub-menu>
    <el-menu-item v-else :index="resolveIndex(node)">
      <el-icon v-if="node.icon"><component :is="node.icon" /></el-icon>
      <template #title>{{ node.name }}</template>
    </el-menu-item>
  </template>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getSidebarMenus } from '@/api/menu'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'

// 允许组件在模板中递归调用自身
defineOptions({ name: 'SideMenu' })

const props = defineProps({
  /** 端维度：1=前台端 / 2=后台端（仅顶层用于拉取菜单树） */
  type: { type: Number, default: 2 },
  /** 递归节点（嵌套层传入单个节点） */
  node: { type: Object, default: null },
  /** 是否为嵌套递归节点（隐藏顶层 el-menu 包裹） */
  nested: { type: Boolean, default: false }
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

const visibleMenus = computed(() => filterByPermission(tree.value))

function hasChildren(n) {
  return !!(n && n.children && n.children.length)
}

function resolveIndex(n) {
  return n && n.path ? n.path : `menu-${n && n.id}`
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

async function load() {
  if (props.nested) return
  try {
    fetched.value = (await getSidebarMenus(props.type)) || []
  } catch (e) {
    fetched.value = []
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
