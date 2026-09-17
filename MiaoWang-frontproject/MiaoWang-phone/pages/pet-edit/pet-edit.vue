<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，所以每个子页自绘返回栏 -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">{{ isEdit ? '编辑宠物' : '添加宠物' }}</text>
			<view class="nav__spacer"></view>
		</view>

		<view v-if="notFound" class="state">
			<text class="state__title">这只宠物不在你的档案里</text>
			<text class="state__desc">可能已经被删除了，没法编辑</text>
			<view class="btn btn--primary state__btn" @click="goBack"><text>返回</text></view>
		</view>

		<view v-else class="form">
			<view class="field">
				<text class="field__label">叫什么</text>
				<input
					class="field__input"
					type="text"
					:value="form.pName"
					placeholder="比如 喵喵"
					placeholder-class="field__ph"
					maxlength="20"
					@input="onNameInput"
				/>
				<text v-if="nameError" class="field__error">{{ nameError }}</text>
			</view>

			<view class="field">
				<text class="field__label">大类</text>
				<picker mode="selector" :range="species" :value="speciesIndex" @change="onSpecies">
					<view class="field__picker">
						<text>{{ form.species }}</text>
						<view class="chev"></view>
					</view>
				</picker>
				<text class="field__hint">必填，只能是后端白名单里的这几个</text>
			</view>

			<view class="field">
				<text class="field__label">具体品种</text>
				<input
					class="field__input"
					type="text"
					:value="form.specificSpecies"
					placeholder="比如 中华田园猫，可以不填"
					placeholder-class="field__ph"
					maxlength="30"
					@input="onSpeciesDetailInput"
				/>
			</view>

			<view class="field">
				<text class="field__label">它怎么称呼你</text>
				<input
					class="field__input"
					type="text"
					:value="form.ownerCallName"
					placeholder="比如 铲屎的，可以不填"
					placeholder-class="field__ph"
					maxlength="20"
					@input="onCallNameInput"
				/>
			</view>

			<view class="field">
				<text class="field__label">生日</text>
				<picker mode="date" :value="form.birth" start="1990-01-01" :end="today" @change="onBirth">
					<view class="field__picker">
						<text :class="{ 'is-ph': !form.birth }">{{ form.birth || '选日期' }}</text>
						<view class="chev"></view>
					</view>
				</picker>
				<text class="field__hint">填了才能算出年龄；不填就显示「生日未填」</text>
			</view>

			<view class="field">
				<text class="field__label">性别</text>
				<view class="seg">
					<view class="seg__item" :class="{ 'seg__item--on': form.gender === null }" @click="setGender(null)">
						<text>未填</text>
					</view>
					<view class="seg__item" :class="{ 'seg__item--on': form.gender === 0 }" @click="setGender(0)">
						<text>雌性</text>
					</view>
					<view class="seg__item" :class="{ 'seg__item--on': form.gender === 1 }" @click="setGender(1)">
						<text>雄性</text>
					</view>
				</view>
			</view>

			<view class="field">
				<text class="field__label">绝育</text>
				<view class="seg">
					<view class="seg__item" :class="{ 'seg__item--on': form.sterile === null }" @click="setSterile(null)">
						<text>未填</text>
					</view>
					<view class="seg__item" :class="{ 'seg__item--on': form.sterile === 0 }" @click="setSterile(0)">
						<text>未绝育</text>
					</view>
					<view class="seg__item" :class="{ 'seg__item--on': form.sterile === 1 }" @click="setSterile(1)">
						<text>已绝育</text>
					</view>
				</view>
				<text v-if="isEdit" class="field__hint">「未填」表示这次不改动这一项（后端只更新传了的字段）</text>
			</view>

			<view v-if="submitError" class="alert">
				<text>{{ submitError }}</text>
			</view>

			<view class="actions">
				<view class="btn btn--primary" :class="{ 'btn--busy': saving }" @click="onSubmit">
					<text>{{ saving ? '保存中' : isEdit ? '保存修改' : '添加宠物' }}</text>
				</view>
				<view class="btn btn--ghost" @click="goBack"><text>取消</text></view>
			</view>
		</view>
	</view>
</template>

<script>
import { pets } from '@/store/pets.js'
import { PET_SPECIES } from '@/api/pet.js'

export default {
	data() {
		return {
			pid: 0,
			isEdit: false,
			notFound: false,
			saving: false,
			nameError: '',
			submitError: '',
			statusBarHeight: 20,
			// 拷进 data：模板只认实例属性
			species: PET_SPECIES.slice(),
			form: {
				pName: '',
				species: '猫',
				specificSpecies: '',
				ownerCallName: '',
				birth: '',
				// null = 未填；提交时转成不传该字段
				gender: null,
				sterile: null
			}
		}
	},
	computed: {
		speciesIndex() {
			const i = this.species.indexOf(this.form.species)
			return i < 0 ? 0 : i
		},
		today() {
			const d = new Date()
			const m = d.getMonth() + 1
			const day = d.getDate()
			return d.getFullYear() + '-' + (m < 10 ? '0' + m : m) + '-' + (day < 10 ? '0' + day : day)
		}
	},
	onLoad(options) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 20
		} catch (e) {
			// 拿不到就用默认值，不该因此打不开页面
		}
		const pid = Number((options && options.pid) || 0)
		this.pid = pid
		this.isEdit = pid > 0
		if (!this.isEdit) return
		pets.fetchMine().then(() => {
			this.prefill()
		})
	},
	methods: {
		prefill() {
			const p = pets.byId(this.pid)
			if (!p) {
				this.notFound = true
				return
			}
			this.form.pName = p.pName || ''
			this.form.species = p.species || '猫'
			this.form.specificSpecies = p.specificSpecies || ''
			this.form.ownerCallName = p.ownerCallName || ''
			this.form.birth = p.birth || ''
			this.form.gender = typeof p.gender === 'number' ? p.gender : null
			this.form.sterile = typeof p.sterilizedState === 'number' ? p.sterilizedState : null
		},
		onNameInput(e) {
			this.form.pName = e.detail.value
			this.nameError = ''
		},
		onSpeciesDetailInput(e) {
			this.form.specificSpecies = e.detail.value
		},
		onCallNameInput(e) {
			this.form.ownerCallName = e.detail.value
		},
		onSpecies(e) {
			const i = Number(e.detail.value)
			this.form.species = this.species[i] || '猫'
		},
		onBirth(e) {
			this.form.birth = e.detail.value
		},
		setGender(v) {
			this.form.gender = v
		},
		setSterile(v) {
			this.form.sterile = v
		},
		validate() {
			const n = this.form.pName.trim()
			if (!n) {
				this.nameError = '给宠物起个名字吧，不然列表里只能显示「未命名宠物」'
				return false
			}
			if (n.length > 20) {
				this.nameError = '名字最多 20 个字'
				return false
			}
			this.nameError = ''
			return true
		},
		async onSubmit() {
			if (this.saving) return
			if (!this.validate()) return
			this.saving = true
			this.submitError = ''
			const body = {
				pName: this.form.pName.trim(),
				species: this.form.species,
				specificSpecies: this.form.specificSpecies.trim() || undefined,
				ownerCallName: this.form.ownerCallName.trim() || undefined,
				birth: this.form.birth || undefined,
				gender: this.form.gender === null ? undefined : this.form.gender,
				sterilizedState: this.form.sterile === null ? undefined : this.form.sterile
			}
			try {
				if (this.isEdit) {
					await pets.update(this.pid, body)
					uni.navigateBack()
				} else {
					const created = await pets.create(body)
					// 用 redirectTo：返回时回到萌宠列表，而不是又看到这张表单
					uni.redirectTo({ url: '/pages/pet-detail/pet-detail?pid=' + created.pid })
				}
			} catch (e) {
				this.submitError = (e && e.message) || '保存失败，请稍后再试'
			} finally {
				this.saving = false
			}
		},
		goBack() {
			uni.navigateBack({
				fail() {
					uni.switchTab({ url: '/pages/pets/pets' })
				}
			})
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $mw-canvas;
	padding-bottom: $mw-space-8;
}

.nav {
	display: flex;
	align-items: center;
	padding: 0 $mw-page-padding $mw-space-3;
	background: $mw-canvas;
}
.nav__back {
	width: 64rpx;
	height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}
.nav__title {
	flex: 1;
	text-align: center;
	font-size: $mw-font-h2;
	font-weight: 600;
	color: $mw-ink;
}
.nav__spacer {
	width: 64rpx;
}

.form {
	margin: 0 $mw-page-padding;
	padding: $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-card;
}

.field {
	margin-bottom: $mw-space-4;
}
.field__label {
	display: block;
	margin-bottom: $mw-space-2;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink-2;
}
.field__input,
.field__picker {
	width: 100%;
	min-height: 88rpx;
	padding: 0 $mw-space-3;
	box-sizing: border-box;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	display: flex;
	align-items: center;
}
.field__input {
	font-size: $mw-font-body;
	color: $mw-ink;
}
.field__picker text {
	flex: 1;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.field__picker .is-ph {
	color: $mw-muted;
}
.field__ph {
	color: $mw-muted;
}
.field__error {
	display: block;
	margin-top: $mw-space-1;
	font-size: 22rpx;
	color: $mw-danger;
}
.field__hint {
	display: block;
	margin-top: $mw-space-1;
	font-size: 22rpx;
	color: $mw-muted;
}

.chev {
	width: 16rpx;
	height: 16rpx;
	flex: none;
	border-right: 3rpx solid $mw-muted;
	border-bottom: 3rpx solid $mw-muted;
	transform: rotate(-45deg);
}
.chev--left {
	transform: rotate(135deg);
	margin-left: 8rpx;
}

.seg {
	display: flex;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	padding: 6rpx;
}
.seg__item {
	flex: 1;
	min-height: 72rpx;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.seg__item text {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.seg__item--on {
	background: $mw-surface;
}
.seg__item--on text {
	color: $mw-brand-ink;
	font-weight: 600;
}

.alert {
	padding: $mw-space-3;
	margin-bottom: $mw-space-3;
	background: rgba(229, 72, 77, 0.08);
	border: 1px solid rgba(229, 72, 77, 0.2);
	border-radius: $mw-radius-input;
}
.alert text {
	font-size: $mw-font-caption;
	color: $mw-danger;
	line-height: 1.6;
}

.actions {
	display: flex;
	gap: $mw-space-2;
	margin-top: $mw-space-4;
}
.btn {
	flex: 1;
	min-height: $mw-touch-min;
	border-radius: $mw-radius-pill;
	display: flex;
	align-items: center;
	justify-content: center;
}
.btn text {
	font-size: $mw-font-caption;
	font-weight: 600;
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
.btn--ghost {
	border: 1px solid $mw-line;
	background: transparent;
}
.btn--ghost text {
	color: $mw-ink;
}

.state {
	margin: $mw-space-6 $mw-page-padding 0;
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
	font-weight: 600;
	color: $mw-ink;
}
.state__desc {
	margin: $mw-space-2 0 $mw-space-4;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.state__btn {
	flex: none;
	padding: 0 $mw-space-6;
}
</style>