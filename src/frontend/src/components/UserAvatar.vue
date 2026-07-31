<template>
  <!-- 通用头像：有图走 /api/profile/avatar/{userId} 取流渲染，无图退化为首字母 + 稳定色。
       供前台顶栏与个人中心复用（ARCH §2.5-101）。 -->
  <el-avatar
    v-if="objectUrl"
    class="if-user-avatar"
    :size="size"
    :src="objectUrl"
    @error="onImgError"
  />
  <el-avatar
    v-else
    class="if-user-avatar if-user-avatar--text"
    :size="size"
    :style="{ backgroundColor: fallbackColor }"
  >
    {{ initial }}
  </el-avatar>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { fetchAvatarBlob } from '@/api/profile'

const props = defineProps({
  /** 用户 id，取头像流用；缺省时只显示首字母 */
  userId: { type: [Number, String], default: null },
  /** user.avatar 相对路径；为空表示未设置头像，直接走首字母兜底 */
  avatar: { type: String, default: '' },
  /** 展示名（昵称 / 姓名 / 账号），用于首字母与稳定色 */
  name: { type: String, default: '' },
  /** 尺寸（px） */
  size: { type: Number, default: 32 },
  /** 版本号：上传头像后自增以强制重新拉取（绕过浏览器与内存缓存） */
  version: { type: Number, default: 0 }
})

/** 首字母兜底可选色板（与主题解耦，保证任意主题下对比度可读） */
const COLOR_PALETTE = [
  '#409eff',
  '#67c23a',
  '#e6a23c',
  '#f56c6c',
  '#909399',
  '#7b68ee',
  '#20b2aa',
  '#ff7f50'
]

const objectUrl = ref('')
/** 图片加载失败标记：失败后本轮不再重试，直接退化首字母 */
const loadFailed = ref(false)

const initial = computed(() => {
  const text = (props.name || '').trim()
  return text ? text.charAt(0).toUpperCase() : 'U'
})

/** 按名字哈希取稳定色：同一个人任何页面颜色一致 */
const fallbackColor = computed(() => {
  const text = (props.name || 'U').trim()
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 1000000007
  }
  return COLOR_PALETTE[Math.abs(hash) % COLOR_PALETTE.length]
})

/** 释放上一张 objectURL，避免 Blob 内存泄漏 */
function revoke() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

async function load() {
  revoke()
  loadFailed.value = false
  if (!props.avatar || !props.userId) {
    return
  }
  try {
    const blob = await fetchAvatarBlob(props.userId)
    // 后端异常时可能回 JSON 错误体，此处只接受图片类型
    if (blob instanceof Blob && blob.size > 0 && String(blob.type).startsWith('image/')) {
      objectUrl.value = URL.createObjectURL(blob)
    }
  } catch (e) {
    // 静默降级为首字母，头像失败不应打断页面
    loadFailed.value = true
  }
}

function onImgError() {
  revoke()
  loadFailed.value = true
}

watch(
  () => [props.userId, props.avatar, props.version],
  () => {
    load()
  },
  { immediate: true }
)

onBeforeUnmount(revoke)
</script>

<style scoped>
.if-user-avatar {
  flex-shrink: 0;
}

.if-user-avatar--text {
  color: #fff;
  font-weight: 600;
}
</style>
