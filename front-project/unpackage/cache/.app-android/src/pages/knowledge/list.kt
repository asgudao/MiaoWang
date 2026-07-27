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
import io.dcloud.uniapp.extapi.navigateTo as uni_navigateTo
import io.dcloud.uniapp.extapi.request as uni_request
open class GenPagesKnowledgeList : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesKnowledgeList) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesKnowledgeList
            val _cache = __ins.renderCache
            val BASE = "http://localhost:8080"
            val keyword = ref("")
            val activeCategory = ref(0)
            val categories = ref(_uA<CT>())
            val list = ref(_uA<KI>())
            fun gen_getToken_fn(): String {
                val t = uni_getStorageSync("token")
                return if (t != null) {
                    t as String
                } else {
                    ""
                }
            }
            val getToken = ::gen_getToken_fn
            fun gen_doRequest_fn(url: String, onOk: (data: UTSJSONObject) -> Unit): Unit {
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/knowledge/list.uvue", 38, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                uni_request<Any>(RequestOptions(url = BASE + url, method = "GET", header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val body = d as UTSJSONObject
                        val code = body.getNumber("code")
                        if (code === 200) {
                            val inner = body.get("data")
                            if (inner != null) {
                                onOk(inner as UTSJSONObject)
                            }
                        }
                    }
                }
                , fail = fun(_): Unit {}))
            }
            val doRequest = ::gen_doRequest_fn
            fun gen_loadCats_fn(): Unit {
                doRequest("/api/knowledge/categories", fun(data: UTSJSONObject): Unit {
                    val arr = data as Any as UTSArray<CT>
                    categories.value = arr
                }
                )
            }
            val loadCats = ::gen_loadCats_fn
            fun gen_loadList_fn(): Unit {
                var url = "/api/knowledge?page=1&size=20"
                if (activeCategory.value > 0) {
                    url += "&categoryId=" + activeCategory.value
                }
                doRequest(url, fun(data: UTSJSONObject): Unit {
                    val records = data.get("records")
                    if (records != null) {
                        list.value = records as Any as UTSArray<KI>
                    }
                }
                )
            }
            val loadList = ::gen_loadList_fn
            fun gen_selectCategory_fn(id: Number): Unit {
                activeCategory.value = id
                loadList()
            }
            val selectCategory = ::gen_selectCategory_fn
            fun gen_doSearch_fn(): Unit {
                doRequest("/api/knowledge/search?keyword=" + keyword.value + "&page=1&size=20", fun(data: UTSJSONObject): Unit {
                    val records = data.get("records")
                    if (records != null) {
                        list.value = records as Any as UTSArray<KI>
                    }
                }
                )
            }
            val doSearch = ::gen_doSearch_fn
            fun gen_goDetail_fn(id: Number): Unit {
                uni_navigateTo(NavigateToOptions(url = "/pages/knowledge/detail?id=" + id))
            }
            val goDetail = ::gen_goDetail_fn
            onShow(fun(){
                loadCats()
                loadList()
            }
            )
            return fun(): Any? {
                return _cE("view", _uM("class" to "kw-page"), _uA(
                    _cE("input", _uM("class" to "kw-search", "modelValue" to unref(keyword), "onInput" to fun(`$event`: UniInputEvent){
                        trySetRefValue(keyword, `$event`.detail.value)
                    }
                    , "placeholder" to "搜索...", "onConfirm" to doSearch), null, 40, _uA(
                        "modelValue"
                    )),
                    _cE("scroll-view", _uM("scroll-x" to "true", "class" to "kw-cats"), _uA(
                        _cE(Fragment, null, RenderHelpers.renderList(unref(categories), fun(cat, __key, __index, _cached): Any {
                            return _cE("view", _uM("class" to "kw-tag", "key" to cat.id, "onClick" to fun(){
                                selectCategory(cat.id)
                            }
                            ), _uA(
                                _cE("text", _uM("style" to _nS(_uM("color" to if (unref(activeCategory) === cat.id) {
                                    "#FFF"
                                } else {
                                    "#666"
                                }
                                ))), _tD(cat.name), 5)
                            ), 8, _uA(
                                "onClick"
                            ))
                        }
                        ), 128)
                    )),
                    _cE("scroll-view", _uM("scroll-y" to "true", "class" to "kw-list"), _uA(
                        _cE(Fragment, null, RenderHelpers.renderList(unref(list), fun(item, __key, __index, _cached): Any {
                            return _cE("view", _uM("class" to "kw-item", "key" to item.id, "onClick" to fun(){
                                goDetail(item.id)
                            }
                            ), _uA(
                                _cE("view", _uM("class" to "kw-cover")),
                                _cE("view", _uM("class" to "kw-info"), _uA(
                                    _cE("text", _uM("class" to "kw-title"), _tD(item.title), 1),
                                    _cE("text", _uM("class" to "kw-meta"), _tD(item.viewCount) + " 次浏览", 1)
                                ))
                            ), 8, _uA(
                                "onClick"
                            ))
                        }
                        ), 128)
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
                return _uM("kw-page" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "kw-search" to _pS(_uM("height" to "72rpx", "marginTop" to "16rpx", "marginRight" to "24rpx", "marginBottom" to "16rpx", "marginLeft" to "24rpx", "paddingTop" to 0, "paddingRight" to "24rpx", "paddingBottom" to 0, "paddingLeft" to "24rpx", "backgroundColor" to "#F5F5F5", "borderTopLeftRadius" to "36rpx", "borderTopRightRadius" to "36rpx", "borderBottomRightRadius" to "36rpx", "borderBottomLeftRadius" to "36rpx", "fontSize" to "28rpx", "color" to "#333333")), "kw-cats" to _pS(_uM("height" to "64rpx", "paddingTop" to "10rpx", "paddingRight" to "24rpx", "paddingBottom" to "10rpx", "paddingLeft" to "24rpx", "backgroundColor" to "#FFFFFF")), "kw-tag" to _pS(_uM("paddingTop" to "8rpx", "paddingRight" to "24rpx", "paddingBottom" to "8rpx", "paddingLeft" to "24rpx", "marginRight" to "14rpx", "borderTopLeftRadius" to "36rpx", "borderTopRightRadius" to "36rpx", "borderBottomRightRadius" to "36rpx", "borderBottomLeftRadius" to "36rpx", "backgroundColor" to "#F5F5F5", "alignItems" to "center", "justifyContent" to "center")), "kw-list" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "paddingTop" to "8rpx", "paddingRight" to 0, "paddingBottom" to "8rpx", "paddingLeft" to 0)), "kw-item" to _pS(_uM("flexDirection" to "row", "alignItems" to "center", "paddingTop" to "20rpx", "paddingRight" to "20rpx", "paddingBottom" to "20rpx", "paddingLeft" to "20rpx", "marginTop" to "6rpx", "marginRight" to "20rpx", "marginBottom" to "6rpx", "marginLeft" to "20rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "14rpx", "borderTopRightRadius" to "14rpx", "borderBottomRightRadius" to "14rpx", "borderBottomLeftRadius" to "14rpx")), "kw-cover" to _pS(_uM("width" to "110rpx", "height" to "110rpx", "borderTopLeftRadius" to "10rpx", "borderTopRightRadius" to "10rpx", "borderBottomRightRadius" to "10rpx", "borderBottomLeftRadius" to "10rpx", "backgroundColor" to "#FFE0CC", "marginRight" to "18rpx")), "kw-info" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%")), "kw-title" to _pS(_uM("fontSize" to "28rpx", "color" to "#333333", "fontWeight" to 600)), "kw-meta" to _pS(_uM("fontSize" to "22rpx", "color" to "#999999", "marginTop" to "6rpx")))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
