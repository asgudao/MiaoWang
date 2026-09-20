<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页自绘返回栏（照 pages/notification/list.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">个人资料</text>
			<view
				v-if="canEdit"
				class="nav__act"
				:class="{ 'nav__act--busy': saving }"
				@click="toggleEdit"
			>
				<text>{{ editing ? '取消' : '编辑' }}</text>
			</view>
			<view v-else class="nav__pad"></view>
		</view>

		<view class="body">
			<!-- 未登录 / 会话过期：软门槛（本页所有接口都要登录身份，匿名必被网关拦） -->
			<view v-if="needLogin" class="state">
				<text class="state__title">{{ sessionExpired ? '登录已过期' : '先登录再看个人资料' }}</text>
				<text class="state__desc">昵称、手机号、邮箱这些只对本人可见，也只能改自己的。登录后再回来。</text>
				<view class="state__btn" @click="goLogin"><text>去登录</text></view>
			</view>

			<template v-else>
				<!-- 四态之一：加载骨架（不是"加载中…"纯文字） -->
				<view v-if="loading && !loaded" class="skeleton">
					<view class="skeleton__hero"></view>
					<view class="skeleton__line"></view>
					<view class="skeleton__line skeleton__line--short"></view>
				</view>

				<!-- 四态之二：首屏失败，说清原因 + 重试 -->
				<view v-else-if="error && !loaded" class="state">
					<text class="state__title">资料没读到</text>
					<text class="state__desc">{{ error }}</text>
					<view class="state__btn" @click="loadAll"><text>重试</text></view>
				</view>

				<template v-else>
					<!-- 写操作的行内反馈：乐观更新失败会回滚，并把后端的话原样显示出来 -->
					<view v-if="saveError" class="inline-err">
						<text class="inline-err__text">{{ saveError }}</text>
						<text class="inline-err__act" @click="clearSaveError">知道了</text>
					</view>
					<text v-else-if="note" class="flash">{{ note }}</text>

					<!-- 头像：没有真头像就显示显示名首字，绝不画一个假头像 -->
					<view class="hero">
						<view class="hero__avatar">
							<image v-if="avatarUrl" class="hero__img" :src="avatarUrl" mode="aspectFill" />
							<text v-else class="hero__initial">{{ initial }}</text>
						</view>
						<view class="hero__main">
							<text class="hero__name">{{ displayName }}</text>
							<text class="hero__sub">{{ avatarSub }}</text>
							<view
								class="hero__btn"
								:class="{ 'hero__btn--busy': avatarBusy }"
								@click="changeAvatar"
							>
								<text>{{ avatarBtnText }}</text>
							</view>
						</view>
					</view>
					<text class="hint">
						头像走 POST /user/avatar（multipart，字段名 file）。后端只收 jpg / jpeg / png / webp，单张不超过 5MB。
					</text>

					<!-- 可改的三项 -->
					<view class="card">
						<text class="card__title">基本资料</text>

						<view class="row">
							<text class="row__k">用户名</text>
							<text class="row__v num">{{ usernameText }}</text>
						</view>
						<text class="row__note">
							用户名改不了：资料接口要求请求体里的 username 与登录身份一致，传成别的会报 B000206。
						</text>

						<view class="row">
							<text class="row__k">显示名称</text>
							<input
								v-if="editing"
								v-model="form.realName"
								class="row__input"
								type="text"
								placeholder="还没填"
								:maxlength="32"
							/>
							<text v-else class="row__v">{{ realNameText }}</text>
						</view>

						<view class="row">
							<text class="row__k">宠物怎么称呼你</text>
							<input
								v-if="editing"
								v-model="form.ownerCallName"
								class="row__input"
								type="text"
								placeholder="还没填"
								:maxlength="32"
							/>
							<text v-else class="row__v">{{ callNameText }}</text>
						</view>

						<view class="row">
							<text class="row__k">邮箱</text>
							<input
								v-if="editing"
								v-model="form.mail"
								class="row__input"
								type="text"
								placeholder="还没填"
							/>
							<text v-else class="row__v">{{ mailText }}</text>
						</view>

						<view v-if="editing" class="actions">
							<view class="btn btn--ghost" @click="cancelEdit"><text>取消</text></view>
							<view
								class="btn btn--primary"
								:class="{ 'btn--busy': saving }"
								@click="saveProfile"
							>
								<text>{{ saving ? '保存中' : '保存' }}</text>
							</view>
						</view>
						<text v-else class="row__note">
							能改的只有这三项：显示名称、宠物怎么称呼你、邮箱。写接口是 PUT /user。
						</text>
					</view>

					<!-- 手机号：只读，且说清为什么只读 -->
					<view class="card">
						<text class="card__title">手机号</text>
						<view class="row">
							<text class="row__k">手机号</text>
							<text class="row__v num">{{ phoneText }}</text>
						</view>
						<text class="card__note">
							后端返回的本来就是脱敏号（137****0402 这种），本页不再二次打码，免得把星号又切一遍。
							换绑手机号要有新号的短信验证码（PUT /user/phone，scene=change_phone），本页还没接这条流程，
							所以这里只读，也不放一个点了没反应的"换绑"按钮。
						</text>
					</view>

					<!-- uid：来源单一，说清从哪来 -->
					<view class="card">
						<text class="card__title">账号</text>
						<view class="row">
							<text class="row__k">uid</text>
							<text class="row__v num">{{ uidText }}</text>
						</view>
						<text class="card__note">{{ uidNote }}</text>
					</view>

					<text class="foot">改完资料后，「我的」页顶部的显示名也会跟着变。</text>
				</template>
			</template>
		</view>
	</view>
</template>

<script>
/**
 * 个人资料（手机端）
 *
 * 这个页面**只做后端真有的能力**，逐条对着源码写（依据都在行内注释里）：
 *
 * | 能做什么 | 走哪个接口 | 依据 |
 * |---|---|---|
 * | 读自己的资料（手机号已脱敏） | GET /user/{username} | UserController.java:34 |
 * | 改显示名称 / 宠物称呼 / 邮箱 | PUT /user | UserController.java:68 + UserUpdateReqDTO.java:14 |
 * | 换头像 | POST /user/avatar（multipart，字段 file） | UserController.java:126 |
 * | 读 uid | GET /community/user/profile（不带 uid = 自己） | 实测 fixture cu-profile-nouid-self.json |
 *
 * **做不到、也没假装能做的**（都在页面上写清楚了，不是偷偷省略）：
 * - **用户名不能改**：`PUT /user` 的 body.username 必须等于登录身份，否则 B000206（UserServiceImpl.java:170）
 * - **手机号换绑**：接口存在（PUT /user/phone）但要新号短信验证码，本页没接这条流程 → 只读，不放死按钮
 * - **密码**：接口存在（PUT /user/password）但成功后会立刻踢掉当前 token，本页不涉及 → 不放入口
 * - **头像在没有值时**：显示显示名首字（复用 store/auth.js 的 initial()），**不画假头像、
 *   也不写"后端没有上传接口"这种假话**（那个接口是存在的）
 *
 * 登录态：手机端没有路由守卫，走既有页内软门槛（`needLogin` / `noteAuth`，与
 * pages/notification/list.vue、pages/user/profile.vue 同一套），**不新造登录弹窗**；
 * 点"去登录"那一下才 `auth.clearSession()`。
 *
 * 不新建 store：状态全在本页 data 里（顺带绕开 tools/check-stores.js 的账号级门禁）。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import { getMyProfile, updateMyProfile, uploadMyAvatar, profileErrorMessage } from '@/api/user.js'
import { getUserProfile } from '@/api/community.js'

/** 头像大小上限：与后端保持一致（MinioProperties.java:93 的扩展名 + A000602 的 5MB） */
const AVATAR_MAX_BYTES = 5 * 1024 * 1024

export default {
	data() {
		return {
			/** 全局 navigationStyle 是 custom，自绘返回栏要自己让开状态栏 */
			statusBarHeight: 0,
			/** GET /user/{username} 的原样返回（模板只读这里，不自己拼兜底之外的东西） */
			info: {},
			/** 社区侧读到的 uid（用户服务没有"按 uid 查"的接口，所以 uid 只能从这里来） */
			uid: null,
			uidError: '',
			loaded: false,
			loading: false,
			error: '',
			/** 编辑态：三项可改字段共用一个表单，保存时整份提交 */
			editing: false,
			form: { realName: '', ownerCallName: '', mail: '' },
			saving: false,
			saveError: '',
			note: '',
			avatarBusy: false,
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
		/** 手机端没有路由守卫：未登录 / 掉线都在页内切软门槛，一个请求都不发 */
		needLogin() {
			return !this.loggedIn || this.sessionExpired
		},
		/** 资料读到了才给"编辑"入口（读不到就别让改） */
		canEdit() {
			return this.loaded && !this.needLogin
		},
		displayName() {
			const i = this.info
			return (i.realName || i.username || auth.state.username || '').trim()
		},
		/**
		 * 没有头像就显示显示名首字。
		 * 复用 store/auth.js 的 `initial()`（同一个人名口径），页面已经拿到新资料时以页面为准。
		 * ⚠️ utils/pet.js 的 `initials()` 是**宠物**口径（吃 pet 对象、兜底给"宠"），这里不能用。
		 */
		initial() {
			return (this.displayName || auth.initial() || '我').slice(0, 1)
		},
		/** 真头像 URL：后端没给就是空串，模板据此决定画 image 还是画首字（不画假头像） */
		avatarUrl() {
			return (this.info && this.info.avatar) || ''
		},
		avatarSub() {
			return this.avatarUrl ? '头像已设置' : '还没有头像，先用显示名的首字代替'
		},
		avatarBtnText() {
			if (this.avatarBusy) return '上传中'
			return this.avatarUrl ? '换一张' : '选一张上传'
		},
		usernameText() {
			return this.info.username || auth.state.username || ''
		},
		realNameText() {
			return this.info.realName || '还没填'
		},
		callNameText() {
			return this.info.ownerCallName || '还没填'
		},
		mailText() {
			return this.info.mail || '还没填'
		},
		/**
		 * 手机号：后端 `UserRespDTO.phone` 上挂了 `PhoneDesensitizationSerializer`，返回的已经是脱敏号
		 * （实测 fixture：`137****0402`）。所以**不再二次打码**（会把星号又切一遍）；
		 * 只有拿到"看起来是完整 11 位数字"的号时才兜底打码，防上游哪天改成不脱敏。
		 */
		phoneText() {
			const p = (this.info && this.info.phone) || ''
			if (!p) return '还没填'
			if (/^\d{11}$/.test(p)) return p.slice(0, 3) + '****' + p.slice(-4)
			return p
		},
		uidText() {
			return this.uid === null || this.uid === undefined ? '这次没读到' : String(this.uid)
		},
		uidNote() {
			const base =
				'uid 取自社区的用户主页接口（GET /community/user/profile，不带 uid 就是自己）。' +
				'它只能从这里来：用户服务没有"按 uid 查"的接口，GET /user/{username} 里的 id 恒为 null。'
			return this.uidError ? base + ' ' + this.uidError : base
		}
	},
	onLoad() {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
		// 未登录时不发请求（经网关必被拦），页面直接给软门槛
		if (!auth.isLoggedIn()) return
		this.loadAll()
	},
	onShow() {
		// 从登录页返回：这时才有身份，补一次加载
		if (this.loggedIn && !this.needLogin && !this.loaded && !this.loading) this.loadAll()
	},
	methods: {
		/** 首屏：读资料。uid 是第二路读，失败了也不把整页变错误态 */
		async loadAll() {
			if (this.needLogin) return
			this.loading = true
			this.error = ''
			try {
				const info = await getMyProfile(auth.state.username)
				this.info = info || {}
				this.loaded = true
				this.syncForm()
				this.loadUid()
			} catch (e) {
				if (this.noteAuth(e)) return
				this.error = profileErrorMessage(e)
			} finally {
				this.loading = false
			}
		},
		/**
		 * uid 与计数在社区侧，单发一路。
		 * ⚠️ 这一路失败**不降级整页**：昵称、手机号、邮箱都已经读到了，
		 *    不能因为 uid 没读到就把一整页换成错误态；如实说"这次没读到"就够了。
		 */
		async loadUid() {
			try {
				const p = await getUserProfile()
				this.uid = p && p.uid !== undefined && p.uid !== null ? p.uid : null
				this.uidError = this.uid === null ? '这次返回里没有 uid。' : ''
			} catch (e) {
				this.uid = null
				this.uidError = e instanceof ApiError && e.isUnauthorized
					? '这次没读到（登录态可能已经过期）。'
					: '这次没读到：' + profileErrorMessage(e)
			}
		},
		/** 把接口值灌进表单（进入编辑态时用，取消时也要回灌） */
		syncForm() {
			this.form = {
				realName: (this.info && this.info.realName) || '',
				ownerCallName: (this.info && this.info.ownerCallName) || '',
				mail: (this.info && this.info.mail) || ''
			}
		},
		toggleEdit() {
			if (!this.canEdit || this.saving) return
			if (this.editing) {
				this.cancelEdit()
				return
			}
			this.syncForm()
			this.saveError = ''
			this.note = ''
			this.editing = true
		},
		cancelEdit() {
			// 取消 = 丢掉未保存的输入，回到接口值
			this.syncForm()
			this.editing = false
			this.saveError = ''
		},
		clearSaveError() {
			this.saveError = ''
		},
		/**
		 * 保存资料：**乐观更新 + 失败回滚 + 显示后端文案**。
		 *
		 * 顺序刻意如此：先把新值落到界面（用户立刻看到结果），再发请求；
		 * 请求失败就把三份字段整份回滚回改前的值，并把后端的 message 原样显示出来
		 * （后端这些 message 就是中文，如「只能改自己的资料」）。
		 *
		 * ⚠️ `username` 必须原样带上：`PUT /user` 用它做"只能改自己"的校验（对不上报 B000206）。
		 */
		async saveProfile() {
			if (this.saving || !this.canEdit) return
			const before = {
				realName: (this.info && this.info.realName) || '',
				ownerCallName: (this.info && this.info.ownerCallName) || '',
				mail: (this.info && this.info.mail) || ''
			}
			const next = {
				realName: (this.form.realName || '').trim(),
				ownerCallName: (this.form.ownerCallName || '').trim(),
				mail: (this.form.mail || '').trim()
			}
			// 乐观更新
			this.info = Object.assign({}, this.info, next)
			this.editing = false
			this.saving = true
			this.saveError = ''
			this.note = ''
			try {
				await updateMyProfile({
					username: this.usernameText,
					realName: next.realName,
					ownerCallName: next.ownerCallName,
					mail: next.mail
				})
				this.note = '已保存'
				// 「我的」页顶部与首页问候都读 store，不刷新会跟前端显示不一致
				auth.fetchMe()
			} catch (e) {
				const authLost = this.noteAuth(e)
				// 失败回滚（整份回滚，不做字段级猜谜）
				this.info = Object.assign({}, this.info, before)
				this.syncForm()
				if (!authLost) {
					this.saveError = profileErrorMessage(e)
					this.editing = true
				}
				return
			} finally {
				this.saving = false
			}
		},
		/**
		 * 换头像：选图 → 本地先卡 5MB（省一次白传）→ 真上传 → 用后端返回的 URL 更新界面。
		 * 失败**不动**现有头像（不把已经好的头像换成空白）。
		 */
		changeAvatar() {
			if (this.needLogin || this.avatarBusy) return
			const self = this
			uni.chooseImage({
				count: 1,
				sizeType: ['compressed', 'original'],
				sourceType: ['album', 'camera'],
				success(res) {
					const paths = (res && res.tempFilePaths) || []
					if (!paths.length) {
						self.saveError = '没有选到图片'
						return
					}
					const files = (res && res.tempFiles) || []
					const size = files[0] && files[0].size
					if (size && size > AVATAR_MAX_BYTES) {
						self.saveError = '这张图 ' + Math.round(size / 1024 / 1024) + 'MB，超过后端 5MB 的上限，换一张小点的'
						return
					}
					self.uploadAvatar(paths[0])
				},
				fail() {
					self.saveError = '没有选到图片（取消或没有相册权限）'
				}
			})
		},
		async uploadAvatar(filePath) {
			this.avatarBusy = true
			this.saveError = ''
			this.note = ''
			try {
				const url = await uploadMyAvatar(filePath)
				// 后端已经把这个 URL 写进库并返回，界面照实显示（乐观更新在这里没有意义：
				// URL 是后端生成的，猜不出来）
				this.info = Object.assign({}, this.info, { avatar: url })
				this.note = '头像已更新'
			} catch (e) {
				if (this.noteAuth(e)) return
				this.saveError = profileErrorMessage(e)
			} finally {
				this.avatarBusy = false
			}
		},
		goBack() {
			const pages = getCurrentPages()
			if (pages.length > 1) uni.navigateBack()
			else uni.switchTab({ url: '/pages/mine/mine' })
		},
		goLogin() {
			// 会话确实过期时先清本地会话，否则从登录页回来还是拿旧 token 反复被拒
			if (this.unauthorized) auth.clearSession()
			uni.navigateTo({ url: '/pages/login/login' })
		},
		/** 掉线统一处理：切回软门槛，不摆一堆"请求失败"红字（沿用既有 noteAuth 约定） */
		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.error = ''
			this.saveError = ''
			this.note = ''
			this.loading = false
			this.saving = false
			this.avatarBusy = false
			this.editing = false
			return true
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $mw-canvas;
}

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
.nav__act {
	display: flex;
	align-items: center;
	justify-content: flex-end;
	min-width: $mw-touch-min;
	min-height: $mw-touch-min;
	padding: 0 $mw-space-2;
}
.nav__act text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.nav__act--busy {
	opacity: 0.6;
}
/* 不编辑时右侧留一个与返回键等宽的占位，标题才是真的居中 */
.nav__pad {
	width: $mw-touch-min;
	height: $mw-touch-min;
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
}

/* ---- 头像块（深色，与首页舞台 / 我的页资料卡同族） ---- */
.hero {
	display: flex;
	align-items: center;
	padding: $mw-space-4;
	background: $mw-night;
	border-radius: $mw-radius-card;
}
.hero__avatar {
	width: 140rpx;
	height: 140rpx;
	border-radius: 50%;
	background: rgba(255, 138, 91, 0.16);
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
	overflow: hidden;
}
.hero__initial {
	font-size: 56rpx;
	font-weight: 700;
	color: $mw-brand;
}
.hero__img {
	width: 100%;
	height: 100%;
}
.hero__main {
	flex: 1;
	min-width: 0;
	margin-left: $mw-space-3;
	display: flex;
	flex-direction: column;
}
.hero__name {
	font-size: 40rpx;
	font-weight: 700;
	color: $mw-on-night;
}
.hero__sub {
	font-size: $mw-font-caption;
	color: $mw-on-night-2;
}
.hero__btn {
	margin-top: $mw-space-2;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	background: $mw-brand-ink;
	display: flex;
	align-items: center;
	justify-content: center;
}
.hero__btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: #ffffff;
}
.hero__btn--busy {
	opacity: 0.6;
}
.hint {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-muted;
}

/* ---- 内容卡 ---- */
.card {
	margin-top: $mw-space-3;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.card__title {
	display: block;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.card__note {
	display: block;
	margin-top: $mw-space-3;
	padding-top: $mw-space-3;
	border-top: 1px solid $mw-line;
	font-size: $mw-font-micro;
	line-height: 1.7;
	color: $mw-muted;
}

/* ---- 键值行 ---- */
.row {
	display: flex;
	align-items: center;
	min-height: $mw-touch-min;
	margin-top: $mw-space-2;
	border-bottom: 1px solid $mw-line;
}
.row__k {
	flex: none;
	width: 220rpx;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.row__v {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
	word-break: break-all;
}
.row__input {
	flex: 1;
	min-width: 0;
	height: $mw-touch-min;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.row__note {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	line-height: 1.6;
	color: $mw-muted;
}
.num {
	font-variant-numeric: tabular-nums;
}

/* ---- 编辑态按钮 ---- */
.actions {
	display: flex;
	gap: $mw-space-2;
	margin-top: $mw-space-3;
}
.btn {
	flex: 1;
	min-height: $mw-btn-h;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.btn text {
	font-size: $mw-font-body;
	font-weight: 600;
}
.btn--ghost {
	border: 1px solid $mw-line;
	background: transparent;
}
.btn--ghost text {
	color: $mw-ink;
}
.btn--primary {
	background: $mw-brand-ink;
}
.btn--primary text {
	color: #ffffff;
}
.btn--busy {
	opacity: 0.6;
}

/* ---- 四态 ---- */
.state {
	padding: $mw-space-6 $mw-space-4;
	display: flex;
	flex-direction: column;
	align-items: center;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.state__title {
	font-size: $mw-font-h2;
	font-weight: 700;
	color: $mw-ink;
}
.state__desc {
	margin: $mw-space-2 0 $mw-space-4;
	font-size: $mw-font-caption;
	line-height: 1.7;
	text-align: center;
	color: $mw-ink-2;
}
.state__btn {
	min-width: 320rpx;
	min-height: $mw-touch-min;
	padding: 0 $mw-space-4;
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
.skeleton {
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}
.skeleton__hero {
	height: 140rpx;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
}
.skeleton__line {
	height: 28rpx;
	margin-top: $mw-space-3;
	border-radius: $mw-radius-tag;
	background: $mw-canvas;
}
.skeleton__line--short {
	width: 55%;
}
.inline-err {
	display: flex;
	align-items: center;
	margin-bottom: $mw-space-3;
	padding: $mw-space-3;
	background: rgba(229, 72, 77, 0.08);
	border: 1px solid rgba(229, 72, 77, 0.2);
	border-radius: $mw-radius-input;
}
.inline-err__text {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-caption;
	line-height: 1.6;
	color: $mw-danger;
}
.inline-err__act {
	flex: none;
	margin-left: $mw-space-2;
	padding: 0 $mw-space-1;
	min-height: $mw-touch-min;
	display: flex;
	align-items: center;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink-2;
}
.flash {
	display: block;
	margin-bottom: $mw-space-3;
	font-size: $mw-font-caption;
	color: $mw-success;
}
.foot {
	display: block;
	margin-top: $mw-space-4;
	text-align: center;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
</style>