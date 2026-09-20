/**
 * 知识库接口（手机端 / JS）
 *
 * 契约唯一来源：**本文件头的实测记录**（2026-09-20 直连网关 7000 逐个实测 + 与
 * `MiaoWang-knowledge` 的 `KnowledgeController` / `KnowledgeServiceImpl` / `KnowledgeConstant`
 * 源码逐条对齐）。同目录 `MiaoWang-frontproject/tools/knowledge-fixtures/_probe-log.txt`
 * 是 Web 侧并行的 50 行实测，结论一致。
 *
 * ⚠️ 知识库是全仓库**唯一"匿名可读"的业务模块**（网关白名单按读路径逐条列：
 * `/knowledge/category`、`/knowledge/fragment`、`/knowledge/fragment/**`）。
 * 所以这里**一律传 `anonymous: true`**：不带身份 → 结构上不可能触发任何身份副作用。
 * 实测：带**垃圾 token**（`username=nope` / `token=garbage`）打这 5 个路径也一律 HTTP 200 + `code:"0"`
 * —— 网关对白名单路径**完全不校验 token**，所以"登录态过期的人照样能读知识库"。
 *
 * ---------------------------------------------------------------------------
 * 实测契约（与 REST 惯例不同、最容易猜错的地方）
 * ---------------------------------------------------------------------------
 * 1) **分类只认字符串 `code`**：`knowledge_category.code` ↔ `knowledge_fragment.category_code`。
 *    记录里**没有 `categoryId`**（`fragment` 的字段只有 12 个：
 *    id / categoryCode / species / breedId / title / content / remark / stage / contentType / tags / source / viewCount）。
 * 2) ⚠️ **`/category` 返回的 12 条不是 12 个不同分类**：是 **4 个 `code` × 3 个 `species`(0 通用/1 猫/2 狗)
 *    的重复行**，同名同码，只有 `id` 与 `species` 不同：
 *    `diet 饮食安全` / `behavior 行为解读` / `care 日常养护` / `health 健康自查`。
 *    去重后只有 **4 个分类** → 页面必须按 `code` 去重（`dedupeCategories`），否则 chips 会出现
 *    "饮食安全" 连排三次。真正的筛选维度是 `categoryCode` × `species`（4 × 3 = 12 种组合）。
 * 3) **筛选参数名是 `categoryCode`**（实测：`category` / `code` / `categoryId` **一律被忽略**，静默返回全部）。
 *    ⚠️ **非法 `categoryCode` 静默回空**（`total:0`），**不报错** → 前端不要靠它做参数校验。
 * 4) **`species` 只认 0 / 1 / 2**：不传（或传空串）= 全部；`0` = 通用（**实测库里没有任何 species=0 的碎片 → `total:0`**）；
 *    `99` / `-1` → `total:0` 不报错；`abc` → **`B000001 系统执行出错`**（类型不匹配，HTTP 200）。
 * 5) **页码分页**：`{pageNum,pageSize,total,pages,records}`。
 *    - `pageNum` < 1（含 0 / 负数）→ 回落 **1**；越界页（如 9999）**不报错**，`records:[]`
 *    - `pageSize` < 1 → 回落 **10**；**上限 50**（`100` / `999` / `1000` 一律被钳成 50）
 *    - 排序固定 **`species ASC, categoryCode ASC, id ASC`**（不是按时间，也不是按浏览量）
 * 6) ⚠️ **搜索不分页、且硬上限 30 条**：`/fragment/search` 的 `data` 是**裸数组**，
 *    `pageNum` / `pageSize` **传了也被忽略**；源码 `.last("LIMIT 30")` + `id DESC`。
 *    → 搜索态**没有"触底加载"**，界面必须**如实**说明"一次最多 30 条"，不能假装还能翻页。
 * 7) ⚠️ **搜索 `keyword` 是必填**：**完全不传** → `B000001 系统执行出错`；
 *    但**传空串 / 纯空格**不报错，等价于"不带关键词"，返回按 `id DESC` 的 **30 条**。
 *    → 所以"关键词为空"这条路由由**前端**决定：空关键词一律走分页接口，**不发搜索请求**。
 * 8) ⚠️⚠️ **搜索是"词内 OR、词间 AND"** —— 按空白切分，**最多取前 8 个词**（`limit(8)`），
 *    每个词内部 `title LIKE %词% OR content LIKE %词%`，但**词与词之间是 AND**（取交集），
 *    结果按 `id DESC`。等价的 SQL：
 *      `((title LIKE %a% OR content LIKE %a%) AND (title LIKE %b% OR content LIKE %b%))`
 *
 *    **2026-09-20 亲测（10 组对照，不是从源码猜的）**：
 *      `呕吐` 10 条 / `白沫` 1 条 / **`呕吐 白沫` 1 条**（正好是两者**交集**，与调换词序无关）
 *      `疫苗` 3 条 / `驱虫` 4 条 / **`疫苗 驱虫` 0 条**（两批记录没有交集）
 *      `猫` 30 条 / `猫 呕吐` **0 条**
 *    → **加词只会越搜越少，多词绝不会搜得更多**。这条对界面文案有直接影响：
 *      **不要**写"多个关键词用空格分隔，搜得更全"（那是 OR 的承诺）；正确的说法是
 *      "用空格加词会收窄结果"。本文件与列表页的提示文案都按 AND 写。
 *    为什么容易写错：源码里 `for` 循环里出现了一个 `w.or()`，第一眼很像"词间 OR"；
 *    但 MyBatis-Plus 的 `and(Consumer)` 会把每一段各自包成括号，两个括号之间仍由外层 AND 连接。
 *    → **以实测为准，不以读源码的直觉为准**。
 *    ⚠️ 关键词里的 `%` / `_` **未转义**（实测 `keyword=%` 命中 30 条）→ 脏字符会退化成通配。
 *    本文件不替用户清洗（那会改变语义），只在纯函数里做"展示层"的长度与空白处理。
 * 9) **搜索支持 `species` + `categoryCode` 过滤**（实测同词按物种拆开后两批**互不重叠**、
 *    且猫+狗命中数正好等于不带物种时的条数）→ 搜索态沿用当前选中的分类与物种。
 * 10) ⚠️ **详情不存在 / 未发布 → `A000001 知识条目不存在`**（HTTP 200，**不是 404、也不是 B 码**）；
 *    路径参数非数字 → `B000001`。**未发布（status=1）与真的不存在给同一个码**，刻意不区分
 *    （2026-09-20 实测：`fragment/410` 当时 status=1 与 `fragment/999999` 返回同一条消息；
 *    后来 410 被发布成正式内容，同一天的 `疫苗` 搜索就把它带出来了 —— 这条也说明
 *    **具体 id 的"未发布"状态会变**，前端只认 `A000001 + 知识条目不存在` 这个组合，不认 id）。
 * 11) ⚠️ **读详情会 `viewCount` +1，且不是幂等读**（源码 `incrementViewCount`：DB 值 + Redis 窗口增量）。
 *    列表接口返回的是**库里的值**（最多滞后一个写回窗口）→ "列表浏览数" 与 "详情浏览数" 可能差 1，属正常。
 * 12) **`/fragment`（不带 `/page`）一次返回全量 409 条**，是给 RAG 索引重建用的内部语义
 *    （`KnowledgeServiceFeign` 也依赖它）→ **前端列表一律用 `/fragment/page`**，本文件**不封装**它。
 * 13) **正文是"碎片"，不是文章**：实测 `content` 平均 **12.4 字**、最长 43 字；`remark` 是补充说明
 *    （343/434 有值，最长 40 字左右）。所以详情页**不能假设有一段长正文**，标题 + content + remark
 *    就是一条完整知识（详见 `knowledgeView`）。
 *
 * ---------------------------------------------------------------------------
 * 本文件里哪些是**纯函数**（无 IO、可在离线断言里直接喂真实 fixture）
 * ---------------------------------------------------------------------------
 *   clampPageNum / clampPageSize / pageStateOf     分页钳制与状态机推导
 *   parseTags                                      标签解析（**分隔符是 `·` 不是逗号**，见下）
 *   excerpt                                        摘要截断
 *   viewCountText                                  浏览量文案
 *   contentTypeMeta / speciesLabel / stageLabel / sourceLabel   展示映射
 *   dedupeCategories                               12 条 → 4 个分类
 *   categoryNameMap                                 code → 中文名
 *   fragmentCard / knowledgeView                    列表卡片 / 详情视图摊平
 *   knowledgeErrorMessage / isMissingFragment       错误码人话
 */
import { api } from '../utils/request.js'

const MODULE = 'knowledge'

/** 列表默认每页条数（后端同值：`KnowledgeConstant.DEFAULT_PAGE_SIZE`） */
export const PAGE_SIZE = 10
/** 每页上限：实测 `pageSize=100/999/1000` 一律被钳成 50（`MAX_PAGE_SIZE`） */
export const MAX_PAGE_SIZE = 50
/** 搜索硬上限：源码 `.last("LIMIT 30")`，**不受任何参数影响** */
export const SEARCH_LIMIT = 30
/** 后端默认页码（`pageNum` < 1 时回落） */
export const DEFAULT_PAGE_NUM = 1

/** 物种：与 `species` 的实测取值一致（0 通用 / 1 猫 / 2 狗） */
export const SPECIES = [
  { value: undefined, label: '全部物种' },
  { value: 1, label: '猫' },
  { value: 2, label: '狗' }
]

/* ------------------------------------------------------------------
 * 5 个只读接口
 *
 * 全部 `anonymous: true`（见文件头）：知识库是公开读，不带身份，
 * 所以"未登录能读"这条**不依赖 token 是否有效**。
 * ------------------------------------------------------------------ */

/**
 * 知识分类列表
 * @param {number} [species] 1 猫 / 2 狗 / 0 通用；**不传返回全部 12 条**
 * @returns {Promise<Array<{id:number,code:string,name:string,species:number,sort:number}>>}
 *
 * ⚠️ 不传 species 是刻意的：拿到 12 条后由 `dedupeCategories` 折成 4 个分类，
 * 这样页面不必为了"知道有哪几个分类"而先选物种。传了只会拿到 4 条（同一批 code）。
 */
export function listCategories (species) {
  return api.get(MODULE, '/knowledge/category', {
    query: { species: species },
    anonymous: true
  })
}

/**
 * 已发布知识碎片**分页**列表（列表页主接口）
 * @param {object} p `{ species, categoryCode, pageNum, pageSize }`
 *   - `species` 1 猫 / 2 狗；不传 = 全部物种（**实测全量 `total=409` = 猫 206 + 狗 203**）
 *   - `categoryCode` diet / behavior / care / health；⚠️ 非法值**静默回空**，不报错
 *   - `pageNum` < 1 会被钳成 1；越界页不报错、`records` 为空
 *   - `pageSize` < 1 回落 10、> 50 钳成 50
 * @returns {Promise<{pageNum:number,pageSize:number,total:number,pages:number,records:object[]}>}
 */
export function listFragments (p) {
  const o = p || {}
  return api.get(MODULE, '/knowledge/fragment/page', {
    query: {
      species: o.species,
      categoryCode: o.categoryCode || undefined,
      pageNum: clampPageNum(o.pageNum),
      pageSize: clampPageSize(o.pageSize)
    },
    anonymous: true
  })
}

/**
 * 知识碎片详情
 * @param {number|string} id
 * @returns {Promise<object>} `KnowledgeFragmentRespDTO`
 * ⚠️ 不存在 / 未发布 → `A000001 知识条目不存在`（HTTP 200）；非数字 id → `B000001`
 * ⚠️ 每调一次后端 `viewCount` +1（不是幂等读）
 */
export function getFragment (id) {
  return api.get(MODULE, '/knowledge/fragment/' + id, { anonymous: true })
}

/**
 * 关键词搜索（**裸数组、硬上限 30 条、不分页**）
 * @param {object} p `{ keyword, species, categoryCode }`
 *   - `keyword` **必填**（完全不传 → `B000001`）；⚠️ 空串 / 纯空格不报错但等价于"无关键词"
 *     → **调用方负责拦截空关键词**（页面走分页接口），本文件不在这里猜用户意图
 *   - 多词按空白切分，最多 8 个；⚠️ **词内 OR、词间 AND**（取交集）→ **加词会越搜越少**，
 *     界面文案不能承诺"多词搜得更全"（实测见文件头第 8 条）
 *   - `species` / `categoryCode` 生效，口径同 `listFragments`
 * @returns {Promise<object[]>} 最多 30 条，按 `id DESC`
 */
export function searchFragments (p) {
  const o = p || {}
  return api.get(MODULE, '/knowledge/fragment/search', {
    query: {
      keyword: o.keyword,
      species: o.species,
      categoryCode: o.categoryCode || undefined
    },
    anonymous: true
  })
}

/**
 * 全量已发布碎片（**409 条，一次性返回**）
 *
 * ⚠️ 这是给 RAG 索引重建 / 内部消费方的语义（`KnowledgeServiceFeign` 依赖它），
 * **前端列表禁止用它**（一次拉 409 条正文）。本文件只把它暴露出来给断言脚本与将来的
 * "离线缓存"用，页面不调。
 */
export function listAllFragments (species, categoryCode) {
  return api.get(MODULE, '/knowledge/fragment', {
    query: { species: species, categoryCode: categoryCode || undefined },
    anonymous: true
  })
}

/* ------------------------------------------------------------------
 * 纯函数：分页钳制 / 状态机
 *
 * 钳制规则与后端 `KnowledgeServiceImpl.normalizePageNum / normalizePageSize`
 * 逐条对齐，**前端先钳一遍**有两个好处：
 *   ① 请求 URL 里不会出现 `pageNum=abc`（那会拿到 `B000001 系统执行出错`，是纯脏错误）
 *   ② 断言脚本可以在没有后端的情况下验"钳制规则"本身
 * ------------------------------------------------------------------ */

/** 页码钳制：非数字 / < 1 → 1（后端同样回落，这里提前对齐） */
export function clampPageNum (n) {
  const v = Number(n)
  if (!isFinite(v) || v < 1) return DEFAULT_PAGE_NUM
  return Math.floor(v)
}

/** 每页条数钳制：非数字 / < 1 → 10；> 50 → 50（实测上限，与后端一致） */
export function clampPageSize (n) {
  const v = Number(n)
  if (!isFinite(v) || v < 1) return PAGE_SIZE
  return Math.min(Math.floor(v), MAX_PAGE_SIZE)
}

/**
 * 从一页响应推导分页状态机（**必须是纯函数**：触底加载的正确性全靠它）
 *
 * ⚠️ 两个"看起来更自然但实测会错"的写法，这里刻意避开：
 *   ① **`hasMore` 不能用 `records.length === pageSize`** —— 最后一页正好装满时（409 = 40×10 + 9，
 *      猫 206 = 20×10 + 6，但 `categoryCode=diet` 的 91 条里就有整页情况）会多请求一次空页。
 *      以响应里的 **`pages`** 为准。
 *   ② **不能拿"响应里的 `pageNum`"去推进下一页** —— 越界时后端会**原样回显**你传的越界页码
 *      （实测 `pageNum=9999` → 响应 `pageNum:9999`），照它推会永远停在越界页。
 *      推进量要用**调用方自己发出去的那个页码**（`sentPageNum`）。
 *
 * @param {object} page 后端响应信封
 * @param {number} sentPageNum 本次请求**实际发出去**的页码（不是响应回显的那个）
 * @returns {{records:object[], total:number, pages:number, nextPageNum:number, hasMore:boolean}}
 */
export function pageStateOf (page, sentPageNum) {
  const p = page || {}
  const records = Array.isArray(p.records) ? p.records : []
  const total = Number(p.total) || 0
  const pages = Number(p.pages) || 0
  const sent = clampPageNum(sentPageNum)
  return {
    records: records,
    total: total,
    pages: pages,
    nextPageNum: sent + 1,
    // pages=0（一条都没有）时 hasMore 必须是 false，否则空态页面会一直"还能加载"
    hasMore: pages > 0 && sent < pages
  }
}

/* ------------------------------------------------------------------
 * 纯函数：标签 / 摘要 / 浏览量
 * ------------------------------------------------------------------ */

/**
 * 标签解析
 *
 * ⚠️ **分隔符是 `·`（U+00B7 中点），不是逗号** —— 这是本模块最容易踩的一个坑：
 * 建表语句的列注释写的是"标签，逗号分隔"，但库里的真实数据是
 * `"安全 · 推荐"` / `"谨慎 · 少量"` / `"饮水 · 重点"` / `"⚠️ 绝对禁忌"` / `"喂养原则"`，
 * **一个逗号都没有**。照注释写 `split(',')` 会把整串当成一个标签。
 * 这里同时兼容 `,` / `，` / `|` / `/`，是因为以后人工录入可能真的用逗号，
 * 但**主分隔符是 `·`**。
 *
 * @param {string} tags 原始串（实测 409 条里只有 51 条非空）
 * @returns {string[]} 去空白、去空项、去重后的标签数组（空输入 → `[]`，绝不返回 null）
 */
export function parseTags (tags) {
  if (tags === null || tags === undefined) return []
  const raw = String(tags)
  if (!raw.trim()) return []
  const out = []
  const parts = raw.split(/[·,，|/]/)
  for (let i = 0; i < parts.length; i++) {
    const t = parts[i].trim()
    if (!t) continue
    if (out.indexOf(t) >= 0) continue
    out.push(t)
  }
  return out
}

/**
 * 摘要截断
 *
 * ⚠️ 知识库的 `content` 是**碎片**（实测平均 12.4 字、最长 43 字），大多数情况下**根本不需要截断**；
 * 这个函数真正防的是"以后灌进来长正文"时卡片被撑爆。按**字符**截断（CJK 安全），
 * 截断处加省略号 `…`（设计系统禁用破折号，省略号不在禁用范围内）。
 *
 * @param {string} text
 * @param {number} [max] 最多保留多少字符（默认 48）
 * @returns {string} 空输入 → `''`
 */
export function excerpt (text, max) {
  const want = max === undefined || max === null ? 48 : Number(max)
  const limit = !isFinite(want) || want < 1 ? 48 : Math.floor(want)
  if (text === null || text === undefined) return ''
  const s = String(text).replace(/\s+/g, ' ').trim()
  if (s.length <= limit) return s
  return s.slice(0, limit) + '…'
}

/**
 * 浏览量文案
 *
 * 实测 409 条里 `viewCount` 绝大多数是 **0**（只有被读过的那几条 > 0）→
 * "浏览 0 次"这种文案满屏都是，读起来像坏掉了。0 单独给一句更自然的话。
 * 数字在样式里配 `tabular-nums`（设计系统要求，滚动/刷新时数字不跳动）。
 *
 * @param {number} n
 * @returns {string}
 */
export function viewCountText (n) {
  const v = Number(n) || 0
  if (v <= 0) return '还没人看过'
  if (v < 10000) return '浏览 ' + v + ' 次'
  return '浏览 ' + (v / 10000).toFixed(1) + ' 万次'
}

/* ------------------------------------------------------------------
 * 纯函数：展示映射
 *
 * 映射表是把后端枚举**翻译成人话**，不是把枚举抄一遍：
 *   contentType: care_schedule / guide / warning / medical（实测 4 种全覆盖）
 *   source:      MANUAL(人工) / EXCEL(手册导入) / LLM(模型生成)——已发布数据实测全是 EXCEL
 *   stage:       演练阶段（幼年/成年/老年），**实测 409 条全是 null** → 必须能优雅处理空值
 *   species:     0 通用 / 1 猫 / 2 狗
 * ------------------------------------------------------------------ */

/** 内容类型 → `{label, tone}`；`tone` 给样式用（default / care / warn / danger） */
const CONTENT_TYPES = {
  guide: { label: '养护指南', tone: 'default' },
  care_schedule: { label: '照护计划', tone: 'care' },
  warning: { label: '安全警示', tone: 'warn' },
  medical: { label: '医疗自查', tone: 'danger' }
}

/**
 * 内容类型映射
 * @param {string} ct
 * @returns {{key:string,label:string,tone:string}}
 *   未知值**不吞**：`label` 回落成原始串（便于发现后端新增枚举），`tone` 回落 `default`。
 *   空值 → `{key:'', label:'养护指南', tone:'default'}`（后端 `content_type` NOT NULL 默认 guide，
 *   真拿到空值说明数据有问题，这里按最中性的那一类显示而不是显示空白）。
 */
export function contentTypeMeta (ct) {
  const key = ct ? String(ct) : ''
  const hit = CONTENT_TYPES[key]
  if (hit) return { key: key, label: hit.label, tone: hit.tone }
  if (!key) return { key: '', label: CONTENT_TYPES.guide.label, tone: CONTENT_TYPES.guide.tone }
  return { key: key, label: key, tone: 'default' }
}

/** 物种映射：1 猫 / 2 狗 / 0 通用；未知与空值都回落「通用」（**不编物种**） */
export function speciesLabel (s) {
  if (s === 1) return '猫'
  if (s === 2) return '狗'
  return '通用'
}

/** 演练阶段映射：实测全是 `null` → 空值回落「通用」（不显示空白标签） */
const STAGES = { 幼年: '幼年', 成年: '成年', 老年: '老年' }

export function stageLabel (stage) {
  if (stage === null || stage === undefined || stage === '') return '通用'
  const s = String(stage).trim()
  if (!s) return '通用'
  return STAGES[s] || s
}

/** 来源映射：EXCEL 手册导入 / LLM 模型生成 / MANUAL 人工整理；未知值原样显示 */
const SOURCES = { EXCEL: '手册导入', LLM: '模型生成', MANUAL: '人工整理' }

export function sourceLabel (source) {
  if (source === null || source === undefined || source === '') return '来源未知'
  const s = String(source)
  return SOURCES[s] || s
}

/* ------------------------------------------------------------------
 * 纯函数：分类去重（12 条 → 4 个分类）
 * ------------------------------------------------------------------ */

/**
 * 把 `/category` 的 12 条折成**真正不同的 4 个分类**
 *
 * 实测：12 条 = 4 个 `code` × 3 个 `species`，`code` 与 `name` 完全相同，只有 `id` / `species` 不同。
 * 按 `code` 去重、按 `sort` 升序（后端已排序，这里再保一遍序，避免上游改排序）。
 * **保留 `speciesList`**：将来若某个分类只在猫或狗下存在，页面能据此决定要不要显示，
 * 而现在 4 个分类的 `speciesList` 都是 `[0,1,2]`。
 *
 * @param {object[]} rows `/category` 原始数组
 * @returns {Array<{code:string,name:string,sort:number,speciesList:number[]}>}
 */
export function dedupeCategories (rows) {
  const list = Array.isArray(rows) ? rows : []
  const byCode = {}
  const order = []
  for (let i = 0; i < list.length; i++) {
    const r = list[i] || {}
    const code = r.code ? String(r.code) : ''
    if (!code) continue
    if (!byCode[code]) {
      byCode[code] = {
        code: code,
        name: r.name ? String(r.name) : code,
        sort: Number(r.sort) || 0,
        speciesList: []
      }
      order.push(code)
    }
    const sp = Number(r.species)
    if (isFinite(sp) && byCode[code].speciesList.indexOf(sp) < 0) {
      byCode[code].speciesList.push(sp)
    }
  }
  const out = order.map(function (c) { return byCode[c] })
  out.sort(function (a, b) {
    if (a.sort !== b.sort) return a.sort - b.sort
    return a.code < b.code ? -1 : a.code > b.code ? 1 : 0
  })
  return out
}

/**
 * `code` → 中文名（模板里**不能**调函数，所以页面用它先算好一张表）
 * @param {object[]} rows `/category` 原始数组（或 `dedupeCategories` 的结果，字段兼容）
 * @returns {Object<string,string>}
 */
export function categoryNameMap (rows) {
  const list = Array.isArray(rows) ? rows : []
  const map = {}
  for (let i = 0; i < list.length; i++) {
    const r = list[i] || {}
    if (r.code) map[String(r.code)] = r.name ? String(r.name) : String(r.code)
  }
  return map
}

/* ------------------------------------------------------------------
 * 纯函数：列表卡片 / 详情视图
 *
 * 页面模板只消费这些摊平后的字段（Options API 模板不能碰 store/utils，
 * 也不能在模板里调函数 —— `check-phone.js` 第 4、5 类检查盯着这两条）。
 * ⚠️ 知识碎片**没有封面图字段**（记录只有 12 个字段，无 coverUrl / imageUrl）
 *   → 卡片是**纯文字**的，不要为它去找图，也不要留一个占位的空图片框。
 * ------------------------------------------------------------------ */

/**
 * 列表卡片视图
 * @param {object} f 原始 fragment DTO
 * @param {Object<string,string>} [nameMap] `code` → 分类中文名
 * @returns {object} 模板直接可用的字段
 */
export function fragmentCard (f, nameMap) {
  const r = f || {}
  const ct = contentTypeMeta(r.contentType)
  const tags = parseTags(r.tags)
  const code = r.categoryCode ? String(r.categoryCode) : ''
  const map = nameMap || {}
  return {
    id: r.id,
    title: r.title ? String(r.title) : '未命名知识条目',
    // content 是这条知识的"答案"（实测很短）→ 卡片直接展示，不截成一句话
    summary: excerpt(r.content, 60),
    hasSummary: Boolean(r.content && String(r.content).trim()),
    remark: r.remark ? String(r.remark) : '',
    hasRemark: Boolean(r.remark && String(r.remark).trim()),
    categoryCode: code,
    categoryName: map[code] || code || '未分类',
    speciesText: speciesLabel(r.species),
    /**
     * ⚠️ **实测已发布记录里 `stage` 与 `breedId` 全是 null**（演练阶段字段从没灌过数据）。
     * 所以额外给出 `hasStage`：界面**只在真有阶段时才渲染"适用：…"这一项**。
     * 若照"字段存在就渲染"的写法，每张卡片/每篇详情都会多出一个恒为「通用」的标签 ——
     * 那不是信息，是噪音，还会让人误以为"这条知识只适用于某个阶段"。
     * `stageLabel` 本身保留：将来真灌了阶段数据，界面不用改就能显示。
     */
    stageText: stageLabel(r.stage),
    hasStage: r.stage !== null && r.stage !== undefined && String(r.stage).trim() !== '',
    contentTypeLabel: ct.label,
    contentTypeTone: ct.tone,
    /** 安全警示 / 医疗自查要在卡片上就有视觉提示，不能点进去才知道 */
    needsCare: ct.tone === 'warn' || ct.tone === 'danger',
    tags: tags,
    hasTags: tags.length > 0,
    viewText: viewCountText(r.viewCount)
  }
}

/**
 * 详情视图
 *
 * ⚠️ 详情页的"正文"实际上是三段：`title`（主张）+ `content`（一句话结论）+ `remark`（补充说明）。
 * 实测 content 平均 12.4 字、remark 最长 40 字左右 —— **不要假设有一段长正文**，
 * 也不要为了"看起来像文章"把这三段拼成一大段假正文（那会篡改知识库的内容边界）。
 * 三段各自独立展示，缺哪段就少哪段。
 *
 * @param {object} f 原始 fragment DTO（详情响应）
 * @param {Object<string,string>} [nameMap]
 */
export function knowledgeView (f, nameMap) {
  const r = f || {}
  const ct = contentTypeMeta(r.contentType)
  const tags = parseTags(r.tags)
  const code = r.categoryCode ? String(r.categoryCode) : ''
  const map = nameMap || {}
  return {
    id: r.id,
    title: r.title ? String(r.title) : '未命名知识条目',
    content: r.content ? String(r.content) : '',
    hasContent: Boolean(r.content && String(r.content).trim()),
    remark: r.remark ? String(r.remark) : '',
    hasRemark: Boolean(r.remark && String(r.remark).trim()),
    categoryCode: code,
    categoryName: map[code] || code || '未分类',
    speciesRaw: r.species,
    speciesText: speciesLabel(r.species),
    /**
     * ⚠️ **实测已发布记录里 `stage` 与 `breedId` 全是 null**（演练阶段字段从没灌过数据）。
     * 所以额外给出 `hasStage`：界面**只在真有阶段时才渲染"适用：…"这一项**。
     * 若照"字段存在就渲染"的写法，每张卡片/每篇详情都会多出一个恒为「通用」的标签 ——
     * 那不是信息，是噪音，还会让人误以为"这条知识只适用于某个阶段"。
     * `stageLabel` 本身保留：将来真灌了阶段数据，界面不用改就能显示。
     */
    stageText: stageLabel(r.stage),
    hasStage: r.stage !== null && r.stage !== undefined && String(r.stage).trim() !== '',
    contentTypeKey: ct.key,
    contentTypeLabel: ct.label,
    contentTypeTone: ct.tone,
    /** 医疗 / 警示类内容必须带免责声明（设计系统 §7 硬要求） */
    needsDisclaimer: ct.tone === 'danger' || ct.tone === 'warn',
    tags: tags,
    hasTags: tags.length > 0,
    sourceText: sourceLabel(r.source),
    viewText: viewCountText(r.viewCount),
    viewCount: Number(r.viewCount) || 0
  }
}

/* ------------------------------------------------------------------
 * 纯函数：错误码人话
 * ------------------------------------------------------------------ */

/**
 * 是不是"知识条目不存在"
 *
 * ⚠️ 这条错误用的是 **`A000001`**（`ClientException(String)` 的通用码），而 `A000001`
 * 在别的模块还表示"参数错误"与"未登录" → **只能靠 message 区分**，不能整体当"不存在"。
 * 实测消息就是 `知识条目不存在`（未发布与真不存在**同一个消息**，刻意不区分）。
 */
export function isMissingFragment (e) {
  if (!e) return false
  if (String(e.code) !== 'A000001') return false
  return /知识条目不存在|不存在/.test(String(e.message || ''))
}

/**
 * 把异常翻译成用户能看懂的一句话
 *
 * 顺序刻意是"先具体、后笼统"：`isMissingFragment` 必须在 `A000001` 的通用分支之前判，
 * 否则"这条知识下架了"会被说成"参数不对"。
 * ⚠️ `A000001` 也用于"未登录"（pet / healthy），但**知识库是匿名可读**、
 * 请求层根本不会带身份（`anonymous: true`）→ 这里不需要处理掉线跳登录。
 */
export function knowledgeErrorMessage (e) {
  if (!e) return '读取失败，请稍后重试'
  const code = String(e.code === undefined || e.code === null ? '' : e.code)
  const msg = String(e.message || '')
  if (isMissingFragment(e)) return '这条知识不存在，或者已经下架了'
  if (code === 'B000001') return '请求参数不对，接口没能理解（这通常是页面 bug，不是网络问题）'
  if (code === 'NETWORK') return msg || '连不上后端，检查网络后重试'
  if (code === 'UNAUTHORIZED' || code === '-1') return '登录状态失效了，不过知识库不需要登录，刷新就能继续读'
  if (msg) return msg
  return '读取失败，请稍后重试'
}

/** 是不是网络/服务不可用（决定空态文案该说"没读到"还是"确实没有"） */
export function isNetworkFailure (e) {
  if (!e) return false
  return String(e.code) === 'NETWORK'
}