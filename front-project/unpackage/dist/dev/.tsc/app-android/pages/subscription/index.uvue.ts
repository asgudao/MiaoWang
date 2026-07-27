type PlanData = { __$originalPosition?: UTSSourceMapPosition<"PlanData", "pages/subscription/index.uvue", 20, 6>; id: number; name: string; price: number; durationDays: number; petLimit: number }


const __sfc__ = defineComponent({
  __name: 'index',
  setup(__props) {
const __ins = getCurrentInstance()!;
const _ctx = __ins.proxy as InstanceType<typeof __sfc__>;
const _cache = __ins.renderCache;

const BASE = "http://localhost:8080"

const plans = ref<PlanData[]>([])
const selectedPlanId = ref(0)

function priceText(price: number): string { return "¥" + (price / 100).toFixed(2) }

function getToken(): string {
	const t = uni.getStorageSync("token")
	return t != null ? t as string : ""
}

function loadPlans(): void {
	const token = getToken()
	const h = new UTSJSONObject(new UTSSourceMapPosition("h", "pages/subscription/index.uvue", 34, 8))
	h["Content-Type"] = "application/json"
	if (token !== "") h["Authorization"] = "Bearer " + token
	uni.request({
		url: BASE + "/api/subscription/plans",
		method: "GET",
		header: h,
		success: (res): void => {
			const d = res.data
			if (d != null) {
				const body = d as UTSJSONObject
				const code = body.getNumber("code")
				if (code === 200) {
					const inner = body.get("data")
					if (inner != null) {
						const arr = inner as any as PlanData[]
						plans.value = arr
						if (arr.length >= 2) selectedPlanId.value = arr[1].id
						else if (arr.length >= 1) selectedPlanId.value = arr[0].id
					}
				}
			}
		},
		fail: (): void => {}
	})
}

function selectPlan(id: number): void { selectedPlanId.value = id }

function buyNow(): void {
	const token = getToken()
	const h = new UTSJSONObject(new UTSSourceMapPosition("h", "pages/subscription/index.uvue", 65, 8))
	h["Content-Type"] = "application/json"
	if (token !== "") h["Authorization"] = "Bearer " + token
	uni.request({
		url: BASE + "/api/subscription/order?userId=1&planId=" + selectedPlanId.value,
		method: "POST",
		header: h,
		success: (): void => { uni.showToast({ title: "订单已创建", icon: "success" }) },
		fail: (): void => {}
	})
}

onShow(() => { loadPlans() })

return (): any | null => {

  return _cE("view", _uM({ class: "sp-page" }), [
    _cE("text", _uM({ class: "sp-title" }), "升级会员"),
    _cE("text", _uM({ class: "sp-sub" }), "解锁更多萌宠"),
    _cE("view", _uM({ class: "sp-plans" }), [
      _cE(Fragment, null, RenderHelpers.renderList(unref(plans), (p, __key, __index, _cached): any => {
        return _cE("view", _uM({
          class: "sp-card",
          key: p.id,
          onClick: () => {selectPlan(p.id)},
          style: _nS(_uM({ borderWidth: unref(selectedPlanId) === p.id ? '3rpx' : '2rpx', borderColor: unref(selectedPlanId) === p.id ? '#FF8C42' : '#E0E0E0' }))
        }), [
          _cE("text", _uM({ class: "sp-name" }), _tD(p.name), 1 /* TEXT */),
          _cE("text", _uM({ class: "sp-price" }), _tD(priceText(p.price)), 1 /* TEXT */),
          _cE("text", _uM({ class: "sp-dur" }), _tD(p.durationDays) + " 天", 1 /* TEXT */),
          _cE("text", _uM({ class: "sp-limit" }), _tD(p.petLimit) + " 只萌宠", 1 /* TEXT */)
        ], 12 /* STYLE, PROPS */, ["onClick"])
      }), 128 /* KEYED_FRAGMENT */)
    ]),
    _cE("view", _uM({
      class: "sp-buy",
      onClick: buyNow
    }), [
      _cE("text", _uM({ class: "sp-buy-text" }), "立即购买")
    ])
  ])
}
}

})
export default __sfc__
const GenPagesSubscriptionIndexStyles = [_uM([["sp-page", _pS(_uM([["backgroundColor", "#FFF8F0"], ["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"], ["alignItems", "center"], ["paddingTop", "60rpx"]]))], ["sp-title", _pS(_uM([["fontSize", "38rpx"], ["fontWeight", 700], ["color", "#333333"]]))], ["sp-sub", _pS(_uM([["fontSize", "24rpx"], ["color", "#999999"], ["marginTop", "8rpx"], ["marginRight", 0], ["marginBottom", "40rpx"], ["marginLeft", 0]]))], ["sp-plans", _pS(_uM([["flexDirection", "row"], ["paddingTop", 0], ["paddingRight", "14rpx"], ["paddingBottom", 0], ["paddingLeft", "14rpx"]]))], ["sp-card", _pS(_uM([["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["alignItems", "center"], ["marginTop", 0], ["marginRight", "6rpx"], ["marginBottom", 0], ["marginLeft", "6rpx"], ["paddingTop", "28rpx"], ["paddingRight", "14rpx"], ["paddingBottom", "28rpx"], ["paddingLeft", "14rpx"], ["backgroundColor", "#FFFFFF"], ["borderTopLeftRadius", "18rpx"], ["borderTopRightRadius", "18rpx"], ["borderBottomRightRadius", "18rpx"], ["borderBottomLeftRadius", "18rpx"]]))], ["sp-name", _pS(_uM([["fontSize", "26rpx"], ["fontWeight", 700], ["color", "#333333"], ["marginBottom", "12rpx"]]))], ["sp-price", _pS(_uM([["fontSize", "44rpx"], ["fontWeight", 700], ["color", "#FF8C42"]]))], ["sp-dur", _pS(_uM([["fontSize", "22rpx"], ["color", "#999999"], ["marginTop", "4rpx"], ["marginRight", 0], ["marginBottom", "10rpx"], ["marginLeft", 0]]))], ["sp-limit", _pS(_uM([["fontSize", "22rpx"], ["color", "#666666"]]))], ["sp-buy", _pS(_uM([["marginTop", "50rpx"], ["width", "600rpx"], ["paddingTop", "22rpx"], ["paddingRight", "22rpx"], ["paddingBottom", "22rpx"], ["paddingLeft", "22rpx"], ["backgroundColor", "#FF8C42"], ["borderTopLeftRadius", "44rpx"], ["borderTopRightRadius", "44rpx"], ["borderBottomRightRadius", "44rpx"], ["borderBottomLeftRadius", "44rpx"], ["alignItems", "center"]]))], ["sp-buy-text", _pS(_uM([["fontSize", "30rpx"], ["color", "#FFFFFF"], ["fontWeight", 600]]))]])]
