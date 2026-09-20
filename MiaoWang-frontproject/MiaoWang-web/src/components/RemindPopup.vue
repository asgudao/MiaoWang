<script setup lang="ts">
/**
 * 软件内提醒弹窗（**本切片的主通道**）
 *
 * 为什么它是主通道：浏览器**不可能**把提醒排给系统（关掉标签页没有执行机会），
 * 而软件内弹窗只要页面在前台就一定能显示。触发点是**进入站点 / 回到前台**
 * （`document.visibilitychange`），所以这条正好绕开了"关掉标签页做不到后台提醒"的死结。
 *
 * 三条必须记住的规矩：
 * 1. **弹不弹由 `api/remind.ts` 的 `fetchRemindPopup()` 决定**（它内部走
 *    `utils/remindSchedule.ts` 的 `decidePopup()` 唯一决策点：总开关 → 免打扰 →
 *    到点了没 → 同一条目同一天有没有弹过）。组件里**不许再判一套**。
 * 2. **记账时机**：只有弹窗**真的渲染出来且页面可见**时才 `markPopupShown()`。
 *    提前记账会让"系统通知兜底"那条路也被去重挡掉 → 那条提醒就彻底丢了。
 * 3. **去处是真的**：整行跳到该宠物的健康档案（`health-overview`，路径 `/pets/:pid/health`），
 *    不是死按钮；关闭按钮只改状态。
 *
 * 挂载点：`App.vue`（全局），所以在任何一个页面回前台都会检查一次。
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { PhBell, PhCaretRight, PhX } from '@phosphor-icons/vue'
import {
  closeRemindPopup,
  fetchRemindPopup,
  markPopupShown,
  remindPopupState,
  type RemindPopupSnapshot,
} from '@/api/remind'
import { remindItemLine, remindItemSentence } from '@/utils/remindSchedule'

const popup = ref<RemindPopupSnapshot>(remindPopupState())
const busy = ref(false)

/** 页面现在是不是可见的（后台标签页里不记账，理由见文件头第 2 条） */
function visible(): boolean {
  if (typeof document === 'undefined') return false
  return document.visibilityState !== 'hidden'
}

/** 这一行的真实去处：该宠物的健康档案 */
function targetOf(pid: string) {
  return { name: 'health-overview', params: { pid: String(pid) } }
}

/** 有没有一个可靠的 pid（没有就不给入口，绝不拿空字符串拼 URL） */
const canOpen = computed(() => popup.value.items.every((it) => !!it.pid))

async function check(force = false) {
  if (busy.value) return
  busy.value = true
  try {
    const snap = await fetchRemindPopup({ force })
    popup.value = snap
    if (!snap.show) return
    // 等这一帧真的渲染出来，再决定记不记账
    await nextTick()
    if (popup.value.show && visible()) markPopupShown()
  } finally {
    busy.value = false
  }
}

function dismiss() {
  popup.value = closeRemindPopup()
}

/** 回前台 / 首次进入站点时检查一次（复用窗口由 `POPUP_REUSE_MS` 合并） */
function onVisibility() {
  if (document.visibilityState === 'visible') void check()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && popup.value.show) dismiss()
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibility)
  document.addEventListener('keydown', onKeydown)
  void check()
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibility)
  document.removeEventListener('keydown', onKeydown)
})

defineExpose({ check, dismiss, popup })
</script>

<template>
  <div
    v-if="popup.show"
    class="mask"
    role="dialog"
    aria-modal="true"
    aria-labelledby="remind-popup-title"
  >
    <div class="box">
      <header class="head">
        <span class="head__icon" aria-hidden="true">
          <PhBell :size="18" weight="regular" />
        </span>
        <div class="head__main">
          <h2 id="remind-popup-title" class="head__title">{{ popup.title }}</h2>
          <p class="head__sub">{{ popup.subtitle }}</p>
        </div>
        <button class="close" type="button" aria-label="关闭提醒" @click="dismiss">
          <PhX :size="16" weight="regular" />
        </button>
      </header>

      <ul class="list">
        <li v-for="it in popup.items" :key="it.key" class="item">
          <RouterLink
            v-if="it.pid"
            class="item__link"
            :to="targetOf(it.pid)"
            :aria-label="remindItemLine(it)"
            @click="dismiss"
          >
            <span class="item__pet">{{ it.petName }}</span>
            <span class="item__text">{{ remindItemSentence(it) }}</span>
            <PhCaretRight class="item__go" :size="14" weight="regular" aria-hidden="true" />
          </RouterLink>
          <!-- 拿不到宠物 id 时**不给假入口**：如实说去哪儿看，不给死按钮 -->
          <p v-else class="item__plain">
            <span class="item__pet">{{ it.petName }}</span>
            <span class="item__text">{{ remindItemSentence(it) }}</span>
          </p>
        </li>
      </ul>

      <p v-if="!canOpen" class="note">
        这次没拿到宠物 id，请到健康档案里对着看一遍（不给点了没反应的入口）
      </p>
      <p v-if="popup.overflow > 0" class="note">
        另外还有 {{ popup.overflow }} 项没列出来，去提醒设置里能看到全部
      </p>

      <footer class="foot">
        <RouterLink class="mw-link" to="/remind/setting" @click="dismiss">提醒设置</RouterLink>
        <button class="mw-btn mw-btn--primary" type="button" @click="dismiss">知道了</button>
      </footer>

      <!-- 诚实降级：浏览器端**做不到**关掉页面还响，这句必须留在弹窗上 -->
      <p class="truth">浏览器端只在页面开着时提醒，关掉标签页就不会响</p>
    </div>
  </div>
</template>

<style scoped>
/* 令牌全部取自 tokens.css 的既有 40 个，**不新增任何令牌值**、不写死颜色 */
.mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: var(--mw-space-4);
  background: rgba(26, 19, 16, 0.72);
}
.box {
  width: 100%;
  max-width: 420px;
  padding: var(--mw-space-4);
  background: var(--mw-surface);
  border-radius: var(--mw-radius-card);
  box-shadow: var(--mw-shadow-float);
}
.head {
  display: flex;
  align-items: flex-start;
  gap: var(--mw-space-3);
}
.head__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: var(--mw-radius-tag);
  background: rgba(255, 176, 32, 0.16);
  color: var(--mw-accent-ink);
}
.head__main {
  flex: 1;
  min-width: 0;
}
.head__title {
  font-size: var(--mw-font-body);
  font-weight: 600;
  color: var(--mw-ink);
}
.head__sub {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.close {
  flex: none;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  background: var(--mw-surface);
  color: var(--mw-ink-2);
  cursor: pointer;
}
.close:hover {
  background: var(--mw-canvas);
}
.list {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  margin: var(--mw-space-4) 0;
}
.item__link,
.item__plain {
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
  color: var(--mw-ink);
}
.item__go {
  flex: none;
  color: var(--mw-muted);
}
.note {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-4);
}
.truth {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
</style>