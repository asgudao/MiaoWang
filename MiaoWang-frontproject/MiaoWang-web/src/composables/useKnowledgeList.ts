/**
 * 知识库列表页的"筛选 + 分页 + 搜索"状态机（Web）
 *
 * ## 为什么是 composable 而不是 Pinia store
 * 知识库是**公开只读**内容，筛选/页码/结果**没有任何跨页面与跨账号的共享价值**：
 * 详情页自己按 id 拉一次就够了，换账号也不该"继承"上一个人的筛选。
 * 而项目门禁 `tools/check-stores.js` 有一条硬规则：**导出了 `reset` 的 store 必须在
 * `stores/auth.ts` 的账号清理路径里被调用**。为公开数据造一个 store 再被迫回答
 * "换账号要不要清知识库筛选"，是个纯粹的伪问题 → 直接用页面级 composable 绕开它。
 *
 * ## 本文件承载的三条"契约陷阱"（都实测过，别改回去）
 * 1. **空关键词绝不发请求**：`/fragment/search` 对空串/纯空格**不报错**，
 *    而是退化成"不限关键词 + LIMIT 30"回 30 条最新 —— 那会让"搜索"静默变成"翻最新"。
 *    闸门是 `isSearchableKeyword()`。
 * 2. **分页推进用"自己发出的页码"**：越界时后端**原样回显** `pageNum`
 *    （`9999` → `9999`）只清空 `records` → 拿回显值推进会永远卡在越界页。
 * 3. **搜索态没有分页**：`search` 是裸数组 + 后端硬 `LIMIT 30`，`pageNum/pageSize` 传了没用
 *    → 搜索态必须隐藏翻页控件，否则用户点"下一页"什么都不会发生。
 *
 * ## 状态机（四种界面态互斥，避免"骨架 + 空态"同时出现）
 * `loading`（首屏骨架）→ `error`（说清原因 + 重试）
 *                    ↓ 成功
 *   `results.length > 0` → 列表；否则 → `empty`（**空态文案分两种**：
 *   "这个筛选下没有内容" vs "没有匹配关键词的内容"，后者要能一键清空关键词）
 */
import { computed, ref } from 'vue'
import {
  categoryName,
  clampPageNum,
  dedupeCategories,
  DEFAULT_PAGE_SIZE,
  hasMore,
  knowledgeErrorMessage,
  listCategories,
  normalizeKeyword,
  pageFragments,
  searchFragments,
  type KnowledgeCategory,
  type KnowledgeFragment,
} from '../api/knowledge'

/** 列表每页条数。12 = 三列卡片正好四行（与社区列表同口径）；**必须 ≤ 后端上限 50** */
export const KNOWLEDGE_PAGE_SIZE = 12

export function useKnowledgeList() {
  /* ---------- 分类（拉全量再按 code 去重，理由见 api/knowledge.ts 文件头） ---------- */

  const categories = ref<KnowledgeCategory[]>([])
  const categoriesLoading = ref(false)
  const categoriesError = ref('')

  /* ---------- 筛选条件 ---------- */

  /** `null` = 全部物种（不传 `species` 参数） */
  const species = ref<number | null>(null)
  /** `null` = 全部分类（不传 `categoryCode` 参数） */
  const categoryCode = ref<string | null>(null)
  /** 输入框里的**原始**文本；真正生效的是 `activeKeyword` */
  const keyword = ref('')
  /** 已提交生效的关键词（空串 = 非搜索态） */
  const activeKeyword = ref('')

  /* ---------- 结果 ---------- */

  const items = ref<KnowledgeFragment[]>([])
  /** 初始 `true`：首屏直接给骨架，不先闪一下"还没有内容"的空态 */
  const loading = ref(true)
  const error = ref('')

  /** 分页进度。`pageNum` 是**我们发出去的**页码，不是响应回显的 */
  const pageNum = ref(1)
  const pages = ref(0)
  const total = ref(0)

  const searchMode = computed(() => activeKeyword.value !== '')
  const hasNext = computed(() => hasMore(pageNum.value, { pages: pages.value }))
  const hasPrev = computed(() => pageNum.value > 1)
  const isEmpty = computed(() => !loading.value && !error.value && items.value.length === 0)

  /** 当前筛选的人话描述（空态与标题都用它，避免两处文案各说一套） */
  const scopeText = computed(() => {
    const parts: string[] = []
    if (species.value !== null) parts.push(species.value === 1 ? '猫' : species.value === 2 ? '狗' : '通用')
    const name = categoryName(categories.value, categoryCode.value)
    if (name) parts.push(name)
    return parts.length ? parts.join('，') : '全部内容'
  })

  /** 结果条数的说明。搜索态只能报"已返回 N 条"（**没有 total**，后端 LIMIT 30） */
  const countText = computed(() => {
    if (searchMode.value) return items.value.length >= 30 ? '已返回前 30 条' : `找到 ${items.value.length} 条`
    return `共 ${total.value} 条`
  })

  /* ---------- 读 ---------- */

  function noteError(e: unknown): string {
    return knowledgeErrorMessage(e)
  }

  async function loadCategories() {
    categoriesLoading.value = true
    categoriesError.value = ''
    try {
      // ⚠️ 不带 species：`?species=1` 只回该物种专属的 4 行、不含通用行，
      // 而碎片用的恰恰是通用 code → 按 species 拉会让分类名查不到
      const list = await listCategories()
      categories.value = dedupeCategories(Array.isArray(list) ? list : [])
    } catch (e) {
      categoriesError.value = noteError(e)
    } finally {
      categoriesLoading.value = false
    }
  }

  /** 归一化成"当前筛选 + 页码"的请求参数（分页与搜索两条路共用） */
  function filters() {
    return { species: species.value, categoryCode: categoryCode.value }
  }

  /** 拉某一页（**页码由调用方给**，不读响应回显） */
  async function loadPage(page: number) {
    const sent = clampPageNum(page)
    loading.value = true
    error.value = ''
    // ⚠️ 页码在**发请求前**就记下来（记的是"我们发出去的"值，不是响应回显的 `resp.pageNum`）。
    // 放在 await 之后会有一个真实缺陷：这一页**失败**时页码停留在上一页，
    // 于是错误态的 `retry()` 会去重试**上一页**而不是用户刚点的那一页。
    pageNum.value = sent
    try {
      const resp = await pageFragments({ ...filters(), pageNum: sent, pageSize: KNOWLEDGE_PAGE_SIZE })
      items.value = Array.isArray(resp?.records) ? resp.records : []
      total.value = Number(resp?.total ?? 0) || 0
      pages.value = Number(resp?.pages ?? 0) || 0
    } catch (e) {
      error.value = noteError(e)
      items.value = []
    } finally {
      loading.value = false
    }
  }

  /** 搜索（**永远第 1 页、没有分页**） */
  async function runSearch(kw: string) {
    const clean = normalizeKeyword(kw)
    // 闸门：空/纯空格/纯通配符 → 不发请求，回到浏览态
    if (!clean) {
      activeKeyword.value = ''
      await loadPage(1)
      return
    }
    activeKeyword.value = clean
    loading.value = true
    error.value = ''
    try {
      const list = await searchFragments({ keyword: clean, ...filters() })
      items.value = Array.isArray(list) ? list : []
      // 搜索没有分页 → 把分页进度归零，避免翻页控件显示一个不存在的"第 1/41 页"
      total.value = items.value.length
      pages.value = 0
      pageNum.value = 1
    } catch (e) {
      error.value = noteError(e)
      items.value = []
    } finally {
      loading.value = false
    }
  }

  /** 首屏：分类与第一页互不依赖，一起发 */
  async function init() {
    await Promise.all([loadCategories(), loadPage(1)])
  }

  /* ---------- 交互 ---------- */

  /** 换物种 / 换分类 / 清空关键词 → 都回到第 1 页重拉（搜索态下是重跑搜索） */
  async function applyFilters(next?: { species?: number | null; categoryCode?: string | null }) {
    if (next && 'species' in next) species.value = next.species ?? null
    if (next && 'categoryCode' in next) categoryCode.value = next.categoryCode ?? null
    if (searchMode.value) await runSearch(activeKeyword.value)
    else await loadPage(1)
  }

  /** 提交搜索框（回车 / 点按钮） */
  async function submitSearch() {
    await runSearch(keyword.value)
  }

  /** 清空搜索，回到浏览态（空态里的一键操作） */
  async function clearSearch() {
    keyword.value = ''
    activeKeyword.value = ''
    await loadPage(1)
  }

  async function goPage(page: number) {
    if (searchMode.value) return // 搜索态没有分页
    await loadPage(page)
  }
  const nextPage = () => goPage(pageNum.value + 1)
  const prevPage = () => goPage(pageNum.value - 1)
  const retry = () => (searchMode.value ? runSearch(activeKeyword.value) : loadPage(pageNum.value))

  return {
    // 分类
    categories,
    categoriesLoading,
    categoriesError,
    loadCategories,
    // 筛选
    species,
    categoryCode,
    keyword,
    activeKeyword,
    // 结果
    items,
    loading,
    error,
    pageNum,
    pages,
    total,
    // 派生
    searchMode,
    hasNext,
    hasPrev,
    isEmpty,
    scopeText,
    countText,
    // 动作
    init,
    loadPage,
    applyFilters,
    submitSearch,
    clearSearch,
    goPage,
    nextPage,
    prevPage,
    retry,
  }
}

/** 每页条数的展示用常量（页面里的分页控件需要它算"第几页 / 共几页"） */
export const PAGE_SIZE_LABEL = `${DEFAULT_PAGE_SIZE} 条/页`