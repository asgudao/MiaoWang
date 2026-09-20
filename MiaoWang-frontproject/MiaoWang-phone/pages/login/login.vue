<template>
	<view class="page">
		<!-- 深色叙事带：与 Web 端登录页的左侧深色面板等价 -->
		<view class="lead">
			<view class="lead__bar" :style="{ paddingTop: statusBarHeight + 'px' }">
				<view class="back" @click="goBack"><text>‹</text></view>
				<text class="lead__brand">喵汪</text>
			</view>
			<text class="lead__head">欢迎回来</text>
			<text class="lead__sub">登录之后，电子宠物会记得你上次说过的话，也会接着提醒你还没做完的照护。</text>
		</view>

		<view class="form">
			<view class="seg">
				<view class="seg__item" :class="{ 'seg__item--on': mode === 'password' }" @click="setMode('password')">
					<text>用户名密码</text>
				</view>
				<view class="seg__item" :class="{ 'seg__item--on': mode === 'phone' }" @click="setMode('phone')">
					<text>手机号</text>
				</view>
			</view>

			<view v-if="error" class="alert"><text>{{ error }}</text></view>

			<template v-if="mode === 'password'">
				<view class="field">
					<text class="field__label">用户名</text>
					<input class="field__input" type="text" v-model="username" placeholder="注册时用的用户名"
						placeholder-class="ph" />
					<text v-if="err.username" class="field__error">{{ err.username }}</text>
				</view>
				<view class="field">
					<text class="field__label">密码</text>
					<input class="field__input" password type="text" v-model="password" placeholder="你的密码"
						placeholder-class="ph" />
					<text v-if="err.password" class="field__error">{{ err.password }}</text>
				</view>
			</template>

			<template v-else>
				<view class="field">
					<text class="field__label">手机号</text>
					<input class="field__input" type="number" maxlength="11" v-model="phone"
						placeholder="11 位手机号" placeholder-class="ph" />
					<text v-if="err.phone" class="field__error">{{ err.phone }}</text>
				</view>
				<view class="field">
					<text class="field__label">图片验证码</text>
					<view class="row">
						<input class="field__input field__input--grow" type="text" maxlength="6" v-model="captchaCode"
							placeholder="看图输入" placeholder-class="ph" />
						<image v-if="captcha" class="captcha" :src="captcha.imageBase64" mode="aspectFit"
							@click="refreshCaptcha" />
					</view>
					<text v-if="err.captchaCode" class="field__error">{{ err.captchaCode }}</text>
				</view>
				<view class="field">
					<text class="field__label">短信验证码</text>
					<view class="row">
						<input class="field__input field__input--grow" type="number" maxlength="6" v-model="smsCode"
							placeholder="6 位数字" placeholder-class="ph" />
						<view class="send" :class="{ 'send--off': sending || countdown > 0 }" @click="onSendSms">
							<text>{{ countdown > 0 ? countdown + ' 秒' : (sending ? '发送中' : '获取验证码') }}</text>
						</view>
					</view>
					<text v-if="err.smsCode" class="field__error">{{ err.smsCode }}</text>
					<text class="field__hint">同号码 60 秒 1 条、每天 10 条，验证码 5 分钟内有效</text>
				</view>
			</template>

			<view class="submit" :class="{ 'submit--busy': submitting }" @click="submit">
				<text>{{ submitting ? '登录中' : '登录' }}</text>
			</view>

			<view class="foot">
				<text class="foot__text">还没有账号？</text>
				<text class="foot__link" @click="goRegister">注册一个</text>
			</view>
		</view>
	</view>
</template>

<script>
/**
 * 登录（手机端）
 *
 * 手机号登录三步不能换序：取图形验证码 → 发短信码（带图形验证码）→ 手机号登录（带短信码）。
 * 图形验证码**一次性**，每次发短信后都要重新取一张，否则第二次必然报 A000700。
 */
import * as userApi from '@/api/user.js'
import { authErrorMessage, isCaptchaError } from '@/utils/auth-error.js'
import { auth } from '@/store/auth.js'

export default {
	data() {
		return {
			statusBarHeight: 20,
			mode: 'password',
			username: '',
			password: '',
			phone: '',
			captchaCode: '',
			smsCode: '',
			err: { username: '', password: '', phone: '', captchaCode: '', smsCode: '' },
			error: '',
			captcha: null,
			countdown: 0,
			sending: false,
			submitting: false
		}
	},
	onLoad() {
		try {
			this.statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 20
		} catch (e) {}
		if (auth.isLoggedIn()) this.goBack()
	},
	methods: {
		friendly(e) {
			if (isCaptchaError(e)) this.refreshCaptcha()
			return authErrorMessage(e)
		},
		clearErr() {
			Object.keys(this.err).forEach((k) => { this.err[k] = '' })
			this.error = ''
		},
		// 切到手机号登录时必须先取图形验证码：验证码图是 v-if 渲染的，没取就永远发不出短信
		setMode(m) {
			this.mode = m
			this.clearErr()
			if (m === 'phone' && !this.captcha) this.refreshCaptcha()
		},
		async refreshCaptcha() {
			try {
				this.captcha = await userApi.getCaptcha()
			} catch (e) {
				this.error = this.friendly(e)
			}
		},
		startCountdown() {
			this.countdown = 60
			const timer = setInterval(() => {
				this.countdown -= 1
				if (this.countdown <= 0) clearInterval(timer)
			}, 1000)
		},
		async onSendSms() {
			if (this.sending || this.countdown > 0) return
			this.clearErr()
			if (!/^1[3-9]\d{9}$/.test(this.phone)) {
				this.err.phone = '请输入 11 位手机号'
				return
			}
			if (!this.captchaCode.trim()) {
				this.err.captchaCode = '请输入图片验证码'
				return
			}
			if (!this.captcha) return

			this.sending = true
			try {
				await userApi.sendSms({
					phone: this.phone,
					scene: 'login',
					captchaKey: this.captcha.captchaKey,
					captchaCode: this.captchaCode.trim()
				})
				this.startCountdown()
			} catch (e) {
				this.error = this.friendly(e)
			} finally {
				this.sending = false
				// 图形验证码无论成功失败都已被消费，必须换新的
				this.captchaCode = ''
				this.refreshCaptcha()
			}
		},
		async submit() {
			if (this.submitting) return
			this.clearErr()

			if (this.mode === 'password') {
				if (!this.username.trim()) this.err.username = '请输入用户名'
				if (!this.password) this.err.password = '请输入密码'
				if (this.err.username || this.err.password) return
			} else {
				if (!/^1[3-9]\d{9}$/.test(this.phone)) this.err.phone = '请输入 11 位手机号'
				if (!/^\d{4,6}$/.test(this.smsCode.trim())) this.err.smsCode = '请输入收到的短信验证码'
				if (this.err.phone || this.err.smsCode) return
			}

			this.submitting = true
			try {
				if (this.mode === 'password') {
					await auth.login(this.username.trim(), this.password)
				} else {
					await auth.loginWithPhone(this.phone, this.smsCode.trim())
				}
				uni.showToast({ title: '登录成功', icon: 'none' })
				setTimeout(() => this.goBack(), 400)
			} catch (e) {
				this.error = this.friendly(e)
			} finally {
				this.submitting = false
			}
		},
		goBack() {
			const pages = getCurrentPages()
			if (pages.length > 1) uni.navigateBack()
			else uni.switchTab({ url: '/pages/index/index' })
		},
		goRegister() {
			uni.navigateTo({ url: '/pages/register/register' })
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

/* ---------- 深色叙事带 ---------- */
.lead {
	padding: 0 $mw-page-padding $mw-space-8;
	background:
		radial-gradient(120% 70% at 50% -10%, rgba(255, 152, 96, 0.12), transparent 60%),
		$mw-night;
}

.lead__bar {
	display: flex;
	align-items: center;
	gap: $mw-space-3;
	padding-bottom: $mw-space-6;
}

.back {
	width: 64rpx;
	height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 1px solid $mw-night-line;
	border-radius: 50%;
}
.back text {
	font-size: 44rpx;
	line-height: 1;
	color: $mw-on-night;
	margin-top: -8rpx;
}

.lead__brand {
	font-size: 34rpx;
	font-weight: 700;
	color: $mw-on-night;
	letter-spacing: -0.02em;
}

.lead__head {
	display: block;
	font-size: 64rpx;
	font-weight: 700;
	letter-spacing: -0.03em;
	color: $mw-on-night;
	line-height: 1.15;
}

.lead__sub {
	display: block;
	margin-top: $mw-space-3;
	font-size: $mw-font-caption;
	color: $mw-on-night-2;
	line-height: 1.7;
}

/* ---------- 表单 ---------- */
.form {
	padding: $mw-space-6 $mw-page-padding 0;
}

.seg {
	display: flex;
	padding: 6rpx;
	gap: 4rpx;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-pill;
}

.seg__item {
	flex: 1;
	min-height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: $mw-radius-pill;
}
.seg__item text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink-2;
}
.seg__item--on {
	background: $mw-surface;
	box-shadow: $mw-shadow-float;
}
.seg__item--on text {
	color: $mw-ink;
}

.alert {
	margin-top: $mw-space-4;
	padding: $mw-space-3 $mw-space-4;
	background: rgba(229, 72, 77, 0.1);
	border: 1px solid rgba(229, 72, 77, 0.25);
	border-radius: $mw-radius-input;
}
.alert text {
	font-size: $mw-font-caption;
	color: $mw-danger;
}

.field {
	margin-top: $mw-space-4;
	display: flex;
	flex-direction: column;
}
.field__label {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
	margin-bottom: $mw-space-2;
}
.field__input {
	height: $mw-touch-min;
	padding: 0 $mw-space-4;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.field__input--grow {
	flex: 1;
}
.field__error {
	margin-top: $mw-space-1;
	font-size: 22rpx;
	color: $mw-danger;
}
.field__hint {
	margin-top: $mw-space-1;
	font-size: 22rpx;
	color: $mw-muted;
}
.ph {
	color: $mw-muted;
}

.row {
	display: flex;
	align-items: center;
	gap: $mw-space-2;
}

.captcha {
	width: 200rpx;
	height: $mw-touch-min;
	background: $mw-surface;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
}

.send {
	min-height: $mw-touch-min;
	padding: 0 $mw-space-4;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	background: $mw-surface;
}
.send text {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.send--off {
	opacity: 0.45;
}

.submit {
	margin-top: $mw-space-6;
	min-height: $mw-btn-h;
	display: flex;
	align-items: center;
	justify-content: center;
	background: $mw-brand-ink;
	border-radius: $mw-radius-pill;
}
.submit text {
	font-size: 32rpx;
	font-weight: 600;
	color: #ffffff;
}
.submit--busy {
	opacity: 0.6;
}

.foot {
	margin: $mw-space-4 0 $mw-space-8;
	display: flex;
	justify-content: center;
	gap: 6rpx;
}
.foot__text {
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.foot__link {
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
</style>