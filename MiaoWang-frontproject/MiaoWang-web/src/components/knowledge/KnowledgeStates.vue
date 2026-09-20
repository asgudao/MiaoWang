<script setup lang="ts">
/**
 * 知识库列表的四态外壳（骨架 / 错误 / 空 / 有数据）
 *
 * 与 `components/health/RecordListStates.vue` 同思路（默认插槽 = 有数据的内容），
 * 但知识库有一个它没有的岔路：**空态有两种，下一步动作完全不同** ——
 * - "这个筛选下没有内容" → 动作是**换个分类/清筛选**
 * - "没有匹配关键词的内容" → 动作是**清空关键词**（多词 AND 是这句话最常见的成因）
 * 所以空态标题/描述/动作文案都由页面传进来，组件不猜。
 */
import { PhArrowClockwise, PhMagnifyingGlass, PhTray } from '@phosphor-icons/vue'

defineProps<{
  loading?: boolean
  error?: string
  /** 列表是不是空的（由页面判断，组件不去猜插槽内容） */
  empty?: boolean
  emptyTitle?: string
  emptyDesc?: string
  /** 空态动作按钮的文案；不传就不显示按钮 */
  emptyAction?: string
  /**
   * 空态的**成因**，决定图标与下一步动作该指向哪里：
   * `search` = 关键词没命中（下一步是清关键词）；`filter` = 筛选下没内容（下一步是换筛选）。
   * 显式传参，**不去猜标题文案里有没有某个词**（那种实现一改文案就静默失效）。
   */
  emptyKind?: 'search' | 'filter'
  /** 骨架卡片数 */
  skeletonCards?: number
}>()

const emit = defineEmits<{
  retry: []
  'empty-action': []
}>()
</script>

<template>
  <div v-if="loading" class="grid" aria-hidden="true">
    <span v-for="i in skeletonCards ?? 6" :key="i" class="mw-skeleton sk" />
  </div>

  <div v-else-if="error" class="mw-state mw-state--box">
    <PhArrowClockwise :size="24" aria-hidden="true" />
    <p class="mw-state__title">没能读到知识库内容</p>
    <p>{{ error }}</p>
    <button class="mw-btn mw-btn--ghost" type="button" @click="emit('retry')">重试</button>
  </div>

  <div v-else-if="empty" class="mw-state mw-state--box">
    <PhMagnifyingGlass v-if="emptyKind === 'search'" :size="24" aria-hidden="true" />
    <PhTray v-else :size="24" aria-hidden="true" />
    <p class="mw-state__title">{{ emptyTitle || '还没有内容' }}</p>
    <p v-if="emptyDesc">{{ emptyDesc }}</p>
    <button
      v-if="emptyAction"
      class="mw-btn mw-btn--ghost"
      type="button"
      @click="emit('empty-action')"
    >
      {{ emptyAction }}
    </button>
  </div>

  <slot v-else />
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--mw-space-3);
}
.sk {
  display: block;
  height: 132px;
}
</style>