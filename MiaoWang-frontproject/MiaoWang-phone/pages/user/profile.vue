<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页自绘返回栏（照 pages/community/detail.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">用户主页</text>
			<text class="nav__refresh" @click="refresh">刷新</text>
		</view>

		<view class="body">
			<!-- 未登录 / 会话过期：软门槛，先给下一步。社区接口经网关一律要登录身份，匿名请求必被拦 -->
			<view v-if="needLogin" class="state">
				<text class="state__title">{{ sessionExpired ? '登录已过期' : '先登录再看主页' }}</text>
				<text class="state__desc">社区接口经过网关时一律要带登录身份，登录后主页资料、他的帖子和他的宠物都能读。</text>
				<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			</view>

			<!-- 没带 uid：uid 只能来自社区 DTO 或页面参数（user 模块没有按 uid 查的接口，见契约 §B.4） -->
			<view v-else-if="!uid" class="state">
				<text class="state__title">没拿到用户编号</text>
				<text class="state__desc">从社区帖子或评论里点作者名进来才会带 uid，直接打开这个地址读不到任何人的主页。</text>
				<view class="state__btn" @click="goCommunity"><text>返回社区</text></view>
			</view>

			<!-- 头部骨架（不是"加载中…"纯文字） -->
			<view v-else-if="profileLoading && !view" class="skeleton">
				<view class="skeleton__card"></view>
			</view>

			<!-- 头部失败：说清原因 + 重试 + 退路 -->
			<view v-else-if="profileError && !view" class="state">
				<text class="state__title">主页没读到</text>
				<text class="state__desc">{{ profileError }}</text>
				<view class="state__btn" @click="loadAll"><text>重试</text></view>
				<view class="state__ghost" @click="goCommunity"><text>返回社区</text></view>
			</view>

			<template v-else-if="view">
				<!-- 已有资料时刷新失败：行内报错 + 重试，不把读着的内容换成错误页 -->
				<view v-if="profileError" class="inline-err">
					<text class="inline-err__text">{{ profileError }}</text>
					<text class="inline-err__act" @click="loadProfile">重试</text>
				</view>

				<view class="hero">
					<view class="hero__head">
						<!-- 全站都没有头像字段（miaowang_user.avatar 全 null、社区 DTO 无该字段）→ 只画名字首字，不挂 image -->
						<view class="hero__disc"><text>{{ view.initial }}</text></view>
						<view class="hero__who">
							<text class="hero__name">{{ view.name }}</text>
							<text v-if="!view.hasName" class="hero__id">喵友编号 {{ view.uid }}</text>
						</view>
					</view>

					<view class="stats">
						<view v-for="s in view.stats" :key="s.key" class="stat">
							<text class="stat__num">{{ s.value }}</text>
							<text class="stat__label">{{ s.label }}</text>
						</view>
					</view>

					<!-- self=true 时不渲染关注按钮：接口对自己恒 followed=false，真源里没有"关注自己"这个动作 -->
					<view v-if="!view.isSelf" class="follow-row">
						<view
							class="follow"
							:class="{ 'follow--on': view.followed, 'follow--busy': view.followBusy }"
							@click="toggleFollow"
						>
							<text>{{ view.followBusy ? '提交中' : view.followText }}</text>
						</view>
					</view>
					<text v-else class="note">这是你自己的主页，所以不显示关注按钮。</text>

					<text v-if="followNote" class="flash">{{ followNote }}</text>
					<view v-if="followError" class="inline-err">
						<text class="inline-err__text">{{ followError }}</text>
						<text class="inline-err__act" @click="toggleFollow">重试</text>
					</view>
				</view>

				<!-- 分段：他的帖子 / 他点赞的 / 他的宠物（"他的评论"后端没有接口，见契约 §B.2.5） -->
				<view class="segs">
					<view
						v-for="t in segs"
						:key="t.key"
						class="seg"
						:class="{ 'seg--on': t.on }"
						@click="selectTab(t.key)"
					>
						<text>{{ t.label }}</text>
					</view>
				</view>
				<text class="seg-note">{{ segNote }}</text>

				<!-- 他的帖子 / 他点赞的：**游标分页**（nextCursor 原样回传，hasMore 只认响应字段） -->
				<template v-if="tab !== 'pet'">
					<view v-if="listLoading && !cards.length" class="skeleton">
						<view class="skeleton__card skeleton__card--short"></view>
						<view class="skeleton__card skeleton__card--short"></view>
					</view>
					<view v-else-if="listError && !cards.length" class="state">
						<text class="state__desc">{{ listError }}</text>
						<view class="state__btn" @click="reloadList"><text>重试</text></view>
					</view>
					<text v-else-if="!cards.length" class="hint">{{ emptyText }}</text>
					<template v-else>
						<view v-if="listError" class="inline-err">
							<text class="inline-err__text">{{ listError }}</text>
							<text class="inline-err__act" @click="reloadList">重试</text>
						</view>

						<view v-for="p in cards" :key="p.postId" class="post" @click="goPost(p.postId)">
							<text class="post__title">{{ p.title }}</text>
							<image v-if="p.hasCover" class="post__cover" :src="p.coverUrl" mode="aspectFill" />
							<view v-if="p.hasTopics" class="topics">
								<text v-for="(t, ti) in p.topics" :key="ti" class="topic">#{{ t }}</text>
							</view>
							<view class="post__foot">
								<text class="post__meta">{{ p.timeText }}</text>
								<text class="post__meta">{{ p.likeText }}</text>
								<text class="post__meta">{{ p.commentText }}</text>
								<text class="post__meta post__meta--dim">{{ p.viewText }}</text>
							</view>
						</view>

						<!-- 触底会自动加载；这里同样给可点入口，失败不静默 -->
						<view class="foot">
							<text v-if="listLoadingMore" class="foot__text">正在加载更多</text>
							<view v-else-if="listMoreError" class="foot__err">
								<text class="foot__err-text">{{ listMoreError }}</text>
								<text class="foot__act" @click="loadMore">重试</text>
							</view>
							<text v-else-if="hasMore" class="foot__act" @click="loadMore">加载更多</text>
							<text v-else class="foot__text">{{ endText }}</text>
						</view>
					</template>
				</template>

				<!-- 他的宠物：**裸数组、没有分页**（接口里唯一不带信封的那个） -->
				<template v-else>
					<view v-if="petsLoading && !petCards.length" class="skeleton">
						<view class="skeleton__card skeleton__card--short"></view>
					</view>
					<view v-else-if="petsError && !petCards.length" class="state">
						<text class="state__desc">{{ petsError }}</text>
						<view class="state__btn" @click="reloadList"><text>重试</text></view>
					</view>
					<text v-else-if="!petCards.length" class="hint">{{ emptyText }}</text>
					<template v-else>
						<view v-if="petsError" class="inline-err">
							<text class="inline-err__text">{{ petsError }}</text>
							<text class="inline-err__act" @click="reloadList">重试</text>
						</view>

						<view v-for="pet in petCards" :key="pet.pid" class="pet">
							<view class="pet__disc"><text>{{ pet.initial }}</text></view>
							<view class="pet__main">
								<text class="pet__name">{{ pet.name }}</text>
								<text class="pet__meta">{{ pet.breedText }}</text>
								<text class="pet__meta">{{ pet.ageText }}</text>
							</view>
							<view class="pet__tags">
								<text class="ptag">{{ pet.genderText }}</text>
								<text class="ptag">{{ pet.sterileText }}</text>
							</view>
						</view>
					</template>
				</template>
			</template>
		</view>
	</view>
</template>

<script>
/**
 * 用户主页（手机端 / Options API）
 *
 * 契约唯一来源：`.dsh-tmp/knowledge-userpage-contract.md` **§B**（88 次只读 GET 实测）；
 * 脱敏 fixture 在 `tools/userpage-fixtures/`，离线断言在 `tools/check-userpage-phone.mjs`。
 *
 * 后端零改动就能做出来：4 个「按用户查」的已有接口 + 关注写接口。四条硬事实决定这一页长什么样：
 *
 * 1. 🔴 **uid 只能从外面带进来**：`GET /user/{username}` 的 `id` 恒 null、user 模块**没有按 uid 查**
 *    → 页面参数 `?uid=` 是唯一入口（社区列表 / 详情 / 评论 / 回复的作者名都接上了这个入口）。
 *    没带 uid 就给"怎么进来"的说明，不留白屏。
 * 2. 🔴 **没有头像**：`miaowang_user.avatar` 全表 null、社区所有 DTO 都无头像字段 → 这里**只画名字首字**，
 *    **一个 `<image>` 都不挂**（挂了就是永远空的占位）。
 * 3. 🔴 **`username` 可为 null**（社区只从帖子表反查昵称 → 从没发过帖的人恒 null，实测含 uid=1）
 *    → 兜底走 `profileName` 的 **`喵友 #uid`**；**不写"匿名用户"**（后端从没返回过这个词）。
 * 4. 🔴 **"他的评论"做不了**（community 全部 11 个 controller 里没有任何 uid 维度的评论接口，
 *    探测 `/community/user/comment` 返回的是未映射路径的 `B000001`）→ 分段只有：他的帖子 / 他点赞的 / 他的宠物。
 *
 * 分页纪律（与 `pages/community/detail.vue` 同一套）：
 * - `post` / `like` 是**游标**（`{records,size,hasMore,nextCursor}`，**没有 total**）：`nextCursor` **原样回传**，
 *   "还有没有下一页"**只认响应里的 `hasMore`**。⚠️ 禁止 `records.length === size`：
 *   `size` 是**归一化后的请求值**（实测 `size=0` 回显 10、`999` 回显 50），`records` 也可能少于 size。
 * - `like` 的游标是 **`like_id`**（点赞明细主键）不是 `postId` → 更不能自己加减。
 * - "有更多但没有游标"是明确的死循环入口 → 收尾停住（`loadCursor` 里有兜底）。
 * - `pet` 是**裸数组、没有分页**，且 🔴 **uid 必须显式传**（不传恒 `[]` 且不报错，会静默显示"他还没有宠物"）。
 *
 * 关注按钮（写接口，**本端从未真调过**，后端也只有源码依据）：
 * - 初始态**只能取 `profile.followed`**（"是否已关注"没有独立接口，三条探测路径全是未映射的 `B000001`）
 * - `self=true` 时**不渲染按钮**，方法里再挡一层（杜绝"关注自己"打到写接口）
 * - 乐观更新 + 失败回滚 + 把后端的话显示出来；掉线（网关 `state:-1` / `B000310`）走既有 `needLogin` 软门槛，
 *   **不新造登录弹窗**
 *
 * 四态齐全：加载（骨架）/ 空（按分段给不同文案）/ 错（说清原因 + 重试）/ 未登录（去登录）；
 * 下拉刷新 + 触底加载沿用 `detail.vue` 的既有模式；模板只访问实例属性（卡片一律在 computed 里摊平）。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import {
	USER_CURSOR_SIZE,
	followUser,
	getUserProfile,
	listUserLikes,
	listUserPets,
	listUserPosts,
	unfollowUser
} from '@/api/community.js'
import { communityErrorMessage, petCard, postCard, profileView } from '@/utils/community.js'

export default {
	data() {
		return {
			statusBarHeight: 0,
			/** 目标用户（页面参数；0 = 没带） */
			uid: 0,
			/** UserProfileRespDTO | null */
			profile: null,
			profileLoading: false,
			profileError: '',
			/**
			 * 关注按钮的**本地交互态**（接口 DTO 不回写，避免两处真源）
			 * `followed` 初值取 `profile.followed`（唯一真源）；`delta` 只作用在粉丝数上。
			 */
			followInter: { followed: null, busy: false, delta: 0 },
			followError: '',
			followNote: '',
			/** 'post' | 'like' | 'pet' */
			tab: 'post',
			/** 每个分段的原始 DTO 与游标分开存：切回来还能接着看，不必重新请求 */
			rawMap: { post: [], like: [] },
			cursorMap: { post: null, like: null },
			hasMoreMap: { post: false, like: false },
			loadedMap: { post: false, like: false, pet: false },
			listLoading: false,
			listLoadingMore: false,
			listError: '',
			listMoreError: '',
			/** PetBriefRespDTO[]（裸数组接口） */
			pets: [],
			petsLoading: false,
			petsError: '',
			/** 会话确实过期了（本地还有 token 但后端不认） */
			unauthorized: false
		}
	},
	computed: {
		loggedIn() {
			return auth.isLoggedIn()
		},
		sessionExpired() {
			return auth.isLoggedIn() && this.unauthorized
		},
		needLogin() {
			return !this.loggedIn || this.sessionExpired
		},
		/** 头部 → 模板字段（摊平在 utils 里，模板只 {{ }}，不调函数） */
		view() {
			return this.profile ? profileView(this.profile, this.followInter) : null
		},
		segs() {
			const current = this.tab
			return [
				{ key: 'post', label: '他的帖子', on: current === 'post' },
				{ key: 'like', label: '他点赞的', on: current === 'like' },
				{ key: 'pet', label: '他的宠物', on: current === 'pet' }
			]
		},
		/** 当前分段的帖子卡（复用社区列表那套摊平，不给主页另造一份口径） */
		cards() {
			return (this.rawMap[this.tab] || []).map(postCard)
		},
		petCards() {
			return this.pets.map(petCard)
		},
		/** "还有没有下一页"只认接口给的 hasMore */
		hasMore() {
			return !!this.hasMoreMap[this.tab]
		},
		emptyText() {
			if (this.tab === 'post') return '他还没有发过帖子'
			if (this.tab === 'like') return '他还没有点赞过帖子'
			return '他还没有添加宠物'
		},
		endText() {
			const n = (this.rawMap[this.tab] || []).length
			return n ? '一共 ' + n + ' 条，没有更多了' : ''
		},
		segNote() {
			if (this.tab === 'post') return '按发布时间倒序，一页 ' + USER_CURSOR_SIZE + ' 条，往下滑会自动加载更多。'
			if (this.tab === 'like') return '他点赞过的帖子。这个列表的游标是点赞记录的编号、不是帖子编号，所以顺序和帖子流不完全一样。'
			return '他的宠物档案。别人的宠物详情页打不开，所以这里只做展示。'
		}
	},
	onLoad(options) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		const raw = options && options.uid ? Number(options.uid) : 0
		this.uid = isNaN(raw) ? 0 : raw
		if (!this.uid) return
		// 未登录时不发请求（经网关必被拦），页面直接给软门槛
		if (!auth.isLoggedIn()) return
		this.loadAll()
	},
	onShow() {
		// 从登录页返回：这时才有身份，补一次加载
		if (this.uid && !this.profile && !this.profileLoading && this.loggedIn && !this.needLogin) {
			this.loadAll()
		}
	},
	onPullDownRefresh() {
		this.refresh()
	},
	onReachBottom() {
		this.loadMore()
	},
	methods: {
		/** 首屏 / 重试：资料头部与当前分段一起拉（互不阻塞，各自报错） */
		async loadAll() {
			if (!this.loggedIn) return
			this.unauthorized = false
			this.profileError = ''
			this.listError = ''
			this.listMoreError = ''
			await Promise.all([this.loadProfile(), this.loadList(true)])
		},

		async loadProfile() {
			if (!this.uid || !this.loggedIn) return
			this.profileLoading = true
			this.profileError = ''
			try {
				const p = await getUserProfile(this.uid)
				if (!p) {
					this.profile = null
					this.profileError = '接口这次没有返回主页资料，稍后重试一次'
					return
				}
				this.profile = p
				// 关注态只认接口给的 followed；粉丝数的乐观增量同步清零（重新以接口为准）
				this.followInter = { followed: !!p.followed, busy: false, delta: 0 }
				this.followError = ''
				this.followNote = ''
			} catch (e) {
				if (this.noteAuth(e)) return
				// 保留已经读到的那份资料（模板会用行内错误条提示刷新失败），首屏失败时 profile 本来就是 null
				this.profileError = communityErrorMessage(e)
			} finally {
				this.profileLoading = false
			}
		},

		/** 拉当前分段的列表：`pet` 走裸数组，`post` / `like` 走游标 */
		async loadList(reset) {
			if (!this.uid || !this.loggedIn) return
			if (this.tab === 'pet') return this.loadPets()
			return this.loadCursor(reset)
		},

		/**
		 * 游标分页
		 * @param {boolean} reset true = 第一页（首屏 / 切分段 / 重试），false = 用 `nextCursor` 续拉
		 */
		async loadCursor(reset) {
			const key = this.tab
			if (key !== 'post' && key !== 'like') return
			if (!reset && (this.cursorMap[key] === null || this.cursorMap[key] === undefined)) {
				// 没有游标就别硬拉（否则会把第一页又请求一遍）
				this.hasMoreMap[key] = false
				return
			}
			if (reset) {
				this.listLoading = true
				this.listError = ''
				this.rawMap[key] = []
				this.cursorMap[key] = null
				this.hasMoreMap[key] = false
				this.loadedMap[key] = false
			} else {
				if (this.listLoadingMore) return
				this.listLoadingMore = true
				this.listMoreError = ''
			}
			try {
				// `lastId` 一律原样回传游标（like 的游标是 like_id，不是 postId → 绝不自己算）
				const params = {
					uid: this.uid,
					lastId: reset ? undefined : this.cursorMap[key],
					size: USER_CURSOR_SIZE
				}
				const page = key === 'post' ? await listUserPosts(params) : await listUserLikes(params)
				const records = (page && page.records) || []
				this.rawMap[key] = reset ? records : (this.rawMap[key] || []).concat(records)
				// ⚠️ 只认响应里的 hasMore：size 是归一化后的**请求值**、records 也可能少于 size（跨服务 / 已删帖）
				this.hasMoreMap[key] = !!(page && page.hasMore)
				this.cursorMap[key] = page ? page.nextCursor : null
				// "说还有更多、却没给游标"是死循环入口 → 收尾停住
				if (this.hasMoreMap[key] && (this.cursorMap[key] === null || this.cursorMap[key] === undefined)) {
					this.hasMoreMap[key] = false
				}
				this.loadedMap[key] = true
			} catch (e) {
				if (this.noteAuth(e)) return
				const text = communityErrorMessage(e)
				if (reset) this.listError = text
				else this.listMoreError = text
			} finally {
				this.listLoading = false
				this.listLoadingMore = false
			}
		},

		/**
		 * 他的宠物：**裸数组接口，没有分页**
		 * 🔴 `uid` **必须显式传** —— 实测不传恒 `[]` 且**不报错**，会静默显示成"他还没有宠物"
		 */
		async loadPets() {
			if (!this.uid || !this.loggedIn) return
			this.petsLoading = true
			this.petsError = ''
			try {
				const rows = await listUserPets(this.uid)
				this.pets = Array.isArray(rows) ? rows : []
				this.loadedMap.pet = true
			} catch (e) {
				if (this.noteAuth(e)) return
				this.petsError = communityErrorMessage(e)
			} finally {
				this.petsLoading = false
			}
		},

		selectTab(key) {
			if (key === this.tab) return
			this.tab = key
			this.listError = ''
			this.listMoreError = ''
			// 已经加载过的分段直接用手里那份（切回来不白闪，也不重复请求）
			if (!this.loadedMap[key]) this.loadList(true)
		},

		reloadList() {
			this.loadList(true)
		},

		loadMore() {
			if (this.needLogin) return
			// 宠物是裸数组、没有分页
			if (this.tab === 'pet') return
			if (!this.hasMore || this.listLoading || this.listLoadingMore) return
			this.loadList(false)
		},

		async refresh() {
			if (!this.loggedIn) {
				uni.stopPullDownRefresh()
				return
			}
			this.unauthorized = false
			await this.loadAll()
			uni.stopPullDownRefresh()
		},

		/* ---------------- 关注 / 取消关注（写接口，**本端未实测**） ---------------- */

		/**
		 * 乐观更新：先改本地（含粉丝数 +1），失败再回滚，并把后端的话显示出来。
		 * `self=true` 时按钮本来就不渲染，这里再挡一层，杜绝"关注自己"打到写接口。
		 */
		async toggleFollow() {
			if (this.needLogin) {
				this.toast('先登录再关注')
				return
			}
			if (!this.uid || this.followInter.busy) return
			if (this.view && this.view.isSelf) return
			const next = !this.followInter.followed
			const beforeFollowed = this.followInter.followed
			const beforeDelta = this.followInter.delta
			this.followInter.followed = next
			// 关注成功后"他的粉丝 +1"是逻辑必然 → 一起乐观；取关同理回 0；失败一并回滚
			this.followInter.delta = next ? 1 : 0
			this.followInter.busy = true
			this.followError = ''
			this.followNote = ''
			try {
				if (next) await followUser(this.uid)
				else await unfollowUser(this.uid)
				this.followNote = next ? '已关注' : '已取消关注'
			} catch (e) {
				this.followInter.followed = beforeFollowed
				this.followInter.delta = beforeDelta
				const text = communityErrorMessage(e)
				this.followError = (next ? '关注没成功：' : '取消关注没成功：') + text
				// 写操作掉线要显式处理：光说"登录已过期"却不给下一步，用户会卡在原地反复点
				if (this.noteAuth(e)) {
					this.toast('登录已过期，请重新登录')
					return
				}
				this.toast(text)
			} finally {
				this.followInter.busy = false
			}
		},

		goPost(postId) {
			if (!postId) return
			uni.navigateTo({ url: '/pages/community/detail?postId=' + postId })
		},

		goBack() {
			const pages = getCurrentPages()
			if (pages.length > 1) uni.navigateBack()
			else uni.switchTab({ url: '/pages/community/community' })
		},

		goCommunity() {
			uni.switchTab({ url: '/pages/community/community' })
		},

		goLogin() {
			// 会话确实过期时先清本地会话，否则从登录页回来还是拿旧 token 反复被拒
			if (this.unauthorized) auth.clearSession()
			uni.navigateTo({ url: '/pages/login/login' })
		},

		toast(title) {
			uni.showToast({ title: title, icon: 'none' })
		},

		/** 掉线统一处理：切回软门槛，不摆一堆"请求失败" */
		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.profileError = ''
			this.listError = ''
			this.listMoreError = ''
			this.petsError = ''
			this.followError = ''
			this.profileLoading = false
			this.listLoading = false
			this.listLoadingMore = false
			this.petsLoading = false
			this.followInter.busy = false
			return true
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

/* ---- 顶部返回栏（自绘箭头，素材已废弃） ---- */
.nav {
	display: flex;
	align-items: center;
	padding: 0 $mw-space-3;
	background: $mw-surface;
	border-bottom: 1rpx solid $mw-line;
}
.nav__back {
	display: flex;
	align-items: center;
	justify-content: center;
	width: $mw-touch-min;
	height: $mw-touch-min;
}
.nav__title {
	flex: 1;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.nav__refresh {
	padding: 0 $mw-space-2;
	min-width: $mw-touch-min;
	text-align: right;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.chev {
	width: 20rpx;
	height: 20rpx;
	border-top: 4rpx solid $mw-ink-2;
	border-right: 4rpx solid $mw-ink-2;
}
.chev--left {
	transform: rotate(-135deg);
}

.body {
	padding: $mw-space-3 $mw-page-padding $mw-space-8;
	background: $mw-canvas;
	min-height: 100vh;
}

/* ---- 资料头部 ---- */
.hero {
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.hero__head {
	display: flex;
	align-items: center;
}
/* 全站没有头像字段 → 这里只是"名字首字"色块，不是头像占位（挂了 image 才是永远空的那张图） */
.hero__disc {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	background: rgba(194, 65, 12, 0.1);
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
}
.hero__disc text {
	font-size: 36rpx;
	font-weight: 700;
	color: $mw-brand-ink;
}
.hero__who {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-3;
	display: flex;
	flex-direction: column;
}
.hero__name {
	font-size: $mw-font-h1;
	font-weight: 700;
	line-height: 1.4;
	letter-spacing: -0.02em;
	color: $mw-ink;
}
.hero__id {
	margin-top: 4rpx;
	font-size: $mw-font-micro;
	color: $mw-muted;
	font-variant-numeric: tabular-nums;
}

/* ---- 四个计数：数字优先（tabular-nums，不跳动） ---- */
.stats {
	display: flex;
	margin-top: $mw-space-4;
	padding-top: $mw-space-3;
	border-top: 1px solid $mw-line;
}
.stat {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
}
.stat__num {
	font-size: $mw-font-h2;
	font-weight: 700;
	color: $mw-ink;
	font-variant-numeric: tabular-nums;
}
.stat__label {
	margin-top: 4rpx;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}

/* ---- 关注按钮（触控 ≥ $mw-touch-min） ---- */
.follow-row {
	margin-top: $mw-space-3;
}
.follow {
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	border: 1px solid $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.follow text {
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-brand-ink;
}
.follow--on {
	background: $mw-surface;
	border-color: $mw-line;
}
.follow--on text {
	color: $mw-ink-2;
}
.follow--busy {
	opacity: 0.6;
}
.note {
	display: block;
	margin-top: $mw-space-3;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-muted;
}
.flash {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-success;
}

/* ---- 分段控件 ---- */
.segs {
	display: flex;
	margin-top: $mw-space-4;
	padding: 6rpx;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
}
.seg {
	flex: 1;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.seg text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink-2;
}
.seg--on {
	background: $mw-brand-ink;
}
.seg--on text {
	color: #ffffff;
}
.seg-note {
	display: block;
	margin: $mw-space-2 0 $mw-space-3;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-ink-2;
}

/* ---- 帖子卡 ---- */
.post {
	padding: $mw-space-3 $mw-space-4;
	margin-bottom: $mw-space-2;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.post__title {
	display: block;
	font-size: $mw-font-body;
	font-weight: 600;
	line-height: 1.5;
	color: $mw-ink;
}
.post__cover {
	display: block;
	width: 100%;
	height: 320rpx;
	margin-top: $mw-space-2;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
}
.topics {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-2;
}
.topic {
	margin-right: $mw-space-1;
	margin-bottom: 6rpx;
	padding: 4rpx 14rpx;
	border-radius: $mw-radius-tag;
	background: rgba(74, 118, 56, 0.12);
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-success;
}
.post__foot {
	display: flex;
	flex-wrap: wrap;
	margin-top: $mw-space-2;
}
.post__meta {
	margin-right: $mw-space-3;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
	font-variant-numeric: tabular-nums;
}
.post__meta--dim {
	margin-right: 0;
	color: $mw-muted;
}

/* ---- 宠物卡 ---- */
.pet {
	display: flex;
	align-items: center;
	padding: $mw-space-3 $mw-space-4;
	margin-bottom: $mw-space-2;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.pet__disc {
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	background: rgba(74, 118, 56, 0.12);
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
}
.pet__disc text {
	font-size: 32rpx;
	font-weight: 700;
	color: $mw-success;
}
.pet__main {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-3;
	display: flex;
	flex-direction: column;
}
.pet__name {
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.pet__meta {
	margin-top: 4rpx;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.pet__tags {
	flex: none;
	display: flex;
	flex-direction: column;
	align-items: flex-end;
	margin-left: $mw-space-2;
}
.ptag {
	margin-bottom: 6rpx;
	padding: 4rpx 14rpx;
	border-radius: $mw-radius-tag;
	background: $mw-canvas;
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-ink-2;
}

/* ---- 四态 ---- */
.skeleton__card {
	height: 260rpx;
	margin-bottom: $mw-space-3;
	border-radius: $mw-radius-card;
	background: $mw-surface;
	border: 1px solid $mw-line;
	animation: mw-breathe 1.4s ease-in-out infinite;
}
.skeleton__card--short {
	height: 160rpx;
}
@keyframes mw-breathe {
	0% { opacity: 0.55; }
	50% { opacity: 1; }
	100% { opacity: 0.55; }
}
.hint {
	display: block;
	padding: $mw-space-3 0;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.state {
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.state__title {
	display: block;
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
}
.state__desc {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink-2;
}
.state__btn {
	margin-top: $mw-space-3;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.state__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}
.state__ghost {
	margin-top: $mw-space-2;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	border: 1px solid $mw-line;
	display: flex;
	align-items: center;
	justify-content: center;
}
.state__ghost text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.inline-err {
	display: flex;
	align-items: center;
	margin-top: $mw-space-2;
	padding: $mw-space-2;
	background: $mw-surface;
	border: 1px solid rgba(229, 72, 77, 0.3);
	border-radius: $mw-radius-input;
}
.inline-err__text {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-danger;
}
.inline-err__act {
	margin-left: $mw-space-2;
	padding: 0 $mw-space-2;
	min-width: $mw-touch-min;
	text-align: right;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* ---- 底部加载更多 ---- */
.foot {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: $mw-space-3 0;
}
.foot__text {
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.foot__act {
	padding: $mw-space-2 $mw-space-4;
	min-height: $mw-touch-min;
	line-height: 56rpx;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.foot__err {
	display: flex;
	flex-direction: column;
	align-items: center;
	width: 100%;
}
.foot__err-text {
	font-size: $mw-font-micro;
	line-height: 1.6;
	text-align: center;
	color: $mw-danger;
}
</style>