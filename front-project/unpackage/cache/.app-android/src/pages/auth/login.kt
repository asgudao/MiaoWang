@file:Suppress("UNCHECKED_CAST", "USELESS_CAST", "INAPPLICABLE_JVM_NAME", "UNUSED_ANONYMOUS_PARAMETER", "SENSELESS_COMPARISON", "NAME_SHADOWING", "UNNECESSARY_NOT_NULL_ASSERTION")
package uni.UNIuniappx
import io.dcloud.uniapp.*
import io.dcloud.uniapp.extapi.*
import io.dcloud.uniapp.framework.*
import io.dcloud.uniapp.runtime.*
import io.dcloud.uniapp.vue.*
import io.dcloud.uniapp.vue.shared.*
import io.dcloud.unicloud.*
import io.dcloud.uts.*
import io.dcloud.uts.Map
import io.dcloud.uts.Set
import io.dcloud.uts.UTSAndroid
import kotlin.properties.Delegates
import io.dcloud.uniapp.extapi.navigateTo as uni_navigateTo
import io.dcloud.uniapp.extapi.reLaunch as uni_reLaunch
import io.dcloud.uniapp.extapi.request as uni_request
import io.dcloud.uniapp.extapi.setStorageSync as uni_setStorageSync
import io.dcloud.uniapp.extapi.showToast as uni_showToast
open class GenPagesAuthLogin : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesAuthLogin) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesAuthLogin
            val _cache = __ins.renderCache
            val BASE = "http://localhost:8080"
            val phone = ref("")
            fun gen_doLogin_fn(): Unit {
                if (phone.value === "" || phone.value.length !== 11) {
                    uni_showToast(ShowToastOptions(title = "请输入正确的手机号", icon = "none"))
                    return
                }
                val body = UTSJSONObject(UTSSourceMapPosition("body", "pages/auth/login.uvue", 33, 8))
                body["phone"] = phone.value
                body["code"] = "1234"
                uni_request<Any>(RequestOptions(url = BASE + "/api/auth/login", method = "POST", data = body, header = UTSJSONObject(), success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val resp = d as UTSJSONObject
                        val code = resp.getNumber("code")
                        if (code === 200) {
                            val data = resp.get("data")
                            if (data != null) {
                                val loginData = data as UTSJSONObject
                                val t = loginData.getString("token")
                                if (t != null) {
                                    uni_setStorageSync("token", t)
                                    uni_setStorageSync("phone", phone.value)
                                    uni_showToast(ShowToastOptions(title = "登录成功", icon = "success"))
                                    uni_reLaunch(ReLaunchOptions(url = "/pages/index/index"))
                                    return
                                }
                            }
                        }
                        uni_showToast(ShowToastOptions(title = "手机号未注册", icon = "none"))
                    }
                }
                , fail = fun(_): Unit {
                    uni_showToast(ShowToastOptions(title = "网络错误", icon = "none"))
                }
                ))
            }
            val doLogin = ::gen_doLogin_fn
            fun gen_goRegister_fn(): Unit {
                uni_navigateTo(NavigateToOptions(url = "/pages/auth/register"))
            }
            val goRegister = ::gen_goRegister_fn
            return fun(): Any? {
                return _cE("view", _uM("class" to "lg-page"), _uA(
                    _cE("view", _uM("class" to "lg-card"), _uA(
                        _cE("text", _uM("class" to "lg-logo"), "🐱🐶"),
                        _cE("text", _uM("class" to "lg-title"), "喵汪"),
                        _cE("text", _uM("class" to "lg-sub"), "宠物养护助手"),
                        _cE("view", _uM("class" to "lg-field"), _uA(
                            _cE("text", _uM("class" to "lg-label"), "手机号"),
                            _cE("input", _uM("class" to "lg-input", "modelValue" to unref(phone), "onInput" to fun(`$event`: UniInputEvent){
                                trySetRefValue(phone, `$event`.detail.value)
                            }
                            , "type" to "number", "maxlength" to "11", "placeholder" to "请输入手机号"), null, 40, _uA(
                                "modelValue"
                            ))
                        )),
                        _cE("view", _uM("class" to "lg-btn", "onClick" to doLogin), _uA(
                            _cE("text", _uM("class" to "lg-btn-text"), "登录")
                        )),
                        _cE("view", _uM("class" to "lg-switch", "onClick" to goRegister), _uA(
                            _cE("text", _uM("class" to "lg-switch-text"), "没有账号？去注册")
                        ))
                    ))
                ))
            }
        }
        val styles: Map<String, Map<String, Map<String, Any>>> by lazy {
            _nCS(_uA(
                styles0
            ))
        }
        val styles0: Map<String, Map<String, Map<String, Any>>>
            get() {
                return _uM("lg-page" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column", "alignItems" to "center", "justifyContent" to "center", "paddingTop" to "48rpx", "paddingRight" to "48rpx", "paddingBottom" to "48rpx", "paddingLeft" to "48rpx")), "lg-card" to _pS(_uM("width" to "640rpx", "paddingTop" to "60rpx", "paddingRight" to "48rpx", "paddingBottom" to "60rpx", "paddingLeft" to "48rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "24rpx", "borderTopRightRadius" to "24rpx", "borderBottomRightRadius" to "24rpx", "borderBottomLeftRadius" to "24rpx", "alignItems" to "center")), "lg-logo" to _pS(_uM("fontSize" to "80rpx")), "lg-title" to _pS(_uM("fontSize" to "44rpx", "fontWeight" to 700, "color" to "#FF8C42", "marginTop" to "16rpx")), "lg-sub" to _pS(_uM("fontSize" to "26rpx", "color" to "#999999", "marginTop" to "8rpx", "marginRight" to 0, "marginBottom" to "48rpx", "marginLeft" to 0)), "lg-field" to _pS(_uM("width" to "100%", "marginBottom" to "32rpx")), "lg-label" to _pS(_uM("fontSize" to "26rpx", "color" to "#666666", "marginBottom" to "10rpx")), "lg-input" to _pS(_uM("height" to "88rpx", "paddingTop" to 0, "paddingRight" to "24rpx", "paddingBottom" to 0, "paddingLeft" to "24rpx", "backgroundColor" to "#F5F5F5", "borderTopLeftRadius" to "16rpx", "borderTopRightRadius" to "16rpx", "borderBottomRightRadius" to "16rpx", "borderBottomLeftRadius" to "16rpx", "fontSize" to "30rpx", "color" to "#333333")), "lg-btn" to _pS(_uM("width" to "100%", "height" to "88rpx", "backgroundColor" to "#FF8C42", "borderTopLeftRadius" to "44rpx", "borderTopRightRadius" to "44rpx", "borderBottomRightRadius" to "44rpx", "borderBottomLeftRadius" to "44rpx", "alignItems" to "center", "justifyContent" to "center", "marginTop" to "16rpx")), "lg-btn-text" to _pS(_uM("fontSize" to "32rpx", "color" to "#FFFFFF", "fontWeight" to 600)), "lg-switch" to _pS(_uM("marginTop" to "28rpx", "paddingTop" to "12rpx", "paddingRight" to "12rpx", "paddingBottom" to "12rpx", "paddingLeft" to "12rpx")), "lg-switch-text" to _pS(_uM("fontSize" to "26rpx", "color" to "#FF8C42")))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
