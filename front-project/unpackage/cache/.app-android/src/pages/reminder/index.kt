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
open class GenPagesReminderIndex : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenPagesReminderIndex) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenPagesReminderIndex
            val _cache = __ins.renderCache
            val BASE = "http://localhost:8080"
            val pets = ref(_uA<PetWithRem>())
            val typeOptions = _uA(
                "疫苗",
                "驱虫",
                "剪指甲",
                "洗澡",
                "体检"
            )
            val typeKeys = _uA(
                "vaccine",
                "deworm",
                "nail",
                "bath",
                "checkup"
            )
            val showModal = ref(false)
            val addPetId = ref(0)
            val addPetName = ref("")
            val addTypeIdx = ref(0)
            val addDate = ref("")
            val addCycle = ref("90")
            fun gen_getToken_fn(): String {
                val t = uni_getStorageSync("token")
                return if (t != null) {
                    t as String
                } else {
                    ""
                }
            }
            val getToken = ::gen_getToken_fn
            fun gen_parseDate_fn(s: String): Number {
                val parts = s.split("-")
                if (parts.length !== 3) {
                    return 0
                }
                val y = parseInt(parts[0])
                val m = parseInt(parts[1]) - 1
                val d = parseInt(parts[2])
                return Date(y, m, d).getTime()
            }
            val parseDate = ::gen_parseDate_fn
            fun gen_typeLabel_fn(t: String): String {
                if (t === "vaccine") {
                    return "💉 疫苗"
                }
                if (t === "deworm") {
                    return "💊 驱虫"
                }
                if (t === "nail") {
                    return "✂️ 剪指甲"
                }
                if (t === "bath") {
                    return "🛁 洗澡"
                }
                if (t === "checkup") {
                    return "🏥 体检"
                }
                return t
            }
            val typeLabel = ::gen_typeLabel_fn
            fun gen_statusColor_fn(dateStr: String): String {
                val d = parseDate(dateStr)
                val now = Date.now()
                val diff = d - now
                val dayMs: Number = 86400000
                if (diff < 0) {
                    return "#FF4444"
                }
                if (diff < 3 * dayMs) {
                    return "#FF8C42"
                }
                if (diff < 7 * dayMs) {
                    return "#FFD700"
                }
                return "#4CAF50"
            }
            val statusColor = ::gen_statusColor_fn
            fun gen_statusText_fn(dateStr: String): String {
                val d = parseDate(dateStr)
                val now = Date.now()
                val diff = d - now
                val dayMs: Number = 86400000
                if (diff < 0) {
                    return "已过期"
                }
                if (diff < dayMs) {
                    return "今天"
                }
                if (diff < 2 * dayMs) {
                    return "明天"
                }
                if (diff < 7 * dayMs) {
                    val days: Number = Math.ceil(diff / dayMs)
                    return days + "天后"
                }
                return ""
            }
            val statusText = ::gen_statusText_fn
            fun gen_loadReminders_fn(petId: Number, idx: Number): Unit {
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/reminder/index.uvue", 126, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                uni_request<Any>(RequestOptions(url = BASE + "/api/reminders?petId=" + petId, method = "GET", header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val body = d as UTSJSONObject
                        val code = body.getNumber("code")
                        if (code === 200) {
                            val inner = body.get("data")
                            if (inner != null) {
                                val arr = inner as Any as UTSArray<Any>
                                val rems: UTSArray<RemData> = _uA()
                                run {
                                    var k: Number = 0
                                    while(k < arr.length){
                                        val r = arr[k] as UTSJSONObject
                                        val rid = r.getNumber("id")
                                        val rtype = r.getString("ruleType")
                                        val rdate = r.getString("nextDate")
                                        val rcycle = r.getNumber("cycleDays")
                                        rems.push(RemData(id = if (rid != null) {
                                            rid
                                        } else {
                                            0
                                        }
                                        , ruleType = if (rtype != null) {
                                            rtype
                                        } else {
                                            ""
                                        }
                                        , nextDate = if (rdate != null) {
                                            rdate
                                        } else {
                                            ""
                                        }
                                        , cycleDays = if (rcycle != null) {
                                            rcycle
                                        } else {
                                            0
                                        }
                                        , enabled = true))
                                        k++
                                    }
                                }
                                if (idx < pets.value.length) {
                                    val updated = pets.value
                                    updated[idx].reminders = rems
                                    pets.value = updated
                                }
                            }
                        }
                    }
                }
                , fail = fun(_): Unit {}))
            }
            val loadReminders = ::gen_loadReminders_fn
            fun gen_loadData_fn(): Unit {
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/reminder/index.uvue", 172, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                uni_request<Any>(RequestOptions(url = BASE + "/api/pets?userId=1", method = "GET", header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val body = d as UTSJSONObject
                        val code = body.getNumber("code")
                        if (code === 200) {
                            val inner = body.get("data")
                            if (inner != null) {
                                val petList = inner as Any as UTSArray<Any>
                                val result: UTSArray<PetWithRem> = _uA()
                                run {
                                    var i: Number = 0
                                    while(i < petList.length){
                                        val p = petList[i] as UTSJSONObject
                                        val pid = p.getNumber("id")
                                        val pname = p.getString("name")
                                        val pspecies = p.getNumber("species")
                                        result.push(PetWithRem(id = if (pid != null) {
                                            pid
                                        } else {
                                            0
                                        }
                                        , name = if (pname != null) {
                                            pname
                                        } else {
                                            ""
                                        }
                                        , species = if (pspecies != null) {
                                            pspecies
                                        } else {
                                            1
                                        }
                                        , reminders = _uA()))
                                        i++
                                    }
                                }
                                pets.value = result
                                run {
                                    var j: Number = 0
                                    while(j < result.length){
                                        loadReminders(result[j].id, j)
                                        j++
                                    }
                                }
                            }
                        }
                    }
                }
                , fail = fun(_): Unit {}))
            }
            val loadData = ::gen_loadData_fn
            fun gen_openAdd_fn(petId: Number, petName: String): Unit {
                addPetId.value = petId
                addPetName.value = petName
                addTypeIdx.value = 0
                addDate.value = ""
                addCycle.value = "90"
                showModal.value = true
            }
            val openAdd = ::gen_openAdd_fn
            fun gen_onTypePick_fn(e: UTSJSONObject): Unit {
                val detail = e.get("detail") as UTSJSONObject
                val v = detail.getNumber("value")
                if (v != null) {
                    addTypeIdx.value = v
                }
            }
            val onTypePick = ::gen_onTypePick_fn
            fun gen_doAdd_fn(): Unit {
                if (addDate.value === "") {
                    uni_showToast(ShowToastOptions(title = "请输入日期", icon = "none"))
                    return
                }
                val token = getToken()
                val h = UTSJSONObject(UTSSourceMapPosition("h", "pages/reminder/index.uvue", 233, 8))
                h["Content-Type"] = "application/json"
                if (token !== "") {
                    h["Authorization"] = "Bearer " + token
                }
                val body = UTSJSONObject(UTSSourceMapPosition("body", "pages/reminder/index.uvue", 237, 8))
                body["petId"] = addPetId.value
                body["ruleType"] = typeKeys[addTypeIdx.value]
                body["nextDate"] = addDate.value
                body["cycleDays"] = parseInt(addCycle.value)
                body["enabled"] = true
                uni_request<Any>(RequestOptions(url = BASE + "/api/reminders", method = "POST", data = body, header = h, success = fun(res): Unit {
                    val d = res.data
                    if (d != null) {
                        val resp = d as UTSJSONObject
                        val code = resp.getNumber("code")
                        if (code === 200) {
                            uni_showToast(ShowToastOptions(title = "添加成功", icon = "success"))
                            showModal.value = false
                            loadData()
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
            val doAdd = ::gen_doAdd_fn
            onShow(fun(){
                loadData()
            }
            )
            return fun(): Any? {
                val _component_picker = resolveComponent("picker")
                return _cE("view", _uM("class" to "rm-page"), _uA(
                    _cE("scroll-view", _uM("scroll-y" to "true", "class" to "rm-scroll"), _uA(
                        _cE(Fragment, null, RenderHelpers.renderList(unref(pets), fun(pet, __key, __index, _cached): Any {
                            return _cE("view", _uM("class" to "rm-pet-section", "key" to pet.id), _uA(
                                _cE("view", _uM("class" to "rm-pet-header"), _uA(
                                    _cE("text", _uM("class" to "rm-pet-icon"), _tD(if (pet.species === 1) {
                                        "🐱"
                                    } else {
                                        "🐶"
                                    }
                                    ), 1),
                                    _cE("text", _uM("class" to "rm-pet-name"), _tD(pet.name), 1)
                                )),
                                _cE("view", _uM("class" to "rm-list"), _uA(
                                    _cE(Fragment, null, RenderHelpers.renderList(pet.reminders, fun(r, __key, __index, _cached): Any {
                                        return _cE("view", _uM("class" to "rm-card", "key" to r.id), _uA(
                                            _cE("view", _uM("class" to "rm-left"), _uA(
                                                _cE("text", _uM("class" to "rm-type"), _tD(typeLabel(r.ruleType)), 1),
                                                _cE("text", _uM("class" to "rm-date"), _tD(r.nextDate), 1)
                                            )),
                                            _cE("view", _uM("class" to "rm-right"), _uA(
                                                _cE("text", _uM("class" to "rm-cycle"), "每" + _tD(r.cycleDays) + "天", 1),
                                                _cE("view", _uM("class" to "rm-status", "style" to _nS(_uM("backgroundColor" to statusColor(r.nextDate)))), _uA(
                                                    _cE("text", _uM("class" to "rm-status-text"), _tD(statusText(r.nextDate)), 1)
                                                ), 4)
                                            ))
                                        ))
                                    }
                                    ), 128)
                                )),
                                _cE("view", _uM("class" to "rm-add-btn", "onClick" to fun(){
                                    openAdd(pet.id, pet.name)
                                }
                                ), _uA(
                                    _cE("text", _uM("class" to "rm-add-text"), "+ 添加提醒")
                                ), 8, _uA(
                                    "onClick"
                                ))
                            ))
                        }
                        ), 128)
                    )),
                    if (isTrue(unref(showModal))) {
                        _cE("view", _uM("key" to 0, "class" to "rm-modal"), _uA(
                            _cE("view", _uM("class" to "rm-mask", "onClick" to fun(){
                                showModal.value = false
                            }), null, 8, _uA(
                                "onClick"
                            )),
                            _cE("view", _uM("class" to "rm-form"), _uA(
                                _cE("text", _uM("class" to "rm-form-title"), "为 " + _tD(unref(addPetName)) + " 添加提醒", 1),
                                _cE("view", _uM("class" to "rm-field"), _uA(
                                    _cE("text", _uM("class" to "rm-label"), "类型"),
                                    _cV(_component_picker, _uM("range" to typeOptions, "onChange" to onTypePick), _uM("default" to withSlotCtx(fun(): UTSArray<Any> {
                                        return _uA(
                                            _cE("view", _uM("class" to "rm-picker"), _uA(
                                                _cE("text", null, _tD(typeOptions[unref(addTypeIdx)]), 1)
                                            ))
                                        )
                                    }), "_" to 1))
                                )),
                                _cE("view", _uM("class" to "rm-field"), _uA(
                                    _cE("text", _uM("class" to "rm-label"), "日期"),
                                    _cE("input", _uM("class" to "rm-input", "modelValue" to unref(addDate), "onInput" to fun(`$event`: UniInputEvent){
                                        trySetRefValue(addDate, `$event`.detail.value)
                                    }, "placeholder" to "yyyy-MM-dd"), null, 40, _uA(
                                        "modelValue"
                                    ))
                                )),
                                _cE("view", _uM("class" to "rm-field"), _uA(
                                    _cE("text", _uM("class" to "rm-label"), "间隔天数"),
                                    _cE("input", _uM("class" to "rm-input", "modelValue" to unref(addCycle), "onInput" to fun(`$event`: UniInputEvent){
                                        trySetRefValue(addCycle, `$event`.detail.value)
                                    }, "type" to "number", "placeholder" to "如90"), null, 40, _uA(
                                        "modelValue"
                                    ))
                                )),
                                _cE("view", _uM("class" to "rm-actions"), _uA(
                                    _cE("view", _uM("class" to "rm-cancel", "onClick" to fun(){
                                        showModal.value = false
                                    }), _uA(
                                        _cE("text", _uM("class" to "rm-cancel-text"), "取消")
                                    ), 8, _uA(
                                        "onClick"
                                    )),
                                    _cE("view", _uM("class" to "rm-confirm", "onClick" to doAdd), _uA(
                                        _cE("text", _uM("class" to "rm-confirm-text"), "确定")
                                    ))
                                ))
                            ))
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
                return _uM("rm-page" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "rm-scroll" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "paddingTop" to "20rpx", "paddingRight" to "24rpx", "paddingBottom" to "20rpx", "paddingLeft" to "24rpx")), "rm-pet-section" to _pS(_uM("marginBottom" to "28rpx")), "rm-pet-header" to _pS(_uM("flexDirection" to "row", "alignItems" to "center", "marginBottom" to "12rpx")), "rm-pet-icon" to _pS(_uM("fontSize" to "40rpx", "marginRight" to "12rpx")), "rm-pet-name" to _pS(_uM("fontSize" to "34rpx", "fontWeight" to 700, "color" to "#333333")), "rm-list" to _pS(_uM("flexDirection" to "column")), "rm-card" to _pS(_uM("flexDirection" to "row", "justifyContent" to "space-between", "alignItems" to "center", "paddingTop" to "20rpx", "paddingRight" to "20rpx", "paddingBottom" to "20rpx", "paddingLeft" to "20rpx", "marginBottom" to "8rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "14rpx", "borderTopRightRadius" to "14rpx", "borderBottomRightRadius" to "14rpx", "borderBottomLeftRadius" to "14rpx")), "rm-left" to _pS(_uM("flexDirection" to "column")), "rm-type" to _pS(_uM("fontSize" to "26rpx", "color" to "#333333", "fontWeight" to 600)), "rm-date" to _pS(_uM("fontSize" to "22rpx", "color" to "#999999", "marginTop" to "4rpx")), "rm-right" to _pS(_uM("alignItems" to "flex-end")), "rm-cycle" to _pS(_uM("fontSize" to "22rpx", "color" to "#999999")), "rm-status" to _pS(_uM("paddingTop" to "4rpx", "paddingRight" to "16rpx", "paddingBottom" to "4rpx", "paddingLeft" to "16rpx", "borderTopLeftRadius" to "20rpx", "borderTopRightRadius" to "20rpx", "borderBottomRightRadius" to "20rpx", "borderBottomLeftRadius" to "20rpx", "marginTop" to "6rpx")), "rm-status-text" to _pS(_uM("fontSize" to "20rpx", "color" to "#FFFFFF", "fontWeight" to 600)), "rm-add-btn" to _pS(_uM("paddingTop" to "16rpx", "paddingRight" to "16rpx", "paddingBottom" to "16rpx", "paddingLeft" to "16rpx", "backgroundColor" to "#FF8C42", "borderTopLeftRadius" to "12rpx", "borderTopRightRadius" to "12rpx", "borderBottomRightRadius" to "12rpx", "borderBottomLeftRadius" to "12rpx", "alignItems" to "center", "marginTop" to "8rpx")), "rm-add-text" to _pS(_uM("fontSize" to "24rpx", "color" to "#FFFFFF", "fontWeight" to 600)), "rm-modal" to _pS(_uM("position" to "fixed", "top" to 0, "left" to 0, "right" to 0, "bottom" to 0, "alignItems" to "center", "justifyContent" to "center")), "rm-mask" to _pS(_uM("position" to "fixed", "top" to 0, "left" to 0, "right" to 0, "bottom" to 0, "backgroundColor" to "rgba(0,0,0,0.4)")), "rm-form" to _pS(_uM("width" to "600rpx", "paddingTop" to "36rpx", "paddingRight" to "36rpx", "paddingBottom" to "36rpx", "paddingLeft" to "36rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "20rpx", "borderTopRightRadius" to "20rpx", "borderBottomRightRadius" to "20rpx", "borderBottomLeftRadius" to "20rpx")), "rm-form-title" to _pS(_uM("fontSize" to "30rpx", "fontWeight" to 700, "color" to "#333333", "marginBottom" to "28rpx", "textAlign" to "center")), "rm-field" to _pS(_uM("marginBottom" to "20rpx")), "rm-label" to _pS(_uM("fontSize" to "26rpx", "color" to "#666666", "marginBottom" to "8rpx")), "rm-input" to _pS(_uM("height" to "72rpx", "paddingTop" to 0, "paddingRight" to "20rpx", "paddingBottom" to 0, "paddingLeft" to "20rpx", "backgroundColor" to "#F5F5F5", "borderTopLeftRadius" to "12rpx", "borderTopRightRadius" to "12rpx", "borderBottomRightRadius" to "12rpx", "borderBottomLeftRadius" to "12rpx", "fontSize" to "26rpx", "color" to "#333333")), "rm-picker" to _pS(_uM("height" to "72rpx", "paddingTop" to 0, "paddingRight" to "20rpx", "paddingBottom" to 0, "paddingLeft" to "20rpx", "backgroundColor" to "#F5F5F5", "borderTopLeftRadius" to "12rpx", "borderTopRightRadius" to "12rpx", "borderBottomRightRadius" to "12rpx", "borderBottomLeftRadius" to "12rpx", "justifyContent" to "center", "fontSize" to "26rpx", "color" to "#333333")), "rm-actions" to _pS(_uM("flexDirection" to "row", "justifyContent" to "space-between", "marginTop" to "28rpx")), "rm-cancel" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "height" to "72rpx", "backgroundColor" to "#F5F5F5", "borderTopLeftRadius" to "12rpx", "borderTopRightRadius" to "12rpx", "borderBottomRightRadius" to "12rpx", "borderBottomLeftRadius" to "12rpx", "alignItems" to "center", "justifyContent" to "center", "marginRight" to "16rpx")), "rm-cancel-text" to _pS(_uM("fontSize" to "26rpx", "color" to "#666666")), "rm-confirm" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "height" to "72rpx", "backgroundColor" to "#FF8C42", "borderTopLeftRadius" to "12rpx", "borderTopRightRadius" to "12rpx", "borderBottomRightRadius" to "12rpx", "borderBottomLeftRadius" to "12rpx", "alignItems" to "center", "justifyContent" to "center")), "rm-confirm-text" to _pS(_uM("fontSize" to "26rpx", "color" to "#FFFFFF", "fontWeight" to 600)))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
