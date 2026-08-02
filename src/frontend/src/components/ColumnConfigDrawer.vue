<template>
  <!-- 列配置抽屉：复用 FormDrawer，展示全部可用列，支持勾选显隐 + 拖拽排序。
       抽屉内部维护一份 localItems 编辑副本，点击保存时 emit apply 回写 composable。 -->
  <FormDrawer
    :model-value="modelValue"
    :title="t('issue.columnConfig.title')"
    :subtitle="t('issue.columnConfig.subtitle')"
    size="sm"
    :width="DRAWER_WIDTH"
    :loading="false"
    :confirm-text="t('common.action.save')"
    @update:model-value="onVisibleChange"
    @confirm="onApply"
    @closed="onClosed"
  >
    <!-- 头部右侧：重置按钮 -->
    <template #header-extra>
      <el-button link type="primary" size="small" @click="onReset">
        {{ t('issue.columnConfig.reset') }}
      </el-button>
    </template>

    <div class="column-config">
      <!-- 拖拽提示 -->
      <div class="column-config__tip">{{ t('issue.columnConfig.dragTip') }}</div>

      <!-- 列列表（单列表，按 isCustom 属性渲染分区标题） -->
      <div class="column-config__list">
        <template v-for="(item, index) in localItems" :key="item.key">
          <!-- 分区标题：首项或 isCustom 属性变化时渲染 -->
          <div v-if="isSectionStart(index)" class="column-config__section-title">
            {{ item.isCustom ? t('issue.columnConfig.custom') : t('issue.columnConfig.builtin') }}
          </div>

          <!-- 列项：可勾选 + 可拖拽 -->
          <div
            class="column-config__item"
            :class="{ 'is-dragging': draggedKey === item.key }"
            draggable="true"
            @dragstart="onDragStart(item.key, $event)"
            @dragover.prevent="onDragOver(item.key)"
            @drop="onDrop(item.key)"
            @dragend="onDragEnd"
          >
            <el-checkbox v-model="item.visible" :label="item.key">
              <span class="column-config__item-label">{{ item.label }}</span>
            </el-checkbox>
            <el-icon class="column-config__handle" aria-hidden="true">
              <Rank />
            </el-icon>
          </div>
        </template>
      </div>

      <!-- 无自定义列时的降级提示 -->
      <div v-if="!hasCustomColumns" class="column-config__empty">
        {{ t('issue.columnConfig.noCustomFields') }}
      </div>
    </div>
  </FormDrawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Rank } from '@element-plus/icons-vue'
import FormDrawer from '@/components/FormDrawer.vue'

const props = defineProps({
  /** 抽屉显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 全部可用列 [{ key, label, isCustom }] */
  columns: { type: Array, default: () => [] },
  /** 当前可见列 key 数组（空 = 全部可见） */
  visibleKeys: { type: Array, default: () => [] },
  /** 当前列排序 key 数组（空 = 默认顺序） */
  orderKeys: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'apply'])

const { t } = useI18n()

/** 抽屉宽度：与 DictManage 的 sm 档保持一致，窄屏自适应 */
const DRAWER_WIDTH = 'min(480px, 92vw)'

/**
 * 本地编辑副本：[{ key, label, isCustom, visible }]
 * 数组顺序即为列排序顺序；visible 控制是否显示。
 * 仅在抽屉打开时从 props 初始化，编辑期间不与外部状态同步，
 * 避免用户编辑过程中表格列频繁跳动。点击保存时统一 emit apply。
 */
const localItems = ref([])

/** 当前拖拽中的列 key */
const draggedKey = ref(null)

/** 是否存在自定义列 */
const hasCustomColumns = computed(() => localItems.value.some((item) => item.isCustom))

/**
 * 判断某索引是否为分区起始位置（首项或 isCustom 属性变化）。
 * @param {number} index
 * @returns {boolean}
 */
function isSectionStart(index) {
  if (index === 0) return true
  return localItems.value[index].isCustom !== localItems.value[index - 1].isCustom
}

/**
 * 抽屉打开时初始化本地编辑副本。
 * 按 orderKeys 排序（未包含的列追加到末尾），visible 由 visibleKeys 决定。
 */
function initLocalItems() {
  const allCols = props.columns || []
  const order = props.orderKeys && props.orderKeys.length > 0
    ? props.orderKeys
    : allCols.map((c) => c.key)
  const visible = props.visibleKeys || []

  const result = []
  const used = new Set()

  // 按用户排序偏好添加
  for (const key of order) {
    const col = allCols.find((c) => c.key === key)
    if (col) {
      result.push({
        key: col.key,
        label: col.label,
        isCustom: !!col.isCustom,
        visible: visible.length === 0 ? true : visible.includes(col.key)
      })
      used.add(col.key)
    }
  }

  // 追加未在排序列表中的新列（如管理员新增的自定义字段）
  for (const col of allCols) {
    if (!used.has(col.key)) {
      result.push({
        key: col.key,
        label: col.label,
        isCustom: !!col.isCustom,
        visible: visible.length === 0 ? true : visible.includes(col.key)
      })
    }
  }

  localItems.value = result
}

/**
 * 抽屉显隐变化时初始化 / 清理。
 */
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      initLocalItems()
    }
  }
)

/**
 * 列项拖拽：记录被拖拽的列 key。
 * @param {string} key
 * @param {DragEvent} e
 */
function onDragStart(key, e) {
  draggedKey.value = key
  // 设置拖拽效果与数据（Firefox 需设置 dataTransfer 才能触发拖拽）
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', key)
  }
}

/**
 * 拖拽经过某列时：将被拖拽项移动到目标位置。
 * @param {string} targetKey
 */
function onDragOver(targetKey) {
  if (!draggedKey.value || draggedKey.value === targetKey) return
  const items = [...localItems.value]
  const fromIndex = items.findIndex((item) => item.key === draggedKey.value)
  const toIndex = items.findIndex((item) => item.key === targetKey)
  if (fromIndex < 0 || toIndex < 0) return
  const [removed] = items.splice(fromIndex, 1)
  items.splice(toIndex, 0, removed)
  localItems.value = items
}

/**
 * 放置时无需额外操作（onDragOver 已完成位置交换）。
 * @param {string} _targetKey
 */
function onDrop(_targetKey) {
  draggedKey.value = null
}

/** 拖拽结束：清理状态 */
function onDragEnd() {
  draggedKey.value = null
}

/**
 * 保存：提取可见列 key 与排序列 key，emit apply。
 */
function onApply() {
  const visible = localItems.value
    .filter((item) => item.visible)
    .map((item) => item.key)
  const order = localItems.value.map((item) => item.key)
  emit('apply', { visibleKeys: visible, orderKeys: order })
  emit('update:modelValue', false)
  ElMessage.success(t('issue.columnConfig.applySuccess'))
}

/**
 * 重置为默认：全部可见、默认顺序。
 */
function onReset() {
  // 恢复为全部可见 + 按 props.columns 原始顺序
  localItems.value = (props.columns || []).map((col) => ({
    key: col.key,
    label: col.label,
    isCustom: !!col.isCustom,
    visible: true
  }))
}

/**
 * 抽屉关闭动画结束：清理本地状态。
 */
function onClosed() {
  localItems.value = []
  draggedKey.value = null
}

/**
 * 抽屉显隐变化透传。
 * @param {boolean} val
 */
function onVisibleChange(val) {
  emit('update:modelValue', val)
}
</script>

<style scoped>
.column-config {
  display: flex;
  flex-direction: column;
  gap: var(--if-space-sm);
}

.column-config__tip {
  font-size: var(--if-font-xs);
  line-height: 1.4;
  color: var(--text-secondary);
}

.column-config__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.column-config__section-title {
  margin-top: var(--if-space-sm);
  margin-bottom: var(--if-space-xs);
  padding: 0 var(--if-space-xs);
  font-size: var(--if-font-xs);
  font-weight: var(--if-weight-bold);
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 第一个分区标题不需要上方间距 */
.column-config__list > .column-config__section-title:first-child {
  margin-top: 0;
}

.column-config__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--if-space-xs) var(--if-space-sm);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--if-radius-sm);
  background: var(--el-bg-color);
  cursor: grab;
  transition: border-color var(--if-transition-fast),
    box-shadow var(--if-transition-fast), background var(--if-transition-fast);
}

.column-config__item:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
}

.column-config__item:active {
  cursor: grabbing;
}

.column-config__item.is-dragging {
  opacity: 0.5;
  border-style: dashed;
  border-color: var(--el-color-primary);
}

.column-config__item :deep(.el-checkbox) {
  flex: 1;
  min-width: 0;
}

.column-config__item :deep(.el-checkbox__label) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-config__item-label {
  font-size: var(--if-font-base);
  color: var(--text-primary);
}

.column-config__handle {
  flex-shrink: 0;
  margin-left: var(--if-space-sm);
  font-size: 16px;
  color: var(--el-text-color-placeholder);
  cursor: grab;
}

.column-config__handle:active {
  cursor: grabbing;
}

.column-config__empty {
  padding: var(--if-space-md);
  text-align: center;
  font-size: var(--if-font-xs);
  color: var(--text-secondary);
}
</style>
