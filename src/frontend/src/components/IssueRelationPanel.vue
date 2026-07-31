<template>
  <div class="relation-panel">
    <el-divider content-position="left">{{ t('issue.relation.title') }}</el-divider>
    <div v-loading="loading" class="relation-body">
      <!-- 展示模式 -->
      <template v-if="!editable">
        <div class="relation-block">
          <div class="relation-label">{{ t('issue.relation.predecessor') }}</div>
          <div v-if="predecessors.length" class="relation-list">
            <el-tag
              v-for="p in predecessors"
              :key="p.id"
              type="warning"
              class="rel-tag"
              @click="goIssue(p)"
            >{{ p.issueNo }} {{ p.title }}</el-tag>
          </div>
          <span v-else class="rel-empty">{{ t('issue.relation.none') }}</span>
        </div>
        <div class="relation-block">
          <div class="relation-label">{{ t('issue.relation.successor') }}</div>
          <div v-if="successors.length" class="relation-list">
            <el-tag
              v-for="s in successors"
              :key="s.id"
              type="success"
              class="rel-tag"
              @click="goIssue(s)"
            >{{ s.issueNo }} {{ s.title }}</el-tag>
          </div>
          <span v-else class="rel-empty">{{ t('issue.relation.none') }}</span>
        </div>
        <el-button
          v-if="canEdit"
          size="small"
          type="primary"
          link
          class="rel-edit-btn"
          @click="enterEdit"
        >{{ t('issue.relation.edit') }}</el-button>
      </template>

      <!-- 编辑模式 -->
      <template v-else>
        <el-form label-width="68px" size="small">
          <el-form-item :label="t('issue.relation.predecessor')">
            <el-select
              v-model="form.predecessorIds"
              multiple
              filterable
              :placeholder="t('issue.relation.selectPredecessor')"
              style="width: 100%"
            >
              <el-option
                v-for="o in options"
                :key="o.id"
                :label="`${o.issueNo} ${o.title}`"
                :value="o.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('issue.relation.successor')">
            <el-select
              v-model="form.successorIds"
              multiple
              filterable
              :placeholder="t('issue.relation.selectSuccessor')"
              style="width: 100%"
            >
              <el-option
                v-for="o in options"
                :key="o.id"
                :label="`${o.issueNo} ${o.title}`"
                :value="o.id"
              />
            </el-select>
          </el-form-item>
          <div class="relation-actions">
            <el-button size="small" @click="cancelEdit">{{ t('common.action.cancel') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="save">
              {{ t('common.action.save') }}
            </el-button>
          </div>
        </el-form>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getRelations, saveRelations, listIssueOptions } from '@/api/issue'

const props = defineProps({
  issueId: { type: [Number, String], default: null },
  /** 是否允许编辑（由父组件依据 ADMIN / 提交人判定） */
  canEdit: { type: Boolean, default: false }
})
const emit = defineEmits(['updated'])

const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const editable = ref(false)
const predecessors = ref([])
const successors = ref([])
const options = ref([])
const form = reactive({ predecessorIds: [], successorIds: [] })

async function load() {
  if (!props.issueId) return
  loading.value = true
  try {
    const [rel, opts] = await Promise.all([
      getRelations(props.issueId),
      listIssueOptions(props.issueId)
    ])
    predecessors.value = (rel && rel.predecessors) || []
    successors.value = (rel && rel.successors) || []
    options.value = opts || []
  } catch (e) {
    predecessors.value = []
    successors.value = []
    options.value = []
  } finally {
    loading.value = false
  }
}

function enterEdit() {
  form.predecessorIds = predecessors.value.map((p) => p.id)
  form.successorIds = successors.value.map((s) => s.id)
  editable.value = true
}

function cancelEdit() {
  editable.value = false
}

async function save() {
  saving.value = true
  try {
    await saveRelations(props.issueId, {
      predecessorIds: form.predecessorIds,
      successorIds: form.successorIds
    })
    ElMessage.success(t('issue.relation.saveSuccess'))
    editable.value = false
    await load()
    emit('updated')
  } catch (e) {
    // 业务异常（如环路 RELATION_CYCLE）由响应拦截器统一提示
  } finally {
    saving.value = false
  }
}

function goIssue(ref) {
  if (!ref || !ref.id) return
  const base = router.currentRoute.value.path.startsWith('/admin')
    ? '/admin/issues'
    : '/user/my-issues'
  router.push({ path: base, query: { focus: ref.id } })
}

onMounted(load)
watch(
  () => props.issueId,
  (val) => {
    if (val) {
      editable.value = false
      load()
    }
  }
)
</script>

<style scoped>
.relation-body {
  padding: 4px 0;
}
.relation-block {
  margin-bottom: 10px;
}
.relation-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.relation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.rel-tag {
  cursor: pointer;
}
.rel-empty {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
.relation-actions {
  text-align: right;
  margin-top: 8px;
}
</style>
