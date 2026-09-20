<script setup lang="ts">
/**
 * 记录列表的四态外壳（骨架 / 错误 / 空 / 有数据）
 *
 * 六个列表页的四态长得一样，这里统一：默认插槽是"有数据"的内容，
 * 空态里的下一步动作由页面用 `#empty-actions` 插槽给（只有页面知道该去哪新建）。
 */
import { PhArrowClockwise, PhTray } from '@phosphor-icons/vue'

defineProps<{
  loading?: boolean
  error?: string
  /** 列表是不是空的（由页面判断，组件不去猜插槽内容） */
  empty?: boolean
  emptyTitle?: string
  emptyDesc?: string
  /** 骨架屏行数 */
  skeletonRows?: number
}>()

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="mw-rows">
    <span v-for="i in skeletonRows ?? 3" :key="i" class="mw-skeleton sk" />
  </div>

  <div v-else-if="error" class="mw-state mw-state--box">
    <PhArrowClockwise :size="24" aria-hidden="true" />
    <p class="mw-state__title">没能读到记录</p>
    <p>{{ error }}</p>
    <button class="mw-btn mw-btn--ghost" type="button" @click="emit('retry')">重试</button>
  </div>

  <div v-else-if="empty" class="mw-state mw-state--box">
    <PhTray :size="24" aria-hidden="true" />
    <p class="mw-state__title">{{ emptyTitle || '还没有记录' }}</p>
    <p v-if="emptyDesc">{{ emptyDesc }}</p>
    <div class="mw-chips empty-actions">
      <slot name="empty-actions" />
    </div>
  </div>

  <slot v-else />
</template>

<style scoped>
.sk {
  display: block;
  height: 64px;
}
.empty-actions {
  justify-content: center;
}
</style>