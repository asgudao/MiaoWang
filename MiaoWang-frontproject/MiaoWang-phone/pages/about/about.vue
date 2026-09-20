<template>
	<view class="page">
		<!-- 全局 navigationStyle 是 custom，非 tab 页自绘返回栏（照 pages/notification/list.vue） -->
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack">
				<view class="chev chev--left"></view>
			</view>
			<text class="nav__title">关于喵汪</text>
			<view class="nav__pad"></view>
		</view>

		<view class="body">
			<!-- 深色英雄块：与首页舞台 / 我的页资料卡同族 -->
			<view class="hero">
				<view class="hero__row">
					<text class="hero__brand">喵汪</text>
					<text class="hero__ver">v{{ version }}</text>
				</view>
				<text class="hero__desc">猫狗照护助手与电子宠物伴侣。这是一个边学边做的演示工程，不是已上线的商业产品。</text>
			</view>

			<view class="card">
				<text class="card__title">关于这个工程</text>
				<text class="card__p">
					包含手机端（uni-app + Vue 3 + JS）与 Web 端（Vue 3 + TypeScript + Vite）两个前端，后端是一套
					Spring Cloud Alibaba 微服务：网关加用户、宠物、社区、知识库、AI 问答、电子宠物、会员、健康等模块。
				</text>
				<text class="card__p">
					知识库文章由大模型生成后人工审校；AI 问答的答案会带上引用来源。
				</text>
				<text class="card__note">
					当前状态：本地开发中。没有上线运营，也没有真实用户规模数据可以公布，所以这里不写任何访问量、
					注册量或服务人数。
				</text>
			</view>

			<view class="card">
				<text class="card__title">版本</text>
				<view class="kv">
					<text class="kv__k">版本号</text>
					<text class="kv__v">{{ version }}</text>
				</view>
				<view class="kv">
					<text class="kv__k">构建号</text>
					<text class="kv__v">{{ versionCode }}</text>
				</view>
				<text class="card__note">
					两个值来自 MiaoWang-phone/manifest.json 的 versionName 与 versionCode。HBuilderX 工程在运行时读不到
					manifest.json，所以本页只镜像成一处常量（全页引用同一份，不写第二遍）。
					改 manifest.json 必须同步改这里，门禁 tools/check-mine-rest-phone.mjs 会拿 manifest.json 逐字比对。
				</text>
			</view>

			<view class="card">
				<text class="card__title">第三方依赖与开源许可</text>
				<view v-for="d in deps" :key="d.name" class="dep">
					<view class="dep__head">
						<text class="dep__name">{{ d.name }}</text>
						<text class="dep__ver">{{ d.version }}</text>
					</view>
					<view class="dep__meta">
						<text class="dep__lic">{{ d.license }}</text>
						<text class="dep__where">{{ d.where }}</text>
					</view>
					<text class="dep__use">{{ d.use }}</text>
					<text class="dep__src">来源：{{ d.src }}</text>
				</view>
				<text class="card__note">
					上面每一条的版本与许可证都直接读自它自己声明的文件，不是凭记忆写的。手机端的框架 uni-app 由
					HBuilderX 提供，工程内没有 package.json，所以这里不列它的版本与许可证：仓库里没有可核对的文件，
					与其猜一个，不如空着。
				</text>
			</view>

			<view class="card">
				<text class="card__title">3D 模型授权</text>
				<view class="kv">
					<text class="kv__k">文件</text>
					<text class="kv__v kv__v--wrap">{{ model.file }}</text>
				</view>
				<view class="kv">
					<text class="kv__k">体积</text>
					<text class="kv__v">{{ model.bytesText }}</text>
				</view>
				<view class="kv">
					<text class="kv__k">标题</text>
					<text class="kv__v">{{ model.title }}</text>
				</view>
				<view class="kv">
					<text class="kv__k">作者</text>
					<text class="kv__v">{{ model.author }}</text>
				</view>
				<view class="kv">
					<text class="kv__k">来源站</text>
					<text class="kv__v">Sketchfab（模型文件里记录的生成器：{{ model.generator }}）</text>
				</view>
				<view class="kv">
					<text class="kv__k">许可</text>
					<text class="kv__v dep__lic">{{ model.license }}</text>
				</view>
				<text class="card__p mono">{{ model.licenseUrl }}</text>
				<text class="card__p mono">{{ model.source }}</text>
				<text class="card__p">
					以上五项是模型文件自己的元数据（GLB 里 asset.extras 那一段）写明的，不是我们贴上去的标签。
					{{ model.license }} 允许商用，但署名是强制条款：必须标明作者 {{ model.author }} 并附上许可链接。
				</text>
				<text class="card__note">
					红线与现状要分开说。工程内任何界面此前都没有展示过这份署名，这一页是第一次；
					也就是说，署名义务目前还没有被满足，上架或对外分发前必须补齐，否则就是违反许可，与"免不免费"无关。
					工程内技能库 .agents/skills/miaowang-3d-model/SKILL.md 把这条记为「免费不等于可商用，
					付费会员权益上线前必须确认模型素材的商业授权」，口径比模型自带元数据更保守；
					以模型文件自带的 {{ model.license }} 为准的话，要补的是署名，而不是再买一次授权。
					两处不一致的地方如实列在这里，免得以后有人只读到其中一处就下结论。
				</text>
			</view>

			<view class="card">
				<text class="card__title">内容与免责</text>
				<text class="card__p">
					知识库与 AI 问答给出的养护建议仅供参考，不能替代执业兽医的当面诊断。宠物出现异常请及时就医。
				</text>
				<text class="card__p">
					社区内容由用户发布，点赞、评论、关注与消息通知都只对本人可见。
				</text>
			</view>

			<text class="foot">喵汪演示工程 v{{ version }}</text>
		</view>
	</view>
</template>

<script>
/**
 * 关于喵汪（纯静态页，零网络请求）
 *
 * 三条纪律（来自派单铁律 + miaowang-design-system §7）：
 * 1. **不许编**：版本号、依赖版本与许可证、3D 模型授权，全部来自可核对的文件，
 *    并在页面上写明来源文件；读不到来源的（手机端 uni-app）宁可空着也不猜。
 * 2. **不许吹**：不写"已上线""服务 N 万用户"这类无法证实的话。
 * 3. 可见文案里不用破折号（— / ——）、不用「·」当装饰分隔符。
 *
 * 常量表都是**纯数据字面量**（数组 / 对象，不引用别的常量），
 * 好让离线门禁 tools/check-mine-rest-phone.mjs 直接取出来、与真文件逐字比对。
 */

/**
 * 版本号：来源 MiaoWang-phone/manifest.json 的 "versionName"（同文件的 "versionCode" 见下一个常量）。
 * ⚠️ HBuilderX 工程在运行时读不到 manifest.json（它是构建配置，不是可 import 的模块），
 *    所以这里只能镜像成常量。全页只用这一处，门禁会拿 manifest.json 比对，改一边漏一边就变红。
 */
const APP_VERSION = '1.0.0'

/** 构建号：来源同上，manifest.json 的 "versionCode" */
const APP_VERSION_CODE = '100'

/**
 * 第三方依赖清单
 *
 * 每条的 version / license 都是**读出来的**，src 就是读它的那个文件：
 * - Web 端依赖：MiaoWang-web/package.json 声明版本区间，实际版本与 license 读自已安装的
 *   MiaoWang-web/node_modules/<包名>/package.json（version + license 两个字段）
 * - 手机端 three：工程内是本地副本，版本读自 MiaoWang-phone/libs/three/three.module.js 的
 *   `const REVISION = '160'`，许可证读该文件头的 `SPDX-License-Identifier: MIT`
 *
 * ⚠️ 手机端的 Vue 与 uni-app 由 HBuilderX 运行时内置，工程内**没有** package.json，
 *    所以它们不在这张表里（不是漏写，是仓库里没有可核对的文件）。
 */
const DEPS = [
	{
		name: 'three',
		version: '0.160.0',
		license: 'MIT',
		where: 'Web 端',
		use: '首页 3D 电子宠物舞台',
		src: 'MiaoWang-web/node_modules/three/package.json'
	},
	{
		name: 'three（手机端本地副本）',
		version: 'r160',
		license: 'MIT',
		where: '手机端',
		use: 'renderjs 里的 3D 舞台',
		src: 'MiaoWang-phone/libs/three/three.module.js'
	},
	{
		name: 'vue',
		version: '3.5.42',
		license: 'MIT',
		where: 'Web 端',
		use: '前端框架',
		src: 'MiaoWang-web/node_modules/vue/package.json'
	},
	{
		name: 'vue-router',
		version: '5.3.1',
		license: 'MIT',
		where: 'Web 端',
		use: '页面路由',
		src: 'MiaoWang-web/node_modules/vue-router/package.json'
	},
	{
		name: 'pinia',
		version: '4.0.3',
		license: 'MIT',
		where: 'Web 端',
		use: '状态管理',
		src: 'MiaoWang-web/node_modules/pinia/package.json'
	},
	{
		name: '@phosphor-icons/vue',
		version: '2.2.1',
		license: 'MIT',
		where: 'Web 端',
		use: '图标库',
		src: 'MiaoWang-web/node_modules/@phosphor-icons/vue/package.json'
	},
	{
		name: 'qrcode',
		version: '1.5.4',
		license: 'MIT',
		where: 'Web 端',
		use: '渲染支付二维码',
		src: 'MiaoWang-web/node_modules/qrcode/package.json'
	},
	{
		name: 'vite',
		version: '8.3.0',
		license: 'MIT',
		where: 'Web 端开发期',
		use: '构建与开发服务器',
		src: 'MiaoWang-web/node_modules/vite/package.json'
	},
	{
		name: 'typescript',
		version: '6.0.3',
		license: 'Apache-2.0',
		where: 'Web 端开发期',
		use: '类型检查',
		src: 'MiaoWang-web/node_modules/typescript/package.json'
	}
]

/**
 * 3D 模型事实：全部读自 GLB 自己的 JSON chunk（`asset.extras` 那一段）与文件本身。
 * 读法（可复现）：取 GLB 第 12 字节起的 JSON chunk 长度，解析出 `asset.extras`。
 * 门禁脚本会用同一读法重新读一遍，与本页常量逐字比对。
 */
const MODEL = {
	file: 'static/model/cat/toon_cat_free.glb',
	bytes: 205288,
	bytesText: '205,288 字节（约 200 KB）',
	title: 'Toon Cat FREE',
	author: 'Omabuarts Studio',
	authorUrl: 'https://sketchfab.com/omabuarts',
	license: 'CC-BY-4.0',
	licenseUrl: 'http://creativecommons.org/licenses/by/4.0/',
	source: 'https://sketchfab.com/3d-models/toon-cat-free-b2bd1ee7858444bda366110a2d960386',
	generator: 'Sketchfab-12.66.0'
}

export default {
	data() {
		return {
			/** 状态栏高度：全局 navigationStyle 是 custom，自绘返回栏要自己让开 */
			statusBarHeight: 0,
			// 常量表经 data() 暴露给模板（Options API 模板只认实例上的属性）
			version: APP_VERSION,
			versionCode: APP_VERSION_CODE,
			deps: DEPS,
			model: MODEL
		}
	},
	onLoad() {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 0
		} catch (e) {
			this.statusBarHeight = 0
		}
	},
	methods: {
		/** 非 tab 页：能返回就返回，直接从这一页启动时回「我的」tab */
		goBack() {
			const pages = getCurrentPages()
			if (pages.length > 1) uni.navigateBack()
			else uni.switchTab({ url: '/pages/mine/mine' })
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
/* 右侧留一个与返回键等宽的占位，标题才是真的居中 */
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

/* ---- 深色英雄块 ---- */
.hero {
	padding: $mw-space-6 $mw-space-4;
	background: $mw-night;
	border-radius: $mw-radius-card;
}
.hero__row {
	display: flex;
	align-items: baseline;
}
.hero__brand {
	font-size: $mw-font-h1;
	font-weight: 700;
	letter-spacing: -0.02em;
	color: $mw-on-night;
}
.hero__ver {
	margin-left: $mw-space-2;
	font-size: $mw-font-caption;
	font-weight: 600;
	font-variant-numeric: tabular-nums;
	color: $mw-brand;
}
.hero__desc {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-on-night-2;
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
.card__p {
	display: block;
	margin-top: $mw-space-2;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink-2;
}
/* 说明性文字（讲来源、讲现状），比正文更弱一档 */
.card__note {
	display: block;
	margin-top: $mw-space-3;
	padding-top: $mw-space-3;
	border-top: 1px solid $mw-line;
	font-size: $mw-font-micro;
	line-height: 1.7;
	color: $mw-muted;
}
/* 长 URL：允许断行，别撑破卡片 */
.mono {
	font-variant-numeric: tabular-nums;
	word-break: break-all;
	color: $mw-ink-2;
}

/* ---- 键值行 ---- */
.kv {
	display: flex;
	align-items: flex-start;
	margin-top: $mw-space-2;
}
.kv__k {
	flex: none;
	width: 140rpx;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.kv__v {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.kv__v--wrap {
	font-weight: 400;
	word-break: break-all;
}

/* ---- 依赖条目 ---- */
.dep {
	margin-top: $mw-space-3;
	padding: $mw-space-3;
	background: $mw-canvas;
	border-radius: $mw-radius-input;
}
.dep__head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
}
.dep__name {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-body;
	font-weight: 600;
	color: $mw-ink;
	word-break: break-all;
}
.dep__ver {
	flex: none;
	margin-left: $mw-space-2;
	font-size: $mw-font-caption;
	font-weight: 600;
	font-variant-numeric: tabular-nums;
	color: $mw-brand-ink;
}
.dep__meta {
	display: flex;
	align-items: center;
	margin-top: $mw-space-1;
}
.dep__lic {
	flex: none;
	padding: 4rpx 12rpx;
	border-radius: $mw-radius-tag;
	background: rgba(74, 118, 56, 0.12);
	font-size: $mw-font-micro;
	font-weight: 600;
	color: $mw-success;
}
.dep__where {
	margin-left: $mw-space-1;
	font-size: $mw-font-micro;
	color: $mw-muted;
}
.dep__use {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-caption;
	color: $mw-ink-2;
}
.dep__src {
	display: block;
	margin-top: $mw-space-1;
	font-size: $mw-font-micro;
	word-break: break-all;
	color: $mw-muted;
}

.foot {
	display: block;
	margin-top: $mw-space-4;
	text-align: center;
	font-size: $mw-font-micro;
	font-variant-numeric: tabular-nums;
	color: $mw-muted;
}
</style>