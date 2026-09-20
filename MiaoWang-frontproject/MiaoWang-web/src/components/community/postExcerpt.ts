/**
 * 信息流卡片的「内容摘要」判定（Web）
 *
 * 背景：用户要求卡片纵向排成「用户信息 → 标题 → **内容摘要** → **内容展开** → 图片 → 评论条数」。
 *
 * ⚠️ **后端已定稿（2026-09-21，实测转述）**，本文件按定稿口径实现：
 * - `contentExcerpt: string` —— 正文**纯前缀**，按**码点**截 200，**不加省略号、不 trim、不转义**；
 *   **无正文 = `""`（不是 null）**；
 * - `contentTruncated: boolean` —— ⚠️ **只有它为 `true` 时才显示「内容展开」**（这一条同时覆盖
 *   空正文与短文：短文没有可展开的东西，后端给 `false`，前端就**不该**留一个点了没用的入口）；
 * - 列表**故意没有 `content` 字段** ⇒ 展开**必须**读详情 `GET /community/post/{id}?countView=false`
 *   （详情的 `content` 才是全文）。
 *
 * 即便定稿了，判定仍然**只走这一个模块**（防御式，且为了"定稿前后同一处改"）：
 *
 * 1) **不把字段名猜死**：按一份**优先名清单**逐个找（见 `EXCERPT_FIELDS`），
 *    哪个先出现且是**非空字符串**就用哪个；
 * 2) **一个都没有 → 返回 `text: ''`**，调用方据此**整块不渲染摘要区** ——
 *    **绝不拿标题 / 正文 / 别的东西冒充摘要**（那是造假区块）；
 * 3) **定稿后只改一处**：把定稿的字段名填进 `EXCERPT_FIELDS` 的**第一位**即可，
 *    本文件之外没有任何地方硬编码过摘字段名。
 *
 * ⚠️ **不对摘要文本做任何加工**：定稿口径是"纯前缀、不 trim"，
 * 所以这里**不折叠空白、不去首尾空格**（`text` 原样返回）。
 * 只把"**这一段是不是空的**"用 `trim()` 判一下（判空不影响显示内容）。
 *
 * 为什么单独一个"零 import、无副作用"的纯模块（与 `postMediaLayout.ts` / `textFold.ts` 同族）：
 * 摘要区最要命的两种错都是**静态看不出来的**——"字段名拼错"会让摘要**静默消失**（页面照常绿），
 * "拿标题兜底"会让摘要**看起来有、其实是假的**。两者都必须能被门禁**真跑**钉住，
 * 而不是靠人眼扫一眼模板。
 */

/** 后端定稿的截断长度（**码点**，不是字节）；前端只做说明，不做截断 */
export const EXCERPT_CODE_POINT_LIMIT = 200

/** 摘字段的**优先名清单**（定稿后把定稿名放第一位；顺序就是优先级） */
export const EXCERPT_FIELDS = [
  'contentExcerpt',
  'excerpt',
  'contentSummary',
  'summary',
  'contentBrief',
  'brief',
] as const

/**
 * 「摘要是不是被截断」的候选字段名。
 * 口径未定稿，所以这里也按优先名找；**读不到时按"是截断的"处理**（见 `postExcerptOf` 的说明）。
 */
export const EXCERPT_TRUNCATED_FIELDS = [
  'contentTruncated',
  'excerptTruncated',
  'truncated',
  'contentCut',
] as const

/** 摘字段的来源对象（列表 DTO 满足；用索引签名是为了"字段名未定稿"这件事在类型上也成立） */
export interface ExcerptSource {
  [key: string]: unknown
}

export interface PostExcerpt {
  /** 摘要正文；**空串 = 这一块不该渲染**（不是渲染一个空框）。**原样返回，不 trim、不折叠空白** */
  text: string
  /**
   * 后端有没有说"这段是截断的"。
   * ⚠️ 定稿后 `contentTruncated` 恒在，所以"读不到"只是**后端还没上线该字段**时的兜底：
   * 那时按 `true` 处理（保守：宁可多给一个展开入口，也别让用户看不到全文）；
   * 字段在时**一律以字段为准**（`false` 就**不**给展开按钮）。
   */
  truncated: boolean
  /** 实际命中的字段名；一个都没命中是 `null`（用于门禁与排查，页面不显示） */
  field: string | null
  /** `truncated` 是从哪个字段读到的；没读到是 `null`（说明用的是保守默认值） */
  truncatedField: string | null
}

/**
 * 取出摘要文本。
 *
 * ⚠️ **定稿口径是"纯前缀、不 trim、不转义"**，所以这里**原样返回字符串**，
 * 绝不 `replace(/\s+/g,' ')` 或 `trim()` —— 那会改掉后端给的内容（比如正文里的换行/缩进）。
 * 判"空"是为了决定**渲不渲染这一块**，用的是 `trim()`，但**返回值仍是原文**。
 */
function textOf(raw: unknown): string {
  if (typeof raw !== 'string') return ''
  return raw.trim() === '' ? '' : raw
}

/**
 * 从一条帖子（列表 DTO）里取摘要。
 *
 * 返回 `text: ''` 的两种情形**都要让调用方不渲染摘要区**：
 * - 所有候选字段都缺席（后端还没上线这个字段）
 * - 字段在但内容是空白字符串
 */
export function postExcerptOf(post: ExcerptSource | null | undefined): PostExcerpt {
  if (!post) return { text: '', truncated: true, field: null, truncatedField: null }
  let text = ''
  let field: string | null = null
  for (const name of EXCERPT_FIELDS) {
    const t = textOf(post[name])
    if (t) {
      text = t
      field = name
      break
    }
  }
  let truncated = true
  let truncatedField: string | null = null
  for (const name of EXCERPT_TRUNCATED_FIELDS) {
    const raw = post[name]
    if (typeof raw === 'boolean') {
      truncated = raw
      truncatedField = name
      break
    }
  }
  return { text, truncated, field, truncatedField }
}

/**
 * 摘要区该不该渲染的**唯一判据**。
 *
 * 抽成函数是为了让"有没有摘要"这件事只有一个入口：
 * 页面里任何 `v-if` 都必须调它，**不许**写成 `p.contentExcerpt`（那样字段名一变就静默消失，
 * 而且换别的字段兜底时也没人拦得住）。
 */
export function hasExcerpt(post: ExcerptSource | null | undefined): boolean {
  return postExcerptOf(post).text !== ''
}

/**
 * 「内容展开」该不该出现的**唯一判据**（= 有摘要 **且** 后端说这段被截断了）。
 *
 * ⚠️ 定稿口径：**只有 `contentTruncated === true` 才显示展开**
 * （短文、空正文后端给 `false`，前端就不该留一个点了没反应的按钮）。
 * 字段缺席时才退回"有摘要就给入口"的保守行为（后端还没上线该字段的过渡期）。
 */
export function isExcerptTruncated(post: ExcerptSource | null | undefined): boolean {
  const e = postExcerptOf(post)
  if (e.text === '') return false
  return e.truncated
}