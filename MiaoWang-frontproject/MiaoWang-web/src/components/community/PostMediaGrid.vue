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
import { useRouter } from 'vue-router'
import { PhCaretLeft, PhCaretRight, PhPlay, PhX } from '@phosphor-icons/vue'
import {
  mediaGridLayout,
  mediaKey,
  sortMedia,
  type MediaItem,
  type MediaKind,
} from './postMediaLayout'

const props = withDefaults(
  defineProps<{
    /** 接口给的**媒体**数组（图片 + 视频，`kind` 区分；列表的 `coverUrl` 折成单项，见 `postMediaOf`） */
    images?: MediaItem[] | null
    /** `detail` = 详情页（单图上限更高）；`card` = 列表卡片 */
    variant?: 'detail' | 'card'
    /** `true` = 单图不包按钮，由外层链接负责点击（列表卡片用） */
    singleAsLink?: boolean
    /** 无障碍标签前缀，读屏用 */
    label?: string
    /**
     * ⚠️ **2026-09-22**：视频格点一下**进帖子详情页看/播**（用户口径："点击行为默认与照片一致"）。
     * 列表里**不建播放器实例、不内联播放**（一屏挂 N 个 `<video>` 会拖垮滚动）。
     * 不传 = 视频格渲染成不可点（详情页不往本组件喂视频，走页面自己的播放器）。
     */
    postId?: number | string | null
  }>(),
  { images: null, variant: 'detail', singleAsLink: false, label: '帖子配图', postId: null },
)

interface Cell {
  key: string
  url: string
  index: number
  kind: MediaKind
  /** ⚠️ **2026-09-22 新增**：视频格的封面图 URL（后端 `PostVideoRespDTO.coverUrl`，可空） */
  cover: string
}

const sorted = computed(() => sortMedia(props.images))
const cells = computed<Cell[]>(() =>
  sorted.value.map((m, i) => ({
    key: mediaKey(m, i),
    url: String(m.url ?? ''),
    index: i,
    kind: m.kind === 'video' ? 'video' : 'image',
    /** 只有视频有封面（`postMediaLayout.postMediaOf` 已经把 `videos[i].coverUrl` 映射进 `m.cover`） */
    cover: m.kind === 'video' && m.cover ? String(m.cover) : '',
  })),
)

/**
 * ⚠️ **2026-09-22 三轮口径（按顺序读，最后一轮是现状）**
 *
 * ① **第一轮**（用户："视频要有和照片一样的九宫格…效果和照片一样"）：视频格 = 中性占位 + 播放角标，
 *    **列表里不放 `<video>`**（不给每个视频建播放器实例）。
 * ② **第二轮**（用户："九宫格视频也要能显示其中的一个初始页面，**不能是空的**"）：
 *    临时改成在格子里放 `<video preload="metadata">` **争首帧** + 中性遮层兜底。"真首帧"当时就标明
 *    **不保证**（平台行为）。
 * ③ **第三轮**（用户真机/模拟器实测：「视频**没有显示封面**，**也点不开**」）：
 *    真机结果证明第 ② 轮那条路**不成立** —— 列表里的 `<video>` 在 App 端**既没渲出首帧**
 *    （native 组件的首帧要真解码，`preload` 语义在 App 端不保证），**又把点击吞了**
 *    （native 层压在 webview 之上，点格子不进详情）。⇒ **退回第 ① 轮的方案**：
 *    格子里**只有"静态画面 + 自绘角标"**，点击一律走 `openPost()` 进详情页播。
 * ④ **第四轮（现状，2026-09-22 后端交付抽帧封面后）**：后端 `PostVideoRespDTO` **新增
 *    `coverUrl` / `coverObjectKey`**（`dto/resp/PostVideoRespDTO.java:34,39`；抽帧用 jcodec，纯 Java）
 *    ⇒ **有封面就显示真封面**（复用图片格那条 `.media__cellimg` + `object-fit: cover`，几何一个字没改），
 *    **空 / `@error` ⇒ 退回第 ①轮的"中性底 + 角标"**（**绝不空白格**，格子照留、不影响其它格）。
 *    ⚠️ 两端同口径：手机端 `mediaSourceOf` 读 `videos[i].coverUrl`，Web 端 `postMediaOf` 同样只认它。
 *
 * **过期说法已作废（2026-09-22，避免下一个人被误导）**：
 *   ~~`PostVideoRespDTO` 只有 `{url, objectKey}`，没有 `coverUrl` / `poster`~~ —— 那是抽帧交付**之前**的事实；
 *   现在是 `{url, objectKey, coverUrl, coverObjectKey}`（仍**没有 `sort`**，视频顺序依旧是数组原序）。
 *   ⚠️ **两条红线不变**：① 列表里**不放 `<video>`**（native 组件吞点击/不出首帧，真机已否）；
 *   ② **不许编造封面**、**不许拿 `post.coverUrl`（帖子封面 = 第一张图）顶替视频封面**。
 */
const router = useRouter()

/**
 * 被收掉的图（key）：**只剩"加载真失败"这一种**（`@error`）。
 * **key 不能用下标**（收一张后下标会整体前移）。
 * ⚠️ 2026-09-21 起**尺寸退化不再收图**（用户"图片要全部显示"）。
 */
const dropped = ref<string[]>([])
const lightbox = ref(-1)
/**
 * ⚠️ **2026-09-22 新增**：视频**封面图**真加载失败（`@error`）的 url 集合。
 * 只用来把这一格退回"中性底 + 角标"（**绝不空白**）；**不摘格子、不改布局、不影响其它格子**。
 */
const coverFailed = ref<string[]>([])

/** 封面加载失败 → 记下来（模板据此回落中性占位） */
function onCoverError(url: string) {
  if (url && !coverFailed.value.includes(url)) coverFailed.value = [...coverFailed.value, url]
}

/** 换数据就清空收图记录与灯箱，否则重新读一次帖子后原来那张图会一直不显示 */
watch(
  () => props.images,
  () => {
    dropped.value = []
    lightbox.value = -1
    coverFailed.value = []
  },
)

const visible = computed(() => cells.value.filter((c) => !dropped.value.includes(c.key)))
/**
 * ⚠️ **2026-09-22：灯箱只装"图片"格**（`kind === 'image'`）。
 * 视频格的 url 是 `.mp4`，塞进 `<img>` 只会是一张破图；视频的播放位在**详情页**。
 * 灯箱的下标因此是"**图片序列**里的下标"（不是 `visible` 的下标）—— 混用会在
 * 「图 / 视频 / 图」这种帖子上把"下一张"跳到视频上。
 */
const imageCells = computed(() => visible.value.filter((c) => c.kind !== 'video'))
/** `null` = **整块不渲染**（0 个媒体项，或全被收掉） */
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
  if (lightbox.value >= imageCells.value.length) lightbox.value = -1
}

function onImgError(key: string) {
  drop(key)
}

/** 视频格 / 单个视频：**进详情页看与播**（列表里不内联播放，见 `postId` 的说明） */
function openPost() {
  if (props.postId === null || props.postId === undefined || props.postId === '') return
  void router.push({ name: 'community-post', params: { postId: props.postId } })
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
  lightbox.value >= 0 ? (imageCells.value[lightbox.value] ?? null) : null,
)
const canPrev = computed(() => lightbox.value > 0)
const canNext = computed(
  () => lightbox.value >= 0 && lightbox.value < imageCells.value.length - 1,
)

function openLightbox(i: number) {
  if (i < 0 || i >= imageCells.value.length) return
  lightbox.value = i
}
/** 从**格子的 key** 打开：下标要映射到"图片序列"（视频格不在灯箱里） */
function openLightboxFor(key: string) {
  openLightbox(imageIndexOf(key))
}
/** 这个格子在**图片序列**里的下标（-1 = 它本身是视频格）；只给读屏文案用 */
function imageIndexOf(key: string): number {
  return imageCells.value.findIndex((c) => c.key === key)
}
function closeLightbox() {
  lightbox.value = -1
}
function step(delta: number) {
  const next = lightbox.value + delta
  if (next < 0 || next >= imageCells.value.length) return
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
      <template v-if="single.kind !== 'video'">
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
      <!--
        ⚠️ **2026-09-22：恰好 1 个媒体项且它是视频**。
        视频**没有封面/首帧字段可用**（后端 `PostVideoRespDTO` 逐字只有 `{url, objectKey}` —— 不许编字段），
        宽度与单图**共用同一条 1×2 上限**（`.media--card .media__tap`），比例取 16:9；
        里面同样放 `<video preload="metadata">` 尽力出首帧 + 中性遮层保证不空 + 播放角标。
      -->
      <button
        v-else-if="!singleAsLink"
        class="media__tap media__video-single"
        type="button"
        :aria-label="`${label}，视频，点进帖子播放`"
        :disabled="postId === null"
        @click="openPost()"
      >
        <!-- ⚠️ **2026-09-22 第四轮**：有后端抽帧封面（`coverUrl`）就显示真封面（铺满这个 16:9 框）；
             空 / `@error` ⇒ 下面那层中性底 + 角标（**绝不空白**）。 -->
        <img
          v-if="single && single.cover && !coverFailed.includes(single.cover)"
          class="media__video-cover"
          :src="single.cover"
          alt=""
          @error="onCoverError(single.cover)"
        />
        <span class="media__video" aria-hidden="true">
          <PhPlay :size="18" weight="fill" />
        </span>
      </button>
      <span v-else class="media__video-single">
        <img
          v-if="single && single.cover && !coverFailed.includes(single.cover)"
          class="media__video-cover"
          :src="single.cover"
          alt=""
          @error="onCoverError(single.cover)"
        />
        <span class="media__video" aria-hidden="true">
          <PhPlay :size="18" weight="fill" />
        </span>
      </span>
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
        <!--
          ⚠️ **2026-09-22：视频格与图片格共用同一个 `.media__cell`** ——
          同一套行布局、同样的 1/3 单元格、同样的方形裁剪、同样的 `gap` 间距。
          区别只有"格子里面画什么"与"点击去哪"。
        -->
        <button
          v-if="c.kind !== 'video'"
          class="media__cell"
          type="button"
          :aria-label="`${label}，查看第 ${imageIndexOf(c.key) + 1} 张，共 ${imageCells.length} 张`"
          @click="openLightboxFor(c.key)"
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
        <!-- 视频格：**几何与图片格逐项相同**（同一个 `.media__cell`）。
             里面分两层（2026-09-22 第四轮）：① **封面**——后端抽帧的 `coverUrl` 有值就显示真封面，复用图片格 `.media__cellimg` + `object-fit: cover`（几何不变）；
             ⚠️ 封面为空 / `@error` ⇒ 只剩角标 + 格子自己的中性底（`--mw-canvas`）⇒ **绝不是空白格**；
             ② 自绘播放三角角标（无白底、无"视频"二字、**不用 emoji**）。
             点它进帖子详情播（列表里不内联播放；`postId` 没给就渲染成不可点，不做假按钮）。 -->
        <button
          v-else
          class="media__cell media__cell--video"
          type="button"
          :aria-label="`${label}，第 ${i + 1} 格是视频，点进帖子播放`"
          :disabled="postId === null"
          @click="openPost()"
        >
          <img
            v-if="c.cover && !coverFailed.includes(c.cover)"
            class="media__cellimg"
            :src="c.cover"
            alt=""
            @error="onCoverError(c.cover)"
          />
          <span class="media__video" aria-hidden="true">
            <PhPlay :size="18" weight="fill" />
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
      :aria-label="`${label}，第 ${lightbox + 1} 张，共 ${imageCells.length} 张`"
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

      <p class="lb__count mw-num">{{ lightbox + 1 }} / {{ imageCells.length }}</p>
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
/**
 * ⚠️ **2026-09-22 第三轮（现状）**：视频格（用户："视频要有和照片一样的九宫格，效果和照片一样"）。
 *
 * **几何与图片格逐项相同**：它用的就是上面那条 `.media__cell`
 * （`width: 100%` 由网格列决定 / `aspect-ratio: 1` 正方形 / `overflow: hidden` / `border-radius: var(--mw-radius-tag)`），
 * 网格的 `gap: var(--mw-space-2)` 与列数判定（`postMediaLayout.ts`）也一个字没改 ——
 * 这里**只补"格子里面画什么"**。配色只用既有令牌：底 `--mw-canvas`、线 `--mw-line`、图标 `--mw-ink-2`。
 *
 * **为什么格子里不放 `<video>`（三轮的结论）**：
 *   ① 后端**没有封面字段**（`PostVideoRespDTO` 只有 `{url, objectKey}`）⇒ 列表里**拿不到真封面**，
 *      也**不许编造**一个；
 *   ② 试过 `<video preload="metadata">` 争首帧 —— 真机（App 端）实测**既没渲出首帧、又把点击吞了**
 *      （native 层在 webview 之上），用户看到的正是"没有封面 + 点不开"；
 *   ③ ⇒ 列表里只留**静态中性底 + 一枚播放三角**：**永远不会空白/纯黑**（底是既有令牌色），
 *      点击一律落到外层按钮 → `openPost()` 进详情页播（列表里不内联播放、不自动播放）。
 * ⚠️ 取舍如实说明：**格子里看不到画面**。要真封面只能服务端抽帧（ffmpeg/JavaCV + 给
 *    `PostVideoRespDTO` 加封面字段）或用户手工准备封面图 —— 本轮不做。
 */
.media__cell--video {
  display: grid;
  place-items: center;
  background: var(--mw-canvas);
  border: 1px solid var(--mw-line);
  cursor: pointer;
}
/**
 * ⚠️ **2026-09-22 修复（用户：「web 端视频中间没有那个三角形播放键」）**
 *
 * **成因（已核实的准确机理）**：`.media__cell` 是 `aspect-ratio: 1` + `overflow: hidden`，
 * `.media__cell--video` 又是 `display: grid; place-items: center`；而封面 `<img class="media__cellimg">`
 * 是**正常流里的 grid item 且 `height: 100%`** ⇒ 它**独占那一格**，于是后面的角标
 * `<span class="media__video">` 被排进**隐式的第二行**（格子高度已被占满）⇒ 被 `overflow: hidden` **裁掉**。
 * ⇒ 角标**一直都在 DOM 里**（SSR 断言早有 `class="media__video"`），只是**被封面挤出了可视区**（成因②）。
 *
 * **修法**：只在视频格里把封面**移出文档流**（`position: absolute; inset: 0`；尺寸仍由父格决定，
 * **不新增任何 width/height/aspect-ratio**）⇒ 角标重新成为唯一在流子元素、被 `place-items: center` 居中，
 * 又因为排在封面之后而**压在封面之上**。图片格（非 `--video`）的 `.media__cellimg` 原样不动。
 */
.media__cell--video .media__cellimg {
  position: absolute;
  inset: 0;
}
/* 播放角标：**只有一枚三角**，压在**半透明深色圆盘**上（用户 2026-09-22：「视频中间的播放键
   为什么有白色背景，可以去掉吗，还有"视频"两个字也去掉」）。
   - **没有白底/白圆**：圆盘色 = 既有令牌 `--mw-night`（#1a1310）+ `opacity: 0.62`
     （与 `.media__more` 的 `+N` 角标同一个"半透明深色"手法，**不写死 rgba、不引新令牌**）；
   - **没有文案**：`"视频"` 两个字删了，"这是视频"由三角本身表达（图标仍是 `PhPlay`，**不是 emoji**）；
   - `position: relative` + `z-index: 1` 让角标压在**封面**之上（封面是绝对定位的兄弟节点，DOM 里排在前面）；
   - `pointer-events: none`：点击照旧落到外层按钮 = 进详情页播。
   ⚠️ `opacity` 会同时作用在三角上（三角也是半透明白）—— 这是**刻意的**：白底圆圈不要，
      深色半透明圆盘 + 半透明白三角，在格子的暖色底上读得清。 */
.media__video {
  position: relative;
  display: grid;
  place-items: center;
  padding: var(--mw-space-2);
  border-radius: var(--mw-radius-pill);
  background: var(--mw-night);
  color: var(--mw-on-night);
  opacity: 0.62;
  pointer-events: none;
  z-index: 1;
}
/**
 * ⚠️ **恰好 1 个媒体项且它是视频**：宽度与单图**共用同一条 1×2 上限**
 * （`.media--card .media__tap` 已经把 `width` 设成 `var(--pmg-w-2col)`），
 * 这里只给高度方向的比例：**16:9**（视频常见比例）。
 * ⚠️ 这不是"量出来的真比例"——**2026-09-22 之前**后端连封面都没有；现在后端已交付 `coverUrl`
 * （真封面按 `object-fit: cover` 铺满这个框），**16:9 保留为 `coverUrl` 为空时的兜底比例**与点击进场尺寸。
 * 外接矩形不裁切、不做成方形（方形会与"1 个媒体项保持原始比例"的口径打架）。
 * 代价：竖屏视频会在这块 16:9 里显得小，但它只是一个"点进详情播放"的入口。
 */
.media__video-single {
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  border-radius: var(--mw-radius-input);
  background: var(--mw-canvas);
  border: 1px solid var(--mw-line);
  box-sizing: border-box;
}
/* 卡片里由外层 `RouterLink` 负责点击时的形态与上面那块**同一套几何**（只是没有按钮外壳） */
/* ⚠️ **单项视频的真封面**（2026-09-22 后端交付 `coverUrl` 后新增）：
   它只负责"铺满那个 16:9 的框"（`object-fit: cover` 与图片格同一裁剪口径），
   **不参与任何尺寸决策** —— 框的比例仍来自 `.media__video-single`。 */
.media__video-cover {
  position: absolute;
  inset: 0;
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