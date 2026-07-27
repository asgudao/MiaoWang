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
open class GenPagesPetsList : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesPetsList) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesPetsList
            val _cache = __ins.renderCache
            val BASE = "http://localhost:8080"
            val pets = ref(_uA<PetData>())
            fun gen_getToken_fn(): String {
                val token = uni_getStorageSync("token")
                return if (token != null) {
                    token as String
                } else {
                    ""
                }
            }
            val getToken = ::gen_getToken_fn
            fun gen_loadPets_fn(): Unit {
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/pets/list.uvue", 34, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                uni_request<Any>(RequestOptions(url = BASE + "/api/pets", method = "GET", header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val body = d as UTSJSONObject
                        val code = body.getNumber("code")
                        if (code === 200) {
                            val inner = body.get("data")
                            if (inner != null) {
                                pets.value = inner as Any as UTSArray<PetData>
                            }
                        }
                    }
                }
                , fail = fun(_): Unit {}))
            }
            val loadPets = ::gen_loadPets_fn
            fun gen_addPet_fn(): Unit {
                uni_navigateTo(NavigateToOptions(url = "/pages/pets/add"))
            }
            val addPet = ::gen_addPet_fn
            onShow(fun(){
                loadPets()
            }
            )
            return fun(): Any? {
                return _cE("view", _uM("class" to "pl-page"), _uA(
                    _cE("view", _uM("class" to "pl-header"), _uA(
                        _cE("text", _uM("class" to "pl-title"), "我的萌宠")
                    )),
                    _cE("scroll-view", _uM("scroll-y" to "true", "class" to "pl-list"), _uA(
                        _cE(Fragment, null, RenderHelpers.renderList(unref(pets), fun(pet, __key, __index, _cached): Any {
                            return _cE("view", _uM("class" to "pl-card", "key" to pet.id), _uA(
                                _cE("view", _uM("class" to "pl-top"), _uA(
                                    _cE("text", _uM("class" to "pl-avatar"), "🐱"),
                                    _cE("view", _uM("class" to "pl-info"), _uA(
                                        _cE("text", _uM("class" to "pl-name"), _tD(pet.name), 1),
                                        _cE("text", _uM("class" to "pl-breed"), _tD(if (pet.breedName !== "") {
                                            pet.breedName
                                        } else {
                                            "未知品种"
                                        }
                                        ), 1),
                                        _cE("text", _uM("class" to "pl-detail"), _tD(if (pet.gender === 1) {
                                            "公"
                                        } else {
                                            "母"
                                        }
                                        ) + " | " + _tD(pet.age) + " 岁 | " + _tD(pet.weight) + "kg", 1)
                                    ))
                                ))
                            ))
                        }
                        ), 128)
                    )),
                    _cE("view", _uM("class" to "pl-add", "onClick" to addPet), _uA(
                        _cE("text", _uM("class" to "pl-add-text"), "+ 添加宠物")
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
                return _uM("pl-page" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "pl-header" to _pS(_uM("paddingTop" to "32rpx", "paddingRight" to "28rpx", "paddingBottom" to "16rpx", "paddingLeft" to "28rpx")), "pl-title" to _pS(_uM("fontSize" to "40rpx", "fontWeight" to 700, "color" to "#333333")), "pl-list" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "paddingTop" to "8rpx", "paddingRight" to 0, "paddingBottom" to "8rpx", "paddingLeft" to 0)), "pl-card" to _pS(_uM("marginTop" to "12rpx", "marginRight" to "24rpx", "marginBottom" to "12rpx", "marginLeft" to "24rpx", "paddingTop" to "24rpx", "paddingRight" to "24rpx", "paddingBottom" to "24rpx", "paddingLeft" to "24rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "18rpx", "borderTopRightRadius" to "18rpx", "borderBottomRightRadius" to "18rpx", "borderBottomLeftRadius" to "18rpx")), "pl-top" to _pS(_uM("flexDirection" to "row", "alignItems" to "center")), "pl-avatar" to _pS(_uM("fontSize" to "72rpx", "marginRight" to "20rpx")), "pl-info" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "pl-name" to _pS(_uM("fontSize" to "32rpx", "fontWeight" to 700, "color" to "#333333")), "pl-breed" to _pS(_uM("fontSize" to "24rpx", "color" to "#999999", "marginTop" to "4rpx")), "pl-detail" to _pS(_uM("fontSize" to "22rpx", "color" to "#666666", "marginTop" to "4rpx")), "pl-add" to _pS(_uM("marginTop" to "20rpx", "marginRight" to "24rpx", "marginBottom" to "20rpx", "marginLeft" to "24rpx", "paddingTop" to "20rpx", "paddingRight" to "20rpx", "paddingBottom" to "20rpx", "paddingLeft" to "20rpx", "backgroundColor" to "#FF8C42", "borderTopLeftRadius" to "16rpx", "borderTopRightRadius" to "16rpx", "borderBottomRightRadius" to "16rpx", "borderBottomLeftRadius" to "16rpx", "alignItems" to "center")), "pl-add-text" to _pS(_uM("fontSize" to "28rpx", "color" to "#FFFFFF", "fontWeight" to 600)))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
