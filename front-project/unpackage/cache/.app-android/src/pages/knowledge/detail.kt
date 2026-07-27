@file:Suppress("UNCHECKED_CAST", "USELESS_CAST", "INAPPLICABLE_JVM_NAME", "UNUSED_ANONYMOUS_PARAMETER", "SENSELESS_COMPARISON", "NAME_SHADOWING", "UNNECESSARY_NOT_NULL_ASSERTION")
package uni.UNI19DBD81
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
import io.dcloud.uniapp.extapi.setStorageSync as uni_setStorageSync
import io.dcloud.uniapp.extapi.showToast as uni_showToast
open class GenPagesKnowledgeDetail : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesKnowledgeDetail) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesKnowledgeDetail
            val _cache = __ins.renderCache
            val BASE = "http://localhost:8080"
            val detail = ref<DetailData>(DetailData(id = 0, title = "加载中...", content = "", viewCount = 0))
            val isFaved = ref(false)
            fun gen_getToken_fn(): String {
                val t = uni_getStorageSync("token")
                return if (t != null) {
                    t as String
                } else {
                    ""
                }
            }
            val getToken = ::gen_getToken_fn
            fun gen_checkFav_fn(): Unit {
                val raw = uni_getStorageSync("favorites")
                if (raw == null || raw === "") {
                    isFaved.value = false
                    return
                }
                val arr = UTSAndroid.consoleDebugError(JSON.parse(raw as String), " at pages/knowledge/detail.uvue:35") as UTSArray<Any>
                var found = false
                run {
                    var i: Number = 0
                    while(i < arr.length){
                        val item = arr[i] as UTSJSONObject
                        val nid = item.getNumber("id")
                        if (nid != null && nid === detail.value.id) {
                            found = true
                            break
                        }
                        i++
                    }
                }
                isFaved.value = found
            }
            val checkFav = ::gen_checkFav_fn
            fun gen_fetchDetail_fn(id: Number): Unit {
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/knowledge/detail.uvue", 47, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                uni_request<Any>(RequestOptions(url = BASE + "/api/knowledge/" + id, method = "GET", header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val body = d as UTSJSONObject
                        val code = body.getNumber("code")
                        if (code === 200) {
                            val inner = body.get("data")
                            if (inner != null) {
                                detail.value = inner as Any as DetailData
                                checkFav()
                            }
                        }
                    }
                }
                , fail = fun(_): Unit {}))
            }
            val fetchDetail = ::gen_fetchDetail_fn
            fun gen_toggleFav_fn(): Unit {
                val raw = uni_getStorageSync("favorites")
                var arr: UTSArray<Any> = _uA()
                if (raw != null && raw !== "") {
                    arr = UTSAndroid.consoleDebugError(JSON.parse(raw as String), " at pages/knowledge/detail.uvue:75") as UTSArray<Any>
                }
                if (isFaved.value) {
                    val filtered: UTSArray<Any> = _uA()
                    run {
                        var i: Number = 0
                        while(i < arr.length){
                            val item = arr[i] as UTSJSONObject
                            val nid = item.getNumber("id")
                            if (nid != null && nid !== detail.value.id) {
                                filtered.push(item)
                            }
                            i++
                        }
                    }
                    uni_setStorageSync("favorites", JSON.stringify(filtered))
                    isFaved.value = false
                    uni_showToast(ShowToastOptions(title = "已取消收藏", icon = "none"))
                } else {
                    val newItem = UTSJSONObject(UTSSourceMapPosition("newItem", "pages/knowledge/detail.uvue", 88, 9))
                    newItem["id"] = detail.value.id
                    newItem["title"] = detail.value.title
                    val now = Date()
                    newItem["time"] = now.getFullYear() + "-" + (now.getMonth() + 1) + "-" + now.getDate()
                    arr.push(newItem)
                    uni_setStorageSync("favorites", JSON.stringify(arr))
                    isFaved.value = true
                    uni_showToast(ShowToastOptions(title = "已收藏", icon = "success"))
                }
            }
            val toggleFav = ::gen_toggleFav_fn
            onLoad(fun(opt){
                val id = opt?.get("id")
                if (isTruthy(id)) {
                    fetchDetail(parseInt(id as String))
                }
            }
            )
            return fun(): Any? {
                return _cE("view", _uM("class" to "kd-page"), _uA(
                    _cE("scroll-view", _uM("scroll-y" to "true", "class" to "kd-scroll"), _uA(
                        _cE("view", _uM("class" to "kd-cover")),
                        _cE("view", _uM("class" to "kd-body"), _uA(
                            _cE("view", _uM("class" to "kd-title-row"), _uA(
                                _cE("text", _uM("class" to "kd-title"), _tD(unref(detail).title), 1),
                                _cE("view", _uM("class" to "kd-fav", "onClick" to toggleFav), _uA(
                                    _cE("text", _uM("class" to "kd-fav-text"), _tD(if (unref(isFaved)) {
                                        "★"
                                    } else {
                                        "☆"
                                    }
                                    ), 1)
                                ))
                            )),
                            _cE("view", _uM("class" to "kd-meta"), _uA(
                                _cE("text", _uM("class" to "kd-meta-text"), _tD(unref(detail).viewCount) + " 次浏览", 1)
                            )),
                            _cE("text", _uM("class" to "kd-content"), _tD(unref(detail).content), 1)
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
                return _uM("kd-page" to _pS(_uM("backgroundColor" to "#FFFFFF", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "kd-scroll" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%")), "kd-cover" to _pS(_uM("height" to "360rpx", "backgroundColor" to "#FFE0CC")), "kd-body" to _pS(_uM("paddingTop" to "28rpx", "paddingRight" to "28rpx", "paddingBottom" to "28rpx", "paddingLeft" to "28rpx")), "kd-title-row" to _pS(_uM("flexDirection" to "row", "justifyContent" to "space-between", "alignItems" to "center")), "kd-title" to _pS(_uM("fontSize" to "34rpx", "fontWeight" to 700, "color" to "#333333", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%")), "kd-fav" to _pS(_uM("width" to "56rpx", "height" to "56rpx", "alignItems" to "center", "justifyContent" to "center")), "kd-fav-text" to _pS(_uM("fontSize" to "40rpx", "color" to "#FF8C42")), "kd-meta" to _pS(_uM("marginTop" to "12rpx", "marginRight" to 0, "marginBottom" to "24rpx", "marginLeft" to 0)), "kd-meta-text" to _pS(_uM("fontSize" to "24rpx", "color" to "#999999")), "kd-content" to _pS(_uM("fontSize" to "30rpx", "color" to "#444444")))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
