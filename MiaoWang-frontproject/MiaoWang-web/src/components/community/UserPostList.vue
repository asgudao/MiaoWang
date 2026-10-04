<script setup lang="ts">
/**
 * 他的帖子 / 他点赞的帖子（同一个 `PostRespDTO`，同一套游标分页）
 *
 * 只做一件事：把一页页的帖子渲染成卡片网格，末尾给"加载更多"。
 * 分页推进**不在这里**（在 `useUserProfile`）—— 组件连 `nextCursor` 都拿不到，
 * 这样就不可能出现"组件自己拿 `postId` 拼游标"这类写法。
 *
 * 卡片上不显示作者名：这一页本来就只有一个人，省下的位置给时间与计数。
 *
 * 两处与信息流卡片同源的口径（改一边要想着另一边）：
 * 1) **图片区**读图走 `postMediaOf()`：有 images 数组按多图档位排（正方形网格 + 灯箱），
 *    只有 `coverUrl` 就按"单张大图"；**无图时整块不渲染**（原来这里会渲染出一个
 *    16/9 的灰底框 + 标题首字，就是用户看到的"空白"）。
 * 2) **长标题**超过 `textFold.ts` 的阈值才给「展开 / 收起」，行内切换、不跳页。
 */
import { ref } from 'vue'
import { PhArrowClockwise, PhChatCircle, PhEye, PhHeart, PhTray } from '@phosphor-icons/vue'
import PostMediaGrid from './PostMediaGrid.vue'
import { postMediaOf } from './postMediaLayout'
import { foldNeeded } from './textFold'
import {
  countText,
  isPinned,
  relativeTime,
  titleText,
  type PostSummary,
} from '@/api/community'

defineProps<{
  posts: PostSummary[]
  loading?: boolean
  error?: string
  hasMore?: boolean
  loadingMore?: boolean
  emptyTitle?: string
  emptyDesc?: string
}>()

const emit = defineEmits<{ retry: []; 'load-more': [] }>()

/** 媒体（**图片 + 视频**，2026-09-22 起同一个九宫格）：有数组按档位排，只有 coverUrl 就折成"1 张"；都没有就是空数组（整块不渲染） */
const mediaOf = (p: PostSummary) => postMediaOf(p)
/** 单图/单视频档位：这一项直接进帖子；多图档位才走灯箱 */
const singleMedia = (p: PostSummary) => postMediaOf(p).length === 1

/** 折叠状态按 postId 记在本组件（继续翻页不会把已展开的又收回去） */
const expandedIds = ref<number[]>([])
const isExpanded = (postId: number) => expandedIds.value.includes(postId)
function toggleFold(postId: number) {
  expandedIds.value = isExpanded(postId)
    ? expandedIds.value.filter((id) => id !== postId)
    : [...expandedIds.value, postId]
}
const foldable = (p: PostSummary) => foldNeeded(titleText(p.title))
const titleFolded = (p: PostSummary) => foldable(p) && !isExpanded(p.postId)
</script>

<template>
  <div v-if="loading" class="grid" aria-hidden="true">
    <span v-for="i in 4" :key="i" class="mw-skeleton sk" />
  </div>

  <div v-else-if="error" class="mw-state mw-state--box">
    <PhArrowClockwise :size="24" aria-hidden="true" />
    <p class="mw-state__title">没能读到帖子</p>
    <p>{{ error }}</p>
    <button class="mw-btn mw-btn--ghost" type="button" @click="emit('retry')">重试</button>
  </div>

  <div v-else-if="!posts.length" class="mw-state mw-state--box">
    <PhTray :size="24" aria-hidden="true" />
    <p class="mw-state__title">{{ emptyTitle || '这里还没有帖子' }}</p>
    <p v-if="emptyDesc">{{ emptyDesc }}</p>
  </div>

  <template v-else>
    <ul class="grid">
      <li v-for="p in posts" :key="p.postId">
        <!-- 卡片整体不再是一个 <a>：里面出现了「展开」按钮与图片按钮，
             `<a>` 里套按钮是非法 HTML，点「展开」还会连带跳页 -->
        <div class="pc">
          <!-- 单图/单视频 → 整块是进帖子的链接；多图/含视频 → 正方形网格 + 灯箱；无媒体 → 整块不渲染
               ⚠️ **2026-09-22**：`postMediaOf` 现在把 `videos` 也并进同一个九宫格，
               视频格点一下进详情页播（`post-id` 就是为此传的），列表里不内联播放。 -->
          <RouterLink
            v-if="singleMedia(p)"
            class="pc__cover"
            :to="{ name: 'community-post', params: { postId: p.postId } }"
          >
            <PostMediaGrid :images="mediaOf(p)" :post-id="p.postId" variant="card" single-as-link />
          </RouterLink>
          <PostMediaGrid
            v-else
            class="pc__cover"
            :images="mediaOf(p)"
            :post-id="p.postId"
            variant="card"
          />

          <div class="pc__body">
            <!-- isTop 实测是数字 0/1 → 走 isPinned() 按真值判断；原来浮在封面上，
                 封面可能整块不存在，所以改到正文里 -->
            <p v-if="isPinned(p)" class="pc__flags">
              <span class="pc__flag">置顶</span>
            </p>

            <div class="pc__titlespace">
              <span class="pc__title" :class="{ 'pc__title--clamp': titleFolded(p) }">
                <RouterLink :to="{ name: 'community-post', params: { postId: p.postId } }">
                  {{ titleText(p.title) }}
                </RouterLink>
              </span>
              <span v-if="titleFolded(p)" class="pc__fade" aria-hidden="true" />
            </div>
            <button
              v-if="foldable(p)"
              class="pc__fold"
              type="button"
              :aria-expanded="isExpanded(p.postId)"
              @click="toggleFold(p.postId)"
            >
              {{ isExpanded(p.postId) ? '收起' : '展开' }}
            </button>

            <span class="pc__stats mw-num">
              <span class="pc__stat">
                <PhEye :size="13" aria-hidden="true" />
                {{ countText(p.viewCount) }}
              </span>
              <span class="pc__stat">
                <PhHeart :size="13" :weight="p.liked ? 'fill' : 'regular'" aria-hidden="true" />
                {{ countText(p.likeCount) }}
              </span>
              <span class="pc__stat" title="评论总数，含回复">
                <PhChatCircle :size="13" aria-hidden="true" />
                {{ countText(p.commentCount) }}
              </span>
            </span>
            <span class="pc__time">{{ relativeTime(p.createTime) }}</span>
          </div>
        </div>
      </li>
    </ul>

    <div class="more">
      <button
        v-if="hasMore"
        class="mw-btn mw-btn--ghost"
        type="button"
        :disabled="loadingMore"
        @click="emit('load-more')"
      >
        {{ loadingMore ? '正在加载' : '加载更多' }}
      </button>
      <p v-else class="more__end">没有更多了</p>
    </div>
  </template>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(236px, 1fr));
  gap: var(--mw-space-4);
  /* 有图/无图的卡片各自定高，不拉齐（拉齐会在无图帖内部压出空白） */
  align-items: start;
}
.sk {
  display: block;
  height: 250px;
}

.pc {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  color: inherit;
  transition: box-shadow 0.15s ease;
}
.pc:hover {
  box-shadow: var(--mw-shadow-float);
}
/* 只做定位：原来的 16/9 + --mw-canvas 底 + 下边框就是"无图帖的灰框" */
.pc__cover {
  display: block;
}
.pc__flags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
}
.pc__flag {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--mw-space-2);
  border: 1px solid var(--mw-brand-ink);
  border-radius: var(--mw-radius-tag);
  background: var(--mw-surface);
  color: var(--mw-brand-ink);
  font-size: var(--mw-font-micro);
  font-weight: 600;
}
.pc__body {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  flex: 1;
  padding: var(--mw-space-4);
}
.pc__titlespace {
  position: relative;
}
.pc__title {
  display: block;
  font-size: var(--mw-font-body);
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: -0.01em;
}
/* 只在判定"需要展开"时裁两行 */
.pc__title--clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pc__fade {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 1.4em;
  background: linear-gradient(180deg, transparent, var(--mw-surface));
  pointer-events: none;
}
.pc__fold {
  align-self: flex-start;
  min-height: 28px;
  padding: 0;
  border: none;
  background: none;
  color: var(--mw-brand-ink);
  font-family: inherit;
  font-size: var(--mw-font-micro);
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}
.pc__fold:focus-visible {
  outline: 2px solid var(--mw-brand-ink);
  outline-offset: 2px;
}
/* 标题链接跟随原文字色，悬停才变色（整卡不再是一个链接，这层样式要自己带） */
.pc__title a {
  color: inherit;
  text-decoration: none;
}
.pc__title a:hover {
  color: var(--mw-brand-ink);
  text-decoration: underline;
}
.pc__stats {
  display: flex;
  gap: var(--mw-space-4);
  margin-top: auto;
  color: var(--mw-ink-2);
  font-size: var(--mw-font-micro);
}
.pc__stat {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
}
.pc__time {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.more {
  display: flex;
  justify-content: center;
  padding-top: var(--mw-space-6);
}
.more__end {
  font-size: var(--mw-font-caption);
  color: var(--mw-muted);
}
</style>