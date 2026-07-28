type BreedItem = { __$originalPosition?: UTSSourceMapPosition<"BreedItem", "pages/pets/add.uvue", 44, 6>; id: number; name: string; species: number }


const __sfc__ = defineComponent({
  __name: 'add',
  setup(__props) {
const __ins = getCurrentInstance()!;
const _ctx = __ins.proxy as InstanceType<typeof __sfc__>;
const _cache = __ins.renderCache;

const BASE = "http://localhost:8080"

const name = ref("")
const species = ref(1)
const gender = ref(1)
const age = ref("")
const weight = ref("")
const breeds = ref<BreedItem[]>([])
const breedNames = ref<string[]>([])
const selectedBreedIdx = ref(-1)
const selectedBreedId = ref(0)

const chipOn = "padding:16rpx 36rpx;border-radius:32rpx;background-color:#FF8C42;margin-right:20rpx;"
const chipOff = "padding:16rpx 36rpx;border-radius:32rpx;background-color:#F0F0F0;margin-right:20rpx;"
const chipTextOn = "font-size:28rpx;color:#FFF;font-weight:600;"
const chipTextOff = "font-size:28rpx;color:#666;"

function getToken(): string {
	const t = uni.getStorageSync("token")
	return t != null ? t as string : ""
}

function loadBreeds(): void {
	const token = getToken()
	if (token === "") { setTimeout(() => { loadBreeds() }, 1000); return }
	const h = new UTSJSONObject(new UTSSourceMapPosition("h", "pages/pets/add.uvue", 69, 8))
	h["Content-Type"] = "application/json"
	h["Authorization"] = "Bearer " + token
	uni.request({
		url: BASE + "/api/breeds?species=" + species.value,
		method: "GET",
		header: h,
		success: (res): void => {
			const d = res.data
			if (d != null) {
				const body = d as UTSJSONObject
				if (body.getNumber("code") === 200) {
					const data = body.get("data")
					if (data != null) {
						breeds.value = data as any as BreedItem[]
						const names: string[] = []
						for (let i = 0; i < breeds.value.length; i++) { names.push(breeds.value[i].name) }
						breedNames.value = names
					}
				}
			}
		},
		fail: (): void => {}
	})
}

function onBreedPick(e: UTSJSONObject): void {
	const detail = e.get("detail") as UTSJSONObject
	const idxNum = detail.getNumber("value")
	const idx: number = idxNum != null ? idxNum : 0
	selectedBreedIdx.value = idx
	selectedBreedId.value = breeds.value[idx].id
}

function switchSpecies(s: number): void {
	species.value = s
	selectedBreedIdx.value = -1
	selectedBreedId.value = 0
	breedNames.value = []
	loadBreeds()
}

function submit(): void {
	if (name.value === "") { uni.showToast({ title: "请输入宠物名字", icon: "none" }); return }
	if (selectedBreedId.value === 0) { uni.showToast({ title: "请选择品种", icon: "none" }); return }
	if (age.value === "") { uni.showToast({ title: "请输入年龄", icon: "none" }); return }
	if (weight.value === "") { uni.showToast({ title: "请输入体重", icon: "none" }); return }

	const token = getToken()
	const h = new UTSJSONObject(new UTSSourceMapPosition("h", "pages/pets/add.uvue", 118, 8))
	h["Content-Type"] = "application/json"
	if (token !== "") h["Authorization"] = "Bearer " + token

	const body = new UTSJSONObject(new UTSSourceMapPosition("body", "pages/pets/add.uvue", 122, 8))
	body["userId"] = 1
	body["name"] = name.value
	body["species"] = species.value
	body["breedId"] = selectedBreedId.value
	body["gender"] = gender.value
	body["age"] = parseInt(age.value)
	body["weight"] = parseFloat(weight.value)

	uni.request({
		url: BASE + "/api/pets",
		method: "POST",
		data: body,
		header: h,
		success: (res): void => {
			const d = res.data
			if (d != null) {
				const resp = d as UTSJSONObject
				if (resp.getNumber("code") === 200) {
					uni.showToast({ title: "添加成功", icon: "success" })
					uni.navigateBack()
				} else {
					uni.showToast({ title: "添加失败", icon: "none" })
				}
			}
		},
		fail: (): void => { uni.showToast({ title: "网络错误", icon: "none" }) }
	})
}

onShow(() => { loadBreeds() })

return (): any | null => {

const _component_picker = resolveComponent("picker")

  return _cE("view", _uM({ class: "ap-page" }), [
    _cE("scroll-view", _uM({
      "scroll-y": "true",
      class: "ap-scroll"
    }), [
      _cE("view", _uM({ class: "ap-field" }), [
        _cE("text", _uM({ class: "ap-label" }), "宠物名字"),
        _cE("input", _uM({
          class: "ap-input",
          modelValue: unref(name),
          onInput: ($event: UniInputEvent) => {trySetRefValue(name, $event.detail.value)},
          placeholder: "给宠物起个名字"
        }), null, 40 /* PROPS, NEED_HYDRATION */, ["modelValue"])
      ]),
      _cE("view", _uM({ class: "ap-field" }), [
        _cE("text", _uM({ class: "ap-label" }), "种类"),
        _cE("view", _uM({ class: "ap-row" }), [
          _cE("view", _uM({
            class: "ap-chip",
            style: _nS(unref(species)===1?chipOn:chipOff),
            onClick: () => {switchSpecies(1)}
          }), [
            _cE("text", _uM({
              style: _nS(unref(species)===1?chipTextOn:chipTextOff)
            }), "猫", 4 /* STYLE */)
          ], 12 /* STYLE, PROPS */, ["onClick"]),
          _cE("view", _uM({
            class: "ap-chip",
            style: _nS(unref(species)===2?chipOn:chipOff),
            onClick: () => {switchSpecies(2)}
          }), [
            _cE("text", _uM({
              style: _nS(unref(species)===2?chipTextOn:chipTextOff)
            }), "狗", 4 /* STYLE */)
          ], 12 /* STYLE, PROPS */, ["onClick"])
        ])
      ]),
      _cE("view", _uM({ class: "ap-field" }), [
        _cE("text", _uM({ class: "ap-label" }), "品种"),
        _cV(_component_picker, _uM({
          range: unref(breedNames),
          onChange: onBreedPick
        }), _uM({
          default: withSlotCtx((): any[] => [
            _cE("view", _uM({ class: "ap-picker" }), [
              _cE("text", null, _tD(unref(breedNames).length>0&&unref(selectedBreedIdx)>=0 ? unref(breedNames)[unref(selectedBreedIdx)] : "请选择品种"), 1 /* TEXT */)
            ])
          ]),
          _: 1 /* STABLE */
        }), 8 /* PROPS */, ["range"])
      ]),
      _cE("view", _uM({ class: "ap-field" }), [
        _cE("text", _uM({ class: "ap-label" }), "性别"),
        _cE("view", _uM({ class: "ap-row" }), [
          _cE("view", _uM({
            class: "ap-chip",
            style: _nS(unref(gender)===1?chipOn:chipOff),
            onClick: () => {gender.value=1}
          }), [
            _cE("text", _uM({
              style: _nS(unref(gender)===1?chipTextOn:chipTextOff)
            }), "公", 4 /* STYLE */)
          ], 12 /* STYLE, PROPS */, ["onClick"]),
          _cE("view", _uM({
            class: "ap-chip",
            style: _nS(unref(gender)===2?chipOn:chipOff),
            onClick: () => {gender.value=2}
          }), [
            _cE("text", _uM({
              style: _nS(unref(gender)===2?chipTextOn:chipTextOff)
            }), "母", 4 /* STYLE */)
          ], 12 /* STYLE, PROPS */, ["onClick"])
        ])
      ]),
      _cE("view", _uM({ class: "ap-field" }), [
        _cE("text", _uM({ class: "ap-label" }), "年龄(月)"),
        _cE("input", _uM({
          class: "ap-input",
          modelValue: unref(age),
          onInput: ($event: UniInputEvent) => {trySetRefValue(age, $event.detail.value)},
          type: "number",
          placeholder: "输入月龄"
        }), null, 40 /* PROPS, NEED_HYDRATION */, ["modelValue"])
      ]),
      _cE("view", _uM({ class: "ap-field" }), [
        _cE("text", _uM({ class: "ap-label" }), "体重(kg)"),
        _cE("input", _uM({
          class: "ap-input",
          modelValue: unref(weight),
          onInput: ($event: UniInputEvent) => {trySetRefValue(weight, $event.detail.value)},
          type: "number",
          placeholder: "输入体重"
        }), null, 40 /* PROPS, NEED_HYDRATION */, ["modelValue"])
      ]),
      _cE("view", _uM({
        class: "ap-submit",
        onClick: submit
      }), [
        _cE("text", _uM({ class: "ap-submit-text" }), "添加宠物")
      ])
    ])
  ])
}
}

})
export default __sfc__
const GenPagesPetsAddStyles = [_uM([["ap-page", _pS(_uM([["backgroundColor", "#FFF8F0"], ["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"]]))], ["ap-scroll", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["paddingTop", "24rpx"], ["paddingRight", "24rpx"], ["paddingBottom", "24rpx"], ["paddingLeft", "24rpx"]]))], ["ap-field", _pS(_uM([["marginBottom", "32rpx"]]))], ["ap-label", _pS(_uM([["fontSize", "28rpx"], ["fontWeight", 600], ["color", "#333333"], ["marginBottom", "12rpx"]]))], ["ap-input", _pS(_uM([["height", "80rpx"], ["paddingTop", 0], ["paddingRight", "24rpx"], ["paddingBottom", 0], ["paddingLeft", "24rpx"], ["backgroundColor", "#FFFFFF"], ["borderTopLeftRadius", "16rpx"], ["borderTopRightRadius", "16rpx"], ["borderBottomRightRadius", "16rpx"], ["borderBottomLeftRadius", "16rpx"], ["fontSize", "28rpx"], ["color", "#333333"]]))], ["ap-row", _pS(_uM([["flexDirection", "row"]]))], ["ap-chip", _pS(_uM([["alignItems", "center"], ["justifyContent", "center"]]))], ["ap-picker", _pS(_uM([["height", "80rpx"], ["paddingTop", 0], ["paddingRight", "24rpx"], ["paddingBottom", 0], ["paddingLeft", "24rpx"], ["backgroundColor", "#FFFFFF"], ["borderTopLeftRadius", "16rpx"], ["borderTopRightRadius", "16rpx"], ["borderBottomRightRadius", "16rpx"], ["borderBottomLeftRadius", "16rpx"], ["justifyContent", "center"]]))], ["ap-submit", _pS(_uM([["marginTop", "40rpx"], ["height", "88rpx"], ["backgroundColor", "#FF8C42"], ["borderTopLeftRadius", "44rpx"], ["borderTopRightRadius", "44rpx"], ["borderBottomRightRadius", "44rpx"], ["borderBottomLeftRadius", "44rpx"], ["alignItems", "center"], ["justifyContent", "center"]]))], ["ap-submit-text", _pS(_uM([["fontSize", "32rpx"], ["color", "#FFFFFF"], ["fontWeight", 600]]))]])]
