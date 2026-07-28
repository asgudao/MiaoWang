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
import io.dcloud.uniapp.extapi.navigateBack as uni_navigateBack
import io.dcloud.uniapp.extapi.request as uni_request
import io.dcloud.uniapp.extapi.showToast as uni_showToast
open class GenPagesPetsAdd : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesPetsAdd) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesPetsAdd
            val _cache = __ins.renderCache
            val BASE = "http://localhost:8080"
            val name = ref("")
            val species = ref(1)
            val gender = ref(1)
            val age = ref("")
            val weight = ref("")
            val breeds = ref(_uA<BreedItem>())
            val breedNames = ref(_uA<String>())
            val selectedBreedIdx = ref(-1)
            val selectedBreedId = ref(0)
            val chipOn = "padding:16rpx 36rpx;border-radius:32rpx;background-color:#FF8C42;margin-right:20rpx;"
            val chipOff = "padding:16rpx 36rpx;border-radius:32rpx;background-color:#F0F0F0;margin-right:20rpx;"
            val chipTextOn = "font-size:28rpx;color:#FFF;font-weight:600;"
            val chipTextOff = "font-size:28rpx;color:#666;"
            fun gen_getToken_fn(): String {
                val t = uni_getStorageSync("token")
                return if (t != null) {
                    t as String
                } else {
                    ""
                }
            }
            val getToken = ::gen_getToken_fn
            fun gen_loadBreeds_fn(): Unit {
                val token = getToken()
                if (token === "") {
                    setTimeout(fun(){
                        gen_loadBreeds_fn()
                    }
                    , 1000)
                    return
                }
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/pets/add.uvue", 69, 8))
                h["Content-Type"] = "application/json"
                h["Authorization"] = "Bearer " + token
                uni_request<Any>(RequestOptions(url = BASE + "/api/breeds?species=" + species.value, method = "GET", header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val body = d as UTSJSONObject
                        if (body.getNumber("code") === 200) {
                            val data = body.get("data")
                            if (data != null) {
                                breeds.value = data as Any as UTSArray<BreedItem>
                                val names: UTSArray<String> = _uA()
                                run {
                                    var i: Number = 0
                                    while(i < breeds.value.length){
                                        names.push(breeds.value[i].name)
                                        i++
                                    }
                                }
                                breedNames.value = names
                            }
                        }
                    }
                }
                , fail = fun(_): Unit {}))
            }
            val loadBreeds = ::gen_loadBreeds_fn
            fun gen_onBreedPick_fn(e: UTSJSONObject): Unit {
                val detail = e.get("detail") as UTSJSONObject
                val idxNum = detail.getNumber("value")
                val idx: Number = if (idxNum != null) {
                    idxNum
                } else {
                    0
                }
                selectedBreedIdx.value = idx
                selectedBreedId.value = breeds.value[idx].id
            }
            val onBreedPick = ::gen_onBreedPick_fn
            fun gen_switchSpecies_fn(s: Number): Unit {
                species.value = s
                selectedBreedIdx.value = -1
                selectedBreedId.value = 0
                breedNames.value = _uA()
                loadBreeds()
            }
            val switchSpecies = ::gen_switchSpecies_fn
            fun gen_submit_fn(): Unit {
                if (name.value === "") {
                    uni_showToast(ShowToastOptions(title = "请输入宠物名字", icon = "none"))
                    return
                }
                if (selectedBreedId.value === 0) {
                    uni_showToast(ShowToastOptions(title = "请选择品种", icon = "none"))
                    return
                }
                if (age.value === "") {
                    uni_showToast(ShowToastOptions(title = "请输入年龄", icon = "none"))
                    return
                }
                if (weight.value === "") {
                    uni_showToast(ShowToastOptions(title = "请输入体重", icon = "none"))
                    return
                }
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/pets/add.uvue", 118, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                val body = UTSJSONObject(UTSSourceMapPosition("body", "pages/pets/add.uvue", 122, 8))
                body["userId"] = 1
                body["name"] = name.value
                body["species"] = species.value
                body["breedId"] = selectedBreedId.value
                body["gender"] = gender.value
                body["age"] = parseInt(age.value)
                body["weight"] = parseFloat(weight.value)
                uni_request<Any>(RequestOptions(url = BASE + "/api/pets", method = "POST", data = body, header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val resp = d as UTSJSONObject
                        if (resp.getNumber("code") === 200) {
                            uni_showToast(ShowToastOptions(title = "添加成功", icon = "success"))
                            uni_navigateBack(null)
                        } else {
                            uni_showToast(ShowToastOptions(title = "添加失败", icon = "none"))
                        }
                    }
                }
                , fail = fun(_): Unit {
                    uni_showToast(ShowToastOptions(title = "网络错误", icon = "none"))
                }
                ))
            }
            val submit = ::gen_submit_fn
            onShow(fun(){
                loadBreeds()
            }
            )
            return fun(): Any? {
                val _component_picker = resolveComponent("picker")
                return _cE("view", _uM("class" to "ap-page"), _uA(
                    _cE("scroll-view", _uM("scroll-y" to "true", "class" to "ap-scroll"), _uA(
                        _cE("view", _uM("class" to "ap-field"), _uA(
                            _cE("text", _uM("class" to "ap-label"), "宠物名字"),
                            _cE("input", _uM("class" to "ap-input", "modelValue" to unref(name), "onInput" to fun(`$event`: UniInputEvent){
                                trySetRefValue(name, `$event`.detail.value)
                            }
                            , "placeholder" to "给宠物起个名字"), null, 40, _uA(
                                "modelValue"
                            ))
                        )),
                        _cE("view", _uM("class" to "ap-field"), _uA(
                            _cE("text", _uM("class" to "ap-label"), "种类"),
                            _cE("view", _uM("class" to "ap-row"), _uA(
                                _cE("view", _uM("class" to "ap-chip", "style" to _nS(if (unref(species) === 1) {
                                    chipOn
                                } else {
                                    chipOff
                                }
                                ), "onClick" to fun(){
                                    switchSpecies(1)
                                }
                                ), _uA(
                                    _cE("text", _uM("style" to _nS(if (unref(species) === 1) {
                                        chipTextOn
                                    } else {
                                        chipTextOff
                                    }
                                    )), "猫", 4)
                                ), 12, _uA(
                                    "onClick"
                                )),
                                _cE("view", _uM("class" to "ap-chip", "style" to _nS(if (unref(species) === 2) {
                                    chipOn
                                } else {
                                    chipOff
                                }
                                ), "onClick" to fun(){
                                    switchSpecies(2)
                                }
                                ), _uA(
                                    _cE("text", _uM("style" to _nS(if (unref(species) === 2) {
                                        chipTextOn
                                    } else {
                                        chipTextOff
                                    }
                                    )), "狗", 4)
                                ), 12, _uA(
                                    "onClick"
                                ))
                            ))
                        )),
                        _cE("view", _uM("class" to "ap-field"), _uA(
                            _cE("text", _uM("class" to "ap-label"), "品种"),
                            _cV(_component_picker, _uM("range" to unref(breedNames), "onChange" to onBreedPick), _uM("default" to withSlotCtx(fun(): UTSArray<Any> {
                                return _uA(
                                    _cE("view", _uM("class" to "ap-picker"), _uA(
                                        _cE("text", null, _tD(if (unref(breedNames).length > 0 && unref(selectedBreedIdx) >= 0) {
                                            unref(breedNames)[unref(selectedBreedIdx)]
                                        } else {
                                            "请选择品种"
                                        }
                                        ), 1)
                                    ))
                                )
                            }
                            ), "_" to 1), 8, _uA(
                                "range"
                            ))
                        )),
                        _cE("view", _uM("class" to "ap-field"), _uA(
                            _cE("text", _uM("class" to "ap-label"), "性别"),
                            _cE("view", _uM("class" to "ap-row"), _uA(
                                _cE("view", _uM("class" to "ap-chip", "style" to _nS(if (unref(gender) === 1) {
                                    chipOn
                                } else {
                                    chipOff
                                }
                                ), "onClick" to fun(){
                                    gender.value = 1
                                }
                                ), _uA(
                                    _cE("text", _uM("style" to _nS(if (unref(gender) === 1) {
                                        chipTextOn
                                    } else {
                                        chipTextOff
                                    }
                                    )), "公", 4)
                                ), 12, _uA(
                                    "onClick"
                                )),
                                _cE("view", _uM("class" to "ap-chip", "style" to _nS(if (unref(gender) === 2) {
                                    chipOn
                                } else {
                                    chipOff
                                }
                                ), "onClick" to fun(){
                                    gender.value = 2
                                }
                                ), _uA(
                                    _cE("text", _uM("style" to _nS(if (unref(gender) === 2) {
                                        chipTextOn
                                    } else {
                                        chipTextOff
                                    }
                                    )), "母", 4)
                                ), 12, _uA(
                                    "onClick"
                                ))
                            ))
                        )),
                        _cE("view", _uM("class" to "ap-field"), _uA(
                            _cE("text", _uM("class" to "ap-label"), "年龄(月)"),
                            _cE("input", _uM("class" to "ap-input", "modelValue" to unref(age), "onInput" to fun(`$event`: UniInputEvent){
                                trySetRefValue(age, `$event`.detail.value)
                            }
                            , "type" to "number", "placeholder" to "输入月龄"), null, 40, _uA(
                                "modelValue"
                            ))
                        )),
                        _cE("view", _uM("class" to "ap-field"), _uA(
                            _cE("text", _uM("class" to "ap-label"), "体重(kg)"),
                            _cE("input", _uM("class" to "ap-input", "modelValue" to unref(weight), "onInput" to fun(`$event`: UniInputEvent){
                                trySetRefValue(weight, `$event`.detail.value)
                            }
                            , "type" to "number", "placeholder" to "输入体重"), null, 40, _uA(
                                "modelValue"
                            ))
                        )),
                        _cE("view", _uM("class" to "ap-submit", "onClick" to submit), _uA(
                            _cE("text", _uM("class" to "ap-submit-text"), "添加宠物")
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
                return _uM("ap-page" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "ap-scroll" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "paddingTop" to "24rpx", "paddingRight" to "24rpx", "paddingBottom" to "24rpx", "paddingLeft" to "24rpx")), "ap-field" to _pS(_uM("marginBottom" to "32rpx")), "ap-label" to _pS(_uM("fontSize" to "28rpx", "fontWeight" to 600, "color" to "#333333", "marginBottom" to "12rpx")), "ap-input" to _pS(_uM("height" to "80rpx", "paddingTop" to 0, "paddingRight" to "24rpx", "paddingBottom" to 0, "paddingLeft" to "24rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "16rpx", "borderTopRightRadius" to "16rpx", "borderBottomRightRadius" to "16rpx", "borderBottomLeftRadius" to "16rpx", "fontSize" to "28rpx", "color" to "#333333")), "ap-row" to _pS(_uM("flexDirection" to "row")), "ap-chip" to _pS(_uM("alignItems" to "center", "justifyContent" to "center")), "ap-picker" to _pS(_uM("height" to "80rpx", "paddingTop" to 0, "paddingRight" to "24rpx", "paddingBottom" to 0, "paddingLeft" to "24rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "16rpx", "borderTopRightRadius" to "16rpx", "borderBottomRightRadius" to "16rpx", "borderBottomLeftRadius" to "16rpx", "justifyContent" to "center")), "ap-submit" to _pS(_uM("marginTop" to "40rpx", "height" to "88rpx", "backgroundColor" to "#FF8C42", "borderTopLeftRadius" to "44rpx", "borderTopRightRadius" to "44rpx", "borderBottomRightRadius" to "44rpx", "borderBottomLeftRadius" to "44rpx", "alignItems" to "center", "justifyContent" to "center")), "ap-submit-text" to _pS(_uM("fontSize" to "32rpx", "color" to "#FFFFFF", "fontWeight" to 600)))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
