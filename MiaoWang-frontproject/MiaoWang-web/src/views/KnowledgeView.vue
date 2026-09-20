<script setup lang="ts">
/**
 * 知识库列表（Web）
 *
 * 数据全部来自 `miaowang-knowledge`（经网关 7000），**没有一条硬编码内容**。
 * 契约与实测口径见 `@/api/knowledge` 的文件头，这里只说页面级的三条决定：
 *
 * 1) **这是"碎片库"不是"文章库"**：`content` 实测平均 12 字、最长 43 字，
 *    且**没有封面图、没有摘要、没有发布时间、没有作者**（DTO 就 12 个字段）。
 *    所以列表用**紧凑文字网格**（不摆大图卡、不做长文摘要位），信息密度靠归类行 + 标签挣。
 * 2) **公开只读，未登录完全可用** —— 路由刻意**不挂 `requiresAuth`**，
 *    请求全部 `anonymous: true`（不带 token）。所以这里**没有"去登录"态**：
 *    一个公开内容页把未登录用户推向登录页是错的。
 * 3) **搜索态与浏览态是两套控件**：搜索是裸数组、后端硬 `LIMIT 30`、**没有分页**
 *    → 搜索时必须隐藏翻页控件（点了也不会有反应，比不显示更糟）。
 *
 * 状态与请求都在 `useKnowledgeList()`，本页只做编排与排版（composition surface）。
 */
import { computed, onMounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { PhChatCircleDots, PhInfo, PhMagnifyingGlass } from '@phosphor-icons/vue'
import { categoryName, contentTypeText, type KnowledgeFragment } from '@/api/knowledge'
import { useKnowledgeList } from '@/composables/useKnowledgeList'
import KnowledgeCard from '@/components/knowledge/KnowledgeCard.vue'
import KnowledgeFilters from '@/components/knowledge/KnowledgeFilters.vue'
import KnowledgeStates from '@/components/knowledge/KnowledgeStates.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()

const {
  categories,
  categoriesLoading,
  species,
  categoryCode,
  keyword,
  items,
  loading,
  error,
  pageNum,
  pages,
  searchMode,
  hasNext,
  hasPrev,
  isEmpty,
  scopeText,
  countText,
  init,
  loadCategories,
  applyFilters,
  submitSearch,
  clearSearch,
  nextPage,
  prevPage,
  retry,
  loadPage,
} = useKnowledgeList()

/**
 * 首屏：先吃 URL query（`?category=diet&species=1`）再拉数据。
 *
 * 为什么要读 query：详情页底部的"看这个分类的全部内容"、以及从别处深链过来时，
 * 都靠 URL 表达筛选。**只做"进入时读一次"这一向**，不把筛选实时写回 URL ——
 * 双向同步会引入"改筛选就 push 历史"的复杂度，而知识库筛选的分享价值很低（收益不抵复杂度）。
 */
onMounted(async () => {
  const qCategory = typeof route.query.category === 'string' ? route.query.category : ''
  const qSpecies = typeof route.query.species === 'string' ? Number(route.query.species) : NaN
  const patch: { categoryCode?: string | null; species?: number | null } = {}
  if (qCategory) patch.categoryCode = qCategory
  if (qSpecies === 1 || qSpecies === 2) patch.species = qSpecies
  if (Object.keys(patch).length) {
    await loadCategories()
    await applyFilters(patch)
    return
  }
  await init()
})

/** 分类名在渲染前查好（12 行里同一个 code 重复 3 次、同名，所以按 code 查是安全的） */
function nameOf(item: KnowledgeFragment) {
  return categoryName(categories.value, item.categoryCode)
}

/**
 * 空态分两种，下一步动作完全不同：搜索态 → 清关键词；浏览态 → 换筛选。
 * 这个区分不是文案洁癖：多词 AND 会让"疫苗 驱虫"这类输入**必然**空，
 * 只有把"清空关键词"直接摆在空态里，用户才知道该改什么。
 */
const emptyState = computed(() =>
  searchMode.value
    ? {
        kind: 'search' as const,
        title: '没有匹配关键词的内容',
        desc: '知识库是"碎片"条目（一条一个知识点），词越多越难同时命中。先减到一个词，或者换个说法再试。',
        action: '清空关键词',
      }
    : {
        kind: 'filter' as const,
        title: '这个筛选下还没有内容',
        desc: '换一个分类或物种看看；也可以直接搜一个词（例如"呕吐""疫苗"）。',
        action: '回到全部内容',
      },
)

function onEmptyAction() {
  if (searchMode.value) clearSearch()
  else applyFilters({ species: null, categoryCode: null })
}

/** 筛选栏改物种/分类 → 回到第 1 页重拉（搜索态下是重跑搜索） */
const onSpecies = (v: number | null) => applyFilters({ species: v })
const onCategory = (v: string | null) => applyFilters({ categoryCode: v })

/** 分类栏里剔掉"这个分类一条内容都没有"的情况由后端数据决定，不在前端写死 */
const typeHints = computed(() =>
  ['warning', 'medical', 'care_schedule', 'guide']
    .map((t) => contentTypeText(t))
    .filter(Boolean),
)
</script>

<template>
  <div class="mw-container mw-page">
    <header class="khead">
      <h1 class="mw-h1">知识库</h1>
      <p class="mw-caption">
        经人工审校的养护要点，一条一个知识点。内容仅供参考，具体请咨询兽医。
      </p>
    </header>

    <KnowledgeFilters
      v-model:keyword="keyword"
      :species="species"
      :category-code="categoryCode"
      :categories="categories"
      :categories-loading="categoriesLoading"
      @change:species="onSpecies"
      @change:category="onCategory"
      @submit="submitSearch"
    />

    <div class="klayout">
      <main class="kmain">
        <div class="kbar">
          <p class="kbar__scope">
            <span class="kbar__label">当前范围</span>
            <span>{{ scopeText }}</span>
            <span v-if="searchMode" class="kbar__kw">关键词「{{ keyword.trim() }}」</span>
          </p>
          <p class="kbar__count mw-num">{{ countText }}</p>
        </div>

        <KnowledgeStates
          :loading="loading"
          :error="error"
          :empty="isEmpty"
          :empty-title="emptyState.title"
          :empty-desc="emptyState.desc"
          :empty-action="emptyState.action"
          :empty-kind="emptyState.kind"
          :skeleton-cards="6"
          @retry="retry"
          @empty-action="onEmptyAction"
        >
          <ul class="kgrid">
            <li v-for="item in items" :key="item.id">
              <KnowledgeCard :item="item" :category-name="nameOf(item)" />
            </li>
          </ul>
        </KnowledgeStates>

        <!--
          搜索态**不渲染**翻页控件：`/fragment/search` 是裸数组 + 后端硬 LIMIT 30，
          `pageNum/pageSize` 传了也被忽略 —— 摆一个点了没反应的"下一页"比不摆更糟。
        -->
        <nav v-if="!searchMode && pages > 1" class="kpager" aria-label="分页">
          <button class="mw-btn mw-btn--ghost" type="button" :disabled="!hasPrev" @click="prevPage">
            上一页
          </button>
          <span class="kpager__pos">
            第 <span class="mw-num">{{ pageNum }}</span> / <span class="mw-num">{{ pages }}</span> 页
          </span>
          <button class="mw-btn mw-btn--ghost" type="button" :disabled="!hasNext" @click="nextPage">
            下一页
          </button>
        </nav>

        <!-- 第一页也给一次"返回顶部/去最后一页"会让小数据量显得啰嗦，所以只在页数多时给直达 -->
        <p v-if="!searchMode && pages > 6" class="kjump">
          共 {{ pages }} 页，
          <button class="mw-link kjump__btn" type="button" @click="loadPage(pages)">跳到最后一页</button>
        </p>
      </main>

      <aside class="kside">
        <section class="kpanel">
          <h2 class="kpanel__title">
            <PhMagnifyingGlass :size="16" aria-hidden="true" />
            怎么搜更准
          </h2>
          <ul class="kpanel__list">
            <li><strong>一个词最准</strong>。后端是「与」关系，多个词要同时出现才算命中。</li>
            <li>搜的是<strong>标题与正文</strong>里的字面包含，不做同义词。</li>
            <li>
              搜索结果<strong>最多 30 条</strong>、<strong>不能翻页</strong>，没有命中就别再往后翻了。
            </li>
          </ul>
        </section>

        <section class="kpanel">
          <h2 class="kpanel__title">
            <PhInfo :size="16" aria-hidden="true" />
            内容类型
          </h2>
          <p class="kpanel__desc">每条内容都带一个类型徽标，看到「安全警示」请优先读完。</p>
          <p class="kpanel__chips">
            <span v-for="t in typeHints" :key="t" class="mw-badge kpanel__badge">{{ t }}</span>
          </p>
        </section>

        <section class="kpanel kpanel--ask">
          <h2 class="kpanel__title">
            <PhChatCircleDots :size="16" aria-hidden="true" />
            还有具体问题
          </h2>
          <p class="kpanel__desc">
            知识库是固定的条目，答不了"我家这只现在怎么办"。这类问题去 AI 问答，说清症状与时间。
          </p>
          <RouterLink class="mw-btn mw-btn--primary kpanel__cta" to="/ai">去 AI 问答</RouterLink>
          <p v-if="!auth.isLoggedIn" class="kpanel__note">AI 问答需要登录，会先带你去登录页。</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.khead {
  margin-bottom: var(--mw-space-6);
}
.khead .mw-caption {
  margin-top: var(--mw-space-2);
  max-width: 62ch;
}

.klayout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--mw-space-8);
  align-items: start;
}

.kbar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin-bottom: var(--mw-space-4);
  padding-bottom: var(--mw-space-3);
  border-bottom: 1px solid var(--mw-line);
}
.kbar__scope {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink);
  font-weight: 600;
}
.kbar__label {
  font-size: var(--mw-font-micro);
  font-weight: 600;
  color: var(--mw-muted);
}
.kbar__kw {
  font-weight: 500;
  color: var(--mw-brand-ink);
}
.kbar__count {
  flex: none;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.kgrid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--mw-space-3);
}
.kgrid > li {
  display: flex;
}
/* 子组件的根元素会带上父组件的 scope 属性 → 普通选择器就能撑满格子，不需要 :deep */
.kgrid > li > * {
  width: 100%;
}

.kpager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--mw-space-4);
  margin-top: var(--mw-space-6);
}
.kpager__pos {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.kjump {
  margin-top: var(--mw-space-3);
  text-align: center;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.kjump__btn {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  cursor: pointer;
}

.kside {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
}
.kpanel {
  padding: var(--mw-space-4);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.kpanel--ask {
  background: var(--mw-canvas);
}
.kpanel__title {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-caption);
  font-weight: 700;
  color: var(--mw-ink);
  margin-bottom: var(--mw-space-3);
}
.kpanel__desc {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
  margin-bottom: var(--mw-space-3);
}
.kpanel__list {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
  padding-left: var(--mw-space-4);
  list-style: disc;
}
.kpanel__list strong {
  color: var(--mw-ink);
}
.kpanel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
}
.kpanel__badge {
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
}
.kpanel__cta {
  display: inline-flex;
  width: 100%;
  justify-content: center;
}
.kpanel__note {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

@media (max-width: 960px) {
  .klayout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>