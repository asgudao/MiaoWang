/**
 * 知识库接口（Web）—— 公开内容区，**5 个 GET，全部匿名可读**
 *
 * 契约唯一来源（本文件所有"实测"结论都可复现）：
 *   - 源码：`MiaoWang-backproject/MiaoWang-knowledge/.../controller/KnowledgeController.java`
 *     （整个 knowledge 服务**只有这一个 controller**）、
 *     `.../service/impl/KnowledgeServiceImpl.java`、`.../constant/KnowledgeConstant.java`、
 *     `.../dto/resp/KnowledgeFragmentRespDTO.java`、`.../dao/entity/KnowledgeFragmentDO.java`
 *   - 复现工具：`MiaoWang-frontproject/tools/fetch-knowledge-fixtures.js`（探测 + 固化 fixture）、
 *     `tools/probe-knowledge-search.js`（多词语义专项）
 *   - fixture：`tools/knowledge-fixtures/web-*.json`（真实响应快照，**不含 token**）
 *
 * ## 本端刻意做的三件事（都不是"随手这么写"）
 *
 * 1) **全部走 `anonymous: true`（不发送本地 username/token）**。
 *    知识库在网关白名单里（只放行 `category` / `fragment` / `fragment/**` 三条读路径），
 *    匿名直接放行。为什么不带 token 更安全：`request.ts` 里 **HTTP 401/403 会无条件
 *    `auth.clear()` 清掉登录态**，而公开内容读**不该有任何身份副作用** ——
 *    一个过期 token 让"逛知识库"把人踢下线是最难查的一类 bug。
 *    （实测：白名单路径**带**假 token 也会被放行 —— 网关先判白名单、跳过 JWT 校验；
 *    但"能带"不等于"该带"，这里选择不带。）
 *
 * 2) **分类只拉一次全量，不带 `species`**。
 *    实测 `/category` 是 **12 行 = 4 个 `code` × 3 个 `species`（0 通用 / 1 猫 / 2 狗）**，
 *    同一个 `code` 的三行 **`name` 完全相同**、只有 `id` 与 `species` 不同。
 *    而 `?species=1` 只会回**该物种专属的那 4 行、不含 species=0 的通用行**；
 *    可碎片用的恰恰是通用 code（`fragment?species=1&categoryCode=diet` 有 51 条）
 *    → **一旦按 species 拉分类，分类栏就会出现"有文章却无名可显示"**。
 *    所以这里拉全量再按 `code` 去重（12 → 4），分类名永远查得到。
 *
 * 3) **分页推进用"自己发出的页码"，不看响应回显的 `pageNum`**。
 *    实测越界页（`pageNum=9999`）后端会**原样回显 9999** 只把 `records` 清空
 *    → 照回显推进就永远卡在越界页出不来。`hasMore` 一律以响应 `pages` 为准。
 *
 * ## 最容易踩的几条（都实测过，别按 REST 惯例猜）
 *
 * - **`/fragment/page` 只有 4 个查询参数**：`species` / `categoryCode` / `pageNum` / `pageSize`。
 *   ⚠️ **没有 `stage`、没有 `breedId`、没有 `contentType`** —— 传了被**静默忽略**
 *   （实测：带上它们与不带，返回的 id 序列逐字相同）。所以这三个字段**不能当筛选条件**。
 * - 筛选用错参数名也会被静默忽略：`category` / `categoryId` / `code` / `category_code`
 *   实测一律回全量（`total` 仍是 409）→ 参数名**只能**是 `categoryCode`。
 * - **两套返回形状**，解析方式完全不同：
 *   - `Paged`（页码信封）：`fragment/page` → `{pageNum,pageSize,total,pages,records}`
 *   - **裸数组**：`category` / `fragment` / `fragment/search` → 直接是 `[...]`，**没有** `total`
 * - 分页钳制（后端 `KnowledgeConstant`）：`pageNum<1 → 1`、`pageSize<1 → 10`、`pageSize>50 → 50`。
 * - **非法值的三种行为**（别统一处理）：`species`/`categoryCode` 非法 → **静默回 `[]`**；
 *   `pageNum=abc` / `species=abc` → `B000001 系统执行出错`（HTTP 仍是 **200**）；详情不存在 → `A000001`。
 * - **`species` 只有 1（猫）/ 2（狗）**；`species=0`（分类表里的"通用"）**一篇已发布文章都没有**（`total=0`）
 *   → 界面上不能把"通用"渲染成一个永远为空的筛选值。
 * - **`stage` 在当前数据里恒为 `null`**（409 条全 null）、**`breedId` 恒为 `null`**、`source` 恒为 `EXCEL`
 *   → 展示层不能依赖它们；`stage` 为空时**不要渲染标签**（别显示"通用"骗用户）。
 * - ⚠️ **`tags` 的分隔符是 `·`（U+00B7 中点），不是逗号**。建表注释与 DO 注释都写"逗号分隔"，
 *   **注释是错的**：51 条非空 tags 里**一个逗号都没有**，`·` 出现 22 次，其余 29 条是
 *   **没有分隔符的单标签**（`喂养原则`、`⚠️ 绝对禁忌`）。按逗号 split 会把整串当一个标签。
 * - ⚠️ **`/fragment/search` 的多词之间是 AND，不是 OR**。源码写的是逐词 `w.or()`，但紧跟的
 *   `w.and(Consumer)` 会被 MyBatis-Plus 补上 `AND` 并把悬空的 `OR` 顶掉 → 运行期是 AND。
 *   三条独立证据见 `tools/probe-knowledge-search.js`。**后果**：`"疫苗 驱虫"` 回 **0** 条
 *   （本地 OR 应为 4）→ **多词只会越搜越少**，UI 不拿它当卖点。
 * - ⚠️ **搜索没有分页**（裸数组 + 后端硬 `LIMIT 30`），`pageNum`/`pageSize` 传了也没用。
 * - ⚠️ **空串 / 纯空格关键词不报错**，而是退化成"不限关键词 + LIMIT 30"**回 30 条最新**
 *   → **空关键词绝不能发请求**（那会把"搜索"变成"翻最新 30 条"）。用 `isSearchableKeyword()` 拦住。
 * - ⚠️ `%` / `_` **未转义**（当 LIKE 通配符）：`keyword=%` 会命中 30 条"全部"
 *   → 纯通配符输入也当空关键词处理。
 * - 关键词**没有长度上限校验**（120 字 → 回 `[]`，不报错），与社区 `B000327` 相反。
 * - ⚠️ **DTO 里没有封面图字段**（实测字段就 12 个，且没有任何 `*url` / `*image*` / `cover*`）
 *   → 卡片必须**以文字为主**，不要假设有图。
 * - 记录里**没有 `categoryId`**，只有 `categoryCode`（字符串）→ 分类与文章**只能按 `code` 关联**。
 *
 * ## 浏览量（唯一有副作用的读）
 * `GET /fragment/{id}` 会对 Redis 计数 `INCR`（1 天 TTL，随后由定时任务合并写回 MySQL）：
 * 详情返回的是 **DB 值 + 未写回增量**（实时），列表返回的是**上次写回的值**（最多滞后一个窗口）。
 * 所以**同一篇文章在列表和详情里的浏览量可能不一样**，这是设计如此，不是 bug。
 */
import { ApiError, api } from './request'

/** 资源前缀（服务名 `miaowang-knowledge` 由请求层按 `SERVICE` 补上） */
const BASE = '/knowledge'

/** 后端 `KnowledgeConstant` 的三个分页常量（**必须与后端一致**，否则页码显示会骗人） */
export const DEFAULT_PAGE_NUM = 1
export const DEFAULT_PAGE_SIZE = 10
/** 每页上限：传 999 会被后端钳成 50 */
export const MAX_PAGE_SIZE = 50

/** URL 里 `tags` 的真实分隔符：`·`（U+00B7 中点），**不是逗号**（建表注释是错的） */
export const TAG_SEPARATOR = '\u00b7'

/* ---------- 数据模型（字段名照实测，不按 REST 惯例猜） ---------- */

/**
 * 分类（`GET /category` 的项，实测字段恰为这 5 个）。
 *
 * ⚠️ `code` 在 12 行里**重复 3 次**：`species` = 0 通用 / 1 猫 / 2 狗，**三行 `name` 完全相同**。
 * 展示前必须按 `code` 去重（用 `dedupeCategories()`）。
 */
export interface KnowledgeCategory {
  id: number
  /** 与 `KnowledgeFragment.categoryCode` 关联的键（**字符串**，不是 id） */
  code: string
  name: string
  /** 0 = 通用 / 1 = 猫 / 2 = 狗（**分类**才有 0；碎片的 species 只有 1/2） */
  species: number
  sort: number
}

/**
 * 知识碎片（`/fragment`、`/fragment/page` 的 `records`、`/fragment/search`、`/fragment/{id}`
 * **四个接口共用同一个 DTO**，实测字段恰为这 12 个）。
 *
 * ⚠️ **没有封面图字段**；**没有 `categoryId`**。
 * ⚠️ `stage` / `breedId` 在当前数据里**恒为 null**；`remark` / `tags` 可为 null。
 */
export interface KnowledgeFragment {
  id: number
  /** 关联 `KnowledgeCategory.code` */
  categoryCode: string
  /** 1 = 猫 / 2 = 狗 */
  species: number
  /** 品种 id，`null` = 全品种通用（实测**恒为 null**，品种库还没接） */
  breedId: number | null
  title: string
  /** 正文。实测很短（平均 12 字、最长 43 字）→ "摘要"基本就是原文 */
  content: string
  /** 备注/补充说明 */
  remark: string | null
  /** 适用阶段：幼年/成年/老年，`null` = 通用（实测**恒为 null**） */
  stage: string | null
  /** `care_schedule` / `guide` / `warning` / `medical` */
  contentType: string
  /** 标签，**`·`（U+00B7）分隔**、可为 null */
  tags: string | null
  /** 来源：EXCEL / LLM / MANUAL */
  source: string
  /**
   * 浏览量。列表里是"上次写回的值"，详情里是"DB 值 + 未写回增量"（实时）
   * → **同一篇文章两处可能不同**，这是设计如此。
   */
  viewCount: number | null
}

/** 页码分页信封（**只有 `/fragment/page` 用**；形状与社区一致） */
export interface Paged<T> {
  pageNum: number
  pageSize: number
  total: number
  pages: number
  records: T[]
}

/* ---------- 读接口（全部匿名，见文件头第 1 条） ---------- */

/**
 * 分类列表（**裸数组，没有分页信封**）。
 *
 * ⚠️ **刻意不支持 `species` 过滤**：后端支持，但 `?species=1` 只回该物种专属的 4 行、
 * **不含 species=0 的通用行**，而碎片用的恰恰是通用 code → 按 species 拉会让分类名查不到。
 * 全量只有 12 行（去重后 4 个），一次拉完最稳。
 *
 * 非法 `species` 的行为（留档，本端不用）：`99` → 静默 `[]`；`abc` → `B000001`。
 */
export function listCategories() {
  return api.get<KnowledgeCategory[]>('knowledge', `${BASE}/category`, { anonymous: true })
}

/**
 * 已发布碎片**全量**（裸数组，一次 409 条正文）。
 *
 * ⚠️ **页面不要用这个** —— 那是 409 条正文一把梭；列表页用 `pageFragments()`。
 * 它保留在这里是因为分之一：① 契约里确实有这 5 个接口之一；
 * ② "小数据量下按分类做客户端分组"这类场景偶尔需要。RAG 索引重建等内部消费方用的也是它。
 */
export function listAllFragments(params: {
  species?: number | null
  categoryCode?: string | null
} = {}) {
  return api.get<KnowledgeFragment[]>('knowledge', `${BASE}/fragment`, {
    anonymous: true,
    query: {
      species: params.species ?? undefined,
      categoryCode: params.categoryCode ?? undefined,
    },
  })
}

/**
 * 分页列表（**页码信封**）—— 列表页主力接口。
 *
 * `pageNum` / `pageSize` 会先按后端规则钳制（`<1` 回落默认、`>50` 截到 50）再发出去，
 * 这样**请求里带的页码就是后端实际用的页码**（否则界面上显示"第 0 页"而数据是第 1 页）。
 *
 * ⚠️ 筛选**只能**用 `species` + `categoryCode` 这两个（见文件头）。
 */
export function pageFragments(params: {
  species?: number | null
  categoryCode?: string | null
  pageNum?: number
  pageSize?: number
} = {}) {
  return api.get<Paged<KnowledgeFragment>>('knowledge', `${BASE}/fragment/page`, {
    anonymous: true,
    query: {
      species: params.species ?? undefined,
      categoryCode: params.categoryCode ?? undefined,
      pageNum: clampPageNum(params.pageNum),
      pageSize: clampPageSize(params.pageSize),
    },
  })
}

/**
 * 详情（`A000001 知识条目不存在` 覆盖"不存在 / id=0 / 未发布(status=1)"三种情况，
 * **刻意不可区分**；`id` 非数字 → `B000001`）。
 *
 * ⚠️ 这是**唯一有副作用的读**：会对 Redis 浏览量 `INCR`（并把实时值返回）。
 * 所以详情页刷新一次就真的 +1 浏览量，**不要**为了"预取"或埋点反复调它。
 */
export function getFragment(id: number) {
  return api.get<KnowledgeFragment>('knowledge', `${BASE}/fragment/${id}`, { anonymous: true })
}

/**
 * 关键词搜索（**裸数组，没有分页**，后端硬 `LIMIT 30`，按 `id` 倒序=最新在前）。
 *
 * ⚠️ 调用前**必须**用 `isSearchableKeyword()` 确认关键词可用：空串/纯空格**不报错**，
 * 而是退化成"不限关键词 + LIMIT 30"回 30 条最新 —— 那会让"搜索"静默变成"翻最新"。
 *
 * ⚠️ 多词是 **AND**（全词都要出现）→ "疫苗 驱虫" 回 0 条。UI 不要宣传多词。
 *
 * `species` / `categoryCode` 与关键词是 **AND** 关系（实测 `呕吐` 9 条 = 猫 3 + 狗 6）。
 */
export function searchFragments(params: {
  keyword: string
  species?: number | null
  categoryCode?: string | null
}) {
  return api.get<KnowledgeFragment[]>('knowledge', `${BASE}/fragment/search`, {
    anonymous: true,
    query: {
      keyword: params.keyword,
      species: params.species ?? undefined,
      categoryCode: params.categoryCode ?? undefined,
    },
  })
}

/* ---------- 纯函数（离线可测，`tools/check-knowledge-web.js` 直接断言这些） ---------- */

/**
 * 页码钳制（**与后端 `normalizePageNum` 同规则**）：`null` / `<1` → 1。
 *
 * ⚠️ 不能用 `Number.isFinite` 之外的宽松判断：`'2'` 这种字符串也要能进来（来路不明的 query）。
 */
export function clampPageNum(value: unknown): number {
  const n = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(n) || n < DEFAULT_PAGE_NUM) return DEFAULT_PAGE_NUM
  return Math.floor(n)
}

/**
 * 每页条数钳制（**与后端 `normalizePageSize` 同规则**）：`null` / `<1` → 10，`>50` → 50。
 */
export function clampPageSize(value: unknown): number {
  const n = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(n) || n < 1) return DEFAULT_PAGE_SIZE
  return Math.min(Math.floor(n), MAX_PAGE_SIZE)
}

/** 总页数（`pages`）——**以响应给的值优先**，缺失时才按 `total/pageSize` 自己算 */
export function totalPages(total: number | null | undefined, pageSize: number | null | undefined): number {
  const size = clampPageSize(pageSize)
  const t = Math.max(0, Number(total ?? 0) || 0)
  return size <= 0 ? 0 : Math.ceil(t / size)
}

/**
 * 还有没有下一页。
 *
 * ⚠️ **必须用"自己发出的页码"（`sentPageNum`）+ 响应的 `pages` 判断**，
 * 不能拿响应回显的 `pageNum`：实测越界页（`pageNum=9999`）后端**原样回显 9999**
 * → 照回显推进就永远出不来。
 */
export function hasMore(sentPageNum: number, page: Pick<Paged<unknown>, 'pages'> | null | undefined): boolean {
  if (!page) return false
  return clampPageNum(sentPageNum) < Math.max(0, Number(page.pages ?? 0) || 0)
}

/**
 * 分类按 `code` 去重（12 → 4），保留**第一个**出现的（后端已按 `sort` 升序返回）。
 *
 * 为什么必须去重：实测 12 行 = 4 个 `code` × 3 个 `species`，同 code 的 `name` 完全相同
 * → 不去重分类栏会把「饮食安全」连排三次。
 */
export function dedupeCategories(list: KnowledgeCategory[] | null | undefined): KnowledgeCategory[] {
  if (!Array.isArray(list)) return []
  const seen = new Set<string>()
  const out: KnowledgeCategory[] = []
  for (const c of list) {
    const code = (c?.code ?? '').trim()
    if (!code || seen.has(code)) continue
    seen.add(code)
    out.push(c)
  }
  return out
}

/**
 * 按 `code` 取分类名；查不到返回空串（**不编名字、也不退回 code** —— code 是内部编码，
 * 显示成 `care` 对用户没有意义）。
 */
export function categoryName(categories: KnowledgeCategory[] | null | undefined, code: string | null | undefined) {
  const key = (code ?? '').trim()
  if (!key || !Array.isArray(categories)) return ''
  return categories.find((c) => c.code === key)?.name ?? ''
}

/**
 * 解析 `tags`。
 *
 * ⚠️ 分隔符是 **`·`（U+00B7）`，不是逗号**：建表注释与 DO 注释都写"逗号分隔"，**注释是错的**
 * （实测 51 条非空 tags 里逗号出现 **0** 次，`·` 出现 22 次）。所以 `"a,b"` 是**一个**标签。
 *
 * 容忍实测出现过的全部脏输入：`null` / `undefined` / 空串 / 纯空格 / 只有分隔符 /
 * 首尾分隔符 / 连续分隔符 / 分隔符两侧空格 / emoji（`⚠️ 绝对禁忌`）。
 * 只做 split + trim + 丢空段 + 去重，**不猜测别的分隔符**。
 */
export function parseTags(raw: string | null | undefined): string[] {
  if (typeof raw !== 'string') return []
  const out: string[] = []
  const seen = new Set<string>()
  for (const part of raw.split(TAG_SEPARATOR)) {
    const t = part.trim()
    if (!t || seen.has(t)) continue
    seen.add(t)
    out.push(t)
  }
  return out
}

/**
 * 摘要截断（按**字符**数，不按字节；中文一个字算一个）。
 *
 * 实测正文很短（平均 12 字、最长 43 字），所以正常数据基本**不会**触发截断 ——
 * 但空值、超长值（未来导入新内容）都必须安全：空 → `''`，超长 → 截断 + `…`。
 */
export function excerpt(text: string | null | undefined, max = 60): string {
  const t = (text ?? '').trim()
  if (!t) return ''
  const limit = Math.max(1, Math.floor(max))
  if (t.length <= limit) return t
  return `${t.slice(0, limit)}…`
}

/** 浏览量文案（`null`/`0` 都不显示"0 次浏览"这种废话，但也不谎报有数据） */
export function viewCountText(n: number | null | undefined): string {
  const v = Number(n ?? 0)
  if (!Number.isFinite(v) || v <= 0) return '暂无浏览'
  if (v < 10_000) return `${v} 次浏览`
  const wan = v / 10_000
  return `${wan >= 100 ? Math.round(wan) : Number(wan.toFixed(1))} 万次浏览`
}

/**
 * `species` → 展示名。
 *
 * 取值来自源码注释（`KnowledgeFragmentDO` / `KnowledgeFragmentRespDTO`：`1=猫 2=狗`）。
 * ⚠️ 分类表还有 `0=通用`，但**碎片的 species 实测只有 1/2**（`species=0` 的文章一篇都没有）
 * → 这里把 0 也认出来（配置文件角度它合法），但 UI 不该用它当筛选值。
 * 未知值**不编名字**，如实显示 `未知物种(9)` 便于发现问题。
 *
 * 入参故意收 `string`：从路由 query（`?species=1`）或 `localStorage` 来的值**运行时就是字符串**，
 * 签名里只写 `number` 会逼调用方到处 `Number()` 转换，反而更容易漏掉空值判断。
 */
export function speciesText(species: number | string | null | undefined): string {
  // ⚠️ **必须先挡空值**：`Number(null)` 和 `Number('')` 都等于 `0`，直接 switch 会把
  // "没有物种"误判成 `0=通用` —— 那是在编造后端没给的信息（与 `stageText` 同一条原则）。
  if (species === null || species === undefined || species === '') return ''
  switch (Number(species)) {
    case 0:
      return '通用'
    case 1:
      return '猫'
    case 2:
      return '狗'
    default:
      return Number.isFinite(Number(species)) ? `未知物种(${species})` : ''
  }
}

/**
 * `stage` → 展示名。
 *
 * ⚠️ **当前数据里 `stage` 恒为 `null`**（409 条全 null）→ 空值返回 `''`，
 * 让 UI **不渲染**这个标签。**刻意不把 null 显示成"通用"**：那是在编造后端没说的信息。
 */
export function stageText(stage: string | null | undefined): string {
  const s = (stage ?? '').trim()
  return s
}

/**
 * `contentType` → 展示名（取值与中文名来自 `KnowledgeFragmentDO` 的字段注释）。
 * 未知类型返回 `''`（宁可少一个标签，也不把内部编码 `foo_bar` 甩到用户脸上）。
 */
export function contentTypeText(contentType: string | null | undefined): string {
  switch ((contentType ?? '').trim()) {
    case 'care_schedule':
      return '护理日程'
    case 'guide':
      return '科普'
    case 'warning':
      return '安全警示'
    case 'medical':
      return '医疗自查'
    default:
      return ''
  }
}

/**
 * 内容类型 → 视觉档位（**只给类名，不写死颜色**，具体色值由设计系统令牌决定）。
 * `warning` 是"安全警示"（最该被看见），`medical` 是"医疗自查"（需就医语境），
 * `care_schedule` 是"该做事了"（时间语义），`guide` 是普通科普。
 */
export function contentTypeTone(contentType: string | null | undefined): 'danger' | 'accent' | 'brand' | 'neutral' {
  switch ((contentType ?? '').trim()) {
    case 'warning':
      return 'danger'
    case 'medical':
      return 'accent'
    case 'care_schedule':
      return 'brand'
    default:
      return 'neutral'
  }
}

/**
 * 关键词能不能搜 —— **这是防"空搜索静默变成翻最新"的闸门**。
 *
 * 实测：空串 / 纯空格 / `%` / `_` 这类输入后端**都不报错**，而是退化成
 * "不限关键词 + LIMIT 30"**回 30 条最新**。UI 上那看起来像"搜到了 30 条"，
 * 其实与搜索毫无关系 → 必须在前端拦住，让空态停在分页接口上。
 *
 * 规则：trim 后非空，且**不是"只有 LIKE 通配符"**（`%` / `_` 未转义，纯通配符等于匹配全部）。
 */
export function isSearchableKeyword(raw: string | null | undefined): boolean {
  const t = (raw ?? '').trim()
  if (!t) return false
  return t.replace(/[%_]/g, '').trim().length > 0
}

/**
 * 收敛成"可发送的关键词"：取 trim 后的值；不可搜时返回空串（调用方据此不发请求）。
 *
 * ⚠️ **刻意不做"多词拆分/去重/取第一个词"**：多词是 **AND** 语义（实测），
 * 前端擅自改写用户输入会让"0 条"变成更难解释的行为。原样发出去、用 UI 文案说明
 * "需要同时包含全部词"才是最诚实的做法。
 */
export function normalizeKeyword(raw: string | null | undefined): string {
  const t = (raw ?? '').trim()
  return isSearchableKeyword(t) ? t : ''
}

/** 关键词里的词数（>1 时 UI 应提示"需要同时包含全部词"） */
export function keywordTermCount(raw: string | null | undefined): number {
  const t = (raw ?? '').trim()
  if (!t) return 0
  return t.split(/\s+/).filter(Boolean).length
}

/** 标题兜底：实测 `title` 是 NOT NULL，但空串照样能显示成一片空白，给个中性占位 */
export function titleText(title: string | null | undefined): string {
  const t = (title ?? '').trim()
  return t || '未命名条目'
}

/**
 * 知识库错误码 → 人话。
 *
 * ⚠️ 注意 `A000001` 在本项目里**身兼两职**（参数错 + pet/healthy 的"未登录"），
 * 但知识库是公开接口、**不需要登录** → 这里把 `A000001` 按"条目不存在/参数不对"解释，
 * **不做"掉线跳登录"**（那会让公开页莫名把人推向登录页）。
 */
export function knowledgeErrorMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return (e as Error)?.message || '未知错误'
  switch (e.code) {
    case 'NETWORK':
      // message 里带着实际请求地址，比笼统文案有用 → 直接透传
      return e.message || '连不上后端，请检查后端进程与 Vite 代理'
    case 'A000001':
      // 详情不存在 / id=0 / 未发布 —— 后端刻意不区分
      return '这篇内容不存在或已下架，回列表看看别的'
    case 'B000001':
      return '这个请求没能被处理。可能是筛选参数写错了（知识库只支持 species 与 categoryCode 两个筛选维度），也可能是服务正在重启'
    case '404':
      return '知识库服务没找到（多半是服务名或路由写错了）'
    default:
      return e.message || '请求失败，请稍后再试'
  }
}