<script setup lang="ts">
/**
 * 用户主页（Web）
 *
 * 页面形态：**资料头部**（昵称兜底 + 帖子/获赞/关注/粉丝四个统计 + 关注按钮）
 * ＋ **五段**（他的帖子 / 他点赞的 / 他的宠物 / 他关注的 / 关注他的）。
 * 数据与状态机全在 `composables/useUserProfile.ts`，这里只做编排与渲染。
 *
 * 几条必须记住的边界（都来自 `.dsh-tmp/knowledge-userpage-contract.md` §B 的实测）：
 * - **没有头像**：社区 DTO 无头像字段、`miaowang_user.avatar` 全 null → 第一版不设计头像，
 *   头部用昵称首字色块，页面上没有任何 `<img>` 是给头像用的。
 * - **`username` 可为 null**（从没发过帖的人，含 uid=1）→ 一律走 `profileName()` 兜底成「喵友 #uid」，
 *   **不写「匿名用户」**（后端从没返回过这种占位）。
 * - **没有"他的评论"接口**（`/community/user/comment` 实测是未映射的 `B000001`）→ 不做那个分段。
 * - **未登录走软门槛**（页内提示 + 去登录），沿用帖子详情的约定，不新造登录弹窗。
 * - **自己的主页不显示关注按钮**（判据是接口给的 `profile.self`，不是前端比 uid）。
 *
 * `uid` 只能从路由参数 / 社区 DTO 拿：`GET /user/{username}` 返回的 `id` 恒为 null，
 * user 模块也没有"按 uid 查"，所以这一页**不会**去 user 模块查任何东西。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { PhSignIn } from '@phosphor-icons/vue'
import { useUserProfile, type UserTab } from '@/composables/useUserProfile'
import { useAuthStore } from '@/stores/auth'
import UserFollowList from '@/components/community/UserFollowList.vue'
import UserPetList from '@/components/community/UserPetList.vue'
import UserPostList from '@/components/community/UserPostList.vue'
import UserProfileHeader from '@/components/community/UserProfileHeader.vue'

const route = useRoute()
const authStore = useAuthStore()
const loggedIn = computed(() => authStore.isLoggedIn)

/** 路由已用 `\d+` 限定，这里再兜一层 Number 转换，避免把 "abc" 发到后端换来一个 B000001 */
const uid = computed(() => Number(route.params.uid))

const {
  profile,
  profileLoading,
  profileError,
  unauthorized,
  displayName,
  showFollowButton,
  followed,
  followBusy,
  followError,
  toggleFollow,
  posts,
  liked,
  following,
  followers,
  pets,
  ensureLoaded,
  loadMore,
  load,
} = useUserProfile(uid)

/** 未登录软门槛：页内就地说清楚 + 一个去登录的入口（不弹窗、不硬跳） */
const needLogin = computed(() => !loggedIn.value || unauthorized.value)

/**
 * 分段：顺序、标签、以及每一段空态要说的话。
 * 第一段在首屏就加载，其余切过去时才拉（见 `selectTab`）。
 */
const TAB_ORDER: UserTab[] = ['post', 'like', 'pet', 'following', 'follower']
const TAB_LABEL: Record<UserTab, string> = {
  post: '他的帖子',
  like: '他点赞的',
  pet: '他的宠物',
  following: '他关注的',
  follower: '关注他的',
}
const TAB_EMPTY: Record<UserTab, { title: string; desc: string }> = {
  post: { title: 'TA 还没发过帖', desc: '社区里发过的帖子才会出现在这里。' },
  like: { title: 'TA 还没点过赞', desc: '这份名单按点赞时间倒序，已删的帖子会被跳过。' },
  pet: { title: 'TA 还没有宠物档案', desc: '' },
  following: { title: 'TA 还没有关注任何人', desc: '' },
  follower: { title: '还没有人关注 TA', desc: '' },
}

const tab = ref<UserTab>('post')

async function selectTab(next: UserTab) {
  tab.value = next
  await ensureLoaded(next)
}

onMounted(() => {
  if (needLogin.value) return
  void load()
})

/**
 * 同一路由记录内从一份名单点进另一个人的主页时，组件是复用的 →
 * 必须自己看着 `uid` 重来一遍并把分段复位（否则会显示上一个人的列表）。
 */
watch(uid, () => {
  if (needLogin.value) return
  tab.value = 'post'
  void load()
})
</script>

<template>
  <div class="mw-container mw-page">
    <RouterLink class="back" to="/community">返回社区</RouterLink>

    <section v-if="needLogin" class="mw-state gate">
      <PhSignIn :size="26" aria-hidden="true" />
      <p class="mw-state__title">登录后才能看别人的主页</p>
      <p>主页的接口经网关全部要求登录，未登录时一个字段都拿不到。</p>
      <RouterLink
        class="mw-btn mw-btn--primary"
        :to="{ name: 'login', query: { redirect: route.fullPath } }"
      >
        去登录
      </RouterLink>
    </section>

    <template v-else>
      <UserProfileHeader
        :profile="profile"
        :name="displayName"
        :loading="profileLoading"
        :error="profileError"
        :followed="followed"
        :busy="followBusy"
        :follow-error="followError"
        :can-follow="showFollowButton"
        @retry="load"
        @toggle-follow="toggleFollow"
      />

      <template v-if="profile">
        <div class="tabs">
          <div class="mw-seg" role="tablist" aria-label="主页内容分段">
            <button
              v-for="key in TAB_ORDER"
              :key="key"
              class="mw-seg__item"
              :class="{ 'mw-seg__item--on': tab === key }"
              type="button"
              role="tab"
              :aria-selected="tab === key"
              @click="selectTab(key)"
            >
              {{ TAB_LABEL[key] }}
            </button>
          </div>
        </div>

        <section class="panel">
          <UserPostList
            v-if="tab === 'post'"
            :posts="posts.items"
            :loading="posts.loading"
            :error="posts.error"
            :has-more="posts.hasMore"
            :loading-more="posts.loadingMore"
            :empty-title="TAB_EMPTY[tab].title"
            :empty-desc="TAB_EMPTY[tab].desc"
            @retry="ensureLoaded('post', true)"
            @load-more="loadMore('post')"
          />

          <UserPostList
            v-else-if="tab === 'like'"
            :posts="liked.items"
            :loading="liked.loading"
            :error="liked.error"
            :has-more="liked.hasMore"
            :loading-more="liked.loadingMore"
            :empty-title="TAB_EMPTY[tab].title"
            :empty-desc="TAB_EMPTY[tab].desc"
            @retry="ensureLoaded('like', true)"
            @load-more="loadMore('like')"
          />

          <UserPetList
            v-else-if="tab === 'pet'"
            :pets="pets.items"
            :loading="pets.loading"
            :error="pets.error"
            @retry="ensureLoaded('pet', true)"
          />

          <UserFollowList
            v-else-if="tab === 'following'"
            :users="following.items"
            :loading="following.loading"
            :error="following.error"
            :has-more="following.hasMore"
            :loading-more="following.loadingMore"
            :empty-title="TAB_EMPTY[tab].title"
            :empty-desc="TAB_EMPTY[tab].desc"
            @retry="ensureLoaded('following', true)"
            @load-more="loadMore('following')"
          />

          <UserFollowList
            v-else
            :users="followers.items"
            :loading="followers.loading"
            :error="followers.error"
            :has-more="followers.hasMore"
            :loading-more="followers.loadingMore"
            :empty-title="TAB_EMPTY[tab].title"
            :empty-desc="TAB_EMPTY[tab].desc"
            @retry="ensureLoaded('follower', true)"
            @load-more="loadMore('follower')"
          />
        </section>

        <p class="note">
          昵称取自社区，从不发帖的人在这里显示成「喵友 #编号」。社区接口没有头像字段，所以这一页没有头像。
          关注与粉丝名单都用游标翻页，上一次拿到的游标原样传给下一次。
        </p>
      </template>
    </template>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
  margin-bottom: var(--mw-space-4);
  color: var(--mw-brand-ink);
  font-size: var(--mw-font-caption);
  font-weight: 600;
  text-decoration: none;
}
.back:hover {
  text-decoration: underline;
}

.gate {
  border: 1px dashed var(--mw-line);
  border-radius: var(--mw-radius-card);
}

/* 五段在窄屏会超出，允许横向滚动，不换行堆成两排 */
.tabs {
  margin-top: var(--mw-space-4);
  overflow-x: auto;
}
.tabs .mw-seg {
  white-space: nowrap;
}

.panel {
  padding-top: var(--mw-space-4);
}

.note {
  margin-top: var(--mw-space-6);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
  line-height: var(--mw-line-height);
}
</style>