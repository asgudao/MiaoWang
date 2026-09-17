/**
 * 宠物档案（手机端 store）
 *
 * 本工程不引 Pinia，用 reactive 单例。
 * ⚠️ 暴露的是普通对象，**模板里不能直接写 pets.list / pets.byId()** ——
 * Options API 的模板只认实例上的属性，必须先经 computed / methods 转一手
 * （2026-09-17 白屏事故就是这么来的，tools/check-phone.js 会静态拦住）。
 *
 * 读操作把错误收进 state.error（页面显示错误态 + 重试），
 * 写操作抛出去让表单就地显示错误。
 */
import { reactive } from 'vue'
import * as petApi from '../api/pet.js'
import { petErrorMessage } from '../utils/pet.js'

const state = reactive({
	list: [],
	loading: false,
	/** 是否成功拉取过一次（区分"还没拉"和"拉到了空列表"） */
	loaded: false,
	error: '',
	/** 写操作进行中，按钮禁用防重复提交 */
	busy: false
})

async function fetchMine (force) {
	if (state.loading) return
	if (state.loaded && !force) return
	state.loading = true
	state.error = ''
	try {
		state.list = (await petApi.listMyPets()) || []
		state.loaded = true
	} catch (e) {
		state.error = petErrorMessage(e)
	} finally {
		state.loading = false
	}
}

function byId (pid) {
	const id = Number(pid)
	for (let i = 0; i < state.list.length; i++) {
		if (Number(state.list[i].pid) === id) return state.list[i]
	}
	return null
}

/** 新建：后端回带 pid 的完整对象，直接塞进列表 */
async function create (body) {
	state.busy = true
	try {
		const pet = await petApi.createPet(body)
		state.list = state.list.concat([pet])
		state.loaded = true
		return pet
	} catch (e) {
		throw new Error(petErrorMessage(e))
	} finally {
		state.busy = false
	}
}

/** 修改：PUT 不回数据，本地按提交的字段打补丁（省一次列表请求） */
async function update (pid, body) {
	state.busy = true
	try {
		await petApi.updatePet(pid, body)
		const id = Number(pid)
		state.list = state.list.map(function (p) {
			if (Number(p.pid) !== id) return p
			return Object.assign({}, p, body)
		})
	} catch (e) {
		throw new Error(petErrorMessage(e))
	} finally {
		state.busy = false
	}
}

async function remove (pid) {
	state.busy = true
	try {
		await petApi.deletePet(pid)
		const id = Number(pid)
		state.list = state.list.filter(function (p) {
			return Number(p.pid) !== id
		})
	} catch (e) {
		throw new Error(petErrorMessage(e))
	} finally {
		state.busy = false
	}
}

/** 退出登录时清空，别让下一个账号看到上一个人的宠物 */
function reset () {
	state.list = []
	state.loaded = false
	state.error = ''
}

export const pets = {
	state: state,
	count () {
		return state.list.length
	},
	byId: byId,
	fetchMine: fetchMine,
	create: create,
	update: update,
	remove: remove,
	reset: reset
}