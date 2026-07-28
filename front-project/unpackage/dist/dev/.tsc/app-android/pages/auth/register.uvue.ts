
const __sfc__ = defineComponent({
  __name: 'register',
  setup(__props) {
const __ins = getCurrentInstance()!;
const _ctx = __ins.proxy as InstanceType<typeof __sfc__>;
const _cache = __ins.renderCache;

const BASE = "http://localhost:8080"
const phone = ref("")
const nickname = ref("")

function doRegister(): void {
	if (phone.value === "" || phone.value.length !== 11) {
		uni.showToast({ title: "请输入正确的手机号", icon: "none" })
		return
	}
	const body = new UTSJSONObject(new UTSSourceMapPosition("body", "pages/auth/register.uvue", 38, 8))
	body["phone"] = phone.value
	body["code"] = "1234"
	if (nickname.value !== "") body["nickname"] = nickname.value
	uni.request({
		url: BASE + "/api/auth/register",
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
							uni.showToast({ title: "注册成功", icon: "success" })
							uni.reLaunch({ url: "/pages/index/index" })
							return
						}
					}
				}
				uni.showToast({ title: "该手机号已注册", icon: "none" })
			}
		},
		fail: (): void => { uni.showToast({ title: "网络错误", icon: "none" }) }
	})
}

function goLogin(): void {
	uni.navigateBack()
}

return (): any | null => {

  return _cE("view", _uM({ class: "rg-page" }), [
    _cE("view", _uM({ class: "rg-card" }), [
      _cE("text", _uM({ class: "rg-title" }), "注册账号"),
      _cE("text", _uM({ class: "rg-sub" }), "加入喵汪，科学养宠"),
      _cE("view", _uM({ class: "rg-field" }), [
        _cE("text", _uM({ class: "rg-label" }), "手机号"),
        _cE("input", _uM({
          class: "rg-input",
          modelValue: unref(phone),
          onInput: ($event: UniInputEvent) => {trySetRefValue(phone, $event.detail.value)},
          type: "number",
          maxlength: "11",
          placeholder: "请输入手机号"
        }), null, 40 /* PROPS, NEED_HYDRATION */, ["modelValue"])
      ]),
      _cE("view", _uM({ class: "rg-field" }), [
        _cE("text", _uM({ class: "rg-label" }), "昵称（选填）"),
        _cE("input", _uM({
          class: "rg-input",
          modelValue: unref(nickname),
          onInput: ($event: UniInputEvent) => {trySetRefValue(nickname, $event.detail.value)},
          placeholder: "给自己起个昵称"
        }), null, 40 /* PROPS, NEED_HYDRATION */, ["modelValue"])
      ]),
      _cE("view", _uM({
        class: "rg-btn",
        onClick: doRegister
      }), [
        _cE("text", _uM({ class: "rg-btn-text" }), "注册")
      ]),
      _cE("view", _uM({
        class: "rg-switch",
        onClick: goLogin
      }), [
        _cE("text", _uM({ class: "rg-switch-text" }), "已有账号？去登录")
      ])
    ])
  ])
}
}

})
export default __sfc__
const GenPagesAuthRegisterStyles = [_uM([["rg-page", _pS(_uM([["backgroundColor", "#FFF8F0"], ["flexGrow", 1], ["flexShrink", 1], ["flexBasis", "0%"], ["flexDirection", "column"], ["alignItems", "center"], ["justifyContent", "center"], ["paddingTop", "48rpx"], ["paddingRight", "48rpx"], ["paddingBottom", "48rpx"], ["paddingLeft", "48rpx"]]))], ["rg-card", _pS(_uM([["width", "640rpx"], ["paddingTop", "60rpx"], ["paddingRight", "48rpx"], ["paddingBottom", "60rpx"], ["paddingLeft", "48rpx"], ["backgroundColor", "#FFFFFF"], ["borderTopLeftRadius", "24rpx"], ["borderTopRightRadius", "24rpx"], ["borderBottomRightRadius", "24rpx"], ["borderBottomLeftRadius", "24rpx"], ["alignItems", "center"]]))], ["rg-title", _pS(_uM([["fontSize", "38rpx"], ["fontWeight", 700], ["color", "#333333"]]))], ["rg-sub", _pS(_uM([["fontSize", "26rpx"], ["color", "#999999"], ["marginTop", "8rpx"], ["marginRight", 0], ["marginBottom", "48rpx"], ["marginLeft", 0]]))], ["rg-field", _pS(_uM([["width", "100%"], ["marginBottom", "28rpx"]]))], ["rg-label", _pS(_uM([["fontSize", "26rpx"], ["color", "#666666"], ["marginBottom", "10rpx"]]))], ["rg-input", _pS(_uM([["height", "88rpx"], ["paddingTop", 0], ["paddingRight", "24rpx"], ["paddingBottom", 0], ["paddingLeft", "24rpx"], ["backgroundColor", "#F5F5F5"], ["borderTopLeftRadius", "16rpx"], ["borderTopRightRadius", "16rpx"], ["borderBottomRightRadius", "16rpx"], ["borderBottomLeftRadius", "16rpx"], ["fontSize", "30rpx"], ["color", "#333333"]]))], ["rg-btn", _pS(_uM([["width", "100%"], ["height", "88rpx"], ["backgroundColor", "#FF8C42"], ["borderTopLeftRadius", "44rpx"], ["borderTopRightRadius", "44rpx"], ["borderBottomRightRadius", "44rpx"], ["borderBottomLeftRadius", "44rpx"], ["alignItems", "center"], ["justifyContent", "center"], ["marginTop", "16rpx"]]))], ["rg-btn-text", _pS(_uM([["fontSize", "32rpx"], ["color", "#FFFFFF"], ["fontWeight", 600]]))], ["rg-switch", _pS(_uM([["marginTop", "28rpx"], ["paddingTop", "12rpx"], ["paddingRight", "12rpx"], ["paddingBottom", "12rpx"], ["paddingLeft", "12rpx"]]))], ["rg-switch-text", _pS(_uM([["fontSize", "26rpx"], ["color", "#FF8C42"]]))]])]
