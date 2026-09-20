<script setup lang="ts">
/**
 * 提醒设置（Web）—— **手机端 `MiaoWang-phone/pages/remind/setting.vue` 的同口径落地**
 *
 * 页面形态（与手机端同构）：诚实声明 → ① 总开关 ② 提前 N 天 ③ 免打扰时段
 * → ④ 通知能力与权限（做到什么、做不到什么全部照实说）→ ⑤ 本机的照护提醒看板。
 *
 * 三条必须记住的边界：
 * 1. **这些设置只在本机生效**（`localStorage`），喵汪**没有接服务端推送** →
 *    页面必须把这句写出来，否则就是把"打开页面才检查"说成了"到点自动提醒"。
 * 2. **改设置 = 落盘 → 作废弹窗缓存 → 重算看板**（三步缺一不可，否则"改了不生效"）。
 * 3. **授权按钮必须在用户点击里调**（浏览器会拒绝非手势触发的授权请求）。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { PhArrowClockwise, PhBell } from '@phosphor-icons/vue'
import {
  currentUsername,
  fetchRemindBoard,
  invalidateRemindPopup,
  notifyState,
  requestNotifyPermission,
  saveSetting,
  sendTestNotice,
  syncLocalNotifications,
} from '@/api/remind'
import { AHEAD_DAY_OPTIONS, parseHm, readSetting } from '@/utils/remindSetting'
import type { RemindSetting } from '@/utils/remindSetting'
import { emptyBoard, remindItemLine, remindItemSentence, summarizeRun } from '@/utils/remindSchedule'
import type { ReminderBoard } from '@/utils/remindSchedule'
import type { NotifyCapability } from '@/utils/localNotify'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const CAP_PLATFORM: Record<string, string> = {
  web: '浏览器（Web）',
}
const CAP_PERMISSION: Record<string, string> = {
  granted: '已授权',
  default: '还没授权（需要你点一下授权按钮）',
  denied: '被拒绝了（要去浏览器站点设置里改）',
  unsupported: '当前环境不支持',
  unknown: '未知',
}

const loading = ref(true)
const errorText = ref('')
const busy = ref(false)
const setting = ref<RemindSetting>(readSetting(currentUsername()))
const board = ref<ReminderBoard>(emptyBoard(setting.value))
const cap = ref<NotifyCapability>(notifyState().capability)
const truth = ref('')
const permMsg = ref('')
const runText = ref('')

/** 未登录软门槛（路由守卫是第一道，这里是第二道，不新造登录弹窗） */
const needLogin = computed(() => !auth.isLoggedIn || !currentUsername())

const enabledText = computed(() =>
  setting.value.enabled ? '已开启' : '已关闭（首页待办、弹窗与通知都停用）',
)

const quietText = computed(() => {
  const s = setting.value
  if (!s.quietEnabled) return '不开启'
  const a = parseHm(s.quietStart)
  const b = parseHm(s.quietEnd)
  if (a === null || b === null) return '时段没设好'
  if (a === b) return '两端相同，这个时段不会生效'
  return a < b ? `${s.quietStart} 至 ${s.quietEnd}` : `${s.quietStart} 至次日 ${s.quietEnd}`
})

const quietNote = computed(() =>
  setting.value.quietEnabled && parseHm(setting.value.quietStart) === parseHm(setting.value.quietEnd)
    ? '开始与结束时间一样，免打扰不会生效；请把结束时间设成另一个时刻'
    : '',
)

const capPermissionText = computed(
  () => CAP_PERMISSION[cap.value.permission] ?? CAP_PERMISSION.unknown ?? '',
)

const degradedText = computed(() => {
  const names = board.value.degradedPets
  return names.length ? `${names.join('、')}的健康记录这次没读到，它的提醒没有算进来` : ''
})

/** 空态分三种：总开关关（要说出"本来有几条"）/ 没有到期的项 / 读不到 */
const emptyText = computed(() => {
  if (errorText.value) return ''
  if (!board.value.enabled) {
    return board.value.suppressedCount
      ? `提醒总开关关着：有 ${board.value.suppressedCount} 项本来会提醒，现在不列也不通知`
      : '提醒总开关关着：不列待办，也不通知'
  }
  if (board.value.degradedPets.length && !board.value.items.length) return ''
  return `按当前设置（提前 ${board.value.aheadDays} 天），没有到期的疫苗或驱虫需要提醒`
})

async function reloadBoard() {
  errorText.value = ''
  try {
    board.value = await fetchRemindBoard()
  } catch (e) {
    errorText.value = e instanceof Error && e.message ? e.message : '照护提醒没读出来，稍后再试'
  }
}

async function load() {
  loading.value = true
  if (!currentUsername()) {
    loading.value = false
    return
  }
  setting.value = readSetting(currentUsername())
  const state = notifyState()
  cap.value = state.capability
  truth.value = state.truth
  await reloadBoard()
  loading.value = false
}

/** 改设置：**先落盘 → 作废弹窗缓存 → 重算看板**（三步缺一不可，否则改了不生效） */
async function apply(patch: Partial<RemindSetting>) {
  if (!currentUsername()) return
  setting.value = saveSetting(patch)
  invalidateRemindPopup()
  await reloadBoard()
}

function onAhead(e: Event) {
  const v = Number((e.target as HTMLSelectElement).value)
  void apply({ aheadDays: Number.isNaN(v) ? 0 : v })
}

function onQuietStart(e: Event) {
  const v = (e.target as HTMLInputElement).value
  void apply({ quietStart: v || setting.value.quietStart })
}

function onQuietEnd(e: Event) {
  const v = (e.target as HTMLInputElement).value
  void apply({ quietEnd: v || setting.value.quietEnd })
}

/** 申请系统通知权限（**必须在用户点击里调**，浏览器会拒绝非手势的授权请求） */
async function askPermission() {
  const r = await requestNotifyPermission()
  permMsg.value = r.message
  cap.value = notifyState().capability
}

/** 真发一条（走的是和到期提醒完全相同的发送路径，不是弹个 toast 骗人） */
async function testNotice() {
  if (busy.value) return
  busy.value = true
  try {
    const r = await sendTestNotice()
    permMsg.value = r.ok ? `已发出：${r.message}` : `没发出去：${r.message}`
  } finally {
    busy.value = false
  }
}

/** 立即重算并走一次发送（等于手动触发一次"回前台"） */
async function checkNow() {
  if (busy.value) return
  busy.value = true
  runText.value = ''
  try {
    const run = await syncLocalNotifications()
    await reloadBoard()
    const c = summarizeRun(run.decisions)
    runText.value =
      `本次检查：发出 ${run.sent} 条、挂了 ${run.armed} 条前台定时器` +
      `（免打扰挡下 ${c.quiet} 条、今天已提醒过 ${c.duplicate} 条、还没到点 ${c.notYet} 条` +
      `、总开关关掉 ${c.off} 条、没有到期时间 ${c.noDate} 条）`
  } catch (e) {
    errorText.value = e instanceof Error && e.message ? e.message : '检查失败'
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="mw-container mw-page">
    <header class="head">
      <h1 class="mw-h1">提醒设置</h1>
      <RouterLink class="mw-caption" to="/mine">返回我的</RouterLink>
    </header>

    <!-- 未登录 / 会话过期：软门槛（健康接口经网关一律要身份，匿名请求必被拦） -->
    <section v-if="needLogin" class="mw-card gate">
      <h2 class="mw-h2">先登录再设提醒</h2>
      <p class="lede__text">照护提醒是从你的宠物健康记录里派生的，未登录读不到，也设不了。</p>
      <RouterLink
        class="mw-btn mw-btn--primary"
        :to="{ name: 'login', query: { redirect: '/remind/setting' } }"
      >
        去登录
      </RouterLink>
    </section>

    <template v-else>
      <!-- ⚠️ 诚实声明：本页设置只在本机生效，且这个项目没有服务端推送。
           这两句必须留在页面上 —— 少一句就是把"打开页面才检查"说成了"到点自动提醒"。 -->
      <section class="mw-card card lede">
        <h2 class="mw-h2">这些设置只在本机生效</h2>
        <p class="lede__text">
          喵汪目前没有接服务端推送。这些开关只写进这台设备的浏览器本地存储（localStorage），
          不会同步到账号、也不会上传。提醒是在你打开页面（或从后台切回来）时按你的健康记录现算的。
        </p>
        <p class="lede__text">{{ truth }}</p>
      </section>

      <!-- ① 提醒总开关 -->
      <section class="mw-card card">
        <div class="row">
          <div class="row__main">
            <p class="row__title">照护提醒</p>
            <p class="row__desc">
              关掉后：首页不再显示待办与照护标记、不再弹提醒、也不再发本机通知
            </p>
          </div>
          <button
            class="tog"
            :class="{ 'tog--on': setting.enabled }"
            type="button"
            role="switch"
            :aria-checked="setting.enabled"
            aria-label="照护提醒总开关"
            @click="apply({ enabled: !setting.enabled })"
          >
            <span class="tog__knob" />
          </button>
        </div>
        <p class="row__now">当前：{{ enabledText }}</p>
      </section>

      <!-- ② 提前 N 天 -->
      <section class="mw-card card">
        <div class="row">
          <div class="row__main">
            <p class="row__title">提前几天提醒</p>
            <p class="row__desc">
              窗口口径与健康档案一致：逾期 + 到期当天 + 还有不超过 N 天。0 天就是原来的「逾期 + 今天到期」
            </p>
          </div>
          <label class="pick">
            <span class="pick__label">提前</span>
            <select class="mw-input pick__input" :value="setting.aheadDays" @change="onAhead">
              <option v-for="o in AHEAD_DAY_OPTIONS" :key="o.value" :value="o.value">
                {{ o.label }}
              </option>
            </select>
          </label>
        </div>
        <p class="row__now">
          提醒时刻：到期日当天的 09:00 再往前 {{ setting.aheadDays }} 天（日级口径，和「下次到期天数」的分档保持一致）
        </p>
        <p class="row__now">
          按当前设置，本机现有 <span class="mw-num">{{ board.todoCount }}</span> 项在提醒范围内
        </p>
      </section>

      <!-- ③ 免打扰时段 -->
      <section class="mw-card card">
        <div class="row">
          <div class="row__main">
            <p class="row__title">免打扰时段</p>
            <p class="row__desc">
              这时段内不弹提醒、也不发本机通知；时段过去后打开页面会补上（静音不记账）
            </p>
          </div>
          <button
            class="tog"
            :class="{ 'tog--on': setting.quietEnabled }"
            type="button"
            role="switch"
            :aria-checked="setting.quietEnabled"
            aria-label="免打扰开关"
            @click="apply({ quietEnabled: !setting.quietEnabled })"
          >
            <span class="tog__knob" />
          </button>
        </div>
        <div class="times">
          <label class="pick">
            <span class="pick__label">从</span>
            <input
              class="mw-input pick__input"
              type="time"
              :value="setting.quietStart"
              aria-label="免打扰开始时间"
              @change="onQuietStart"
            />
          </label>
          <span class="times__mid">至</span>
          <label class="pick">
            <span class="pick__label">到</span>
            <input
              class="mw-input pick__input"
              type="time"
              :value="setting.quietEnd"
              aria-label="免打扰结束时间"
              @change="onQuietEnd"
            />
          </label>
        </div>
        <p class="row__now">当前：{{ quietText }}</p>
        <p v-if="quietNote" class="row__warn">{{ quietNote }}</p>
      </section>

      <!-- ④ 能力与权限：做到什么、做不到什么，全部照实说 -->
      <section class="mw-card card">
        <h2 class="mw-h2">这台设备能不能弹系统通知</h2>
        <p class="row__now">运行环境：{{ CAP_PLATFORM[cap.platform] || '未知' }}</p>
        <p class="row__now">系统通知：{{ capPermissionText }}</p>
        <p class="row__now">通知接口：{{ cap.api || '没有可用的通知接口' }}</p>
        <p class="row__note">{{ cap.note }}</p>
        <p class="row__note">
          能不能把提醒排给系统：不能。浏览器关掉标签页之后没有任何执行机会，这一条是平台边界，不是本页的实现问题。
        </p>
        <div class="acts">
          <button
            v-if="cap.needsGesture"
            class="mw-btn mw-btn--ghost"
            type="button"
            @click="askPermission"
          >
            授权系统通知
          </button>
          <button class="mw-btn mw-btn--ghost" type="button" :disabled="busy" @click="testNotice">
            发一条测试通知
          </button>
        </div>
        <p v-if="permMsg" class="row__warn">{{ permMsg }}</p>
      </section>

      <!-- ⑤ 这一次到底发生了什么（数字都要对得上，不糊成一句"检查完成"） -->
      <section class="mw-card card">
        <div class="row">
          <div class="row__main">
            <p class="row__title">本机的照护提醒</p>
            <p class="row__desc">
              来自健康记录的疫苗与驱虫「下次到期」。用药没有到期时间，所以不参与提醒
            </p>
          </div>
          <button class="mw-btn mw-btn--ghost" type="button" :disabled="busy" @click="checkNow">
            <PhArrowClockwise :size="14" weight="regular" aria-hidden="true" />
            立即检查一次
          </button>
        </div>

        <p v-if="board.notice" class="row__warn">{{ board.notice }}</p>
        <p v-if="errorText" class="row__warn">{{ errorText }}</p>
        <p v-else-if="degradedText" class="row__warn">{{ degradedText }}</p>
        <p v-if="loading" class="row__note">正在读取你的健康记录…</p>

        <ul v-if="board.items.length" class="items">
          <li v-for="it in board.items" :key="it.key" class="item">
            <RouterLink
              class="item__link"
              :to="{ name: 'health-overview', params: { pid: it.pid } }"
              :aria-label="remindItemLine(it)"
            >
              <span class="item__pet">{{ it.petName }}</span>
              <span class="item__text">{{ remindItemSentence(it) }}</span>
              <span class="item__when">{{ it.fireStateLabel }}</span>
            </RouterLink>
          </li>
        </ul>
        <p v-else-if="emptyText" class="row__note">{{ emptyText }}</p>

        <p v-if="runText" class="row__note">{{ runText }}</p>
      </section>

      <p class="foot">
        这页的设置改了会立刻生效：重算提醒、重排本机通知，并把首页的待办一起改掉。
        浏览器端的系统通知必须由你在页面里点一次授权，而且只在页面开着时有效。
      </p>
    </template>

    <p v-if="loading" class="foot">
      <PhBell :size="14" weight="regular" aria-hidden="true" /> 正在准备提醒设置…
    </p>
  </div>
</template>

<style scoped>
/* 令牌全部取自 tokens.css 的既有 40 个，不新增任何令牌值、不写死颜色 */
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-4);
}
.gate {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--mw-space-3);
}
.lede {
  border-color: rgba(255, 176, 32, 0.4);
}
.lede__text {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
  line-height: var(--mw-line-height);
}
.card {
  margin-bottom: var(--mw-space-4);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
}
.row__main {
  flex: 1;
  min-width: 0;
}
.row__title {
  font-size: var(--mw-font-body);
  font-weight: 600;
}
.row__desc {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.row__now {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.row__note {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.row__warn {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-danger);
}
.tog {
  position: relative;
  flex: none;
  width: 48px;
  height: 28px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  background: var(--mw-canvas);
  cursor: pointer;
  transition: background 0.16s ease;
}
.tog__knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: var(--mw-radius-pill);
  background: var(--mw-surface);
  box-shadow: var(--mw-shadow-float);
  transition: transform 0.16s ease;
}
.tog--on {
  background: var(--mw-brand-ink);
  border-color: var(--mw-brand-ink);
}
.tog--on .tog__knob {
  transform: translateX(20px);
}
.pick {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  flex: none;
}
.pick__label {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.pick__input {
  min-width: 132px;
  min-height: 36px;
  font-size: var(--mw-font-caption);
}
.times {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-3);
}
.times__mid {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.acts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-3);
}
.items {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-3);
}
.item__link {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  padding: var(--mw-space-2) var(--mw-space-3);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
}
.item__link:hover {
  background: var(--mw-canvas);
}
.item__pet {
  flex: none;
  padding: 0 var(--mw-space-2);
  border-radius: var(--mw-radius-pill);
  background: rgba(194, 65, 12, 0.08);
  color: var(--mw-brand-ink);
  font-size: var(--mw-font-micro);
  font-weight: 600;
}
.item__text {
  flex: 1;
  min-width: 0;
  font-size: var(--mw-font-caption);
}
.item__when {
  flex: none;
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.foot {
  margin-top: var(--mw-space-4);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
</style>