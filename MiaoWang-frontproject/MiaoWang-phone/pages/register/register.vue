<template>
	<view class="page">
		<view class="lead">
			<view class="lead__bar" :style="{ paddingTop: statusBarHeight + 'px' }">
				<view class="back" @click="goBack"><text>‹</text></view>
				<text class="lead__brand">喵汪</text>
			</view>
			<text class="lead__head">建个档</text>
			<text class="lead__sub">注册之后就能给猫狗建健康档案、设照护提醒，还能领一只会说话的电子宠物。</text>
		</view>

		<view class="form">
			<view v-if="error" class="alert"><text>{{ error }}</text></view>

			<view class="field">
				<text class="field__label">用户名</text>
				<input class="field__input" type="text" v-model="username" placeholder="3 到 20 位字母、数字或下划线"
					placeholder-class="ph" />
				<text v-if="err.username" class="field__error">{{ err.username }}</text>
			</view>

			<view class="field">
				<text class="field__label">密码</text>
				<input class="field__input" password type="text" v-model="password" placeholder="至少 6 位"
					placeholder-class="ph" />
				<text v-if="err.password" class="field__error">{{ err.password }}</text>
			</view>

			<view class="field">
				<text class="field__label">确认密码</text>
				<input class="field__input" password type="text" v-model="confirm" placeholder="再输一遍"
					placeholder-class="ph" />
				<text v-if="err.confirm" class="field__error">{{ err.confirm }}</text>
			</view>

			<view class="field">
				<text class="field__label">真实姓名</text>
				<input class="field__input" type="text" v-model="realName" placeholder="用于账号归属"
					placeholder-class="ph" />
				<text v-if="err.realName" class="field__error">{{ err.realName }}</text>
			</view>

			<view class="field">
				<text class="field__label">宠物怎么称呼你（可留空）</text>
				<input class="field__input" type="text" maxlength="20" v-model="ownerCallName"
					placeholder="比如：铲屎官、妈妈、老张" placeholder-class="ph" />
			</view>

			<view class="field">
				<text class="field__label">手机号</text>
				<input class="field__input" type="number" maxlength="11" v-model="phone" placeholder="11 位手机号"
					placeholder-class="ph" />
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
				<text class="field__hint">短信通道当前是 mock，验证码打在 user 服务日志里</text>
			</view>

			<view class="submit" :class="{ 'submit--busy': submitting }" @click="submit">
				<text>{{ submitting ? '注册中' : '注册并登录' }}</text>
			</view>

			<view class="foot">
				<text class="foot__text">已经有账号了？</text>
				<text class="foot__link" @click="goLogin">去登录</text>
			</view>
		</view>
	</view>
</template>

<script>
/**
 * 注册（手机端）
 *
 * 坑：`POST /user` 的 smsCode 必填且 scene 必须是 register（与登录码不通用）；
 * 图形验证码一次性，每次发短信后都要重新取；注册成功后直接自动登录。
 * 另外**不做用户名实时查重**：has-username 需要登录态（网关未白名单，实测匿名必返回 state:-1），
 * 注册时本来就还没登录，重复用户名只能等提交后由后端返回 B000201「用户名已存在」。
 */
import * as userApi from '@/api/user.js'
import { authErrorMessage, isCaptchaError } from '@/utils/auth-error.js'
import { auth } from '@/store/auth.js'

export default {
	data() {
		return {
			statusBarHeight: 20,
			username: '',
			password: '',
			confirm: '',
			realName: '',
			phone: '',
			ownerCallName: '',
			captchaCode: '',
			smsCode: '',
			err: { username: '', password: '', confirm: '', realName: '', phone: '', captchaCode: '', smsCode: '' },
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
		// 图形验证码必须先取：验证码图是 v-if 渲染的，没取就永远发不出短信
		this.refreshCaptcha()
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
			this.err.phone = ''
			this.err.captchaCode = ''
			this.error = ''
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
					// 注册码与登录码分场景存，这里必须是 register
					scene: 'register',
					captchaKey: this.captcha.captchaKey,
					captchaCode: this.captchaCode.trim()
				})
				this.startCountdown()
			} catch (e) {
				this.error = this.friendly(e)
			} finally {
				this.sending = false
				this.captchaCode = ''
				this.refreshCaptcha()
			}
		},
		async submit() {
			if (this.submitting) return
			this.clearErr()

			if (!/^[A-Za-z0-9_]{3,20}$/.test(this.username.trim())) {
				this.err.username = '用户名用 3 到 20 位字母、数字或下划线'
			}
			if (this.password.length < 6) this.err.password = '密码至少 6 位'
			if (this.confirm !== this.password) this.err.confirm = '两次输入的密码不一致'
			if (!this.realName.trim()) this.err.realName = '请填写真实姓名'
			if (!/^1[3-9]\d{9}$/.test(this.phone)) this.err.phone = '请输入 11 位手机号'
			if (!/^\d{4,6}$/.test(this.smsCode.trim())) this.err.smsCode = '请输入收到的短信验证码'
			const bad = Object.keys(this.err).some((k) => this.err[k])
			if (bad) return

			this.submitting = true
			try {
				await auth.register({
					username: this.username.trim(),
					password: this.password,
					realName: this.realName.trim(),
					phone: this.phone,
					ownerCallName: this.ownerCallName.trim() || undefined,
					smsCode: this.smsCode.trim()
				})
				uni.showToast({ title: '注册成功', icon: 'none' })
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
		goLogin() {
			uni.redirectTo({ url: '/pages/login/login' })
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

.lead {
	padding: 0 $mw-page-padding $mw-space-6;
	background:
		radial-gradient(120% 70% at 50% -10%, rgba(53, 214, 168, 0.1), transparent 60%),
		$mw-night;
}

.lead__bar {
	display: flex;
	align-items: center;
	gap: $mw-space-3;
	padding-bottom: $mw-space-4;
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
	font-size: 56rpx;
	font-weight: 700;
	letter-spacing: -0.03em;
	color: $mw-on-night;
	line-height: 1.15;
}

.lead__sub {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	color: $mw-on-night-2;
	line-height: 1.7;
}

.form {
	padding: $mw-space-4 $mw-page-padding 0;
}

.alert {
	margin-top: $mw-space-2;
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
	margin-top: $mw-space-3;
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