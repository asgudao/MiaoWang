type PetData = { __$originalPosition?: UTSSourceMapPosition<"PetData", "pages/pets/list.uvue", 23, 6>; id: number; name: string; breedName: string; gender: number; age: number; weight: number }


const __sfc__ = defineComponent({
  __name: 'list',
  setup(__props) {
const __ins = getCurrentInstance()!;
const _ctx = __ins.proxy as InstanceType<typeof __sfc__>;
const _cache = __ins.renderCache;

const BASE = "http://localhost:8080"

const pets = ref<PetData[]>([])

function getToken(): string {
	const token = uni.getStorageSync("token")
	return token != null ? token as string : ""
}

function loadPets(): void {
	const token = getToken()
	const h = new UTSJSONObject(new UTSSourceMapPosition("h", "pages/pets/list.uvue", 34, 8))
	h["Content-Type"] = "application/json"
	if (token !== "") h["Authorization"] = "Bearer " + token
	uni.request({
		url: BASE + "/api/pets",
		method: "GET",
		header: h,
		success: (res): void => {
			const d = res.data
			if (d != null) {
				const body = d as UTSJSONObject
				const code = body.getNumber("code")
				if (code === 200) {
					const inner = body.get("data")
					if (inner != null) pets.value = inner as any as PetData[]
				}
			}
		},
		fail: (): void => {}
	})
}

function addPet(): void {
	uni.navigateTo({ url: "/pages/pets/add" })
}

onShow(() => { loadPets() })

return (): any | null => {

  return _cE("view", _uM({ class: "pl-page" }), [
    _cE("view", _uM({ class: "pl-header" }), [
      _cE("text", _uM({ class: "pl-title" }), "我的萌宠")
    ]),
    _cE("scroll-view", _uM({
      "scroll-y": "true",
      class: "pl-list"
    }), [
      _cE(Fragment, null, RenderHelpers.renderList(unref(pets), (pet, __key, __index, _cached): any => {
        return _cE("view", _uM({
          class: "pl-card",
          key: pet.id
        }), [
          _cE("view", _uM({ class: "pl-top" }), [
            _cE("text", _uM({ class: "pl-avatar" }), "🐱"),
            _cE("view", _uM({ class: "pl-info" }), [
              _cE("text", _uM({ class: "pl-name" }), _tD(pet.name), 1 /* TEXT */),
              _cE("text", _uM({ class: "pl-breed" }), _tD(pet.breedName !== "" ? pet.breedName : "未知品种"), 1 /* TEXT */),
              _cE("text", _uM({ class: "pl-detail" }), _tD(pet.gender === 1 ? "公" : "母") + " | " + _tD(pet.age) + " 岁 | " + _tD(pet.weight) + "kg", 1 /* TEXT */)
            ])
          ])
        ])
      }), 128 /* KEYED_FRAGMENT */)
    ]),
    _cE("view", _uM({
      class: "pl-add",
      onClick: addPet
    }), [
      _cE("text", _uM({ class: "pl-add-text" }), "+ 添加宠物")
    ])
  ])
}
}

})
export default __sfc__
const GenPagesPetsListStyles = [_uM([["pl-page", _pS(_uM([["backgroundColor", "#FFF8F0"], ["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"]]))], ["pl-header", _pS(_uM([["paddingTop", "32rpx"], ["paddingRight", "28rpx"], ["paddingBottom", "16rpx"], ["paddingLeft", "28rpx"]]))], ["pl-title", _pS(_uM([["fontSize", "40rpx"], ["fontWeight", 700], ["color", "#333333"]]))], ["pl-list", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["paddingTop", "8rpx"], ["paddingRight", 0], ["paddingBottom", "8rpx"], ["paddingLeft", 0]]))], ["pl-card", _pS(_uM([["marginTop", "12rpx"], ["marginRight", "24rpx"], ["marginBottom", "12rpx"], ["marginLeft", "24rpx"], ["paddingTop", "24rpx"], ["paddingRight", "24rpx"], ["paddingBottom", "24rpx"], ["paddingLeft", "24rpx"], ["backgroundColor", "#FFFFFF"], ["borderTopLeftRadius", "18rpx"], ["borderTopRightRadius", "18rpx"], ["borderBottomRightRadius", "18rpx"], ["borderBottomLeftRadius", "18rpx"]]))], ["pl-top", _pS(_uM([["flexDirection", "row"], ["alignItems", "center"]]))], ["pl-avatar", _pS(_uM([["fontSize", "72rpx"], ["marginRight", "20rpx"]]))], ["pl-info", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"]]))], ["pl-name", _pS(_uM([["fontSize", "32rpx"], ["fontWeight", 700], ["color", "#333333"]]))], ["pl-breed", _pS(_uM([["fontSize", "24rpx"], ["color", "#999999"], ["marginTop", "4rpx"]]))], ["pl-detail", _pS(_uM([["fontSize", "22rpx"], ["color", "#666666"], ["marginTop", "4rpx"]]))], ["pl-add", _pS(_uM([["marginTop", "20rpx"], ["marginRight", "24rpx"], ["marginBottom", "20rpx"], ["marginLeft", "24rpx"], ["paddingTop", "20rpx"], ["paddingRight", "20rpx"], ["paddingBottom", "20rpx"], ["paddingLeft", "20rpx"], ["backgroundColor", "#FF8C42"], ["borderTopLeftRadius", "16rpx"], ["borderTopRightRadius", "16rpx"], ["borderBottomRightRadius", "16rpx"], ["borderBottomLeftRadius", "16rpx"], ["alignItems", "center"]]))], ["pl-add-text", _pS(_uM([["fontSize", "28rpx"], ["color", "#FFFFFF"], ["fontWeight", 600]]))]])]
