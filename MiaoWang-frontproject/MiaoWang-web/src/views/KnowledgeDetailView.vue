<script setup lang="ts">
/**
 * 知识碎片详情（Web）
 *
 * ## 与"文章详情"完全不同的三点（都来自实测契约，别按文章页改）
 * 1. **没有标题图、没有发布时间、没有作者、没有摘要** —— DTO 只有 12 个字段。
 *    正文实测**平均 12 字、最长 43 字**：这是"一个知识点一条"，所以正文就该短，
 *    排版不做"长文阅读节奏"（不用大段行高、不做目录、不做阅读进度）。
 * 2. **`GET /fragment/{id}` 是唯一有副作用的读**：后端会对 Redis 浏览量 `INCR`
 *    （并把"DB 值 + 未写回增量"作为实时值返回）→ **刷新一次就真的 +1**。
 *    所以这里**不做任何预取/轮询/埋点式重复调用**，只在 URL 的 id 变化时拉一次。
 * 3. `A000001 知识条目不存在` **同时覆盖"不存在 / id=0 / 未发布(status=1)"** 三种情况，
 *    后端刻意不可区分 → 错误态**不能**说"该内容已下架"这种断定，只说"没找到"。
 *
 * ## 为什么要有"同分类其他条目"
 * 单条内容太短（十几字），单独一篇撑不起一屏，用户读完也没有下一步。
 * 同分类列表是**唯一不需要额外接口**就能拿到的相关内容（`/fragment/page?categoryCode=`），
 * 而且它是真实数据、不是编出来的"推荐"。
 */
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { PhArrowLeft, PhArrowClockwise, PhEye, PhStethoscope } from '@phosphor-icons/vue'
import {
  categoryName,
  contentTypeText,
  contentTypeTone,
  excerpt,
  getFragment,
  knowledgeErrorMessage,
  listCategories,
  pageFragments,
  parseTags,
  speciesText,
  titleText,
  viewCountText,
  type KnowledgeCategory,
  type KnowledgeFragment,
} from '@/api/knowledge'
import KnowledgeCard from '@/components/knowledge/KnowledgeCard.vue'

const route = useRoute()

const item = ref<KnowledgeFragment | null>(null)
const loading = ref(true)
const error = ref('')

const categories = ref<KnowledgeCategory[]>([])
const related = ref<KnowledgeFragment[]>([])
const relatedLoading = ref(false)

/** URL 里的 id。非数字（理论上被路由 `\d+` 挡住，但直接改地址栏仍可能进来）→ 直接判错 */
const id = computed(() => {
  const raw = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  const n = Number(raw)
  return Number.isInteger(n) && n > 0 ? n : 0
})

const typeLabel = computed(() => contentTypeText(item.value?.contentType))
const badgeClass = computed(() =>
  contentTypeTone(item.value?.contentType) === 'danger'
    ? 'mw-badge--danger'
    : contentTypeTone(item.value?.contentType) === 'accent'
      ? 'mw-badge--time'
      : 'mw-badge--neutral',
)
const tags = computed(() => parseTags(item.value?.tags))
const sp = computed(() => speciesText(item.value?.species))
const catName = computed(() => categoryName(categories.value, item.value?.categoryCode))
const views = computed(() => Number(item.value?.viewCount ?? 0) || 0)

/** 分类与同分类条目都用同一个 code；分类名要在渲染前就有，所以先拉分类再拉同分类 */
async function loadRelated(code: string, excludeId: number) {
  relatedLoading.value = true
  related.value = []
  try {
    // 同分类取一页（每页 12），把自己剔掉。**这是唯一能拿到"相关内容"的方式**
    const page = await pageFragments({ categoryCode: code, pageNum: 1, pageSize: 12 })
    related.value = (page?.records ?? []).filter((r) => r.id !== excludeId).slice(0, 3)
  } catch {
    // 相关内容是"锦上添花"，拿不到就静默不显示 —— 不能让它的失败把正文也变成错误态
    related.value = []
  } finally {
    relatedLoading.value = false
  }
}

/** 非数字 / 0 的 id 是"地址栏被改坏了"，不是后端错误 —— 给一句自己的文案 */
const BAD_ID_MESSAGE = '这个地址里的编号不对，回列表挑一条内容吧'

async function load(targetId: number) {
  loading.value = true
  error.value = ''
  item.value = null
  related.value = []
  if (!targetId) {
    loading.value = false
    error.value = BAD_ID_MESSAGE
    return
  }
  try {
    const data = await getFragment(targetId)
    item.value = data
    // 分类没拉到不影响正文，所以**并行**而不是串行
    if (!categories.value.length) {
      listCategories()
        .then((list) => {
          categories.value = Array.isArray(list) ? list : []
        })
        .catch(() => {
          categories.value = []
        })
    }
    if (data?.categoryCode) void loadRelated(data.categoryCode, data.id)
  } catch (e) {
    error.value = knowledgeErrorMessage(e)
  } finally {
    loading.value = false
  }
}

/**
 * 必须 watch `route.params.id`：详情页会在"同分类的其他条目"之间互相跳转，
 * 组件被复用、`onMounted` 不会再触发 —— 只写 onMounted 的话点了相关条目页面不会变。
 */
watch(id, (next) => load(next), { immediate: true })

const retry = () => load(id.value)
</script>

<template>
  <div class="mw-container mw-page">
    <RouterLink class="back" :to="{ name: 'knowledge' }">
      <PhArrowLeft :size="16" aria-hidden="true" />
      返回知识库
    </RouterLink>

    <div v-if="loading" class="dwrap">
      <span class="mw-skeleton sk-title" />
      <span class="mw-skeleton sk-line" />
      <span class="mw-skeleton sk-body" />
    </div>

    <div v-else-if="error" class="mw-state mw-state--box">
      <PhArrowClockwise :size="24" aria-hidden="true" />
      <p class="mw-state__title">没能读到这条内容</p>
      <p>{{ error }}</p>
      <div class="mw-chips err-actions">
        <button class="mw-btn mw-btn--ghost" type="button" @click="retry">重试</button>
        <RouterLink class="mw-btn mw-btn--primary" :to="{ name: 'knowledge' }">回列表</RouterLink>
      </div>
    </div>

    <article v-else-if="item" class="doc">
      <header class="doc__head">
        <p class="doc__tags-line">
          <span v-if="typeLabel" class="mw-badge" :class="badgeClass">{{ typeLabel }}</span>
          <span v-if="sp" class="doc__sp">{{ sp }}</span>
          <span v-if="catName" class="doc__cat">{{ catName }}</span>
        </p>
        <h1 class="doc__title">{{ titleText(item.title) }}</h1>
        <p class="doc__meta">
          <span v-if="views > 0" class="doc__views">
            <PhEye :size="14" aria-hidden="true" />
            <span class="mw-num">{{ viewCountText(item.viewCount) }}</span>
          </span>
          <span class="mw-num doc__id">编号 {{ item.id }}</span>
        </p>
      </header>

      <!--
        正文实测极短（平均 12 字）→ 这里给"要点"块而不是长文段落：
        一块一眼看完，也不会因为字少显得页面空。
      -->
      <section class="doc__section">
        <h2 class="doc__label">要点</h2>
        <p class="doc__content">{{ excerpt(item.content, 400) || '这条内容暂时没有正文。' }}</p>
      </section>

      <section v-if="item.remark" class="doc__section doc__section--note">
        <h2 class="doc__label">补充说明</h2>
        <p class="doc__remark">{{ item.remark }}</p>
      </section>

      <p v-if="tags.length" class="doc__tagrow">
        <span v-for="t in tags" :key="t" class="dtag">{{ t }}</span>
      </p>

      <p class="doc__disclaimer">
        <PhStethoscope :size="14" aria-hidden="true" />
        内容仅供参考，不能替代兽医诊断；宠物状态不对请尽快就医。
      </p>
    </article>

    <section v-if="!loading && !error && item" class="related">
      <h2 class="related__title">「{{ catName || '同分类' }}」的其他条目</h2>
      <p v-if="relatedLoading" class="mw-caption">正在找同分类的条目…</p>
      <template v-else-if="related.length">
        <ul class="related__grid">
          <li v-for="r in related" :key="r.id">
            <KnowledgeCard :item="r" :category-name="catName" />
          </li>
        </ul>
        <RouterLink class="mw-link related__all" :to="{ name: 'knowledge', query: { category: item.categoryCode } }">
          看这个分类的全部内容
        </RouterLink>
      </template>
      <p v-else class="mw-caption">这个分类下暂时没有别的条目。</p>
    </section>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  margin-bottom: var(--mw-space-4);
  font-size: var(--mw-font-caption);
  font-weight: 600;
  color: var(--mw-ink-2);
}
.back:hover {
  color: var(--mw-brand-ink);
  text-decoration: none;
}

.dwrap {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.sk-title {
  height: 40px;
  width: 60%;
}
.sk-line {
  height: 20px;
  width: 30%;
}
.sk-body {
  height: 120px;
}
.err-actions {
  justify-content: center;
}

.doc {
  max-width: 68ch;
  padding: var(--mw-space-6);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}

.doc__tags-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--mw-space-2);
}
.doc__tags-line .mw-badge--neutral {
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
}
.doc__sp,
.doc__cat {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.doc__title {
  margin-top: var(--mw-space-3);
  font-size: var(--mw-font-h1);
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.02em;
  color: var(--mw-ink);
}

.doc__meta {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  margin-top: var(--mw-space-3);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.doc__views {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
}

.doc__section {
  margin-top: var(--mw-space-6);
  padding-top: var(--mw-space-4);
  border-top: 1px solid var(--mw-line);
}
.doc__label {
  margin-bottom: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--mw-muted);
}
.doc__content {
  font-size: var(--mw-font-h2);
  line-height: 1.55;
  color: var(--mw-ink);
}
/* 补充说明是"次一级信息"，用左侧细线做归属，不要抢正文 */
.doc__section--note .doc__remark {
  padding-left: var(--mw-space-3);
  border-left: 2px solid var(--mw-line);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}

.doc__tagrow {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-6);
}
.dtag {
  padding: 2px var(--mw-space-3);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}

.doc__disclaimer {
  display: flex;
  align-items: flex-start;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-6);
  padding: var(--mw-space-3);
  border-radius: var(--mw-radius-input);
  background: rgba(255, 176, 32, 0.12);
  color: var(--mw-accent-ink);
  font-size: var(--mw-font-micro);
}

.related {
  margin-top: var(--mw-space-8);
}
.related__title {
  margin-bottom: var(--mw-space-4);
  font-size: var(--mw-font-h2);
  font-weight: 600;
  letter-spacing: -0.01em;
}
.related__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--mw-space-3);
}
.related__grid > li {
  display: flex;
}
.related__grid > li > * {
  width: 100%;
}
.related__all {
  display: inline-block;
  margin-top: var(--mw-space-4);
  font-size: var(--mw-font-caption);
  font-weight: 600;
}
</style>