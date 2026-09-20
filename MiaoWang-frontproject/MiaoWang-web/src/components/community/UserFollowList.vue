<script setup lang="ts">
/**
 * 关注 / 粉丝列表（`/community/follow/following|follower`，都是游标分页）
 *
 * 每一行自带 `followed`（我有没有关注这个人）→ 直接当标签显示，**不在行内放关注按钮**：
 * 行内按钮要维护"每一行各自的状态 + 回滚"，而这一页的主线是"看关系"，点名字进对方主页就够了。
 *
 * 昵称一律走 `profileName()`：列表里 `username` 实测可为 null（uid=1 就是）。
 */
import { PhArrowClockwise, PhTray, PhUserCircleCheck } from '@phosphor-icons/vue'
import { profileName, type FollowUser } from '@/api/community'

defineProps<{
  users: FollowUser[]
  loading?: boolean
  error?: string
  hasMore?: boolean
  loadingMore?: boolean
  emptyTitle?: string
  emptyDesc?: string
}>()

const emit = defineEmits<{ retry: []; 'load-more': [] }>()
</script>

<template>
  <div v-if="loading" class="mw-rows" aria-hidden="true">
    <span v-for="i in 3" :key="i" class="mw-skeleton sk" />
  </div>

  <div v-else-if="error" class="mw-state mw-state--box">
    <PhArrowClockwise :size="24" aria-hidden="true" />
    <p class="mw-state__title">没能读到这份名单</p>
    <p>{{ error }}</p>
    <button class="mw-btn mw-btn--ghost" type="button" @click="emit('retry')">重试</button>
  </div>

  <div v-else-if="!users.length" class="mw-state mw-state--box">
    <PhTray :size="24" aria-hidden="true" />
    <p class="mw-state__title">{{ emptyTitle || '这份名单是空的' }}</p>
    <p v-if="emptyDesc">{{ emptyDesc }}</p>
  </div>

  <template v-else>
    <ul class="mw-rows">
      <li v-for="u in users" :key="u.uid" class="mw-row">
        <span class="fu__badge" aria-hidden="true">{{ profileName(u.username, u.uid).slice(0, 1) }}</span>
        <div class="mw-row__main">
          <RouterLink
            class="fu__name"
            :to="{ name: 'user-profile', params: { uid: u.uid } }"
          >
            {{ profileName(u.username, u.uid) }}
          </RouterLink>
          <p class="mw-row__sub mw-num">#{{ u.uid }}</p>
        </div>
        <span v-if="u.followed" class="fu__tag">
          <PhUserCircleCheck :size="14" aria-hidden="true" />
          已关注
        </span>
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
.sk {
  display: block;
  height: 62px;
}
.fu__badge {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(194, 65, 12, 0.1);
  color: var(--mw-brand-ink);
  font-weight: 700;
}
.fu__name {
  font-weight: 600;
  color: inherit;
  text-decoration: none;
}
.fu__name:hover {
  text-decoration: underline;
}
.fu__tag {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  flex: none;
  padding: 2px var(--mw-space-2);
  border-radius: var(--mw-radius-tag);
  background: rgba(74, 118, 56, 0.12);
  color: var(--mw-success);
  font-size: var(--mw-font-micro);
  font-weight: 600;
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