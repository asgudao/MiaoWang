<script setup lang="ts">
/**
 * 用户主页的头部（昵称 + 四个统计 + 关注按钮）
 *
 * 三个刻意的取舍：
 * 1) **不做头像**。社区所有 DTO 都没有头像字段，`miaowang_user.avatar` 实测也全 null
 *    → 渲染空 `<img>` 只有裂图这一种结果。这里沿用社区卡片的"昵称首字色块"。
 * 2) 关注按钮的显隐由 `canFollow` 决定（页面传 `showFollowButton`），组件**不自己拿 uid 比大小**：
 *    判据是后端给的 `profile.self` —— 前端自己拼 uid 一旦拿不到（手机号登录只回 token）就会错。
 * 3) 昵称兜底**不在这一层做**：页面已经把 `profileName()` 的结果当 `name` 传进来，
 *    组件只渲染，避免同一个兜底逻辑在两个地方各写一遍。
 */
import { computed } from 'vue'
import { PhArrowClockwise, PhUserMinus, PhUserPlus } from '@phosphor-icons/vue'
import { countText, initialOf, type UserProfile } from '@/api/community'

const props = defineProps<{
  profile: UserProfile | null
  /** 已兜底过的昵称（`profileName()` 的结果） */
  name: string
  loading?: boolean
  error?: string
  /** 我有没有关注 TA（初始态只能来自 `profile.followed`） */
  followed?: boolean
  busy?: boolean
  /** 关注/取关失败时后端给的话 */
  followError?: string
  /** 是否显示关注按钮：自己的主页一律不显示 */
  canFollow?: boolean
}>()

const emit = defineEmits<{ retry: []; 'toggle-follow': [] }>()

/** 四个统计：顺序即视觉权重（帖子 / 获赞 / 关注 / 粉丝） */
const stats = computed(() => {
  const p = props.profile
  if (!p) return []
  return [
    { key: 'post', label: '帖子', value: p.postCount },
    { key: 'like', label: '获赞', value: p.likeReceivedCount },
    { key: 'following', label: '关注', value: p.followingCount },
    { key: 'follower', label: '粉丝', value: p.followerCount },
  ]
})
</script>

<template>
  <div v-if="loading" class="head-skeleton" aria-hidden="true">
    <span class="mw-skeleton sk sk--badge" />
    <span class="mw-skeleton sk sk--name" />
    <span class="mw-skeleton sk sk--stats" />
  </div>

  <div v-else-if="error" class="mw-state mw-state--box">
    <PhArrowClockwise :size="24" aria-hidden="true" />
    <p class="mw-state__title">这位的主页没能读到</p>
    <p>{{ error }}</p>
    <button class="mw-btn mw-btn--ghost" type="button" @click="emit('retry')">重试</button>
  </div>

  <section v-else-if="profile" class="head mw-card">
    <div class="head__top">
      <!-- 没有头像字段：昵称首字做色块，不渲染空 img -->
      <span class="head__badge" aria-hidden="true">{{ initialOf(name) }}</span>

      <div class="head__id">
        <h1 class="head__name">{{ name }}</h1>
        <p class="head__uid mw-num">#{{ profile.uid }}</p>
      </div>

      <p v-if="profile.self" class="head__self">这是你自己的主页</p>

      <button
        v-else-if="canFollow && followed"
        class="mw-btn mw-btn--ghost"
        type="button"
        :disabled="busy"
        :aria-pressed="true"
        @click="emit('toggle-follow')"
      >
        <PhUserMinus :size="16" aria-hidden="true" />
        {{ busy ? '处理中' : '取消关注' }}
      </button>

      <button
        v-else-if="canFollow"
        class="mw-btn mw-btn--primary"
        type="button"
        :disabled="busy"
        :aria-pressed="false"
        @click="emit('toggle-follow')"
      >
        <PhUserPlus :size="16" aria-hidden="true" />
        {{ busy ? '处理中' : '关注' }}
      </button>
    </div>

    <ul class="stats">
      <li v-for="s in stats" :key="s.key" class="stats__item">
        <span class="stats__num mw-num">{{ countText(s.value) }}</span>
        <span class="stats__label">{{ s.label }}</span>
      </li>
    </ul>

    <p v-if="followError" class="mw-alert mw-alert--danger head__err">{{ followError }}</p>
  </section>
</template>

<style scoped>
.head-skeleton {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  padding: var(--mw-space-6);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.sk {
  display: block;
}
.sk--badge {
  width: 54px;
  height: 54px;
  flex: none;
  border-radius: var(--mw-radius-pill);
}
.sk--name {
  width: 180px;
  height: 28px;
}
.sk--stats {
  width: 220px;
  height: 40px;
  margin-left: auto;
}

.head {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
}
.head__top {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  flex-wrap: wrap;
}
.head__badge {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(194, 65, 12, 0.1);
  color: var(--mw-brand-ink);
  font-size: var(--mw-font-h2);
  font-weight: 700;
}
.head__id {
  min-width: 0;
}
.head__name {
  font-size: var(--mw-font-h1);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
.head__uid {
  font-size: var(--mw-font-caption);
  color: var(--mw-muted);
}
/* 看自己的主页时替掉关注按钮，免得出现一个必然失败的按钮 */
.head__self {
  margin-left: auto;
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}

.stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-8);
  padding-top: var(--mw-space-3);
  border-top: 1px solid var(--mw-line);
}
.stats__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stats__num {
  font-size: var(--mw-font-h2);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.stats__label {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.head__err {
  margin-top: var(--mw-space-1);
}
</style>