type FavItem = { __$originalPosition?: UTSSourceMapPosition<"FavItem", "pages/favorites/index.uvue", 22, 6>; id: number; title: string; time: string }


const __sfc__ = defineComponent({
  __name: 'index',
  setup(__props) {
const __ins = getCurrentInstance()!;
const _ctx = __ins.proxy as InstanceType<typeof __sfc__>;
const _cache = __ins.renderCache;

const favList = ref<FavItem[]>([])

function loadFavs(): void {
	const raw = uni.getStorageSync("favorites")
	if (raw != null && raw !== "") {
		const arr = UTSAndroid.consoleDebugError(JSON.parse(raw as string), " at pages/favorites/index.uvue:29") as any[]
		const result: FavItem[] = []
		for (let i = 0; i < arr.length; i++) {
			const item = arr[i] as UTSJSONObject
			const nid = item.getNumber("id")
			const ntitle = item.getString("title")
			const ntime = item.getString("time")
			result.push({
				id: nid != null ? nid : 0,
				title: ntitle != null ? ntitle : "",
				time: ntime != null ? ntime : ""
			})
		}
		favList.value = result
	} else {
		favList.value = []
	}
}

function removeFav(id: number): void {
	const raw = uni.getStorageSync("favorites")
	if (raw != null && raw !== "") {
		const arr = UTSAndroid.consoleDebugError(JSON.parse(raw as string), " at pages/favorites/index.uvue:51") as any[]
		const filtered: any[] = []
		for (let i = 0; i < arr.length; i++) {
			const item = arr[i] as UTSJSONObject
			const nid = item.getNumber("id")
			if (nid != null && nid !== id) filtered.push(item)
		}
		uni.setStorageSync("favorites", JSON.stringify(filtered))
	}
	loadFavs()
	uni.showToast({ title: "已取消收藏", icon: "none" })
}

function goDetail(id: number): void {
	uni.navigateTo({ url: "/pages/knowledge/detail?id=" + id })
}

onShow(() => { loadFavs() })

return (): any | null => {

  return _cE("view", _uM({ class: "fv-page" }), [
    unref(favList).length === 0
      ? _cE("view", _uM({
          key: 0,
          class: "fv-empty"
        }), [
          _cE("text", _uM({ class: "fv-empty-icon" }), "⭐"),
          _cE("text", _uM({ class: "fv-empty-text" }), "还没有收藏"),
          _cE("text", _uM({ class: "fv-empty-hint" }), "在知识库文章详情页可以收藏")
        ])
      : _cC("v-if", true),
    unref(favList).length > 0
      ? _cE("scroll-view", _uM({
          key: 1,
          "scroll-y": "true",
          class: "fv-scroll"
        }), [
          _cE(Fragment, null, RenderHelpers.renderList(unref(favList), (item, __key, __index, _cached): any => {
            return _cE("view", _uM({
              class: "fv-item",
              key: item.id,
              onClick: () => {goDetail(item.id)}
            }), [
              _cE("view", _uM({ class: "fv-cover" })),
              _cE("view", _uM({ class: "fv-info" }), [
                _cE("text", _uM({ class: "fv-title" }), _tD(item.title), 1 /* TEXT */),
                _cE("text", _uM({ class: "fv-time" }), _tD(item.time), 1 /* TEXT */)
              ]),
              _cE("view", _uM({
                class: "fv-del",
                onClick: withModifiers(() => {removeFav(item.id)}, ["stop"])
              }), [
                _cE("text", _uM({ class: "fv-del-text" }), "✕")
              ], 8 /* PROPS */, ["onClick"])
            ], 8 /* PROPS */, ["onClick"])
          }), 128 /* KEYED_FRAGMENT */)
        ])
      : _cC("v-if", true)
  ])
}
}

})
export default __sfc__
const GenPagesFavoritesIndexStyles = [_uM([["fv-page", _pS(_uM([["backgroundColor", "#FFF8F0"], ["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"]]))], ["fv-empty", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["alignItems", "center"], ["justifyContent", "center"]]))], ["fv-empty-icon", _pS(_uM([["fontSize", "80rpx"], ["marginBottom", "20rpx"]]))], ["fv-empty-text", _pS(_uM([["fontSize", "30rpx"], ["color", "#999999"], ["fontWeight", 600]]))], ["fv-empty-hint", _pS(_uM([["fontSize", "24rpx"], ["color", "#CCCCCC"], ["marginTop", "10rpx"]]))], ["fv-scroll", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["paddingTop", "16rpx"], ["paddingRight", "24rpx"], ["paddingBottom", "16rpx"], ["paddingLeft", "24rpx"]]))], ["fv-item", _pS(_uM([["flexDirection", "row"], ["alignItems", "center"], ["paddingTop", "20rpx"], ["paddingRight", "20rpx"], ["paddingBottom", "20rpx"], ["paddingLeft", "20rpx"], ["marginBottom", "10rpx"], ["backgroundColor", "#FFFFFF"], ["borderTopLeftRadius", "14rpx"], ["borderTopRightRadius", "14rpx"], ["borderBottomRightRadius", "14rpx"], ["borderBottomLeftRadius", "14rpx"]]))], ["fv-cover", _pS(_uM([["width", "90rpx"], ["height", "90rpx"], ["borderTopLeftRadius", "10rpx"], ["borderTopRightRadius", "10rpx"], ["borderBottomRightRadius", "10rpx"], ["borderBottomLeftRadius", "10rpx"], ["backgroundColor", "#FFE0CC"], ["marginRight", "16rpx"]]))], ["fv-info", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"]]))], ["fv-title", _pS(_uM([["fontSize", "28rpx"], ["color", "#333333"], ["fontWeight", 600]]))], ["fv-time", _pS(_uM([["fontSize", "22rpx"], ["color", "#CCCCCC"], ["marginTop", "6rpx"]]))], ["fv-del", _pS(_uM([["width", "56rpx"], ["height", "56rpx"], ["borderTopLeftRadius", "28rpx"], ["borderTopRightRadius", "28rpx"], ["borderBottomRightRadius", "28rpx"], ["borderBottomLeftRadius", "28rpx"], ["backgroundColor", "#FFEEEE"], ["alignItems", "center"], ["justifyContent", "center"]]))], ["fv-del-text", _pS(_uM([["fontSize", "28rpx"], ["color", "#FF4444"]]))]])]
