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
import io.dcloud.uniapp.extapi.setStorageSync as uni_setStorageSync
import io.dcloud.uniapp.extapi.showToast as uni_showToast
open class GenPagesFavoritesIndex : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesFavoritesIndex) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesFavoritesIndex
            val _cache = __ins.renderCache
            val favList = ref(_uA<FavItem>())
            fun gen_loadFavs_fn(): Unit {
                val raw = uni_getStorageSync("favorites")
                if (raw != null && raw !== "") {
                    val arr = UTSAndroid.consoleDebugError(JSON.parse(raw as String), " at pages/favorites/index.uvue:29") as UTSArray<Any>
                    val result: UTSArray<FavItem> = _uA()
                    run {
                        var i: Number = 0
                        while(i < arr.length){
                            val item = arr[i] as UTSJSONObject
                            val nid = item.getNumber("id")
                            val ntitle = item.getString("title")
                            val ntime = item.getString("time")
                            result.push(FavItem(id = if (nid != null) {
                                nid
                            } else {
                                0
                            }, title = if (ntitle != null) {
                                ntitle
                            } else {
                                ""
                            }, time = if (ntime != null) {
                                ntime
                            } else {
                                ""
                            }))
                            i++
                        }
                    }
                    favList.value = result
                } else {
                    favList.value = _uA()
                }
            }
            val loadFavs = ::gen_loadFavs_fn
            fun gen_removeFav_fn(id: Number): Unit {
                val raw = uni_getStorageSync("favorites")
                if (raw != null && raw !== "") {
                    val arr = UTSAndroid.consoleDebugError(JSON.parse(raw as String), " at pages/favorites/index.uvue:51") as UTSArray<Any>
                    val filtered: UTSArray<Any> = _uA()
                    run {
                        var i: Number = 0
                        while(i < arr.length){
                            val item = arr[i] as UTSJSONObject
                            val nid = item.getNumber("id")
                            if (nid != null && nid !== id) {
                                filtered.push(item)
                            }
                            i++
                        }
                    }
                    uni_setStorageSync("favorites", JSON.stringify(filtered))
                }
                loadFavs()
                uni_showToast(ShowToastOptions(title = "已取消收藏", icon = "none"))
            }
            val removeFav = ::gen_removeFav_fn
            fun gen_goDetail_fn(id: Number): Unit {
                uni_navigateTo(NavigateToOptions(url = "/pages/knowledge/detail?id=" + id))
            }
            val goDetail = ::gen_goDetail_fn
            onShow(fun(){
                loadFavs()
            }
            )
            return fun(): Any? {
                return _cE("view", _uM("class" to "fv-page"), _uA(
                    if (unref(favList).length === 0) {
                        _cE("view", _uM("key" to 0, "class" to "fv-empty"), _uA(
                            _cE("text", _uM("class" to "fv-empty-icon"), "⭐"),
                            _cE("text", _uM("class" to "fv-empty-text"), "还没有收藏"),
                            _cE("text", _uM("class" to "fv-empty-hint"), "在知识库文章详情页可以收藏")
                        ))
                    } else {
                        _cC("v-if", true)
                    }
                    ,
                    if (unref(favList).length > 0) {
                        _cE("scroll-view", _uM("key" to 1, "scroll-y" to "true", "class" to "fv-scroll"), _uA(
                            _cE(Fragment, null, RenderHelpers.renderList(unref(favList), fun(item, __key, __index, _cached): Any {
                                return _cE("view", _uM("class" to "fv-item", "key" to item.id, "onClick" to fun(){
                                    goDetail(item.id)
                                }), _uA(
                                    _cE("view", _uM("class" to "fv-cover")),
                                    _cE("view", _uM("class" to "fv-info"), _uA(
                                        _cE("text", _uM("class" to "fv-title"), _tD(item.title), 1),
                                        _cE("text", _uM("class" to "fv-time"), _tD(item.time), 1)
                                    )),
                                    _cE("view", _uM("class" to "fv-del", "onClick" to withModifiers(fun(){
                                        removeFav(item.id)
                                    }, _uA(
                                        "stop"
                                    ))), _uA(
                                        _cE("text", _uM("class" to "fv-del-text"), "✕")
                                    ), 8, _uA(
                                        "onClick"
                                    ))
                                ), 8, _uA(
                                    "onClick"
                                ))
                            }), 128)
                        ))
                    } else {
                        _cC("v-if", true)
                    }
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
                return _uM("fv-page" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "fv-empty" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "alignItems" to "center", "justifyContent" to "center")), "fv-empty-icon" to _pS(_uM("fontSize" to "80rpx", "marginBottom" to "20rpx")), "fv-empty-text" to _pS(_uM("fontSize" to "30rpx", "color" to "#999999", "fontWeight" to 600)), "fv-empty-hint" to _pS(_uM("fontSize" to "24rpx", "color" to "#CCCCCC", "marginTop" to "10rpx")), "fv-scroll" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "paddingTop" to "16rpx", "paddingRight" to "24rpx", "paddingBottom" to "16rpx", "paddingLeft" to "24rpx")), "fv-item" to _pS(_uM("flexDirection" to "row", "alignItems" to "center", "paddingTop" to "20rpx", "paddingRight" to "20rpx", "paddingBottom" to "20rpx", "paddingLeft" to "20rpx", "marginBottom" to "10rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "14rpx", "borderTopRightRadius" to "14rpx", "borderBottomRightRadius" to "14rpx", "borderBottomLeftRadius" to "14rpx")), "fv-cover" to _pS(_uM("width" to "90rpx", "height" to "90rpx", "borderTopLeftRadius" to "10rpx", "borderTopRightRadius" to "10rpx", "borderBottomRightRadius" to "10rpx", "borderBottomLeftRadius" to "10rpx", "backgroundColor" to "#FFE0CC", "marginRight" to "16rpx")), "fv-info" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "fv-title" to _pS(_uM("fontSize" to "28rpx", "color" to "#333333", "fontWeight" to 600)), "fv-time" to _pS(_uM("fontSize" to "22rpx", "color" to "#CCCCCC", "marginTop" to "6rpx")), "fv-del" to _pS(_uM("width" to "56rpx", "height" to "56rpx", "borderTopLeftRadius" to "28rpx", "borderTopRightRadius" to "28rpx", "borderBottomRightRadius" to "28rpx", "borderBottomLeftRadius" to "28rpx", "backgroundColor" to "#FFEEEE", "alignItems" to "center", "justifyContent" to "center")), "fv-del-text" to _pS(_uM("fontSize" to "28rpx", "color" to "#FF4444")))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
