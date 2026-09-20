/**
 * 用户主页的数据编排（Web）
 *
 * 为什么单独抽一层：主页有**四份游标列表 + 一份一次性数组 + 头部 + 关注按钮**，
 * 全塞进 SFC 会让"游标推进"和"乐观更新回滚"这两件最容易写错的事埋在模板里。
 * 这里只放数据与状态机，`views/UserProfileView.vue` 只做编排与渲染。
 *
 * 三条被实测钉死的规则（写错任何一条，界面在假数据下照样跑绿）：
 * 1) **游标推进只用响应给的 `nextCursor`**，`hasMore` 也只看响应；`size` 是**请求值**，
 *    拿 `records.length` 或 `size` 去推"还有没有下一页"都会错（实测 `size=999` 时
 *    `data.size===50` 而 `records.length===5`）。
 * 2) **`pet` 必须显式传 uid**：不传后端恒回 `[]`（不回落成"我的宠物"），
 *    所以 `listUserPets(uid.value)` 里那个参数不是装饰。
 * 3) **关注按钮的初始态只能来自 `profile.followed`** —— "是否已关注"没有独立接口
 *    （`/follow/is-following` 之类实测全是 `B000001` 未映射），自己猜必然显示错。
 *    写操作（关注/取关）**未实测**，所以做"乐观更新 + 失败回滚 + 把后端文案显示出来"。
 *
 * ⚠️ 空 `hasMore`+空游标的组合要挡住：`hasMore` 为真但 `nextCursor` 为 null 时若继续放行续页，
 * 下一次会拿 `null` 再拉第一页 → 列表里同一页无限重复。这里把"游标存在"也算进判据。
 */
import { computed, ref, type Ref } from 'vue'
import {
  USER_PAGE_SIZE,
  followUser,
  getUserProfile,
  listFollowers,
  listFollowing,
  listUserLikedPosts,
  listUserPets,
  listUserPosts,
  profileName,
  unfollowUser,
  userProfileErrorMessage,
  type CursorPage,
  type FollowUser,
  type PostSummary,
  type UserPetBrief,
  type UserProfile,
} from '@/api/community'
import { ApiError } from '@/api/request'

/** 主页上分段的四种"游标列表"（`pet` 是一次性数组，不在其中） */
export type UserListKind = 'post' | 'like' | 'following' | 'follower'
/** 分段本身（含宠物） */
export type UserTab = UserListKind | 'pet'

/** 游标列表的通用状态（`nextCursor` 原样保存，`loaded` 用来做"切到该段才加载"） */
export interface CursorState<T> {
  items: T[]
  loading: boolean
  loadingMore: boolean
  error: string
  hasMore: boolean
  cursor: number | null
  loaded: boolean
}

function emptyCursor<T>(): CursorState<T> {
  return {
    items: [],
    loading: false,
    loadingMore: false,
    error: '',
    hasMore: false,
    cursor: null,
    loaded: false,
  }
}

interface PetState {
  items: UserPetBrief[]
  loading: boolean
  error: string
  loaded: boolean
}

export function useUserProfile(uid: Ref<number>) {
  /* ---------- 头部 ---------- */

  const profile = ref<UserProfile | null>(null)
  const profileLoading = ref(true)
  const profileError = ref('')
  /** 网关 `state:-1` 或业务未登录码 → 页面走**软门槛**（页内提示 + 去登录），不新造弹窗 */
  const unauthorized = ref(false)

  const displayName = computed(() =>
    profileName(profile.value?.username, profile.value?.uid ?? uid.value),
  )
  const isSelf = computed(() => Boolean(profile.value?.self))
  /**
   * 关注按钮**只对"别人的主页"显示**：自己的主页没有关注自己的接口，
   * 显示了只会拿到一个必然失败的按钮（判据用后端给的 `self`，不用前端比对 uid）。
   */
  const showFollowButton = computed(() => (profile.value ? !profile.value.self : false))

  /* ---------- 关注状态 ---------- */

  const followed = ref(false)
  const followBusy = ref(false)
  const followError = ref('')

  /* ---------- 四份游标列表 + 宠物 ---------- */

  const posts = ref<CursorState<PostSummary>>(emptyCursor<PostSummary>())
  const liked = ref<CursorState<PostSummary>>(emptyCursor<PostSummary>())
  const following = ref<CursorState<FollowUser>>(emptyCursor<FollowUser>())
  const followers = ref<CursorState<FollowUser>>(emptyCursor<FollowUser>())
  const pets = ref<PetState>({ items: [], loading: false, error: '', loaded: false })

  /** 任何一处拿到"未登录"都要把整页切成软门槛（四个接口经网关全都要登录） */
  function noteError(e: unknown): string {
    if (e instanceof ApiError && e.isUnauthorized) unauthorized.value = true
    return userProfileErrorMessage(e)
  }

  /**
   * 拉一页游标列表。
   *
   * `fetchPage(lastId)` 里的 `lastId` **就是上一次响应里的 `nextCursor`**（首页传 null）；
   * 续页的放行判据全部来自上一次响应（`hasMore` + `cursor !== null`），不看本地条数。
   */
  async function fetchCursorPage<T>(
    state: Ref<CursorState<T>>,
    fetchPage: (lastId: number | null) => Promise<CursorPage<T>>,
    more: boolean,
  ) {
    const cur = state.value
    if (more) {
      if (cur.loading || cur.loadingMore || !cur.hasMore || cur.cursor === null) return
      cur.loadingMore = true
    } else {
      if (cur.loading) return
      cur.loading = true
    }
    cur.error = ''
    try {
      const page = await fetchPage(more ? cur.cursor : null)
      const records = Array.isArray(page?.records) ? page.records : []
      cur.items = more ? cur.items.concat(records) : records
      // ⚠️ 原样回传后端给的游标，绝不自己做 ±1 或用 postId 拼
      const next = page?.nextCursor
      cur.cursor = next === undefined || next === null ? null : next
      // 空游标时不再放行续页，否则下一轮会拿 null 又拉一次第一页
      cur.hasMore = Boolean(page?.hasMore) && cur.cursor !== null
      cur.loaded = true
    } catch (e) {
      cur.error = noteError(e)
    } finally {
      cur.loading = false
      cur.loadingMore = false
    }
  }

  /** 每段自己的请求（`uid` 一律显式传，不依赖后端"不传就是自己"的回落） */
  function loadList(kind: UserListKind, more = false) {
    const id = uid.value
    switch (kind) {
      case 'post':
        return fetchCursorPage(
          posts,
          (lastId) => listUserPosts({ uid: id, lastId, size: USER_PAGE_SIZE }),
          more,
        )
      case 'like':
        return fetchCursorPage(
          liked,
          (lastId) => listUserLikedPosts({ uid: id, lastId, size: USER_PAGE_SIZE }),
          more,
        )
      case 'following':
        return fetchCursorPage(
          following,
          (lastId) => listFollowing({ uid: id, lastId, size: USER_PAGE_SIZE }),
          more,
        )
      case 'follower':
        return fetchCursorPage(
          followers,
          (lastId) => listFollowers({ uid: id, lastId, size: USER_PAGE_SIZE }),
          more,
        )
    }
  }

  /** 宠物是**一次性裸数组**（没有分页信封），所以不走 fetchCursorPage */
  async function loadPets() {
    const st = pets.value
    if (st.loading) return
    st.loading = true
    st.error = ''
    try {
      // ⚠️ 这个 uid 不能省：不传后端恒回 []，界面会显示成"他没有宠物"
      const list = await listUserPets(uid.value)
      st.items = Array.isArray(list) ? list : []
      st.loaded = true
    } catch (e) {
      st.error = noteError(e)
    } finally {
      st.loading = false
    }
  }

  /** 切到某一段时才加载（`force` 给"重试"用） */
  async function ensureLoaded(kind: UserTab, force = false) {
    switch (kind) {
      case 'post':
        if (force || !posts.value.loaded) await loadList('post')
        return
      case 'like':
        if (force || !liked.value.loaded) await loadList('like')
        return
      case 'following':
        if (force || !following.value.loaded) await loadList('following')
        return
      case 'follower':
        if (force || !followers.value.loaded) await loadList('follower')
        return
      case 'pet':
        if (force || !pets.value.loaded) await loadPets()
        return
    }
  }

  /** 翻页（只在"还有下一页"时才会真的发请求） */
  function loadMore(kind: UserListKind) {
    return loadList(kind, true)
  }

  /* ---------- 头部加载 ---------- */

  async function loadProfile() {
    profileLoading.value = true
    profileError.value = ''
    try {
      const resp = await getUserProfile(uid.value)
      profile.value = resp ?? null
      // 关注按钮初始态的唯一来源（没有独立的"是否已关注"接口）
      followed.value = Boolean(resp?.followed)
      followError.value = ''
    } catch (e) {
      profile.value = null
      profileError.value = noteError(e)
    } finally {
      profileLoading.value = false
    }
  }

  /**
   * 关注 / 取消关注。
   *
   * 乐观更新：按钮文案与"粉丝数"先动，失败**必须**把两者一起退回原值并把后端的话显示出来
   * （写接口未实测，用户看到的"成功"不能是前端编的）。
   */
  async function toggleFollow() {
    const p = profile.value
    if (!p || p.self || followBusy.value) return
    const before = { followed: followed.value, followerCount: Number(p.followerCount ?? 0) }
    const next = !before.followed
    followed.value = next
    p.followerCount = Math.max(0, before.followerCount + (next ? 1 : -1))
    followBusy.value = true
    followError.value = ''
    try {
      if (next) await followUser(uid.value)
      else await unfollowUser(uid.value)
    } catch (e) {
      // 回滚：状态与计数一起退回，避免"按钮说已关注、数字没变"
      followed.value = before.followed
      p.followerCount = before.followerCount
      followError.value = noteError(e)
    } finally {
      followBusy.value = false
    }
  }

  function clearFollowError() {
    followError.value = ''
  }

  /* ---------- 重置与首屏 ---------- */

  function reset() {
    profile.value = null
    profileLoading.value = true
    profileError.value = ''
    unauthorized.value = false
    followed.value = false
    followBusy.value = false
    followError.value = ''
    posts.value = emptyCursor<PostSummary>()
    liked.value = emptyCursor<PostSummary>()
    following.value = emptyCursor<FollowUser>()
    followers.value = emptyCursor<FollowUser>()
    pets.value = { items: [], loading: false, error: '', loaded: false }
  }

  /** 首屏只拉"头部 + 他的帖子"，其余分段切过去时再拉（`ensureLoaded`） */
  async function load() {
    reset()
    await Promise.all([loadProfile(), loadList('post')])
  }

  return {
    // 头部
    profile,
    profileLoading,
    profileError,
    unauthorized,
    displayName,
    isSelf,
    showFollowButton,
    // 关注
    followed,
    followBusy,
    followError,
    toggleFollow,
    clearFollowError,
    // 列表
    posts,
    liked,
    following,
    followers,
    pets,
    loadList,
    loadPets,
    ensureLoaded,
    loadMore,
    // 生命周期
    load,
    reset,
  }
}