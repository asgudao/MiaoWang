/**
 * 六类记录列表页共用的"读 + 删"状态机（Web）
 *
 * 为什么值得抽出来：六个列表页的读/删/错误/骨架屏完全一样，只有"列怎么渲染"不同。
 * 这里只做三件事：拉列表、删一条、删完刷新概览（条数与逾期提醒要跟着变）。
 *
 * 三个约定：
 * - 读操作把错误收进 `error`（页面显示错误态 + 重试）；删除失败也收进 `error`（不弹窗）
 * - `busyId` 只锁住"正在删的那一条"，别的行不禁用（删 A 的同时还能看 B）
 * - 删除是后端的**逻辑删除**，成功后本地直接过滤掉，不整页重拉（少一次请求）
 */
import { ref, type Ref } from 'vue'
import { healthErrorMessage } from '../api/health'
import { useHealthStore } from '../stores/health'

export interface RecordListItem {
  id: number
}

export function useRecordList<T extends RecordListItem>(fetcher: (pid: number) => Promise<T[]>) {
  const health = useHealthStore()

  const items = ref([]) as Ref<T[]>
  /** 初始就是 true：首屏直接给骨架，不先闪一下"还没有记录"的空态 */
  const loading = ref(true)
  const error = ref('')
  const busyId = ref<number | null>(null)

  async function load(pid: number) {
    loading.value = true
    error.value = ''
    try {
      const list = await fetcher(pid)
      items.value = Array.isArray(list) ? list : []
    } catch (e) {
      error.value = healthErrorMessage(e)
    } finally {
      loading.value = false
    }
  }

  /** 删除一条；成功返回 true（页面据此决定要不要提示） */
  async function remove(id: number, deleter: (id: number) => Promise<unknown>): Promise<boolean> {
    busyId.value = id
    error.value = ''
    try {
      await deleter(id)
      items.value = items.value.filter((r) => r.id !== id)
      // 概览的条数、额度、逾期提醒都要跟着变（概览 + 疫苗全量两次请求）
      await health.refresh()
      return true
    } catch (e) {
      error.value = healthErrorMessage(e)
      return false
    } finally {
      busyId.value = null
    }
  }

  return { items, loading, error, busyId, load, remove }
}