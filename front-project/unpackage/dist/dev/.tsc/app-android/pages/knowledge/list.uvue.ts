type KI = { __$originalPosition?: UTSSourceMapPosition<"KI", "pages/knowledge/list.uvue", 33, 6>; id: number; title: string; viewCount: number }
type CT = { __$originalPosition?: UTSSourceMapPosition<"CT", "pages/knowledge/list.uvue", 34, 6>; id: number; name: string; species: number }


const __sfc__ = defineComponent({
  __name: 'list',
  setup(__props) {
const __ins = getCurrentInstance()!;
const _ctx = __ins.proxy as InstanceType<typeof __sfc__>;
const _cache = __ins.renderCache;

const BASE = "http://localhost:8080"
const keyword = ref("")
const activeSpecies = ref(1)
const activeCategory = ref(0)
const categories = ref<CT[]>([])
const list = ref<KI[]>([])

const speciesOn = "padding:16rpx 48rpx;border-radius:36rpx;background-color:#FF8C42;margin-right:20rpx;"
const speciesOff = "padding:16rpx 48rpx;border-radius:36rpx;background-color:#F0F0F0;margin-right:20rpx;"
const speciesTextOn = "font-size:28rpx;color:#FFF;font-weight:600;"
const speciesTextOff = "font-size:28rpx;color:#666;"

function getToken(): string {
	const t = uni.getStorageSync("token")
	return t != null ? t as string : ""
}

function doRequest(url: string, onOk: (data: UTSJSONObject) => void): void {
	const token = getToken()
	const h = new UTSJSONObject(new UTSSourceMapPosition("h", "pages/knowledge/list.uvue", 54, 8))
	h["Content-Type"] = "application/json"
	if (token !== "") h["Authorization"] = "Bearer " + token
	uni.request({
		url: BASE + url,
		method: "GET",
		header: h,
		success: (res): void => {
			const d = res.data
			if (d != null) {
				const body = d as UTSJSONObject
				const code = body.getNumber("code")
				if (code === 200) {
					const inner = body.get("data")
					if (inner != null) onOk(inner as UTSJSONObject)
				}
			}
		},
		fail: (): void => {}
	})
}

function loadCats(): void {
	doRequest("/api/knowledge/categories?species=" + activeSpecies.value, (data: UTSJSONObject): void => {
		const arr = data as any as CT[]
		categories.value = arr
	})
}

function loadList(): void {
	let url = "/api/knowledge?page=1&size=20"
	if (activeCategory.value > 0) {
		url += "&categoryId=" + activeCategory.value
	} else {
		url += "&species=" + activeSpecies.value
	}
	doRequest(url, (data: UTSJSONObject): void => {
		const records = data.get("records")
		if (records != null) list.value = records as any as KI[]
	})
}

function switchSpecies(s: number): void {
	activeSpecies.value = s
	activeCategory.value = 0
	loadCats()
	loadList()
}

function selectCategory(id: number): void { activeCategory.value = id; loadList() }

function doSearch(): void {
	doRequest("/api/knowledge/search?keyword=" + keyword.value + "&page=1&size=20", (data: UTSJSONObject): void => {
		const records = data.get("records")
		if (records != null) list.value = records as any as KI[]
	})
}

function goDetail(id: number): void { uni.navigateTo({ url: "/pages/knowledge/detail?id=" + id }) }

onShow(() => { loadCats(); loadList() })

return (): any | null => {

  return _cE("view", _uM({ class: "kw-page" }), [
    _cE("view", _uM({ class: "kw-species-bar" }), [
      _cE("view", _uM({
        class: "kw-species-chip",
        style: _nS(unref(activeSpecies)===1?speciesOn:speciesOff),
        onClick: () => {switchSpecies(1)}
      }), [
        _cE("text", _uM({
          style: _nS(unref(activeSpecies)===1?speciesTextOn:speciesTextOff)
        }), "猫", 4 /* STYLE */)
      ], 12 /* STYLE, PROPS */, ["onClick"]),
      _cE("view", _uM({
        class: "kw-species-chip",
        style: _nS(unref(activeSpecies)===2?speciesOn:speciesOff),
        onClick: () => {switchSpecies(2)}
      }), [
        _cE("text", _uM({
          style: _nS(unref(activeSpecies)===2?speciesTextOn:speciesTextOff)
        }), "狗", 4 /* STYLE */)
      ], 12 /* STYLE, PROPS */, ["onClick"])
    ]),
    _cE("input", _uM({
      class: "kw-search",
      modelValue: unref(keyword),
      onInput: ($event: UniInputEvent) => {trySetRefValue(keyword, $event.detail.value)},
      placeholder: "搜索...",
      onConfirm: doSearch
    }), null, 40 /* PROPS, NEED_HYDRATION */, ["modelValue"]),
    _cE("scroll-view", _uM({
      "scroll-x": "true",
      class: "kw-cats"
    }), [
      _cE("view", _uM({ class: "kw-cats-inner" }), [
        _cE(Fragment, null, RenderHelpers.renderList(unref(categories), (cat, __key, __index, _cached): any => {
          return _cE("view", _uM({
            class: "kw-tag",
            key: cat.id,
            onClick: () => {selectCategory(cat.id)}
          }), [
            _cE("text", _uM({
              style: _nS(_uM({ color: unref(activeCategory)===cat.id ? '#FFF' : '#666' }))
            }), _tD(cat.name), 5 /* TEXT, STYLE */)
          ], 8 /* PROPS */, ["onClick"])
        }), 128 /* KEYED_FRAGMENT */)
      ])
    ]),
    _cE("scroll-view", _uM({
      "scroll-y": "true",
      class: "kw-list"
    }), [
      _cE(Fragment, null, RenderHelpers.renderList(unref(list), (item, __key, __index, _cached): any => {
        return _cE("view", _uM({
          class: "kw-item",
          key: item.id,
          onClick: () => {goDetail(item.id)}
        }), [
          _cE("view", _uM({ class: "kw-cover" })),
          _cE("view", _uM({ class: "kw-info" }), [
            _cE("text", _uM({ class: "kw-title" }), _tD(item.title), 1 /* TEXT */),
            _cE("text", _uM({ class: "kw-meta" }), _tD(item.viewCount) + " 次浏览", 1 /* TEXT */)
          ])
        ], 8 /* PROPS */, ["onClick"])
      }), 128 /* KEYED_FRAGMENT */)
    ])
  ])
}
}

})
export default __sfc__
const GenPagesKnowledgeListStyles = [_uM([["kw-page", _pS(_uM([["backgroundColor", "#FFF8F0"], ["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"]]))], ["kw-species-bar", _pS(_uM([["flexDirection", "row"], ["justifyContent", "center"], ["paddingTop", "20rpx"], ["paddingRight", "24rpx"], ["paddingBottom", "12rpx"], ["paddingLeft", "24rpx"], ["backgroundColor", "#FFFFFF"]]))], ["kw-species-chip", _pS(_uM([["alignItems", "center"], ["justifyContent", "center"]]))], ["kw-search", _pS(_uM([["height", "72rpx"], ["marginTop", "12rpx"], ["marginRight", "24rpx"], ["marginBottom", "12rpx"], ["marginLeft", "24rpx"], ["paddingTop", 0], ["paddingRight", "24rpx"], ["paddingBottom", 0], ["paddingLeft", "24rpx"], ["backgroundColor", "#F5F5F5"], ["borderTopLeftRadius", "36rpx"], ["borderTopRightRadius", "36rpx"], ["borderBottomRightRadius", "36rpx"], ["borderBottomLeftRadius", "36rpx"], ["fontSize", "28rpx"], ["color", "#333333"]]))], ["kw-cats", _pS(_uM([["height", "66rpx"], ["paddingLeft", "24rpx"], ["backgroundColor", "#FFFFFF"], ["flexDirection", "row"]]))], ["kw-cats-inner", _pS(_uM([["flexDirection", "row"]]))], ["kw-tag", _pS(_uM([["paddingTop", "8rpx"], ["paddingRight", "24rpx"], ["paddingBottom", "8rpx"], ["paddingLeft", "24rpx"], ["marginRight", "14rpx"], ["borderTopLeftRadius", "36rpx"], ["borderTopRightRadius", "36rpx"], ["borderBottomRightRadius", "36rpx"], ["borderBottomLeftRadius", "36rpx"], ["backgroundColor", "#F5F5F5"], ["alignItems", "center"], ["justifyContent", "center"]]))], ["kw-list", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["paddingTop", "8rpx"], ["paddingRight", 0], ["paddingBottom", "8rpx"], ["paddingLeft", 0]]))], ["kw-item", _pS(_uM([["flexDirection", "row"], ["alignItems", "center"], ["paddingTop", "20rpx"], ["paddingRight", "20rpx"], ["paddingBottom", "20rpx"], ["paddingLeft", "20rpx"], ["marginTop", "6rpx"], ["marginRight", "20rpx"], ["marginBottom", "6rpx"], ["marginLeft", "20rpx"], ["backgroundColor", "#FFFFFF"], ["borderTopLeftRadius", "14rpx"], ["borderTopRightRadius", "14rpx"], ["borderBottomRightRadius", "14rpx"], ["borderBottomLeftRadius", "14rpx"]]))], ["kw-cover", _pS(_uM([["width", "110rpx"], ["height", "110rpx"], ["borderTopLeftRadius", "10rpx"], ["borderTopRightRadius", "10rpx"], ["borderBottomRightRadius", "10rpx"], ["borderBottomLeftRadius", "10rpx"], ["backgroundColor", "#FFE0CC"], ["marginRight", "18rpx"]]))], ["kw-info", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"]]))], ["kw-title", _pS(_uM([["fontSize", "28rpx"], ["color", "#333333"], ["fontWeight", 600]]))], ["kw-meta", _pS(_uM([["fontSize", "22rpx"], ["color", "#999999"], ["marginTop", "6rpx"]]))]])]
