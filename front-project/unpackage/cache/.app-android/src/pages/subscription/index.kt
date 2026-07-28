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
import io.dcloud.uniapp.extapi.getStorageSync as uni_getStorageSync
import io.dcloud.uniapp.extapi.request as uni_request
import io.dcloud.uniapp.extapi.showToast as uni_showToast
open class GenPagesSubscriptionIndex : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesSubscriptionIndex) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesSubscriptionIndex
            val _cache = __ins.renderCache
            val BASE = "http://localhost:8080"
            val plans = ref(_uA<PlanData>())
            val selectedPlanId = ref(0)
            fun gen_priceText_fn(price: Number): String {
                return "¥" + (price / 100).toFixed(2)
            }
            val priceText = ::gen_priceText_fn
            fun gen_getToken_fn(): String {
                val t = uni_getStorageSync("token")
                return if (t != null) {
                    t as String
                } else {
                    ""
                }
            }
            val getToken = ::gen_getToken_fn
            fun gen_loadPlans_fn(): Unit {
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/subscription/index.uvue", 34, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                uni_request<Any>(RequestOptions(url = BASE + "/api/subscription/plans", method = "GET", header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val body = d as UTSJSONObject
                        val code = body.getNumber("code")
                        if (code === 200) {
                            val inner = body.get("data")
                            if (inner != null) {
                                val arr = inner as Any as UTSArray<PlanData>
                                plans.value = arr
                                if (arr.length >= 2) {
                                    selectedPlanId.value = arr[1].id
                                } else if (arr.length >= 1) {
                                    selectedPlanId.value = arr[0].id
                                }
                            }
                        }
                    }
                }
                , fail = fun(_): Unit {}))
            }
            val loadPlans = ::gen_loadPlans_fn
            fun gen_selectPlan_fn(id: Number): Unit {
                selectedPlanId.value = id
            }
            val selectPlan = ::gen_selectPlan_fn
            fun gen_buyNow_fn(): Unit {
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/subscription/index.uvue", 65, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                uni_request<Any>(RequestOptions(url = BASE + "/api/subscription/order?userId=1&planId=" + selectedPlanId.value, method = "POST", header = h, success = fun(_): Unit {
                    uni_showToast(ShowToastOptions(title = "订单已创建", icon = "success"))
                }
                , fail = fun(_): Unit {}))
            }
            val buyNow = ::gen_buyNow_fn
            onShow(fun(){
                loadPlans()
            }
            )
            return fun(): Any? {
                return _cE("view", _uM("class" to "sp-page"), _uA(
                    _cE("text", _uM("class" to "sp-title"), "升级会员"),
                    _cE("text", _uM("class" to "sp-sub"), "解锁更多萌宠"),
                    _cE("view", _uM("class" to "sp-plans"), _uA(
                        _cE(Fragment, null, RenderHelpers.renderList(unref(plans), fun(p, __key, __index, _cached): Any {
                            return _cE("view", _uM("class" to "sp-card", "key" to p.id, "onClick" to fun(){
                                selectPlan(p.id)
                            }
                            , "style" to _nS(_uM("borderWidth" to if (unref(selectedPlanId) === p.id) {
                                "3rpx"
                            } else {
                                "2rpx"
                            }
                            , "borderColor" to if (unref(selectedPlanId) === p.id) {
                                "#FF8C42"
                            } else {
                                "#E0E0E0"
                            }
                            ))), _uA(
                                _cE("text", _uM("class" to "sp-name"), _tD(p.name), 1),
                                _cE("text", _uM("class" to "sp-price"), _tD(priceText(p.price)), 1),
                                _cE("text", _uM("class" to "sp-dur"), _tD(p.durationDays) + " 天", 1),
                                _cE("text", _uM("class" to "sp-limit"), _tD(p.petLimit) + " 只萌宠", 1)
                            ), 12, _uA(
                                "onClick"
                            ))
                        }
                        ), 128)
                    )),
                    _cE("view", _uM("class" to "sp-buy", "onClick" to buyNow), _uA(
                        _cE("text", _uM("class" to "sp-buy-text"), "立即购买")
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
                return _uM("sp-page" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column", "alignItems" to "center", "paddingTop" to "60rpx")), "sp-title" to _pS(_uM("fontSize" to "38rpx", "fontWeight" to 700, "color" to "#333333")), "sp-sub" to _pS(_uM("fontSize" to "24rpx", "color" to "#999999", "marginTop" to "8rpx", "marginRight" to 0, "marginBottom" to "40rpx", "marginLeft" to 0)), "sp-plans" to _pS(_uM("flexDirection" to "row", "paddingTop" to 0, "paddingRight" to "14rpx", "paddingBottom" to 0, "paddingLeft" to "14rpx")), "sp-card" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "alignItems" to "center", "marginTop" to 0, "marginRight" to "6rpx", "marginBottom" to 0, "marginLeft" to "6rpx", "paddingTop" to "28rpx", "paddingRight" to "14rpx", "paddingBottom" to "28rpx", "paddingLeft" to "14rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "18rpx", "borderTopRightRadius" to "18rpx", "borderBottomRightRadius" to "18rpx", "borderBottomLeftRadius" to "18rpx")), "sp-name" to _pS(_uM("fontSize" to "26rpx", "fontWeight" to 700, "color" to "#333333", "marginBottom" to "12rpx")), "sp-price" to _pS(_uM("fontSize" to "44rpx", "fontWeight" to 700, "color" to "#FF8C42")), "sp-dur" to _pS(_uM("fontSize" to "22rpx", "color" to "#999999", "marginTop" to "4rpx", "marginRight" to 0, "marginBottom" to "10rpx", "marginLeft" to 0)), "sp-limit" to _pS(_uM("fontSize" to "22rpx", "color" to "#666666")), "sp-buy" to _pS(_uM("marginTop" to "50rpx", "width" to "600rpx", "paddingTop" to "22rpx", "paddingRight" to "22rpx", "paddingBottom" to "22rpx", "paddingLeft" to "22rpx", "backgroundColor" to "#FF8C42", "borderTopLeftRadius" to "44rpx", "borderTopRightRadius" to "44rpx", "borderBottomRightRadius" to "44rpx", "borderBottomLeftRadius" to "44rpx", "alignItems" to "center")), "sp-buy-text" to _pS(_uM("fontSize" to "30rpx", "color" to "#FFFFFF", "fontWeight" to 600)))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
