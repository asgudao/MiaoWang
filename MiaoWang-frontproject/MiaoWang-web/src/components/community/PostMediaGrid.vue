<script setup lang="ts">
/**
 * 帖子图片区（Web，列表卡片与详情页共用）
 *
 * 排列规则（QQ空间 / 朋友圈 / 微博 同款，逐档实现）：
 * - **1 张** → 单张大图：保留原始长宽比，只限最大宽/高（横图不拉成长条、竖图不裁成方块）
 * - **2 张** → 一行两张；**3 张** → 一行三张；**4 张** → 2×2；**5~9 张** → 3×3
 * - **超过 9 张** → 只排 9 张，第 9 张右下角盖「+N」
 * - 网格格子正方形裁切（`object-fit: cover`）、间距一致；点开看大图（灯箱），**从点击的那张开始**
 * - **0 张** → **整块不渲染**（根节点都不输出）：不留空白、不留高度、不留灰框
 * - 图片**加载失败**（`@error`）或**尺寸退化**（小到不可能是内容图）→ 收掉那一张；
 *   全被收掉时整块跟着消失（不会剩下空框）
 *
 * 分工：**档位判定全在 `postMediaLayout.ts`**（可单独 import 真跑的纯函数），
 * 本组件只负责"把判定结果画出来"与交互（灯箱）。
 *
 * 两个刻意的取舍，别顺手改：
 * 1) **列表卡里的单图不外包按钮**（`singleAsLink`）：单图没有"相册"可翻，点它进帖子更合理，
 *    所以由外层 `RouterLink` 负责跳转（这也是社区信息流原有的入口，不该被这次改动砍掉）。
 *    多图档位才走灯箱 —— 那时"点开看大图"才是用户要的。
 * 2) 灯箱**不锁 body 滚动**：多一个全局副作用就多一处能和别的页面串味的地方，
 *    而遮罩是 `position: fixed` 的全屏层，视觉上已经盖住了。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { PhCaretLeft, PhCaretRight, PhX } from '@phosphor-icons/vue'
import {
  mediaGridLayout,
  mediaKey,
  sortMedia,
  type MediaItem,
} from './postMediaLayout'

const props = withDefaults(
  defineProps<{
    /** 接口给的图片数组（列表可能是单张 `coverUrl` 折成的单项，见 `postMediaOf`） */
    images?: MediaItem[] | null
    /** `detail` = 详情页（单图上限更高）；`card` = 列表卡片 */
    variant?: 'detail' | 'card'
    /** `true` = 单图不包按钮，由外层链接负责点击（列表卡片用） */
    singleAsLink?: boolean
    /** 无障碍标签前缀，读屏用 */
    label?: string
  }>(),
  { images: null, variant: 'detail', singleAsLink: false, label: '帖子配图' },
)

interface Cell {
  key: string
  url: string
  index: number
}

const sorted = computed(() => sortMedia(props.images))
const cells = computed<Cell[]>(() =>
  sorted.value.map((m, i) => ({ key: mediaKey(m, i), url: String(m.url ?? ''), index: i })),
)

/**
 * 被收掉的图（key）：**只剩"加载真失败"这一种**（`@error`）。
 * **key 不能用下标**（收一张后下标会整体前移）。
 * ⚠️ 2026-09-21 起**尺寸退化不再收图**（用户"图片要全部显示"）。
 */
const dropped = ref<string[]>([])
const lightbox = ref(-1)

/** 换数据就清空收图记录与灯箱，否则重新读一次帖子后原来那张图会一直不显示 */
watch(
  () => props.images,
  () => {
    dropped.value = []
    lightbox.value = -1
  },
)

const visible = computed(() => cells.value.filter((c) => !dropped.value.includes(c.key)))
/** `null` = **整块不渲染**（0 张图，或全被收掉） */
const layout = computed(() => mediaGridLayout(visible.value.length))
const shownCells = computed(() => visible.value.slice(0, layout.value?.shown ?? 0))

const single = computed<Cell | null>(() =>
  layout.value?.mode === 'single' ? (visible.value[0] ?? null) : null,
)
const singleUrl = computed(() => single.value?.url ?? '')

function drop(key: string) {
  if (dropped.value.includes(key)) return
  dropped.value = [...dropped.value, key]
  // 正在看的那张被收掉了就把灯箱关掉，免得停在一张已经不存在的图上
  if (lightbox.value >= visible.value.length) lightbox.value = -1
}

function onImgError(key: string) {
  drop(key)
}

/* ⚠️ **2026-09-21 用户最终裁决（分档）**：
 * 「一张图就**原比例不裁剪**，超过一张就**裁剪**排成九宫格」。
 * 所以这里**没有** `onImgLoad`、**没有**按尺寸收图（`isDegenerate` 已不再被调用）：
 * - **单图**：`<img>` 靠固有比例 + `max-width: 100%` 完整显示（不裁剪、不设高度上限）；
 * - **多图**：缩略格子统一 `object-fit: cover` **裁剪**成方格（这是"排整齐"的前提）。
 *
 * ⚠️ **加载真失败**（`@error` → `drop()`）仍然收掉那张图 —— 那是"这张图拿不到"，
 * 不是"这张图太小"；混为一谈就会把坏图静默当作"没图"。
 * ⚠️ 极小图（库里那张 8×8）**照字面显示**（在多图里就是一个裁剪后的模糊方块），
 * 不隐藏 —— 这是用户明确选过的口径。 */

/* ---------- 灯箱 ---------- */

const current = computed<Cell | null>(() =>
  lightbox.value >= 0 ? (visible.value[lightbox.value] ?? null) : null,
)
const canPrev = computed(() => lightbox.value > 0)
const canNext = computed(() => lightbox.value >= 0 && lightbox.value < visible.value.length - 1)

function openLightbox(i: number) {
  if (i < 0 || i >= visible.value.length) return
  lightbox.value = i
}
function closeLightbox() {
  lightbox.value = -1
}
function step(delta: number) {
  const next = lightbox.value + delta
  if (next < 0 || next >= visible.value.length) return
  lightbox.value = next
}
function onKey(e: KeyboardEvent) {
  if (lightbox.value < 0) return
  if (e.key === 'Escape') closeLightbox()
  else if (e.key === 'ArrowLeft') step(-1)
  else if (e.key === 'ArrowRight') step(1)
}
// SSR 里没有 window，而 onMounted 在服务端不执行 → 只在挂载后才碰 window
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <!-- 0 张图 → 根节点都不输出：既没有占位块，也没有保留的高度 -->
  <div v-if="layout" class="media" :class="[`media--${variant}`, `media--${layout.mode}`]">
    <!-- 1 张 → **原比例完整显示、不裁剪**（用户："如果是一张图，就原比例不裁剪"）；
         竖长图就让它长，不设高度上限。 -->
    <template v-if="single">
      <button
        v-if="!singleAsLink"
        class="media__tap"
        type="button"
        :aria-label="`${label}，查看大图`"
        @click="openLightbox(0)"
      >
        <img
          class="media__img"
          :src="singleUrl"
          alt=""
          loading="lazy"
          @error="onImgError(single.key)"
        />
      </button>
      <img
        v-else
        class="media__img"
        :src="singleUrl"
        alt=""
        loading="lazy"
        @error="onImgError(single.key)"
      />
    </template>

    <!-- 2 张以上 → **裁剪成方格**网格（用户："超过一张就进行裁剪，排成对应的九宫格等"）；
         列数由张数决定：2→一行 2 格、3→一行 3 格、4→2×2、5~9→三列；
         超过 9 张只排 9 张，第 9 张盖「+N」（后端侧上限就是 9，这一支实际不可达）。 -->
    <ul
      v-else-if="layout.mode === 'grid'"
      class="media__grid"
      :class="`media__grid--c${layout.columns}`"
    >
      <li v-for="(c, i) in shownCells" :key="c.key">
        <button
          class="media__cell"
          type="button"
          :aria-label="`${label}，查看第 ${i + 1} 张，共 ${visible.length} 张`"
          @click="openLightbox(i)"
        >
          <img
            class="media__cellimg"
            :src="c.url"
            alt=""
            loading="lazy"
            @error="onImgError(c.key)"
          />
          <span v-if="layout.overflow && i === layout.shown - 1" class="media__more">
            <span class="media__more-veil" aria-hidden="true" />
            <span class="media__more-num mw-num">+{{ layout.overflow }}</span>
          </span>
        </button>
      </li>
    </ul>

    <!-- 灯箱：从点击的那张开始，左右可翻，Esc 关闭 -->
    <div
      v-if="current"
      class="lb"
      role="dialog"
      aria-modal="true"
      :aria-label="`${label}，第 ${lightbox + 1} 张，共 ${visible.length} 张`"
      @click.self="closeLightbox"
    >
      <span class="lb__veil" aria-hidden="true" />
      <img class="lb__img" :src="current.url" alt="" />

      <button class="lb__close" type="button" aria-label="关闭大图" @click="closeLightbox">
        <PhX :size="18" aria-hidden="true" />
      </button>
      <button
        v-if="canPrev"
        class="lb__nav lb__nav--prev"
        type="button"
        aria-label="上一张"
        @click="step(-1)"
      >
        <PhCaretLeft :size="18" aria-hidden="true" />
      </button>
      <button
        v-if="canNext"
        class="lb__nav lb__nav--next"
        type="button"
        aria-label="下一张"
        @click="step(1)"
      >
        <PhCaretRight :size="18" aria-hidden="true" />
      </button>

      <p class="lb__count mw-num">{{ lightbox + 1 }} / {{ visible.length }}</p>
    </div>
  </div>
</template>

<style scoped>
.media {
  margin: 0;
}

/**
 * ⚠️ **2026-09-21 用户要求（Web）**：「照片**不一定非得占满**所有的标签的宽度，**可以留一点空**出来，
 * 在把标签页**变窄一点**的基础上，**再让照片整体大概占用标签页的 2/3** 即可」。
 *
 * ⇒ 卡片里的**整块媒体区**（单图与多图九宫格**一起**）宽度 = 卡片**内容宽度**的 2/3，右侧留白。
 *   - 用**百分比**而不是写死像素：卡片宽 / 右栏宽 / 视口变，媒体区跟着等比缩，不会出现"固定宽度溢出"。
 *   - `2/3` 写成 `66.6667%`（`calc(100% * 2 / 3)` 同值，但百分数更直观、少一层计算）。
 *   - **左对齐、右侧留白**（不是居中）：媒体区上下的标题 / 摘要 / 话题标签**全部左对齐**，
 *     居中会让这一块与上方文字的左边界错开，形成一条"锯齿"——
 *     用户的原话也是"与上面的文字左对齐"这个方向。所以这里用 `align-self: flex-start`
 *     （`.card__body` 是 `display:flex; flex-direction:column`，默认 `stretch` 会强行拉满宽，
 *     必须显式 `flex-start` 才真的窄下来）。
 *   - ⚠️ **卡片本身宽度不动**（`.feed` 那版 1080px）；右栏 `300px` 也不动。
 *   - ⚠️ **不裁剪/裁剪的分档规则不受影响**：只是"整块变窄"，
 *     单图仍在 2/3 宽度内按原比例显示，多图仍是方形裁剪格、列数仍由张数决定。
 */
.media--card {
  width: 66.6667%;
  max-width: 66.6667%;
  align-self: flex-start;
  /**
   * ⚠️ **2026-09-21 用户要求：「限制单张图的宽度最宽为 1×2 的宽度」**（1×2 = 一行两格）。
   *
   * ⇒ 这里是**一处具名声明**：卡片内"一行两格"的**整块宽度** = 媒体区的 2/3
   *   （= 2 个 1/3 格 + 1 个 gap）。
   *   它被 **两处共用**：① 多图 2 列档（2 张 / 4 张）的 `.media__grid--c2`；
   *   ② 单图的宽度上限 `.media--card .media__img` / `.media__tap`。
   *   共用同一处 ⇒ 「单图的最大宽度」与「2 张/4 张的整块宽度」**必然相等**，
   *   不会出现"两个地方各写一个 2/3、以后改一处忘另一处"。
   *
   * ⚠️ **要改成"媒体区的 1/2"只需改这一行**：把整个式子换成 `50%` 即可
   *   （单图上限与 2 列档整块会一起变成 1/2；格子/列数/裁剪规则都不用动）。
   *
   * ⚠️ 为什么式子不是裸的 `66.6667%`：因为一个 2 列档块里只有 **1 个 gap**（3 列档有 2 个），
   *   要让单格与 3 列档**严格相等**，块宽必须是 `2 格 + 1 gap` 反推出来的那条式子。
   *
   * `100%` 在**使用处**解析（自定义属性是文本替换）⇒ 这里指 `.media--card` 的**内容宽**（= 媒体区宽）。
   * 单图的 `<button class="media__tap">` 与 `<ul class="media__grid">` 都是 `.media--card` 的直接子元素，
   * 所以两者拿到的 `100%` 是同一个数。
   */
  --pmg-w-2col: calc(((100% - 2 * var(--mw-space-2)) / 3) * 2 + var(--mw-space-2));
}
/**
 * ⚠️ 窄屏响应式（**≤640px**）：小视口下媒体区若仍是卡片宽的 2/3，格子会小到看不清，
 * 所以 **≤640px 时媒体区放大到满宽**（`100%`）。
 *
 * ⚠️ **多图的"单格 = 媒体区 1/3"规则在窄屏下保持不变**（不去改 `.media__grid--c2/--c3`）：
 *   - 这样"2×2 的格 = 3×3 的格"这条用户要求在窄屏下**依然成立**（跨档严格相等是全局性质）；
 *   - 而且格子并不会变小：媒体区从 2/3 放大到 100%，单格 = 100%/3 反而**比宽屏时更大**。
 *     以 360px 视口为例：卡片内容宽 ≈ 360 − 2×16 = 328px ⇒ 单格 ≈ (328 − 2×8)/3 ≈ **104px**，
 *     是能看清的缩略图尺寸；320px 视口下仍有 ≈ 91px。
 *   - 阈值取 **640px** 的理由：>640px 时媒体区 2/3 仍有 ≥ 约 350px 可用；≤640px 进入手机量级，
 *     满宽更合适（手机端另有它自己的实现，这里是 Web 的小视口兜底）。
 */
@media (max-width: 640px) {
  .media--card {
    width: 100%;
    max-width: 100%;
  }
}

/* ---- 单张大图 ---- */
.media__tap {
  display: block;
  width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  cursor: zoom-in;
}
.media__img {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  margin: 0 auto;
  border-radius: var(--mw-radius-input);
}
/**
 * ⚠️ **2026-09-21 单图宽度上限（用户：「限制单张图的宽度最宽为 1×2 的宽度」）**：
 *   卡片里的单图最宽 = `--pmg-w-2col`（一行两格的整块宽 = 媒体区的 2/3）。
 *   - 只**收窄宽度上限**，`width/height: auto` 与"无 max-height"保持不变 ⇒ **仍按原比例、不裁剪、不限高**。
 *   - `margin: 0`（顶掉上面的 `margin: 0 auto`）⇒ **左边界与标题/摘要对齐**（居中会让左边界错开）；
 *     用户口径「媒体区…左对齐」+ 要"与 2 张/4 张整块宽度一致"⇒ 左边界也要一致。
 *   - `.media__tap`（单图的可点按钮）也一起收窄：否则图片右边的空白区仍然是"可点开大图"的热区，
 *     点在没有图的地方却弹灯箱。
 *   - ⚠️ **只作用于 `.media--card`**：详情页与灯箱（`.media--detail` / `.lm__img`）都是**另一套类**，
 *     依旧满宽显示完整原图，**不受此限**。
 */
.media--card .media__img,
.media--card .media__tap {
  max-width: var(--pmg-w-2col);
}
.media--card .media__tap {
  width: var(--pmg-w-2col);
}
.media--card .media__img {
  margin: 0;
}
/* ⚠️ **2026-09-21 用户最终裁决（分档）**：
   「一张图就**原比例不裁剪**，超过一张就**裁剪**排成九宫格」。
   ⇒ `.media--card/.media--detail .media__img` 的 `max-height` **删掉**（"不要收窄图片高度"），
   单图靠 `.media__img` 的 `width/height: auto` + `max-width: 100%` **完整显示、绝不裁剪**。
   裁剪**只发生在多图缩略格子**（见下面的 `.media__cellimg`）。 */

/* ---- 网格（裁剪成方格，间距一致） ---- */
.media__grid {
  display: grid;
  gap: var(--mw-space-2);
  /* `<ul>` 的浏览器默认 margin 会把整块往里推，破坏"与上方文字左对齐" —— 显式清零。 */
  margin: 0;
}
/* ⚠️ 网格**没有**整体高度上限（原来有 `max-height: 420px; overflow: hidden`，会把整行切掉）——
   现在高度由"方形格子 × 列数"自然决定，不裁整块。 */
/**
 * ⚠️ **2026-09-21 删掉的一条内边距（配合"单格跨档严格相等 / 单图上限 = 2 列档整块宽"）**：
 *   这里原来有 `.media--card .media__grid { padding: var(--mw-space-2) }`。
 *   全局是 `box-sizing: border-box`（`main.css:10`），所以 `width: 100%` **包含** padding ⇒
 *   3 列档的真实格子宽会变成 `(媒体区 − 2×padding − 2×gap) / 3`，
 *   而 2 列档与单图用的是"媒体区 − 2×gap"这条式子 —— **两边差一个 padding**，
 *   "单图上限 == 2 张整块宽"就无法严格成立（会差约 5.3px），格子跨档也不严格相等。
 *   删掉它之后：格子的外边界就是媒体区边界，而且**与上方标题/摘要的左边界严格对齐**
 *   （原来那 8px 反而把网格往右推了 8px，破坏了左对齐）。
 *   如果以后想要"媒体区里再留一圈白"，**不要**在这里加 padding，
 *   应当把 `.media--card` 的宽度调小 —— 那样才不会破坏上面两条等式。
 */
/**
 * ⚠️ **2026-09-21 用户拍板：多图档位的"单格宽度"必须跨档一致**。
 * 原话：「**2×2 的情况下，单独一个格的照片宽度应该要与 3×3 的单个照片宽度相同**」，
 * 并选定：「4 张仍是 2×2，但整块缩到媒体区 2/3 宽」「2 张也统一成 1/3 宽的单格」。
 *
 * 目标表（`媒体区` = 上面那条 `.media--card`，即卡片内容宽的 2/3）：
 * | 张数 | 排列 | 单格宽 | 整块宽（媒体区内） |
 * | 2    | 一行 2 格 | **1/3** | **2/3**（右侧留白） |
 * | 3    | 一行 3 格 | **1/3** | **满宽** |
 * | 4    | **仍是 2×2** | **1/3** | **2/3**（右侧留白） |
 * | 5–9  | 3 列       | **1/3** | **满宽** |
 *
 * **怎么做到"跨档严格相等"**（不是"约等于"）：
 *   3 列档：块宽 = 100%，`3 格 + 2 个 gap = 100%` ⇒ **单格 = (100% − 2·gap) / 3**。
 *   2 列档：块宽**按 3 列档的格子尺寸反推** = `2 格 + 1 个 gap`
 *          = `calc(((100% − 2·gap) / 3) · 2 + gap)` ⇒ 单格 = (块宽 − gap)/2 = **(100% − 2·gap) / 3**。
 *   两档单格宽度**代数值完全相同**（差 0），所以 2×2 的格与 3×3 的格一样宽 —— 这正是用户要的。
 *   ⚠️ 如果偷懒把 2 列档写成 `width: 66.6667%`，单格会变成 `(66.6667% − gap)/2`，
 *   与 3 列档差 `gap/6`（8px gap 时约 1.3px）—— 肉眼级别但**不严格相等**，门禁按"严格相等"判。
 * ⚠️ **全部相对单位**：`100%` + `calc()` + `var(--mw-space-2)`，**没有任何固定像素宽度**。
 * ⚠️ 方形裁剪规则不变（`.media__cell` 的 `aspect-ratio: 1` + `.media__cellimg` 的 `object-fit: cover`）。
 */
.media__grid--c2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  /* ⚠️ 与单图上限**共用同一处具名声明**（在 `.media--card` 上定义）⇒ 二者必然相等 */
  width: var(--pmg-w-2col);
}
.media__grid--c3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
}
/* ⚠️ **格子就是方形，且必须裁剪**（用户："超过一张就进行裁剪，排成对应的九宫格等"）：
   `aspect-ratio: 1` + `overflow: hidden` 让每格等高，`object-fit: cover` 把图裁进方格。
   ⚠️ 这里裁剪是**用户要的**；只裁缩略格，点开的大图与单图仍是完整原图。 */
.media__cell {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: var(--mw-radius-tag);
  background: none;
  cursor: zoom-in;
}
/* ⚠️ **多图缩略格裁剪**：`cover` + `height: 100%` 把图**按比例裁进方格**（不变形、不拉伸）。
   ⚠️ 单图（`.media__img`）与灯箱/详情页的图**不用**这个类，所以它们是完整原图。 */
.media__cellimg {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
/* 「+N」：遮罩与数字分两层，这样数字不会跟着遮罩一起变半透明 */
.media__more {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
}
.media__more-veil {
  position: absolute;
  inset: 0;
  background: var(--mw-night);
  opacity: 0.62;
}
.media__more-num {
  position: relative;
  color: var(--mw-on-night);
  font-size: var(--mw-font-body);
  font-weight: 700;
}

/* 键盘可达：焦点环用既有令牌，不用浏览器默认蓝 */
.media__tap:focus-visible,
.media__cell:focus-visible,
.lb__close:focus-visible,
.lb__nav:focus-visible {
  outline: 2px solid var(--mw-brand-ink);
  outline-offset: 2px;
}

/* ---- 灯箱 ---- */
.lb {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: var(--mw-space-6);
}
.lb__veil {
  position: absolute;
  inset: 0;
  background: var(--mw-night);
  opacity: 0.94;
}
.lb__img {
  position: relative;
  max-width: 100%;
  max-height: 82vh;
  border-radius: var(--mw-radius-input);
}
.lb__close,
.lb__nav {
  position: absolute;
  display: grid;
  place-items: center;
  min-width: 40px;
  min-height: 40px;
  margin: 0;
  padding: 0;
  border: 1px solid var(--mw-night-line);
  border-radius: var(--mw-radius-pill);
  background: var(--mw-night-2);
  color: var(--mw-on-night);
  cursor: pointer;
}
.lb__close {
  top: var(--mw-space-4);
  right: var(--mw-space-4);
}
.lb__nav {
  top: 50%;
  transform: translateY(-50%);
}
.lb__nav--prev {
  left: var(--mw-space-4);
}
.lb__nav--next {
  right: var(--mw-space-4);
}
.lb__count {
  position: absolute;
  bottom: var(--mw-space-4);
  color: var(--mw-on-night-2);
  font-size: var(--mw-font-caption);
}
</style>