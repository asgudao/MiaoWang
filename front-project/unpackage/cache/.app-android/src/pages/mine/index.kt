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
import io.dcloud.uniapp.extapi.navigateTo as uni_navigateTo
import io.dcloud.uniapp.extapi.reLaunch as uni_reLaunch
import io.dcloud.uniapp.extapi.removeStorageSync as uni_removeStorageSync
import io.dcloud.uniapp.extapi.request as uni_request
import io.dcloud.uniapp.extapi.switchTab as uni_switchTab
open class GenPagesMineIndex : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesMineIndex) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesMineIndex
            val _cache = __ins.renderCache
            val BASE = "http://localhost:8080"
            val user = ref<UserData>(UserData(id = 0, nickname = "", memberType = 0))
            val phoneNum = ref("")
            fun gen_getToken_fn(): String {
                val t = uni_getStorageSync("token")
                return if (t != null) {
                    t as String
                } else {
                    ""
                }
            }
            val getToken = ::gen_getToken_fn
            fun gen_loadProfile_fn(): Unit {
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/mine/index.uvue", 56, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                uni_request<Any>(RequestOptions(url = BASE + "/api/user/profile", method = "GET", header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val body = d as UTSJSONObject
                        val code = body.getNumber("code")
                        if (code === 200) {
                            val inner = body.get("data")
                            if (inner != null) {
                                user.value = inner as Any as UserData
                            }
                        }
                    }
                }
                , fail = fun(_): Unit {}))
            }
            val loadProfile = ::gen_loadProfile_fn
            fun gen_goMyPets_fn(): Unit {
                uni_switchTab(SwitchTabOptions(url = "/pages/pets/list"))
            }
            val goMyPets = ::gen_goMyPets_fn
            fun gen_goReminder_fn(): Unit {
                uni_navigateTo(NavigateToOptions(url = "/pages/reminder/index"))
            }
            val goReminder = ::gen_goReminder_fn
            fun gen_goFavorites_fn(): Unit {
                uni_navigateTo(NavigateToOptions(url = "/pages/favorites/index"))
            }
            val goFavorites = ::gen_goFavorites_fn
            fun gen_goAbout_fn(): Unit {
                uni_navigateTo(NavigateToOptions(url = "/pages/about/index"))
            }
            val goAbout = ::gen_goAbout_fn
            fun gen_goSubscribe_fn(): Unit {
                uni_navigateTo(NavigateToOptions(url = "/pages/subscription/index"))
            }
            val goSubscribe = ::gen_goSubscribe_fn
            fun gen_doLogout_fn(): Unit {
                uni_removeStorageSync("token")
                uni_removeStorageSync("phone")
                uni_reLaunch(ReLaunchOptions(url = "/pages/auth/login"))
            }
            val doLogout = ::gen_doLogout_fn
            onShow(fun(){
                loadProfile()
                val p = uni_getStorageSync("phone")
                phoneNum.value = if (p != null) {
                    p as String
                } else {
                    ""
                }
            }
            )
            return fun(): Any? {
                return _cE("view", _uM("class" to "page-wrap"), _uA(
                    _cE("view", _uM("class" to "profile-header"), _uA(
                        _cE("text", _uM("class" to "avatar-text"), "😺"),
                        _cE("view", _uM("class" to "profile-info"), _uA(
                            _cE("text", _uM("class" to "nickname"), _tD(if (unref(user).nickname !== "") {
                                unref(user).nickname
                            } else {
                                "猫咪爱好者"
                            }
                            ), 1),
                            _cE("text", _uM("class" to "user-phone"), _tD(unref(phoneNum)), 1),
                            _cE("view", _uM("class" to "member-badge", "style" to _nS(_uM("backgroundColor" to if (unref(user).memberType === 2) {
                                "#FFD700"
                            } else {
                                "rgba(255,255,255,0.3)"
                            }
                            ))), _uA(
                                _cE("text", _uM("style" to _nS(_uM("color" to if (unref(user).memberType === 2) {
                                    "#333"
                                } else {
                                    "#FFF"
                                }
                                , "fontSize" to "22rpx", "fontWeight" to "600"))), _tD(if (unref(user).memberType === 2) {
                                    "VIP"
                                } else {
                                    "Free"
                                }
                                ), 5)
                            ), 4)
                        ))
                    )),
                    _cE("view", _uM("class" to "member-card", "onClick" to goSubscribe), _uA(
                        _cE("view", _uM("class" to "member-card-left"), _uA(
                            _cE("text", _uM("class" to "member-card-icon"), "⭐"),
                            _cE("view", _uM("class" to "member-card-text"), _uA(
                                _cE("text", _uM("class" to "member-card-title"), "会员中心"),
                                _cE("text", _uM("class" to "member-card-desc"), "升级解锁更多萌宠")
                            ))
                        )),
                        _cE("text", _uM("class" to "member-card-action"), "升级")
                    )),
                    _cE("view", _uM("class" to "menu-list"), _uA(
                        _cE("view", _uM("class" to "menu-item", "onClick" to goMyPets), _uA(
                            _cE("text", _uM("class" to "menu-text"), "我的萌宠"),
                            _cE("text", _uM("class" to "arrow"), ">")
                        )),
                        _cE("view", _uM("class" to "menu-item", "onClick" to goReminder), _uA(
                            _cE("text", _uM("class" to "menu-text"), "护理日历"),
                            _cE("text", _uM("class" to "arrow"), ">")
                        )),
                        _cE("view", _uM("class" to "menu-item", "onClick" to goFavorites), _uA(
                            _cE("text", _uM("class" to "menu-text"), "收藏"),
                            _cE("text", _uM("class" to "arrow"), ">")
                        )),
                        _cE("view", _uM("class" to "menu-item menu-item-last", "onClick" to goAbout), _uA(
                            _cE("text", _uM("class" to "menu-text"), "关于"),
                            _cE("text", _uM("class" to "arrow"), ">")
                        ))
                    )),
                    _cE("view", _uM("class" to "logout-btn", "onClick" to doLogout), _uA(
                        _cE("text", _uM("class" to "logout-text"), "退出登录")
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
                return _uM("page-wrap" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "profile-header" to _pS(_uM("flexDirection" to "row", "alignItems" to "center", "paddingTop" to "48rpx", "paddingRight" to "32rpx", "paddingBottom" to "24rpx", "paddingLeft" to "32rpx", "backgroundColor" to "#FF8C42")), "avatar-text" to _pS(_uM("fontSize" to "80rpx", "marginRight" to "24rpx")), "profile-info" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%")), "nickname" to _pS(_uM("fontSize" to "36rpx", "fontWeight" to 700, "color" to "#FFFFFF")), "member-badge" to _pS(_uM("paddingTop" to "4rpx", "paddingRight" to "20rpx", "paddingBottom" to "4rpx", "paddingLeft" to "20rpx", "borderTopLeftRadius" to "24rpx", "borderTopRightRadius" to "24rpx", "borderBottomRightRadius" to "24rpx", "borderBottomLeftRadius" to "24rpx", "marginTop" to "8rpx")), "member-card" to _pS(_uM("flexDirection" to "row", "alignItems" to "center", "justifyContent" to "space-between", "marginTop" to "24rpx", "marginRight" to "24rpx", "marginBottom" to "24rpx", "marginLeft" to "24rpx", "paddingTop" to "28rpx", "paddingRight" to "28rpx", "paddingBottom" to "28rpx", "paddingLeft" to "28rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "16rpx", "borderTopRightRadius" to "16rpx", "borderBottomRightRadius" to "16rpx", "borderBottomLeftRadius" to "16rpx")), "member-card-left" to _pS(_uM("flexDirection" to "row", "alignItems" to "center")), "member-card-icon" to _pS(_uM("fontSize" to "40rpx", "marginRight" to "16rpx")), "member-card-text" to _pS(_uM("flexDirection" to "column")), "member-card-title" to _pS(_uM("fontSize" to "28rpx", "fontWeight" to 600, "color" to "#333333")), "member-card-desc" to _pS(_uM("fontSize" to "24rpx", "color" to "#999999")), "member-card-action" to _pS(_uM("fontSize" to "26rpx", "color" to "#FF8C42", "fontWeight" to 600)), "menu-list" to _pS(_uM("marginTop" to "24rpx", "marginRight" to "24rpx", "marginBottom" to "24rpx", "marginLeft" to "24rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "16rpx", "borderTopRightRadius" to "16rpx", "borderBottomRightRadius" to "16rpx", "borderBottomLeftRadius" to "16rpx")), "menu-item" to _pS(_uM("flexDirection" to "row", "justifyContent" to "space-between", "alignItems" to "center", "paddingTop" to "28rpx", "paddingRight" to "24rpx", "paddingBottom" to "28rpx", "paddingLeft" to "24rpx", "borderBottomWidth" to "1rpx", "borderBottomColor" to "#F5F5F5")), "menu-item-last" to _pS(_uM("borderBottomWidth" to 0)), "menu-text" to _pS(_uM("fontSize" to "28rpx", "color" to "#333333")), "arrow" to _pS(_uM("fontSize" to "36rpx", "color" to "#CCCCCC")), "user-phone" to _pS(_uM("fontSize" to "24rpx", "color" to "rgba(255,255,255,0.7)", "marginTop" to "4rpx")), "logout-btn" to _pS(_uM("marginTop" to "24rpx", "marginRight" to "24rpx", "marginBottom" to "24rpx", "marginLeft" to "24rpx", "paddingTop" to "20rpx", "paddingRight" to "20rpx", "paddingBottom" to "20rpx", "paddingLeft" to "20rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "16rpx", "borderTopRightRadius" to "16rpx", "borderBottomRightRadius" to "16rpx", "borderBottomLeftRadius" to "16rpx", "alignItems" to "center")), "logout-text" to _pS(_uM("fontSize" to "28rpx", "color" to "#FF4444")))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
