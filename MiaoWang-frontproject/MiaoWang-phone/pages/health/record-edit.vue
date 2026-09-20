<template>
	<view class="hpage">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav__back" @click="goBack"><view class="chev chev--left"></view></view>
			<text class="nav__title">{{ pageTitle }}</text>
			<view class="nav__spacer"></view>
		</view>

		<view v-if="showGate" class="state">
			<text class="state__title">{{ sessionExpired ? '登录已过期' : '要登录才能记录健康档案' }}</text>
			<text class="state__desc">{{ gateDesc }}</text>
			<view class="state__btn" @click="goLogin"><text>去登录</text></view>
		</view>

		<template v-else>
			<!-- 起手就读不到：说清原因，给返回与重试 -->
			<view v-if="loading" class="skeleton">
				<view class="skeleton__card"></view>
			</view>

			<view v-else-if="loadError" class="state">
				<text class="state__title">{{ type ? '这条记录读不出来' : '这个类型不认识' }}</text>
				<text class="state__desc">{{ loadError }}</text>
				<view class="state__btn" @click="retry"><text>重试</text></view>
				<view class="state__ghost" @click="goBack"><text>返回</text></view>
			</view>

			<template v-else>
				<view v-if="submitError" class="inline-err">
					<text class="inline-err__text">{{ submitError }}</text>
					<text class="inline-err__act" @click="submitError = ''">知道了</text>
				</view>
				<view v-if="uploadError" class="inline-err">
					<text class="inline-err__text">{{ uploadError }}</text>
					<text class="inline-err__act" @click="uploadError = ''">知道了</text>
				</view>
				<view v-if="hintText" class="notice notice--plain">
					<text>{{ hintText }}</text>
				</view>

				<view class="hc">
					<view class="hc__head">
						<text class="hc__title">{{ formTitle }}</text>
						<text class="tag" :class="isEdit ? 'tag--muted' : 'tag--brand'">{{ isEdit ? '编辑' : '新建' }}</text>
					</view>

					<!-- ===================== 疫苗 / 驱虫 ===================== -->
					<template v-if="type === 'preventive'">
						<view class="field">
							<view class="field__head">
								<text class="field__label">类型</text>
								<text class="field__req">必填</text>
							</view>
							<view class="seg">
								<text
									v-for="t in typeChips"
									:key="t.value"
									class="seg__item"
									:class="{ 'seg__item--on': t.on }"
									@click="setField('type', t.value)"
								>{{ t.label }}</text>
							</view>
						</view>
						<view class="field">
							<view class="field__head">
								<text class="field__label">项目名</text>
								<text class="field__req">必填</text>
							</view>
							<input class="field__input" v-model="form.itemName" placeholder="例如「猫三联」「大宠爱」" />
						</view>
						<view class="field">
							<view class="field__head">
								<text class="field__label">这次的时间</text>
								<text class="field__req">必填</text>
							</view>
							<view class="dt">
								<picker class="dt__cell" mode="date" :value="datePart(form.happenedAt)" @change="onPickDate('happenedAt', $event)">
									<view class="dt__box"><text>{{ dateLabel(form.happenedAt) }}</text></view>
								</picker>
								<picker class="dt__cell" mode="time" :value="timePart(form.happenedAt)" @change="onPickTime('happenedAt', $event)">
									<view class="dt__box"><text>{{ timeLabel(form.happenedAt) }}</text></view>
								</picker>
							</view>
						</view>
						<view class="field">
							<view class="field__head">
								<text class="field__label">下次到期时间</text>
								<text class="field__opt">可空</text>
								<text class="field__clear" @click="setField('nextDueAt', '')">清空</text>
							</view>
							<view class="dt">
								<picker class="dt__cell" mode="date" :value="datePart(form.nextDueAt)" @change="onPickDate('nextDueAt', $event)">
									<view class="dt__box"><text>{{ dateLabel(form.nextDueAt) }}</text></view>
								</picker>
								<picker class="dt__cell" mode="time" :value="timePart(form.nextDueAt)" @change="onPickTime('nextDueAt', $event)">
									<view class="dt__box"><text>{{ timeLabel(form.nextDueAt) }}</text></view>
								</picker>
							</view>
							<text class="field__tip">填了这里，列表与概览才能算出「还有几天」「已逾期几天」。</text>
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">医院</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.hospital" placeholder="哪家医院做的" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">批号</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.batchNo" placeholder="疫苗批号" />
						</view>
					</template>

					<!-- ===================== 就诊 ===================== -->
					<template v-if="type === 'medical'">
						<view class="field">
							<view class="field__head">
								<text class="field__label">就诊时间</text>
								<text class="field__req">必填</text>
							</view>
							<view class="dt">
								<picker class="dt__cell" mode="date" :value="datePart(form.visitAt)" @change="onPickDate('visitAt', $event)">
									<view class="dt__box"><text>{{ dateLabel(form.visitAt) }}</text></view>
								</picker>
								<picker class="dt__cell" mode="time" :value="timePart(form.visitAt)" @change="onPickTime('visitAt', $event)">
									<view class="dt__box"><text>{{ timeLabel(form.visitAt) }}</text></view>
								</picker>
							</view>
						</view>
						<view class="field">
							<view class="field__head">
								<text class="field__label">诊断</text>
								<text class="field__req">必填</text>
							</view>
							<input class="field__input" v-model="form.diagnosis" placeholder="医生给的结论，例如「急性肠胃炎」" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">处置</text><text class="field__opt">可空</text></view>
							<textarea class="field__area" v-model="form.treatment" placeholder="怎么治的、开了什么" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">医院</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.hospital" placeholder="哪家医院" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">医生</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.doctor" placeholder="哪位医生" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">费用（元）</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.cost" type="digit" placeholder="例如 380.50" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">附件照片</text><text class="field__opt">可空</text></view>
							<text class="field__tip">后端要求的是逗号分隔的一串地址（不是数组），这里帮你拼好；返回时后端会给回数组。</text>
							<view v-if="hasImages" class="shots__grid">
								<image v-for="(u, ui) in imageUrls" :key="ui" class="shots__img" :src="u" mode="aspectFill" />
							</view>
							<view class="urls">
								<text v-for="(u, ui) in imageUrls" :key="'u' + ui" class="urls__row">
									<text class="urls__text">{{ shortUrl(u) }}</text>
									<text class="urls__del" @click="removeImage(ui)">移除</text>
								</text>
							</view>
							<view class="picker-acts">
								<view class="btn btn--ghost" @click="chooseImages"><text>{{ uploadBusy ? '上传中' : '从相册选图上传' }}</text></view>
							</view>
							<view class="urladd">
								<input class="urladd__input" v-model="imageUrlInput" placeholder="也可以直接粘贴图片地址" />
								<text class="urladd__btn" @click="addImageUrl">加入</text>
							</view>
						</view>
					</template>

					<!-- ===================== 用药 ===================== -->
					<template v-if="type === 'medication'">
						<view class="field">
							<view class="field__head">
								<text class="field__label">药名</text>
								<text class="field__req">必填</text>
							</view>
							<input class="field__input" v-model="form.medicineName" placeholder="例如「速诺（阿莫西林克拉维酸钾）」" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">单次用量</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.dosage" placeholder="例如「半片」「1 袋」" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">频次</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.frequency" placeholder="例如「每日 2 次」" />
						</view>
						<view class="field">
							<view class="field__head">
								<text class="field__label">开始时间</text>
								<text class="field__req">必填</text>
							</view>
							<view class="dt">
								<picker class="dt__cell" mode="date" :value="datePart(form.startAt)" @change="onPickDate('startAt', $event)">
									<view class="dt__box"><text>{{ dateLabel(form.startAt) }}</text></view>
								</picker>
								<picker class="dt__cell" mode="time" :value="timePart(form.startAt)" @change="onPickTime('startAt', $event)">
									<view class="dt__box"><text>{{ timeLabel(form.startAt) }}</text></view>
								</picker>
							</view>
						</view>
						<view class="field">
							<view class="field__head">
								<text class="field__label">结束时间</text>
								<text class="field__opt">可空</text>
								<text class="field__clear" @click="setField('endAt', '')">清空</text>
							</view>
							<view class="dt">
								<picker class="dt__cell" mode="date" :value="datePart(form.endAt)" @change="onPickDate('endAt', $event)">
									<view class="dt__box"><text>{{ dateLabel(form.endAt) }}</text></view>
								</picker>
								<picker class="dt__cell" mode="time" :value="timePart(form.endAt)" @change="onPickTime('endAt', $event)">
									<view class="dt__box"><text>{{ timeLabel(form.endAt) }}</text></view>
								</picker>
							</view>
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">状态</text><text class="field__opt">不选就由结束时间推断</text></view>
							<view class="seg">
								<text
									v-for="s in statusChips"
									:key="s.key"
									class="seg__item"
									:class="{ 'seg__item--on': s.on }"
									@click="setField('status', s.value)"
								>{{ s.label }}</text>
							</view>
							<text class="field__tip">不指定时：结束时间为空就是「用药中」，填了就是「已结束」。</text>
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">关联就诊编号</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.medicalId" type="number" placeholder="这条药是哪次就诊开的" />
						</view>
					</template>

					<!-- ===================== 过敏 ===================== -->
					<template v-if="type === 'allergy'">
						<view class="field">
							<view class="field__head">
								<text class="field__label">过敏原</text>
								<text class="field__req">必填</text>
							</view>
							<input class="field__input" v-model="form.allergen" placeholder="例如「牛奶」「尘螨」" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">严重度</text><text class="field__opt">默认轻度</text></view>
							<view class="seg">
								<text
									v-for="s in severityChips"
									:key="s.value"
									class="seg__item"
									:class="{ 'seg__item--on': s.on }"
									@click="setField('severity', String(s.value))"
								>{{ s.label }}</text>
							</view>
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">反应表现</text><text class="field__opt">可空</text></view>
							<input class="field__input" v-model="form.reaction" placeholder="例如「腹泻、呕吐」" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">注意事项</text><text class="field__opt">可空</text></view>
							<textarea class="field__area" v-model="form.note" placeholder="例如「严禁喂食任何含乳制品」" />
						</view>
					</template>

					<!-- ===================== 症状 ===================== -->
					<template v-if="type === 'symptom'">
						<view class="field">
							<view class="field__head"><text class="field__label">名字从哪来</text><text class="field__req">二选一</text></view>
							<view class="seg">
								<text class="seg__item" :class="{ 'seg__item--on': form.useOption }" @click="setMode(true)">从症状库选</text>
								<text class="seg__item" :class="{ 'seg__item--on': !form.useOption }" @click="setMode(false)">自己填写</text>
							</view>
						</view>

						<view v-if="form.useOption" class="field">
							<view class="field__head">
								<text class="field__label">症状库</text>
								<text class="field__opt">{{ optionsHint }}</text>
							</view>
							<input class="field__input" v-model="keyword" placeholder="搜症状名，例如「食欲」" />
							<view v-if="optionsError" class="inline-err inline-err--in">
								<text class="inline-err__text">{{ optionsError }}</text>
								<text class="inline-err__act" @click="loadOptions">重试</text>
							</view>
							<text v-else-if="drawerOpen" class="field__tip">下面是筛选后的结果，点一条就选中：</text>
							<scroll-view v-if="drawerOpen" class="optlist" scroll-y>
								<view v-for="o in filteredOptions" :key="o.key" class="opt" @click="selectOption(o)">
									<text class="opt__name">{{ o.name }}</text>
									<text v-if="o.hasDescription" class="opt__desc">可能是：{{ o.description }}</text>
									<text v-if="o.hasRemark" class="opt__remark">{{ o.remark }}</text>
								</view>
								<text v-if="!filteredOptions.length" class="field__tip">没有匹配的症状，换个词试试，或者改成「自己填写」。</text>
							</scroll-view>
							<view class="picker-acts">
								<view class="btn btn--ghost" @click="toggleDrawer"><text>{{ drawerOpen ? '收起症状库' : '展开症状库' }}</text></view>
							</view>
							<view class="picked">
								<text class="picked__label">已选</text>
								<text class="picked__value">{{ pickedText }}</text>
							</view>
							<text class="field__tip">选了症状库的条目，后端会用库里的名字覆盖自填名（实测就是这样），所以这一栏提交时不会再传名字。</text>
						</view>

						<view v-else class="field">
							<view class="field__head">
								<text class="field__label">症状名</text>
								<text class="field__req">必填</text>
							</view>
							<input class="field__input" v-model="form.symptomName" placeholder="例如「偶尔打喷嚏」" />
							<text class="field__tip">自己填写时不会传症状库编号，名字原样保存。</text>
						</view>

						<view class="field">
							<view class="field__head">
								<text class="field__label">发生时间</text>
								<text class="field__req">必填</text>
							</view>
							<view class="dt">
								<picker class="dt__cell" mode="date" :value="datePart(form.occurredAt)" @change="onPickDate('occurredAt', $event)">
									<view class="dt__box"><text>{{ dateLabel(form.occurredAt) }}</text></view>
								</picker>
								<picker class="dt__cell" mode="time" :value="timePart(form.occurredAt)" @change="onPickTime('occurredAt', $event)">
									<view class="dt__box"><text>{{ timeLabel(form.occurredAt) }}</text></view>
								</picker>
							</view>
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">严重度</text><text class="field__opt">默认轻度</text></view>
							<view class="seg">
								<text
									v-for="s in severityChips"
									:key="s.value"
									class="seg__item"
									:class="{ 'seg__item--on': s.on }"
									@click="setField('severity', String(s.value))"
								>{{ s.label }}</text>
							</view>
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">具体表现</text><text class="field__opt">可空</text></view>
							<textarea class="field__area" v-model="form.description" placeholder="一天几次、有没有别的异常" />
						</view>
						<view class="field">
							<view class="field__head"><text class="field__label">症状照片</text><text class="field__opt">可空</text></view>
							<text class="field__tip">和第 3 类一样：提交时拼成逗号串，后端返回时是数组，没有照片就是空数组。</text>
							<view v-if="hasImages" class="shots__grid">
								<image v-for="(u, ui) in imageUrls" :key="ui" class="shots__img" :src="u" mode="aspectFill" />
							</view>
							<view class="urls">
								<text v-for="(u, ui) in imageUrls" :key="'u' + ui" class="urls__row">
									<text class="urls__text">{{ shortUrl(u) }}</text>
									<text class="urls__del" @click="removeImage(ui)">移除</text>
								</text>
							</view>
							<view class="picker-acts">
								<view class="btn btn--ghost" @click="chooseImages"><text>{{ uploadBusy ? '上传中' : '从相册选图上传' }}</text></view>
							</view>
							<view class="urladd">
								<input class="urladd__input" v-model="imageUrlInput" placeholder="也可以直接粘贴图片地址" />
								<text class="urladd__btn" @click="addImageUrl">加入</text>
							</view>
						</view>
					</template>

					<!-- ===================== 体重 ===================== -->
					<template v-if="type === 'weight'">
						<view class="field">
							<view class="field__head">
								<text class="field__label">体重（kg）</text>
								<text class="field__req">必填</text>
							</view>
							<input class="field__input" v-model="form.weightKg" type="digit" placeholder="例如 4.25" />
							<text class="field__tip">后端只接受 0.05 到 200 kg，超出会回一条「体重需在 0.05 ~ 200 kg 之间」。</text>
						</view>
						<view class="field">
							<view class="field__head">
								<text class="field__label">测量时间</text>
								<text class="field__req">必填</text>
							</view>
							<view class="dt">
								<picker class="dt__cell" mode="date" :value="datePart(form.measuredAt)" @change="onPickDate('measuredAt', $event)">
									<view class="dt__box"><text>{{ dateLabel(form.measuredAt) }}</text></view>
								</picker>
								<picker class="dt__cell" mode="time" :value="timePart(form.measuredAt)" @change="onPickTime('measuredAt', $event)">
									<view class="dt__box"><text>{{ timeLabel(form.measuredAt) }}</text></view>
								</picker>
							</view>
						</view>
					</template>

					<!-- 备注：六类都有 -->
					<view class="field">
						<view class="field__head"><text class="field__label">备注</text><text class="field__opt">可空</text></view>
						<textarea class="field__area" v-model="form.remark" placeholder="想记的其它信息" />
					</view>

					<view v-if="validationError" class="inline-err inline-err--in">
						<text class="inline-err__text">{{ validationError }}</text>
					</view>

					<view class="submit">
						<view class="btn btn--primary btn--block" @click="submit">
							<text>{{ busy ? '提交中' : isEdit ? '保存修改' : '保存' }}</text>
						</view>
						<view class="btn btn--ghost btn--block submit__cancel" @click="goBack"><text>取消</text></view>
					</view>

					<text class="hc__dim">{{ putNote }}</text>
				</view>

				<text class="foot">{{ timeNote }}</text>
			</template>
		</template>
	</view>
</template>

<script>
/**
 * 健康记录表单（新建 / 编辑，六类共用一页）
 *
 * 契约（实测）：
 * - ⚠️ **PUT 是全量覆盖**：不传的字段会被后端置成 `null`（实测 preventive 省略 `batchNo` → 读回 `null`）。
 *   所以编辑走的是「**读全量 → 合并 → 整体 PUT**」：
 *     `XFormFromDto(dto)` 把 DTO 里每个可写字段搬进表单 → 用户改 → `XSaveReqFromForm(form)` 整体提交。
 *   本页**从不**发局部更新。
 * - ⚠️ **后端没有"单条记录 GET"接口**（六类都只有列表 + PUT/DELETE）→ 编辑页只能**拉列表再按 id 找**
 *   （这就是后端缺口之一：有了单条 GET，编辑页就不用先拉整张列表）。
 * - ⚠️ 时间入参也吃 `yyyy-MM-dd HH:mm:ss`（不传/格式错 → `B000407`）；
 *   这里用日期 + 时间两个 picker 拼，秒固定 `00`。
 * - ⚠️ `attachments` / `images` **入参是逗号串**（出参才是数组）；上传接口回 `{url, objectKey}`，
 *   但记录 DTO 装不下 objectKey → 落库只有 URL（挂账 #67）。
 * - ⚠️ `symptomId` 传了就会覆盖 `symptomName` → 走症状库时**主动不传名字**（诚实，不让用户误会）。
 * - 症状选项接口也要登录（实测匿名被网关拦成 `state:-1`），本页因此先过登录门槛。
 */
import { auth } from '@/store/auth.js'
import { ApiError } from '@/utils/request.js'
import * as healthApi from '@/api/health.js'
import {
	MEDICATION_STATUSES,
	PREVENTIVE_TYPES,
	SEVERITY_OPTIONS,
	allergyFormFromDto,
	allergySaveReqFromForm,
	filterSymptomOptions,
	healthErrorMessage,
	medicalFormFromDto,
	medicalSaveReqFromForm,
	medicationFormFromDto,
	medicationSaveReqFromForm,
	preventiveFormFromDto,
	preventiveSaveReqFromForm,
	symptomFormFromDto,
	symptomSaveReqFromForm,
	symptomSpeciesCode,
	text,
	toDateOnly,
	validateAllergyForm,
	validateMedicalForm,
	validateMedicationForm,
	validatePreventiveForm,
	validateSymptomForm,
	validateWeightForm,
	weightFormFromDto,
	weightSaveReqFromForm
} from '@/utils/health.js'

/** 类型 → 中文名（本页标题用） */
const TYPE_LABELS = {
	preventive: '疫苗与驱虫',
	medical: '就诊',
	medication: '用药',
	allergy: '过敏',
	symptom: '症状',
	weight: '体重'
}

/** 症状库里最多渲染多少条（猫 52 / 狗 46，滚动列表渲染全量没必要） */
const OPTION_RENDER_LIMIT = 30

export default {
	data() {
		return {
			pid: 0,
			type: '',
			editId: null,
			statusBarHeight: 20,
			loading: true,
			loadError: '',
			unauthorized: false,
			/** 六类共用一个表单对象（键由 *FormFromDto 铺，缺键不会崩，只会渲染空） */
			form: {},
			/** 症状库 */
			options: [],
			optionsError: '',
			optionsLoaded: false,
			optionsSpecies: 1,
			keyword: '',
			drawerOpen: false,
			/** 图片 */
			imageUrlInput: '',
			uploadBusy: false,
			uploadError: '',
			busy: false,
			submitError: '',
			validationError: '',
			petName: ''
		}
	},
	computed: {
		loggedIn() {
			return auth.isLoggedIn()
		},
		sessionExpired() {
			return auth.isLoggedIn() && this.unauthorized
		},
		showGate() {
			return !this.loggedIn || this.sessionExpired
		},
		gateDesc() {
			if (this.sessionExpired) return '本地的登录状态后端已经不认了，重新登录一次再记。'
			return '健康档案（含症状库选项）都必须带登录身份，匿名请求会被网关直接拦掉。'
		},
		pageTitle() {
			if (!this.type) return '健康记录'
			return (this.editId ? '编辑' : '记一条') + TYPE_LABELS[this.type]
		},
		formTitle() {
			return this.type ? TYPE_LABELS[this.type] : '健康记录'
		},
		isEdit() {
			return this.editId !== null && this.editId !== undefined
		},
		typeChips() {
			const out = []
			for (let i = 0; i < PREVENTIVE_TYPES.length; i++) {
				const t = PREVENTIVE_TYPES[i]
				out.push({ value: t.value, label: t.label, on: this.form.type === t.value })
			}
			return out
		},
		severityChips() {
			const out = []
			for (let i = 0; i < SEVERITY_OPTIONS.length; i++) {
				const s = SEVERITY_OPTIONS[i]
				out.push({ value: s.value, label: s.label, on: String(this.form.severity) === String(s.value) })
			}
			return out
		},
		statusChips() {
			const list = [{ key: 'none', value: '', label: '不指定', on: this.form.status === '' || this.form.status === null || this.form.status === undefined }]
			for (let i = 0; i < MEDICATION_STATUSES.length; i++) {
				const s = MEDICATION_STATUSES[i]
				list.push({ key: 's' + s.value, value: String(s.value), label: s.label, on: String(this.form.status) === String(s.value) })
			}
			return list
		},
		filteredOptions() {
			// 纯函数在 utils 里，computed 只负责"把结果裁一下"（不按参数调用，参数是 data）
			return filterSymptomOptions(this.options, this.keyword).slice(0, OPTION_RENDER_LIMIT)
		},
		/** 当前表单的图片数组：就诊看 attachments，症状看 images。
		 *  模板里**不直接**碰 form.attachments / form.images —— 表单对象是动态铺的，
		 *  早期渲染时那两个键可能还不存在，`undefined.length` 会在渲染期抛错并白屏。 */
		imageUrls() {
			const raw = this.type === 'medical' ? this.form.attachments : this.form.images
			return Array.isArray(raw) ? raw : []
		},
		hasImages() {
			return this.imageUrls.length > 0
		},
		optionsHint() {
			if (!this.optionsLoaded) return '还没读到'
			return '共 ' + this.options.length + ' 条（' + (this.optionsSpecies === 2 ? '狗' : '猫') + '），最多显示前 ' + OPTION_RENDER_LIMIT + ' 条匹配结果'
		},
		pickedText() {
			if (!this.form.symptomId) return '还没选'
			const found = this.options.filter((o) => String(o.symptomId) === String(this.form.symptomId))[0]
			if (found) return text(found.name, '已选编号 ' + this.form.symptomId)
			return '已选编号 ' + this.form.symptomId + '（当前筛选里看不到它）'
		},
		hintText() {
			if (this.type === 'symptom') return '症状名有两种来源：从症状库里选，或者自己填写。选了库就由库里的名字说了算。'
			if (this.type === 'medical') return '诊断和就诊时间是必填，其余可空。附件在库里只存地址。'
			if (this.type === 'medication') return '结束用药也走这一页：把状态改成已结束、补上结束时间再保存就行。'
			if (this.type === 'preventive') return '下次到期时间填了才有提醒；日期与时间是两个选择器，秒固定为 00。'
			return ''
		},
		putNote() {
			if (!this.isEdit) return ''
			return '编辑是整体覆盖：这一页会把这条记录的全部字段一起提交，漏掉的字段会被清空，所以别在别处同时改同一条。'
		},
		timeNote() {
			return '所有时间都按 yyyy-MM-dd HH:mm:ss 提交（后端要的格式）；显示时会把空格换成 T 再解析，秒会被忽略一部分。'
		}
	},
	onLoad(options) {
		try {
			const info = uni.getSystemInfoSync()
			this.statusBarHeight = info.statusBarHeight || 20
		} catch (e) {
			// 拿不到就用默认值
		}
		const o = options || {}
		this.pid = Number(o.pid || 0)
		this.type = o.type ? String(o.type) : ''
		this.editId = o.id === undefined || o.id === '' ? null : Number(o.id)
		this.load()
	},
	methods: {
		async load() {
			this.loading = true
			this.loadError = ''
			this.validationError = ''
			this.submitError = ''
			// 未登录就**一个请求都不发**（健康接口含症状库都要身份，匿名必被网关拦成 state:-1）
			if (!this.loggedIn) {
				this.loading = false
				return
			}
			if (!TYPE_LABELS[this.type]) {
				this.loading = false
				this.loadError = '地址里的类型是「' + this.type + '」，六类里没有它。回到宠物档案重新进一次。'
				return
			}
			if (this.editId === null && this.type !== 'weight') {
				// 新建：直接铺一个空表单（日期默认现在）
				this.applyForm(this.emptyForm())
			}
			try {
				// 宠物名与物种（症状库要 species=1 猫 / 2 狗）
				const ov = await healthApi.overview(this.pid)
				this.petName = text(ov && ov.petName, '')
				this.optionsSpecies = symptomSpeciesCode(ov && ov.species)
				if (this.isEdit) {
					// ⚠️ 没有单条 GET：只能拉列表再按 id 找（后端缺口）
					const dto = await this.fetchDtoById()
					if (!dto) {
						this.loading = false
						this.loadError =
							'这条记录（编号 ' + this.editId + '）不在列表里了，可能已经被删掉。' +
							'后端没有单条记录查询接口，编辑页只能从列表里找，所以这里找不到就是真的没了。'
						return
					}
					this.applyForm(this.formFromDto(dto))
				} else {
					this.applyForm(this.emptyForm())
				}
				if (this.type === 'symptom') await this.loadOptions()
			} catch (e) {
				if (this.noteAuth(e)) return
				this.loadError = healthErrorMessage(e)
			} finally {
				this.loading = false
			}
		},

		/** 拉这一类列表再按 id 找（没有单条 GET） */
		async fetchDtoById() {
			const rows = await this.listByType()
			const list = Array.isArray(rows) ? rows : []
			for (let i = 0; i < list.length; i++) {
				if (Number(list[i].id) === Number(this.editId)) return list[i]
			}
			return null
		},

		listByType() {
			if (this.type === 'preventive') return healthApi.listPreventive(this.pid)
			if (this.type === 'medical') return healthApi.listMedical(this.pid)
			if (this.type === 'medication') return healthApi.listMedication(this.pid)
			if (this.type === 'allergy') return healthApi.listAllergy(this.pid)
			if (this.type === 'symptom') return healthApi.listSymptom(this.pid)
			return healthApi.listWeight(this.pid)
		},

		emptyForm() {
			if (this.type === 'preventive') return preventiveFormFromDto(null)
			if (this.type === 'medical') return medicalFormFromDto(null)
			if (this.type === 'medication') return medicationFormFromDto(null)
			if (this.type === 'allergy') return allergyFormFromDto(null)
			if (this.type === 'symptom') return symptomFormFromDto(null)
			return weightFormFromDto(null)
		},

		formFromDto(dto) {
			if (this.type === 'preventive') return preventiveFormFromDto(dto)
			if (this.type === 'medical') return medicalFormFromDto(dto)
			if (this.type === 'medication') return medicationFormFromDto(dto)
			if (this.type === 'allergy') return allergyFormFromDto(dto)
			if (this.type === 'symptom') return symptomFormFromDto(dto)
			return weightFormFromDto(dto)
		},

		saveReqFromForm() {
			if (this.type === 'preventive') return preventiveSaveReqFromForm(this.form)
			if (this.type === 'medical') return medicalSaveReqFromForm(this.form)
			if (this.type === 'medication') return medicationSaveReqFromForm(this.form)
			if (this.type === 'allergy') return allergySaveReqFromForm(this.form)
			if (this.type === 'symptom') return symptomSaveReqFromForm(this.form)
			return weightSaveReqFromForm(this.form)
		},

		validate() {
			if (this.type === 'preventive') return validatePreventiveForm(this.form)
			if (this.type === 'medical') return validateMedicalForm(this.form)
			if (this.type === 'medication') return validateMedicationForm(this.form)
			if (this.type === 'allergy') return validateAllergyForm(this.form)
			if (this.type === 'symptom') return validateSymptomForm(this.form)
			return validateWeightForm(this.form)
		},

		createByType(body) {
			if (this.type === 'preventive') return healthApi.createPreventive(this.pid, body)
			if (this.type === 'medical') return healthApi.createMedical(this.pid, body)
			if (this.type === 'medication') return healthApi.createMedication(this.pid, body)
			if (this.type === 'allergy') return healthApi.createAllergy(this.pid, body)
			if (this.type === 'symptom') return healthApi.createSymptom(this.pid, body)
			return healthApi.createWeight(this.pid, body)
		},

		updateByType(body) {
			if (this.type === 'preventive') return healthApi.updatePreventive(this.editId, body)
			if (this.type === 'medical') return healthApi.updateMedical(this.editId, body)
			if (this.type === 'medication') return healthApi.updateMedication(this.editId, body)
			if (this.type === 'allergy') return healthApi.updateAllergy(this.editId, body)
			if (this.type === 'symptom') return healthApi.updateSymptom(this.editId, body)
			return healthApi.updateWeight(this.editId, body)
		},

		/** 把表单整体换成新对象：Vue3 直接赋值不会漏掉旧键，也顺手把响应式重铺一次 */
		applyForm(next) {
			this.form = Object.assign({}, next)
		},

		setField(key, value) {
			const next = Object.assign({}, this.form)
			next[key] = value
			this.form = next
			this.validationError = ''
		},

		setMode(useOption) {
			if (this.form.useOption === useOption) return
			const next = Object.assign({}, this.form)
			next.useOption = useOption
			if (useOption && !next.symptomId && this.options.length) {
				// 切到库选但还没挑：不自动选，让用户自己点
			}
			this.form = next
			this.validationError = ''
		},

		toggleDrawer() {
			this.drawerOpen = !this.drawerOpen
		},

		async loadOptions() {
			this.optionsError = ''
			try {
				const rows = await healthApi.symptomOptions(this.optionsSpecies)
				this.options = Array.isArray(rows) ? rows : []
				this.optionsLoaded = true
			} catch (e) {
				if (this.noteAuth(e)) return
				this.optionsError = '症状库没读到：' + healthErrorMessage(e)
			}
		},

		selectOption(option) {
			const next = Object.assign({}, this.form)
			next.symptomId = String(option.symptomId)
			next.useOption = true
			this.form = next
			this.validationError = ''
			this.drawerOpen = false
		},

		/* ---------- 时间：日期 + 时间两个 picker 拼成 yyyy-MM-dd HH:mm:ss ---------- */

		datePart(value) {
			return toDateOnly(value) || toDateOnly(new Date())
		},

		timePart(value) {
			const d = value ? new Date(String(value).replace(' ', 'T')) : null
			if (!d || isNaN(d.getTime())) {
				const now = new Date()
				return (now.getHours() < 10 ? '0' + now.getHours() : String(now.getHours())) +
					':' + (now.getMinutes() < 10 ? '0' + now.getMinutes() : String(now.getMinutes()))
			}
			return (d.getHours() < 10 ? '0' + d.getHours() : String(d.getHours())) +
				':' + (d.getMinutes() < 10 ? '0' + d.getMinutes() : String(d.getMinutes()))
		},

		dateLabel(value) {
			if (!value) return '选日期'
			return toDateOnly(value)
		},

		timeLabel(value) {
			if (!value) return '选时间'
			return this.timePart(value)
		},

		combine(dateStr, timeStr) {
			const d = dateStr || toDateOnly(new Date())
			const t = timeStr || '00:00'
			return d + ' ' + t + ':00'
		},

		onPickDate(field, e) {
			const value = e && e.detail ? e.detail.value : ''
			this.setField(field, this.combine(value, this.timePart(this.form[field])))
		},

		onPickTime(field, e) {
			const value = e && e.detail ? e.detail.value : ''
			this.setField(field, this.combine(this.datePart(this.form[field]), value))
		},

		/* ---------- 图片 ---------- */

		imageList() {
			if (this.type === 'medical') return this.form.attachments || []
			return this.form.images || []
		},

		setImageList(list) {
			if (this.type === 'medical') this.setField('attachments', list)
			else this.setField('images', list)
		},

		removeImage(index) {
			const list = this.imageList().slice()
			list.splice(index, 1)
			this.setImageList(list)
		},

		shortUrl(url) {
			const s = String(url || '')
			if (s.length <= 46) return s
			return s.slice(0, 22) + '...' + s.slice(s.length - 20)
		},

		addImageUrl() {
			const url = text(this.imageUrlInput)
			this.uploadError = ''
			if (!url) {
				this.uploadError = '先把图片地址粘进输入框'
				return
			}
			const list = this.imageList().slice()
			list.push(url)
			this.setImageList(list)
			this.imageUrlInput = ''
		},

		chooseImages() {
			this.uploadError = ''
			const self = this
			uni.chooseImage({
				count: 3,
				success(res) {
					self.uploadPicked(res && res.tempFilePaths ? res.tempFilePaths : [])
				},
				fail() {
					self.uploadError = '没有选到图片（取消或没有相册权限）'
				}
			})
		},

		async uploadPicked(paths) {
			if (!paths || !paths.length) return
			this.uploadBusy = true
			const added = []
			try {
				for (let i = 0; i < paths.length; i++) {
					const resp = await healthApi.uploadHealthImage(paths[i])
					if (resp && resp.url) added.push(String(resp.url))
				}
				const list = this.imageList().slice().concat(added)
				this.setImageList(list)
			} catch (e) {
				this.uploadError = '图片上传失败：' + ((e && e.message) || '未知原因') + '（也可以先把图片地址粘进来）'
			} finally {
				this.uploadBusy = false
			}
		},

		/* ---------- 提交 ---------- */

		async submit() {
			this.submitError = ''
			const bad = this.validate()
			if (bad) {
				this.validationError = bad
				return
			}
			this.validationError = ''
			this.busy = true
			try {
				const body = this.saveReqFromForm()
				if (this.isEdit) await this.updateByType(body)
				else await this.createByType(body)
				uni.showToast({ title: this.isEdit ? '已保存' : '已记下', icon: 'none' })
				// 返回上一页；列表页 onShow 会自己重读
				uni.navigateBack({
					fail() {
						uni.switchTab({ url: '/pages/pets/pets' })
					}
				})
			} catch (e) {
				if (this.noteAuth(e)) return
				this.submitError = (this.isEdit ? '保存失败：' : '提交失败：') + healthErrorMessage(e)
			} finally {
				this.busy = false
			}
		},

		retry() {
			if (!this.loggedIn) {
				uni.navigateTo({ url: '/pages/login/login' })
				return
			}
			this.load()
		},

		goLogin() {
			if (this.unauthorized) auth.clearSession()
			uni.navigateTo({ url: '/pages/login/login' })
		},

		goBack() {
			uni.navigateBack({
				fail() {
					uni.switchTab({ url: '/pages/pets/pets' })
				}
			})
		},

		noteAuth(e) {
			if (!(e instanceof ApiError) || !e.isUnauthorized) return false
			this.unauthorized = true
			this.loadError = ''
			this.loading = false
			this.busy = false
			this.uploadBusy = false
			return true
		}
	}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
@import '@/styles/health.scss';

.notice--plain {
	background: $mw-surface;
	border-color: $mw-line;
}
.notice--plain text {
	color: $mw-ink-2;
}

/* ---- 表单字段 ---- */
.field {
	padding: $mw-space-3 0;
	border-bottom: 1px solid $mw-line;
}
.field:last-of-type {
	border-bottom: none;
}
.field__head {
	display: flex;
	align-items: center;
	margin-bottom: $mw-space-2;
}
.field__label {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.field__req {
	margin-left: $mw-space-1;
	padding: 2rpx 12rpx;
	border-radius: $mw-radius-tag;
	background: rgba(229, 72, 77, 0.12);
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-danger;
}
.field__opt {
	margin-left: $mw-space-1;
	font-size: 20rpx;
	color: $mw-muted;
}
.field__clear {
	margin-left: $mw-space-2;
	padding: 0 $mw-space-1;
	font-size: 20rpx;
	font-weight: 600;
	color: $mw-brand-ink;
}
.field__input {
	width: 100%;
	min-height: $mw-touch-min;
	padding: 0 $mw-space-3;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-body;
	color: $mw-ink;
}
.field__area {
	width: 100%;
	min-height: 160rpx;
	padding: $mw-space-2 $mw-space-3;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-caption;
	line-height: 1.7;
	color: $mw-ink;
}
.field__tip {
	display: block;
	margin-top: $mw-space-1;
	font-size: 22rpx;
	line-height: 1.6;
	color: $mw-muted;
}

/* ---- 日期 + 时间 ---- */
.dt {
	display: flex;
}
.dt__cell {
	flex: 1;
	margin-right: $mw-space-2;
}
.dt__cell:last-child {
	margin-right: 0;
}
.dt__box {
	min-height: $mw-touch-min;
	padding: 0 $mw-space-3;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	display: flex;
	align-items: center;
	justify-content: center;
}
.dt__box text {
	font-size: $mw-font-caption;
	color: $mw-ink;
	font-variant-numeric: tabular-nums;
}

/* ---- 症状库 ---- */
.optlist {
	max-height: 560rpx;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
}
.opt {
	padding: $mw-space-2 $mw-space-3;
	border-bottom: 1px solid $mw-line;
}
.opt:last-child {
	border-bottom: none;
}
.opt__name {
	display: block;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-ink;
}
.opt__desc {
	display: block;
	margin-top: 2rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: $mw-ink-2;
}
.opt__remark {
	display: block;
	margin-top: 2rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: $mw-accent-ink;
}
.picked {
	display: flex;
	align-items: center;
	margin-top: $mw-space-2;
}
.picked__label {
	flex: none;
	margin-right: $mw-space-2;
	font-size: $mw-font-micro;
	color: $mw-ink-2;
}
.picked__value {
	flex: 1;
	min-width: 0;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}

/* ---- 图片 ---- */
.shots__grid {
	display: flex;
	flex-wrap: wrap;
	margin-bottom: $mw-space-2;
}
.shots__img {
	width: 148rpx;
	height: 148rpx;
	margin: 0 $mw-space-2 $mw-space-2 0;
	border-radius: $mw-radius-input;
	background: $mw-canvas;
}
.urls {
	display: flex;
	flex-direction: column;
}
.urls__row {
	display: flex;
	align-items: center;
	margin-bottom: 6rpx;
}
.urls__text {
	flex: 1;
	min-width: 0;
	font-size: 20rpx;
	color: $mw-muted;
}
.urls__del {
	flex: none;
	margin-left: $mw-space-2;
	padding: 0 $mw-space-1;
	font-size: 22rpx;
	font-weight: 600;
	color: $mw-danger;
}
.urladd {
	display: flex;
	align-items: center;
	margin-top: $mw-space-2;
}
.urladd__input {
	flex: 1;
	min-width: 0;
	min-height: 76rpx;
	padding: 0 $mw-space-2;
	background: $mw-canvas;
	border: 1px solid $mw-line;
	border-radius: $mw-radius-input;
	font-size: $mw-font-micro;
	color: $mw-ink;
}
.urladd__btn {
	flex: none;
	margin-left: $mw-space-2;
	padding: 0 $mw-space-3;
	min-height: 76rpx;
	line-height: 76rpx;
	border-radius: $mw-radius-input;
	background: $mw-surface;
	border: 1px solid $mw-line;
	font-size: $mw-font-caption;
	font-weight: 600;
	color: $mw-brand-ink;
}
.picker-acts {
	margin-top: $mw-space-2;
}
.inline-err--in {
	margin: $mw-space-2 0 0;
}

/* ---- 提交 ---- */
.submit {
	margin-top: $mw-space-4;
}
.submit__cancel {
	margin-top: $mw-space-2;
}
</style>