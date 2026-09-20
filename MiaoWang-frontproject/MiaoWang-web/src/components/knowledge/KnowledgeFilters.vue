<script setup lang="ts">
/**
 * 知识库筛选栏（物种分段控件 + 分类 chips + 搜索框）
 *
 * 三个实测口径决定了这里的形状（**不要按"通用筛选栏"改**）：
 *
 * 1. **物种只有「全部 / 猫 / 狗」三项** —— 后端 `species` 实测只有 1（猫）/ 2（狗），
 *    而分类表里那个 `0`（通用）**一篇已发布文章都没有**（`total=0`）
 *    → 界面上**不提供"通用"这一项**，否则就是一个永远为空的筛选值。
 * 2. **分类 chips 用去重后的 4 个**（`code` 在 12 行里重复 3 次，同 code 同名）→ 去重在
 *    `useKnowledgeList`/`dedupeCategories` 里做，这里只负责渲染。
 * 3. **搜索只能按"单个意图"用**：多词是 **AND**（"疫苗 驱虫"回 0 条）。这里**不改写**用户输入，
 *    只在出现多个词时给一句**诚实提示**（"需要同时包含全部词"），而不是自作聪明地只取第一个词。
 *
 * 组件只上报"用户想改什么"，不发请求、不持有结果 —— 请求与状态都在 `useKnowledgeList()` 里。
 */
import { computed } from 'vue'
import { PhMagnifyingGlass, PhX } from '@phosphor-icons/vue'
import { keywordTermCount, type KnowledgeCategory } from '@/api/knowledge'

const props = defineProps<{
  categories: KnowledgeCategory[]
  categoriesLoading?: boolean
  /** `null` = 全部物种 */
  species: number | null
  /** `null` = 全部分类 */
  categoryCode: string | null
  /** 输入框里的原始文本（双向绑定） */
  keyword: string
}>()

/**
 * 事件名用 `change:` 前缀而不是 `update:` —— 只有 `keyword` 需要双向绑定（`v-model:keyword`），
 * 而物种/分类的"改值"必须**顺带重拉数据**（在页面里做），所以刻意不用 v-model，
 * 避免"值被写了两次（v-model 一次 + 处理函数一次）"这种看不出来的重复赋值。
 */
const emit = defineEmits<{
  'change:species': [value: number | null]
  'change:category': [value: string | null]
  'update:keyword': [value: string]
  /** 提交搜索（回车 / 点放大镜） */
  submit: []
}>()

/**
 * ⚠️ **刻意没有 `species: 0`（通用）**：实测 `species=0` 回 `total=0`。
 * 放上去就是一个永远空的筛选项。
 */
const SPECIES_OPTIONS: { value: number | null; label: string }[] = [
  { value: null, label: '全部物种' },
  { value: 1, label: '猫' },
  { value: 2, label: '狗' },
]

const terms = computed(() => keywordTermCount(props.keyword))
/** 多词时给提示：多词是 AND，会越搜越少 —— 这不是"高级用法"，是必须说明的限制 */
const multiWordHint = computed(() => terms.value > 1)
</script>

<template>
  <section class="filters">
    <div class="filters__row">
      <div class="mw-seg" role="group" aria-label="按物种筛选">
        <button
          v-for="opt in SPECIES_OPTIONS"
          :key="String(opt.value)"
          class="mw-seg__item"
          :class="{ 'mw-seg__item--on': species === opt.value }"
          type="button"
          :aria-pressed="species === opt.value"
          @click="emit('change:species', opt.value)"
        >
          {{ opt.label }}
        </button>
      </div>

      <form class="search" @submit.prevent="emit('submit')">
        <PhMagnifyingGlass class="search__icon" :size="16" aria-hidden="true" />
        <input
          class="mw-input search__input"
          type="search"
          :value="keyword"
          placeholder="搜症状、食材、疫苗…（一个词最准）"
          aria-label="搜索知识库"
          @input="emit('update:keyword', ($event.target as HTMLInputElement).value)"
        />
        <button
          v-if="keyword"
          class="search__clear"
          type="button"
          aria-label="清空关键词"
          @click="emit('update:keyword', '')"
        >
          <PhX :size="14" aria-hidden="true" />
        </button>
      </form>
    </div>

    <div class="chips-wrap">
      <span class="chips-label">分类</span>
      <div class="mw-chips">
        <button
          class="mw-chip"
          :class="{ 'mw-chip--on': categoryCode === null }"
          type="button"
          @click="emit('change:category', null)"
        >
          全部
        </button>
        <button
          v-for="c in categories"
          :key="c.code"
          class="mw-chip"
          :class="{ 'mw-chip--on': categoryCode === c.code }"
          type="button"
          @click="emit('change:category', c.code)"
        >
          {{ c.name }}
        </button>
        <span v-if="categoriesLoading && !categories.length" class="chips-hint">分类加载中…</span>
      </div>
    </div>

    <p v-if="multiWordHint" class="hint">
      你输入了 {{ terms }} 个词：后端是<strong>「与」关系</strong>，需要每条内容<strong
        >同时包含全部词</strong
      >，所以词越多命中越少。找不到时先减到<strong>一个词</strong>再搜。
    </p>
  </section>
</template>

<style scoped>
.filters {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-6);
}

.filters__row {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  flex-wrap: wrap;
}

.search {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 240px;
  max-width: 420px;
}
.search__icon {
  position: absolute;
  left: var(--mw-space-3);
  color: var(--mw-muted);
  pointer-events: none;
}
.search__input {
  width: 100%;
  padding-left: calc(var(--mw-space-3) * 2 + 16px);
  padding-right: var(--mw-space-8);
}
.search__clear {
  position: absolute;
  right: var(--mw-space-2);
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: var(--mw-radius-pill);
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
  cursor: pointer;
}
.search__clear:hover {
  color: var(--mw-ink);
}

.chips-wrap {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  flex-wrap: wrap;
}
.chips-label {
  font-size: var(--mw-font-micro);
  font-weight: 600;
  color: var(--mw-muted);
}
.chips-hint {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.hint {
  padding: var(--mw-space-2) var(--mw-space-3);
  border-radius: var(--mw-radius-input);
  background: rgba(255, 176, 32, 0.12);
  color: var(--mw-accent-ink);
  font-size: var(--mw-font-micro);
}

@media (max-width: 640px) {
  .search {
    max-width: none;
  }
}
</style>