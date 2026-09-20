/**
 * 长文字的"自动收缩 / 展开"判定（Web，纯逻辑、零依赖、可单独 import 真跑）
 *
 * 为什么需要它：CSS 的 `-webkit-line-clamp` 只能**把文字裁掉**，它裁完不会告诉任何人
 * "我裁了"。于是模板里要么永远给一个「展开」按钮（文字本来就短时按钮是个死按钮），
 * 要么永远不给（长文字被静默截断，用户根本不知道后面还有话）—— 两条都是错的。
 * 这里用**字符数**在数据层做一次判定，模板就只按 `foldNeeded()` 的结果决定要不要给按钮。
 *
 * ⚠️ 阈值是按**卡片最窄那一档的真实几何**推出来的，不是拍脑袋（推导见下），
 * 改卡片宽度 / 字号就必须重新推一遍。
 */

/** 折叠时保留几行（与 `.card__title` 的 `-webkit-line-clamp` 必须同值） */
export const FOLD_LINE_LIMIT = 2

/**
 * 超过多少个字符就认为"两行放不下"。
 *
 * 推导（都是本仓库里的真值，不是估计）：
 * - 卡片列宽：`.feed` 是 `grid-template-columns: repeat(auto-fill, minmax(236px, 1fr))`
 *   → **最窄一列 236px**
 * - 卡片内边距：`.card__body { padding: var(--mw-space-4) }` = 16px × 2 → 文字列最窄 236 − 32 = **204px**
 * - 正文字号：`--mw-font-body` = **16px**；中文全角字宽 = 1em = 16px → 204 / 16 = 12.75 → 一行 **12 个全角字**
 * - 两行 = 24 个全角字；正文里总混着半角的数字/字母/标点（比全角窄），
 *   所以按 13 字/行估 → **26**
 *
 * 为什么取 26（偏小）而不是按宽栏的 32 取：
 * 两种错的代价不对称 —— 阈值偏小 → 宽栏下偶尔给一个"点了还是两行"的无害按钮；
 * 阈值偏大 → **窄栏下长标题被静默裁掉且没有「展开」**，那正是用户报的原始问题。
 * 所以宁可偏小。桌面三列布局（约 292px 列宽 ≈ 16 字/行）下真实长标题（31 字以上）才可能碰到。
 *
 * ⚠️ 现有真实数据里**一条都不折叠**：5 条种子帖的标题长度是 14 / 17 / 17 / 19 / 19，
 * 全部 ≤ 26 → 这个功能今天在真实数据上是"备而不发"的（门禁里也照实钉住这一点）。
 */
export const FOLD_CHAR_LIMIT = 26

/**
 * 要不要给「展开」。
 *
 * - 空文字 → `false`（没有可展开的东西）
 * - 硬换行超过 `FOLD_LINE_LIMIT` → `true`（换行是必然断行，字符数少也会超两行）
 * - 否则看字符数
 */
export function foldNeeded(text: string | null | undefined): boolean {
  const raw = String(text ?? '')
  if (!raw) return false
  if (raw.split('\n').length > FOLD_LINE_LIMIT) return true
  return raw.trim().length > FOLD_CHAR_LIMIT
}