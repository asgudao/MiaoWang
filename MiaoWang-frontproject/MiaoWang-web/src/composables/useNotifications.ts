/**
 * 消息通知的数据编排（Web）
 *
 * 为什么单独抽一层：通知页有四件最容易写错的事 —— **游标推进**、**未读数**、
 * **标记已读的乐观更新与回滚**、**筛选切换后重来**。全塞进 SFC 会让它们埋在模板里，
 * 也没法用假 fetch 驱动断言。这里只放数据与状态机，`views/NotificationsView.vue` 只做渲染。
 *
 * **刻意不建 Pinia store**（任务口径）：避开 `check-stores.js` 的"导出 reset 必须接入账号清理"门禁，
 * 也避开换号时要清两处状态的复杂度。代价是"导航铃铛的红点"与"页面里的未读数"是两份本地状态，
 * 靠 `NOTIFICATION_READ_EVENT` 一次性广播对齐（见 `api/community.ts` 里那个常量的说明）。
 *
 * 四条被实测钉死的规则：
 * 1) **游标只用响应的 `nextCursor` 原样回传**，`hasMore` 也只看响应。
 *    后端是 `notification_id < lastId`（往更小的取），拿 `records.length` 当游标 = 页码思维，直接漏页。
 *    `nextCursor` 为 null 时**不再放行续页**，否则下一轮会拿 null 又拉一次第一页 → 列表重复。
 * 2) **未读数是裸数字**，且**只有总数、没有按 type 分组** → 页面不做按 type 的红点。
 * 3) **标已读是写操作、本端从未真调过** → 一律"先乐观改本地、失败再回滚"；
 *    `B000317`（不存在 / 不是我的）**静默回滚不弹错**（那是数据不一致，用户处理不了），
 *    其它错误才把后端的话显示出来。
 * 4) **`readFlag` 1 才算已读**：已读的条目点开不再发标记请求（后端本来也幂等，但没必要发）。
 */
import { computed, ref } from 'vue'
import {
  NOTIFICATION_PAGE_SIZE,
  NOTIFICATION_READ_EVENT,
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  notificationErrorMessage,
  notificationUnread,
  type NotificationItem,
} from '@/api/community'
import { ApiError } from '@/api/request'

/** 页面上的筛选：全部 / 只看未读（**不是按 type 分组的 Tab** —— 后端没有分组计数接口） */
export type NotificationFilter = 'all' | 'unread'

export function useNotifications() {
  /* ---------- 列表（游标分页） ---------- */

  const items = ref<NotificationItem[]>([])
  /** 首屏/重来时是 true（模板据此显示骨架屏，而不是先闪一下空态） */
  const loading = ref(true)
  const loadingMore = ref(false)
  const error = ref('')
  const hasMore = ref(false)
  /** 原样保存后端给的 `nextCursor` */
  const cursor = ref<number | null>(null)
  const loaded = ref(false)
  /** 网关 `state:-1` 或业务未登录码 → 页面走**软门槛**（页内提示 + 去登录），不新造弹窗 */
  const unauthorized = ref(false)

  /* ---------- 未读数（裸数字；入口红点与页头共用这份本地状态） ---------- */

  const unreadCount = ref(0)
  const unreadLoading = ref(true)
  const unreadError = ref('')

  /* ---------- 筛选与写操作 ---------- */

  const filter = ref<NotificationFilter>('all')
  const marking = ref<number[]>([])
  const actionError = ref('')
  /**
   * 续页失败**单独一个字段**：续页挂了不能把已经读到的几条换成整页错误态
   * （那等于"往下翻的时候把上面的内容弄丢了"）。首屏失败才用 `error` 走整页错误态。
   */
  const moreError = ref('')
  const allBusy = ref(false)

  const filtered = computed(() => filter.value === 'unread')
  const isEmpty = computed(() => loaded.value && !loading.value && items.value.length === 0)

  /** 列表请求是否在飞（普通变量，不是 ref：它只用于防重入，不进模板） */
  let listInFlight = false

  function noteError(e: unknown): string {
    if (e instanceof ApiError && e.isUnauthorized) unauthorized.value = true
    return notificationErrorMessage(e)
  }

  /** 标记成功后才广播：让导航铃铛的红点跟页面状态保持一致（无 store 下的最小手段） */
  function broadcastRead() {
    if (typeof window === 'undefined') return
    window.dispatchEvent(new Event(NOTIFICATION_READ_EVENT))
  }

  /**
   * 拉一页。
   *
   * `more=false` 时**不传 `lastId`**（后端没有 `id ≥ lastId` 的数据就退化从顶部返回 → 会重复第一页）；
   * `more=true` 时把上一次的 `cursor` **原样**传回去。
   */
  async function loadPage(more = false) {
    // 同一时刻只允许一个列表请求在飞（用普通变量而不是 `loading`，因为 `reset()` 会先把 loading 置 true）
    if (listInFlight) return
    if (more && (!hasMore.value || cursor.value === null)) return
    listInFlight = true
    if (more) loadingMore.value = true
    else loading.value = true
    if (more) moreError.value = ''
    else error.value = ''
    try {
      const page = await listNotifications({
        lastId: more ? cursor.value : null,
        size: NOTIFICATION_PAGE_SIZE,
        // 不筛选时不传这个参数（实测 `unreadOnly=false` 与不传等价，别在 URL 上留噪声）
        unreadOnly: filtered.value ? true : undefined,
      })
      const records = Array.isArray(page?.records) ? page.records : []
      items.value = more ? items.value.concat(records) : records
      // ⚠️ 原样回传后端给的游标，绝不自己 ±1
      const next = page?.nextCursor
      cursor.value = next === undefined || next === null ? null : Number(next)
      // 空游标时不再放行续页，否则下一轮拿 null 又拉一次第一页
      hasMore.value = Boolean(page?.hasMore) && cursor.value !== null
      loaded.value = true
    } catch (e) {
      const msg = noteError(e)
      // 续页失败只落在 moreError：已加载的那些条目原样留着（列表不会突然变成错误页）
      if (more) moreError.value = msg
      else error.value = msg
    } finally {
      listInFlight = false
      loading.value = false
      loadingMore.value = false
    }
  }

  /** 翻页（只在"还有下一页"时才会真的发请求） */
  function loadMore() {
    return loadPage(true)
  }

  /** 进页面 / 路由切换时拉一次未读数（**不做全站轮询**，全仓库也没有推送设施） */
  async function refreshUnread() {
    unreadLoading.value = true
    unreadError.value = ''
    try {
      unreadCount.value = await getUnreadCount()
    } catch (e) {
      unreadCount.value = 0
      unreadError.value = noteError(e)
    } finally {
      unreadLoading.value = false
    }
  }

  function reset() {
    items.value = []
    loading.value = true
    loadingMore.value = false
    error.value = ''
    moreError.value = ''
    hasMore.value = false
    cursor.value = null
    loaded.value = false
    unauthorized.value = false
    unreadLoading.value = true
    actionError.value = ''
    marking.value = []
    allBusy.value = false
    // unreadCount 刻意**不清零**：清零会让红点在刷新瞬间闪一下消失
  }

  /** 首屏 / 重试 / 切筛选：列表第一页 + 未读数 */
  async function load() {
    reset()
    await loadPage(false)
    await refreshUnread()
  }

  /** 切筛选：清空列表重新从第一页拉（游标是"旧的"，跨筛选复用会漏数据） */
  async function setFilter(next: NotificationFilter) {
    if (filter.value === next) return
    filter.value = next
    await load()
  }

  function isMarking(notificationId: number) {
    return marking.value.includes(notificationId)
  }

  /**
   * 标记单条已读：**先乐观改本地，失败再回滚**。
   *
   * - 已读的条目直接短路（不发请求）—— `readFlag=1` 的不再显示未读样式，点它也没有意义
   * - `B000317`（不存在 / 不是我的）与未登录：**静默回滚**，不把这句话弹给用户
   * - 其它失败（网络 / 后端异常）：回滚 + 把后端的话显示出来
   */
  async function markRead(notificationId: number) {
    const item = items.value.find((n) => n.notificationId === notificationId)
    if (!item) return
    if (!notificationUnread(item.readFlag)) return
    if (isMarking(notificationId)) return

    const before = { readFlag: item.readFlag, unread: unreadCount.value }
    item.readFlag = 1
    unreadCount.value = Math.max(0, before.unread - 1)
    marking.value = marking.value.concat(notificationId)
    try {
      await markNotificationRead(notificationId)
      broadcastRead()
    } catch (e) {
      item.readFlag = before.readFlag
      unreadCount.value = before.unread
      const err = e instanceof ApiError ? e : null
      if (err?.isUnauthorized) unauthorized.value = true
      else if (err?.code !== 'B000317') actionError.value = notificationErrorMessage(e)
    } finally {
      marking.value = marking.value.filter((id) => id !== notificationId)
    }
  }

  /**
   * 全部标为已读：同样是"乐观 + 回滚"。
   * 本地只覆盖**已加载的**条目，但未读数直接归零（后端是把全部未读一次刷掉）。
   */
  async function markAll() {
    if (allBusy.value) return
    const nothingUnread =
      unreadCount.value === 0 && !items.value.some((n) => notificationUnread(n.readFlag))
    if (nothingUnread) return

    const before = items.value.map((n) => ({ id: n.notificationId, readFlag: n.readFlag }))
    const beforeUnread = unreadCount.value
    items.value.forEach((n) => {
      n.readFlag = 1
    })
    unreadCount.value = 0
    allBusy.value = true
    actionError.value = ''
    try {
      await markAllNotificationsRead()
      broadcastRead()
    } catch (e) {
      for (const b of before) {
        const item = items.value.find((n) => n.notificationId === b.id)
        if (item) item.readFlag = b.readFlag
      }
      unreadCount.value = beforeUnread
      const err = e instanceof ApiError ? e : null
      if (err?.isUnauthorized) unauthorized.value = true
      else actionError.value = notificationErrorMessage(e)
    } finally {
      allBusy.value = false
    }
  }

  return {
    // 列表
    items,
    loading,
    loadingMore,
    error,
    moreError,
    hasMore,
    cursor,
    loaded,
    isEmpty,
    unauthorized,
    // 未读数
    unreadCount,
    unreadLoading,
    unreadError,
    // 筛选
    filter,
    filtered,
    setFilter,
    // 写操作
    marking,
    isMarking,
    actionError,
    allBusy,
    markRead,
    markAll,
    // 生命周期
    load,
    loadMore,
    refreshUnread,
    reset,
  }
}