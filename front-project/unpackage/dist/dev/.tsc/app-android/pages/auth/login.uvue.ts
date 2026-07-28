
const __sfc__ = defineComponent({
  __name: 'login',
  setup(__props) {
const __ins = getCurrentInstance()!;
const _ctx = __ins.proxy as InstanceType<typeof __sfc__>;
const _cache = __ins.renderCache;

const BASE = "http://localhost:8080"
const phone = ref("")

function doLogin(): void {
	if (phone.value === "" || phone.value.length !== 11) {
		uni.showToast({ title: "请输入正确的手机号", icon: "none" })
		return
	}
	const body = new UTSJSONObject(new UTSSourceMapPosition("body", "pages/auth/login.uvue", 33, 8))
	body["phone"] = phone.value
	body["code"] = "1234"
	uni.request({
		url: BASE + "/api/auth/login",
		method: "POST",
		data: body,
		header: new UTSJSONObject(),
		success: (res): void => {
			const d = res.data
			if (d != null) {
				const resp = d as UTSJSONObject
				const code = resp.getNumber("code")
				if (code === 200) {
					const data = resp.get("data")
					if (data != null) {
						const loginData = data as UTSJSONObject
						const t = loginData.getString("token")
						if (t != null) {
							uni.setStorageSync("token", t)
							uni.setStorageSync("phone", phone.value)
							uni.showToast({ title: "登录成功", icon: "success" })
							uni.reLaunch({ url: "/pages/index/index" })
							return
						}
					}
				}
				uni.showToast({ title: "手机号未注册", icon: "none" })
			}
		},
		fail: (): void => { uni.showToast({ title: "网络错误", icon: "none" }) }
	})
}

function goRegister(): void {
	uni.navigateTo({ url: "/pages/auth/register" })
}

return (): any | null => {

  return _cE("view", _uM({ class: "lg-page" }), [
    _cE("view", _uM({ class: "lg-card" }), [
      _cE("text", _uM({ class: "lg-logo" }), "🐱🐶"),
      _cE("text", _uM({ class: "lg-title" }), "喵汪"),
      _cE("text", _uM({ class: "lg-sub" }), "宠物养护助手"),
      _cE("view", _uM({ class: "lg-field" }), [
        _cE("text", _uM({ class: "lg-label" }), "手机号"),
        _cE("input", _uM({
          class: "lg-input",
          modelValue: unref(phone),
          onInput: ($event: UniInputEvent) => {trySetRefValue(phone, $event.detail.value)},
          type: "number",
          maxlength: "11",
          placeholder: "请输入手机号"
        }), null, 40 /* PROPS, NEED_HYDRATION */, ["modelValue"])
      ]),
      _cE("view", _uM({
        class: "lg-btn",
        onClick: doLogin
      }), [
        _cE("text", _uM({ class: "lg-btn-text" }), "登录")
      ]),
      _cE("view", _uM({
        class: "lg-switch",
        onClick: goRegister
      }), [
        _cE("text", _uM({ class: "lg-switch-text" }), "没有账号？去注册")
      ])
    ])
  ])
}
}

})
export default __sfc__
const GenPagesAuthLoginStyles = [_uM([["lg-page", _pS(_uM([["backgroundColor", "#FFF8F0"], ["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"], ["alignItems", "center"], ["justifyContent", "center"], ["paddingTop", "48rpx"], ["paddingRight", "48rpx"], ["paddingBottom", "48rpx"], ["paddingLeft", "48rpx"]]))], ["lg-card", _pS(_uM([["width", "640rpx"], ["paddingTop", "60rpx"], ["paddingRight", "48rpx"], ["paddingBottom", "60rpx"], ["paddingLeft", "48rpx"], ["backgroundColor", "#FFFFFF"], ["borderTopLeftRadius", "24rpx"], ["borderTopRightRadius", "24rpx"], ["borderBottomRightRadius", "24rpx"], ["borderBottomLeftRadius", "24rpx"], ["alignItems", "center"]]))], ["lg-logo", _pS(_uM([["fontSize", "80rpx"]]))], ["lg-title", _pS(_uM([["fontSize", "44rpx"], ["fontWeight", 700], ["color", "#FF8C42"], ["marginTop", "16rpx"]]))], ["lg-sub", _pS(_uM([["fontSize", "26rpx"], ["color", "#999999"], ["marginTop", "8rpx"], ["marginRight", 0], ["marginBottom", "48rpx"], ["marginLeft", 0]]))], ["lg-field", _pS(_uM([["width", "100%"], ["marginBottom", "32rpx"]]))], ["lg-label", _pS(_uM([["fontSize", "26rpx"], ["color", "#666666"], ["marginBottom", "10rpx"]]))], ["lg-input", _pS(_uM([["height", "88rpx"], ["paddingTop", 0], ["paddingRight", "24rpx"], ["paddingBottom", 0], ["paddingLeft", "24rpx"], ["backgroundColor", "#F5F5F5"], ["borderTopLeftRadius", "16rpx"], ["borderTopRightRadius", "16rpx"], ["borderBottomRightRadius", "16rpx"], ["borderBottomLeftRadius", "16rpx"], ["fontSize", "30rpx"], ["color", "#333333"]]))], ["lg-btn", _pS(_uM([["width", "100%"], ["height", "88rpx"], ["backgroundColor", "#FF8C42"], ["borderTopLeftRadius", "44rpx"], ["borderTopRightRadius", "44rpx"], ["borderBottomRightRadius", "44rpx"], ["borderBottomLeftRadius", "44rpx"], ["alignItems", "center"], ["justifyContent", "center"], ["marginTop", "16rpx"]]))], ["lg-btn-text", _pS(_uM([["fontSize", "32rpx"], ["color", "#FFFFFF"], ["fontWeight", 600]]))], ["lg-switch", _pS(_uM([["marginTop", "28rpx"], ["paddingTop", "12rpx"], ["paddingRight", "12rpx"], ["paddingBottom", "12rpx"], ["paddingLeft", "12rpx"]]))], ["lg-switch-text", _pS(_uM([["fontSize", "26rpx"], ["color", "#FF8C42"]]))]])]
