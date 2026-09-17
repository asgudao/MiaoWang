/**
 * 宠物档案 store（Web）
 *
 * 读操作把错误收进 `error`（页面展示错误态 + 重试），
 * 写操作**抛出去**让表单就地显示错误（新建/改名失败必须贴着输入框说，不能只给个全局提示）。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as petApi from '../api/pet'
import type { Pet, PetUpsertBody } from '../api/pet'
import { petErrorMessage } from '../utils/pet'

export const usePetsStore = defineStore('pets', () => {
  const list = ref<Pet[]>([])
  const loading = ref(false)
  /** 是否成功拉取过一次（用来区分"还没拉"和"拉到了空列表"） */
  const loaded = ref(false)
  const error = ref('')
  /** 写操作进行中：按钮禁用防重复提交 */
  const busy = ref(false)

  const count = computed(() => list.value.length)

  function byId(pid: number): Pet | null {
    return list.value.find((p) => p.pid === pid) ?? null
  }

  async function fetchMine(force = false) {
    if (loading.value) return
    if (loaded.value && !force) return
    loading.value = true
    error.value = ''
    try {
      list.value = await petApi.listMyPets()
      loaded.value = true
    } catch (e) {
      error.value = petErrorMessage(e)
    } finally {
      loading.value = false
    }
  }

  /** 新建：后端直接回带 pid 的完整对象，直接塞进列表，不用再拉一次 */
  async function create(body: PetUpsertBody): Promise<Pet> {
    busy.value = true
    try {
      const pet = await petApi.createPet(body)
      list.value = [...list.value, pet]
      loaded.value = true
      return pet
    } catch (e) {
      throw new Error(petErrorMessage(e))
    } finally {
      busy.value = false
    }
  }

  /** 修改：PUT 不回数据，所以本地按提交的字段打补丁（省一次列表请求） */
  async function update(pid: number, body: Partial<PetUpsertBody>): Promise<void> {
    busy.value = true
    try {
      await petApi.updatePet(pid, body)
      list.value = list.value.map((p) => (p.pid === pid ? { ...p, ...body } : p))
    } catch (e) {
      throw new Error(petErrorMessage(e))
    } finally {
      busy.value = false
    }
  }

  async function remove(pid: number): Promise<void> {
    busy.value = true
    try {
      await petApi.deletePet(pid)
      list.value = list.value.filter((p) => p.pid !== pid)
    } catch (e) {
      throw new Error(petErrorMessage(e))
    } finally {
      busy.value = false
    }
  }

  /** 退出登录时清空，避免下一个账号看到上一个人的宠物 */
  function reset() {
    list.value = []
    loaded.value = false
    error.value = ''
  }

  return {
    list,
    loading,
    loaded,
    error,
    busy,
    count,
    byId,
    fetchMine,
    create,
    update,
    remove,
    reset,
  }
})
