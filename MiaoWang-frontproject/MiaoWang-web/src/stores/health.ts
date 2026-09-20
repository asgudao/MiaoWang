/**
 * 健康记录 store（Web）
 *
 * 只放**跨页面共享**的东西：当前宠物 pid、概览 `overview`、疫苗/驱虫全量列表（逾期提醒要用）、
 * 以及两个付费墙动作（导出 / AI 解读）的状态。六类记录的列表与增删改留在各自页面里，
 * 因为它们互不共享，而且每类的列与筛选都不一样。
 *
 * 三个刻意的设计：
 * 1) **概览要同时拉 `/{pid}/preventive` 全量**：`overview.nextPreventive` 排除了逾期项
 *    （后端条件是 `.ge(nextDueAt, now)`），所以"逾期提醒"必须自己算。
 * 2) **付费墙不当错误**：`B000403/B000404/B000411/B000412` 收进 `gate`（正常的权益边界），
 *    界面给升级引导；只有真正的故障才进 `error`。**不自动重试**（AI 解读每点一次都真烧 token）。
 * 3) 读操作把错误收进 `error`，写操作抛出去让表单就地显示（与 `stores/pets.ts` 同风格）。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as healthApi from '../api/health'
import type {
  HealthAiAnalysis,
  HealthGate,
  HealthOverview,
  HealthRecordType,
  PreventiveRecord,
} from '../api/health'
import {
  healthErrorMessage,
  healthGateOf,
  mostUrgentPreventive,
  overdueLabel,
  overduePreventives,
  recordCountText,
  saveJsonFile,
} from '../api/health'
import { ApiError } from '../api/request'

export const useHealthStore = defineStore('health', () => {
  /** 当前正在看的宠物（页面切换 pid 时会重新拉） */
  const pid = ref<number | null>(null)
  const overview = ref<HealthOverview | null>(null)
  /** 疫苗/驱虫全量（概览的逾期提醒 + 疫苗页共用同一份） */
  const preventive = ref<PreventiveRecord[]>([])

  const loading = ref(false)
  /** 是否成功拉取过一次（区分"还没拉"和"拉到了空数据"） */
  const loaded = ref(false)
  const error = ref('')
  /** 任何一次请求被判成"未登录"就翻到登录态，不再显示半截页面 */
  const unauthorized = ref(false)

  /** 付费墙 / 前置条件：**正常状态**，不是错误 */
  const gate = ref<HealthGate | null>(null)
  /** 导出成功后的提示（后端只给 JSON，没有文件流） */
  const notice = ref('')

  const exporting = ref(false)
  const aiLoading = ref(false)
  const ai = ref<HealthAiAnalysis | null>(null)

  /* ---------- 派生状态 ---------- */

  const petName = computed(() => overview.value?.petName ?? '')
  const species = computed(() => overview.value?.species ?? '')

  /** 已逾期的疫苗/驱虫（`nextDueDays < 0`），最紧急在前 */
  const overdueList = computed(() => overduePreventives(preventive.value))
  /** 全部预防记录里最紧急的一条（**含逾期**，这是 overview.nextPreventive 给不了的） */
  const mostUrgent = computed(() => mostUrgentPreventive(preventive.value))
  const mostUrgentLabel = computed(() => overdueLabel(mostUrgent.value?.nextDueDays ?? null))
  const hasOverdue = computed(() => overdueList.value.length > 0)

  const recordLimit = computed(() => overview.value?.recordLimit ?? null)
  const totalRecords = computed(() => Number(overview.value?.totalRecords ?? 0))
  const totalText = computed(() => recordCountText(totalRecords.value, recordLimit.value))
  /** 额度用满了没有（不限档位恒 false） */
  const limitReached = computed(() => (overview.value?.limitReachedTypes?.length ?? 0) > 0)

  /** 某类记录的条数（六个 key 后端永远都给） */
  function countOf(type: HealthRecordType): number {
    return Number(overview.value?.recordCounts?.[type] ?? 0)
  }

  /** 某类记录的"3 / 100 条"文案 */
  function countTextOf(type: HealthRecordType): string {
    return recordCountText(countOf(type), recordLimit.value)
  }

  /* ---------- 读 ---------- */

  /** 未登录是"要登录"，其他错误才当错误显示，两者别混成一个红框 */
  function noteError(e: unknown): string {
    if (e instanceof ApiError && e.isUnauthorized) {
      unauthorized.value = true
      return '登录状态失效了，请重新登录'
    }
    return healthErrorMessage(e)
  }

  /**
   * 拉概览 + 疫苗全量。
   * 两个请求互不依赖，用 `allSettled` 而不是 `all`：概览成功、疫苗失败时不该把概览一起丢掉。
   */
  async function load(targetPid: number, force = false) {
    if (pid.value === targetPid && loaded.value && !force) return
    pid.value = targetPid
    loading.value = true
    error.value = ''
    gate.value = null
    notice.value = ''
    const [ov, pv] = await Promise.allSettled([
      healthApi.getOverview(targetPid),
      healthApi.listPreventive(targetPid),
    ])
    let firstError: unknown = null
    if (ov.status === 'fulfilled') {
      overview.value = ov.value
      loaded.value = true
    } else {
      firstError = ov.reason
    }
    if (pv.status === 'fulfilled') {
      preventive.value = Array.isArray(pv.value) ? pv.value : []
    } else if (!firstError) {
      firstError = pv.reason
    }
    if (firstError) error.value = noteError(firstError)
    loading.value = false
  }

  /** 写操作之后刷新概览与逾期提醒（列表由各页面自己重拉） */
  async function refresh() {
    if (pid.value === null) return
    await load(pid.value, true)
  }

  /** 只重拉疫苗/驱虫全量（疫苗页用了筛选后要恢复全量） */
  async function reloadPreventive() {
    if (pid.value === null) return
    try {
      const list = await healthApi.listPreventive(pid.value)
      preventive.value = Array.isArray(list) ? list : []
    } catch (e) {
      error.value = noteError(e)
    }
  }

  /* ---------- 两个付费墙动作（成功返回体未实测，见 api/health.ts 头部） ---------- */

  /**
   * 导出健康档案。档位不够时后端回 `B000404` → 收进 `gate`，界面给升级引导。
   * 成功时后端给的是结构化 JSON，前端自己序列化成文件下载。
   */
  async function exportReport() {
    if (exporting.value || pid.value === null) return
    exporting.value = true
    gate.value = null
    error.value = ''
    notice.value = ''
    try {
      const data = await healthApi.exportHealth(pid.value)
      const name = `健康档案-${petName.value || `宠物${pid.value}`}-${new Date().toISOString().slice(0, 10)}.json`
      notice.value = saveJsonFile(name, data)
        ? '健康档案已导出为 JSON 文件'
        : '已经拿到档案数据，但当前环境不能自动下载文件'
    } catch (e) {
      const g = healthGateOf(e)
      if (g) gate.value = g
      else error.value = noteError(e)
    } finally {
      exporting.value = false
    }
  }

  /**
   * AI 症状解读。
   * ⚠️ 每次都会真的调大模型（十几秒），失败不重试；进行中按钮必须禁用，避免连点重复烧 token。
   */
  async function analyze() {
    if (aiLoading.value || pid.value === null) return
    aiLoading.value = true
    gate.value = null
    error.value = ''
    notice.value = ''
    try {
      ai.value = await healthApi.runAiAnalysis(pid.value)
    } catch (e) {
      const g = healthGateOf(e)
      if (g) gate.value = g
      else error.value = noteError(e)
    } finally {
      aiLoading.value = false
    }
  }

  function dismissGate() {
    gate.value = null
  }

  function dismissNotice() {
    notice.value = ''
  }

  /** 退出登录 / 换账号时清空，避免下一个账号看到上一个人的健康数据 */
  function reset() {
    pid.value = null
    overview.value = null
    preventive.value = []
    loading.value = false
    loaded.value = false
    error.value = ''
    unauthorized.value = false
    gate.value = null
    notice.value = ''
    ai.value = null
    aiLoading.value = false
    exporting.value = false
  }

  return {
    pid,
    overview,
    preventive,
    loading,
    loaded,
    error,
    unauthorized,
    gate,
    notice,
    exporting,
    aiLoading,
    ai,
    petName,
    species,
    overdueList,
    mostUrgent,
    mostUrgentLabel,
    hasOverdue,
    recordLimit,
    totalRecords,
    totalText,
    limitReached,
    countOf,
    countTextOf,
    load,
    refresh,
    reloadPreventive,
    exportReport,
    analyze,
    dismissGate,
    dismissNotice,
    reset,
  }
})