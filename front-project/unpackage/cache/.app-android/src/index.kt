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
import io.dcloud.uniapp.extapi.connectSocket as uni_connectSocket
import io.dcloud.uniapp.extapi.getFileSystemManager as uni_getFileSystemManager
import io.dcloud.uniapp.extapi.getStorageSync as uni_getStorageSync
import io.dcloud.uniapp.extapi.reLaunch as uni_reLaunch
val runBlock1 = run {
    __uniConfig.getAppStyles = fun(): Map<String, Map<String, Map<String, Any>>> {
        return GenApp.styles
    }
}
typealias currentPageCaptureScreenshotCallBack = (base64: String, error: String) -> Unit
fun currentPageCaptureScreenshot(fullPage: Boolean, callback: currentPageCaptureScreenshotCallBack) {
    val pages = getCurrentPages() as UTSArray<UniPage>
    val currentPage = pages[pages.length - 1]
    currentPage.vm?.`$viewToTempFilePath`(ViewToTempFilePathOptions(wholeContent = fullPage, overwrite = true, success = fun(res){
        val fileManager = uni_getFileSystemManager()
        fileManager.readFile(ReadFileOptions(encoding = "base64", filePath = res.tempFilePath, success = fun(readFileRes) {
            callback(readFileRes.data as String, "")
        }
        , fail = fun(err) {
            callback("", "captureScreenshot fail: " + JSON.stringify(err))
        }
        ))
    }
    , fail = fun(err){
        callback("", "captureScreenshot fail: " + JSON.stringify(err))
    }
    ))
}
fun initRuntimeSocket(hosts: String, port: String, id: String): UTSPromise<SocketTask?> {
    if (hosts == "" || port == "" || id == "") {
        return UTSPromise.resolve(null)
    }
    return hosts.split(",").reduce<UTSPromise<SocketTask?>>(fun(promise: UTSPromise<SocketTask?>, host: String): UTSPromise<SocketTask?> {
        return promise.then(fun(socket): UTSPromise<SocketTask?> {
            if (socket != null) {
                return UTSPromise.resolve(socket)
            }
            return tryConnectSocket(host, port, id)
        }
        )
    }
    , UTSPromise.resolve(null))
}
val SOCKET_TIMEOUT: Number = 500
fun tryConnectSocket(host: String, port: String, id: String): UTSPromise<SocketTask?> {
    return UTSPromise(fun(resolve, reject){
        val socket = uni_connectSocket(ConnectSocketOptions(url = "ws://" + host + ":" + port + "/" + id, fail = fun(_) {
            resolve(null)
        }
        ))
        val timer = setTimeout(fun(){
            socket.close(CloseSocketOptions(code = 1006, reason = "connect timeout"))
            resolve(null)
        }
        , SOCKET_TIMEOUT)
        socket.onOpen(fun(e){
            clearTimeout(timer)
            resolve(socket)
        }
        )
        socket.onClose(fun(e){
            clearTimeout(timer)
            resolve(null)
        }
        )
        socket.onError(fun(e){
            clearTimeout(timer)
            resolve(null)
        }
        )
        socket.onMessage(fun(result){
            if (UTSAndroid.`typeof`(result["data"]) == "string") {
                val message = UTSAndroid.consoleDebugError(JSON.parse<UTSJSONObject>(result["data"] as String), " at ../../../../../../../../../Builder/HBuilderX/plugins/uniapp-cli-vite/node_modules/@dcloudio/uni-console/src/runtime/app/socket.ts:67")!!
                if ((message["type"] as String) == "screencap") {
                    val id = message["id"] as String
                    currentPageCaptureScreenshot(message["fullPage"] as Boolean, fun(base64: String, error: String){
                        socket.send(SendSocketMessageOptions(data = JSON.stringify(_uO("id" to id, "base64" to base64, "error" to error))))
                    }
                    )
                }
            }
            resolve(null)
        }
        )
    }
    )
}
fun initRuntimeSocketService(): UTSPromise<Boolean> {
    val hosts: String = "192.168.59.1,192.168.133.1,192.168.1.5,127.0.0.1"
    val port: String = "8090"
    val id: String = "app-android_V5_3SW"
    if (hosts == "" || port == "" || id == "") {
        return UTSPromise.resolve(false)
    }
    return UTSPromise.resolve().then(fun(): UTSPromise<Boolean> {
        return initRuntimeSocket(hosts, port, id).then(fun(socket): Boolean {
            if (socket == null) {
                return false
            }
            socket
            return true
        }
        )
    }
    ).`catch`(fun(): Boolean {
        return false
    }
    )
}
val runBlock2 = run {
    initRuntimeSocketService()
}
open class GenApp : BaseApp {
    constructor(__ins: ComponentInternalInstance) : super(__ins) {
        setCurrentInstance(__ins)
        __ins.proxy = this
        GenApp.setup(this)
    }
    companion object {
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        var setup: (__props: GenApp) -> Any? = fun(__props): Any? {
            val __ins = getCurrentInstance()!!
            val _ctx = __ins.proxy as GenApp
            val _cache = __ins.renderCache
            onLaunch(fun(_options){
                val token = uni_getStorageSync("token")
                if (token == null || token === "") {
                    uni_reLaunch(ReLaunchOptions(url = "/pages/auth/login"))
                }
            }
            )
            return fun(): Any? {
                return null
            }
        }
        val styles: Map<String, Map<String, Map<String, Any>>> by lazy {
            _nCS(_uA())
        }
    }
}
val GenAppClass = CreateVueAppComponent(GenApp::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "app", name = "", inheritAttrs = true, inject = Map(), props = Map(), propsNeedCastKeys = _uA(), emits = Map(), components = Map(), styles = GenApp.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenApp.setup(props as GenApp)
    }
    )
}
, fun(instance): GenApp {
    return GenApp(instance)
}
)
val GenPagesIndexIndexClass = CreateVueComponent(GenPagesIndexIndex::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesIndexIndex.inheritAttrs, inject = GenPagesIndexIndex.inject, props = GenPagesIndexIndex.props, propsNeedCastKeys = GenPagesIndexIndex.propsNeedCastKeys, emits = GenPagesIndexIndex.emits, components = GenPagesIndexIndex.components, styles = GenPagesIndexIndex.styles)
}
, fun(instance, renderer): GenPagesIndexIndex {
    return GenPagesIndexIndex(instance, renderer)
}
)
open class KI (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var title: String,
    @JsonNotNull
    open var viewCount: Number,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("KI", "pages/knowledge/list.uvue", 33, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return KIReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class KIReactiveObject : KI, IUTSReactive<KI> {
    override var __v_raw: KI
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: KI, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, title = __v_raw.title, viewCount = __v_raw.viewCount) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): KIReactiveObject {
        return KIReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var title: String
        get() {
            return _tRG(__v_raw, "title", __v_raw.title, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("title")) {
                return
            }
            val oldValue = __v_raw.title
            __v_raw.title = value
            _tRS(__v_raw, "title", oldValue, value)
        }
    override var viewCount: Number
        get() {
            return _tRG(__v_raw, "viewCount", __v_raw.viewCount, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("viewCount")) {
                return
            }
            val oldValue = __v_raw.viewCount
            __v_raw.viewCount = value
            _tRS(__v_raw, "viewCount", oldValue, value)
        }
}
open class CT (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var name: String,
    @JsonNotNull
    open var species: Number,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("CT", "pages/knowledge/list.uvue", 34, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return CTReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class CTReactiveObject : CT, IUTSReactive<CT> {
    override var __v_raw: CT
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: CT, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, name = __v_raw.name, species = __v_raw.species) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): CTReactiveObject {
        return CTReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var name: String
        get() {
            return _tRG(__v_raw, "name", __v_raw.name, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("name")) {
                return
            }
            val oldValue = __v_raw.name
            __v_raw.name = value
            _tRS(__v_raw, "name", oldValue, value)
        }
    override var species: Number
        get() {
            return _tRG(__v_raw, "species", __v_raw.species, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("species")) {
                return
            }
            val oldValue = __v_raw.species
            __v_raw.species = value
            _tRS(__v_raw, "species", oldValue, value)
        }
}
val GenPagesKnowledgeListClass = CreateVueComponent(GenPagesKnowledgeList::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesKnowledgeList.inheritAttrs, inject = GenPagesKnowledgeList.inject, props = GenPagesKnowledgeList.props, propsNeedCastKeys = GenPagesKnowledgeList.propsNeedCastKeys, emits = GenPagesKnowledgeList.emits, components = GenPagesKnowledgeList.components, styles = GenPagesKnowledgeList.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesKnowledgeList.setup(props as GenPagesKnowledgeList)
    }
    )
}
, fun(instance, renderer): GenPagesKnowledgeList {
    return GenPagesKnowledgeList(instance, renderer)
}
)
open class DetailData (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var title: String,
    @JsonNotNull
    open var content: String,
    @JsonNotNull
    open var viewCount: Number,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("DetailData", "pages/knowledge/detail.uvue", 22, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return DetailDataReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class DetailDataReactiveObject : DetailData, IUTSReactive<DetailData> {
    override var __v_raw: DetailData
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: DetailData, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, title = __v_raw.title, content = __v_raw.content, viewCount = __v_raw.viewCount) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): DetailDataReactiveObject {
        return DetailDataReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var title: String
        get() {
            return _tRG(__v_raw, "title", __v_raw.title, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("title")) {
                return
            }
            val oldValue = __v_raw.title
            __v_raw.title = value
            _tRS(__v_raw, "title", oldValue, value)
        }
    override var content: String
        get() {
            return _tRG(__v_raw, "content", __v_raw.content, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("content")) {
                return
            }
            val oldValue = __v_raw.content
            __v_raw.content = value
            _tRS(__v_raw, "content", oldValue, value)
        }
    override var viewCount: Number
        get() {
            return _tRG(__v_raw, "viewCount", __v_raw.viewCount, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("viewCount")) {
                return
            }
            val oldValue = __v_raw.viewCount
            __v_raw.viewCount = value
            _tRS(__v_raw, "viewCount", oldValue, value)
        }
}
val GenPagesKnowledgeDetailClass = CreateVueComponent(GenPagesKnowledgeDetail::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesKnowledgeDetail.inheritAttrs, inject = GenPagesKnowledgeDetail.inject, props = GenPagesKnowledgeDetail.props, propsNeedCastKeys = GenPagesKnowledgeDetail.propsNeedCastKeys, emits = GenPagesKnowledgeDetail.emits, components = GenPagesKnowledgeDetail.components, styles = GenPagesKnowledgeDetail.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesKnowledgeDetail.setup(props as GenPagesKnowledgeDetail)
    }
    )
}
, fun(instance, renderer): GenPagesKnowledgeDetail {
    return GenPagesKnowledgeDetail(instance, renderer)
}
)
open class PetData (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var name: String,
    @JsonNotNull
    open var breedName: String,
    @JsonNotNull
    open var gender: Number,
    @JsonNotNull
    open var age: Number,
    @JsonNotNull
    open var weight: Number,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("PetData", "pages/pets/list.uvue", 23, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return PetDataReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class PetDataReactiveObject : PetData, IUTSReactive<PetData> {
    override var __v_raw: PetData
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: PetData, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, name = __v_raw.name, breedName = __v_raw.breedName, gender = __v_raw.gender, age = __v_raw.age, weight = __v_raw.weight) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): PetDataReactiveObject {
        return PetDataReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var name: String
        get() {
            return _tRG(__v_raw, "name", __v_raw.name, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("name")) {
                return
            }
            val oldValue = __v_raw.name
            __v_raw.name = value
            _tRS(__v_raw, "name", oldValue, value)
        }
    override var breedName: String
        get() {
            return _tRG(__v_raw, "breedName", __v_raw.breedName, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("breedName")) {
                return
            }
            val oldValue = __v_raw.breedName
            __v_raw.breedName = value
            _tRS(__v_raw, "breedName", oldValue, value)
        }
    override var gender: Number
        get() {
            return _tRG(__v_raw, "gender", __v_raw.gender, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("gender")) {
                return
            }
            val oldValue = __v_raw.gender
            __v_raw.gender = value
            _tRS(__v_raw, "gender", oldValue, value)
        }
    override var age: Number
        get() {
            return _tRG(__v_raw, "age", __v_raw.age, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("age")) {
                return
            }
            val oldValue = __v_raw.age
            __v_raw.age = value
            _tRS(__v_raw, "age", oldValue, value)
        }
    override var weight: Number
        get() {
            return _tRG(__v_raw, "weight", __v_raw.weight, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("weight")) {
                return
            }
            val oldValue = __v_raw.weight
            __v_raw.weight = value
            _tRS(__v_raw, "weight", oldValue, value)
        }
}
val GenPagesPetsListClass = CreateVueComponent(GenPagesPetsList::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesPetsList.inheritAttrs, inject = GenPagesPetsList.inject, props = GenPagesPetsList.props, propsNeedCastKeys = GenPagesPetsList.propsNeedCastKeys, emits = GenPagesPetsList.emits, components = GenPagesPetsList.components, styles = GenPagesPetsList.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesPetsList.setup(props as GenPagesPetsList)
    }
    )
}
, fun(instance, renderer): GenPagesPetsList {
    return GenPagesPetsList(instance, renderer)
}
)
open class BreedItem (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var name: String,
    @JsonNotNull
    open var species: Number,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("BreedItem", "pages/pets/add.uvue", 44, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return BreedItemReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class BreedItemReactiveObject : BreedItem, IUTSReactive<BreedItem> {
    override var __v_raw: BreedItem
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: BreedItem, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, name = __v_raw.name, species = __v_raw.species) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): BreedItemReactiveObject {
        return BreedItemReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var name: String
        get() {
            return _tRG(__v_raw, "name", __v_raw.name, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("name")) {
                return
            }
            val oldValue = __v_raw.name
            __v_raw.name = value
            _tRS(__v_raw, "name", oldValue, value)
        }
    override var species: Number
        get() {
            return _tRG(__v_raw, "species", __v_raw.species, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("species")) {
                return
            }
            val oldValue = __v_raw.species
            __v_raw.species = value
            _tRS(__v_raw, "species", oldValue, value)
        }
}
val GenPagesPetsAddClass = CreateVueComponent(GenPagesPetsAdd::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesPetsAdd.inheritAttrs, inject = GenPagesPetsAdd.inject, props = GenPagesPetsAdd.props, propsNeedCastKeys = GenPagesPetsAdd.propsNeedCastKeys, emits = GenPagesPetsAdd.emits, components = GenPagesPetsAdd.components, styles = GenPagesPetsAdd.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesPetsAdd.setup(props as GenPagesPetsAdd)
    }
    )
}
, fun(instance, renderer): GenPagesPetsAdd {
    return GenPagesPetsAdd(instance, renderer)
}
)
open class RemData (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var ruleType: String,
    @JsonNotNull
    open var nextDate: String,
    @JsonNotNull
    open var cycleDays: Number,
    @JsonNotNull
    open var enabled: Boolean = false,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("RemData", "pages/reminder/index.uvue", 57, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return RemDataReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class RemDataReactiveObject : RemData, IUTSReactive<RemData> {
    override var __v_raw: RemData
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: RemData, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, ruleType = __v_raw.ruleType, nextDate = __v_raw.nextDate, cycleDays = __v_raw.cycleDays, enabled = __v_raw.enabled) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): RemDataReactiveObject {
        return RemDataReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var ruleType: String
        get() {
            return _tRG(__v_raw, "ruleType", __v_raw.ruleType, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("ruleType")) {
                return
            }
            val oldValue = __v_raw.ruleType
            __v_raw.ruleType = value
            _tRS(__v_raw, "ruleType", oldValue, value)
        }
    override var nextDate: String
        get() {
            return _tRG(__v_raw, "nextDate", __v_raw.nextDate, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("nextDate")) {
                return
            }
            val oldValue = __v_raw.nextDate
            __v_raw.nextDate = value
            _tRS(__v_raw, "nextDate", oldValue, value)
        }
    override var cycleDays: Number
        get() {
            return _tRG(__v_raw, "cycleDays", __v_raw.cycleDays, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("cycleDays")) {
                return
            }
            val oldValue = __v_raw.cycleDays
            __v_raw.cycleDays = value
            _tRS(__v_raw, "cycleDays", oldValue, value)
        }
    override var enabled: Boolean
        get() {
            return _tRG(__v_raw, "enabled", __v_raw.enabled, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("enabled")) {
                return
            }
            val oldValue = __v_raw.enabled
            __v_raw.enabled = value
            _tRS(__v_raw, "enabled", oldValue, value)
        }
}
open class PetWithRem (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var name: String,
    @JsonNotNull
    open var species: Number,
    @JsonNotNull
    open var reminders: UTSArray<RemData>,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("PetWithRem", "pages/reminder/index.uvue", 58, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return PetWithRemReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class PetWithRemReactiveObject : PetWithRem, IUTSReactive<PetWithRem> {
    override var __v_raw: PetWithRem
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: PetWithRem, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, name = __v_raw.name, species = __v_raw.species, reminders = __v_raw.reminders) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): PetWithRemReactiveObject {
        return PetWithRemReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var name: String
        get() {
            return _tRG(__v_raw, "name", __v_raw.name, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("name")) {
                return
            }
            val oldValue = __v_raw.name
            __v_raw.name = value
            _tRS(__v_raw, "name", oldValue, value)
        }
    override var species: Number
        get() {
            return _tRG(__v_raw, "species", __v_raw.species, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("species")) {
                return
            }
            val oldValue = __v_raw.species
            __v_raw.species = value
            _tRS(__v_raw, "species", oldValue, value)
        }
    override var reminders: UTSArray<RemData>
        get() {
            return _tRG(__v_raw, "reminders", __v_raw.reminders, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("reminders")) {
                return
            }
            val oldValue = __v_raw.reminders
            __v_raw.reminders = value
            _tRS(__v_raw, "reminders", oldValue, value)
        }
}
val GenPagesReminderIndexClass = CreateVueComponent(GenPagesReminderIndex::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesReminderIndex.inheritAttrs, inject = GenPagesReminderIndex.inject, props = GenPagesReminderIndex.props, propsNeedCastKeys = GenPagesReminderIndex.propsNeedCastKeys, emits = GenPagesReminderIndex.emits, components = GenPagesReminderIndex.components, styles = GenPagesReminderIndex.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesReminderIndex.setup(props as GenPagesReminderIndex)
    }
    )
}
, fun(instance, renderer): GenPagesReminderIndex {
    return GenPagesReminderIndex(instance, renderer)
}
)
open class FavItem (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var title: String,
    @JsonNotNull
    open var time: String,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("FavItem", "pages/favorites/index.uvue", 22, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return FavItemReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class FavItemReactiveObject : FavItem, IUTSReactive<FavItem> {
    override var __v_raw: FavItem
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: FavItem, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, title = __v_raw.title, time = __v_raw.time) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): FavItemReactiveObject {
        return FavItemReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var title: String
        get() {
            return _tRG(__v_raw, "title", __v_raw.title, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("title")) {
                return
            }
            val oldValue = __v_raw.title
            __v_raw.title = value
            _tRS(__v_raw, "title", oldValue, value)
        }
    override var time: String
        get() {
            return _tRG(__v_raw, "time", __v_raw.time, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("time")) {
                return
            }
            val oldValue = __v_raw.time
            __v_raw.time = value
            _tRS(__v_raw, "time", oldValue, value)
        }
}
val GenPagesFavoritesIndexClass = CreateVueComponent(GenPagesFavoritesIndex::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesFavoritesIndex.inheritAttrs, inject = GenPagesFavoritesIndex.inject, props = GenPagesFavoritesIndex.props, propsNeedCastKeys = GenPagesFavoritesIndex.propsNeedCastKeys, emits = GenPagesFavoritesIndex.emits, components = GenPagesFavoritesIndex.components, styles = GenPagesFavoritesIndex.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesFavoritesIndex.setup(props as GenPagesFavoritesIndex)
    }
    )
}
, fun(instance, renderer): GenPagesFavoritesIndex {
    return GenPagesFavoritesIndex(instance, renderer)
}
)
val GenPagesAboutIndexClass = CreateVueComponent(GenPagesAboutIndex::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesAboutIndex.inheritAttrs, inject = GenPagesAboutIndex.inject, props = GenPagesAboutIndex.props, propsNeedCastKeys = GenPagesAboutIndex.propsNeedCastKeys, emits = GenPagesAboutIndex.emits, components = GenPagesAboutIndex.components, styles = GenPagesAboutIndex.styles)
}
, fun(instance, renderer): GenPagesAboutIndex {
    return GenPagesAboutIndex(instance, renderer)
}
)
open class UserData (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var nickname: String,
    @JsonNotNull
    open var memberType: Number,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("UserData", "pages/mine/index.uvue", 44, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return UserDataReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class UserDataReactiveObject : UserData, IUTSReactive<UserData> {
    override var __v_raw: UserData
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: UserData, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, nickname = __v_raw.nickname, memberType = __v_raw.memberType) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UserDataReactiveObject {
        return UserDataReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var nickname: String
        get() {
            return _tRG(__v_raw, "nickname", __v_raw.nickname, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("nickname")) {
                return
            }
            val oldValue = __v_raw.nickname
            __v_raw.nickname = value
            _tRS(__v_raw, "nickname", oldValue, value)
        }
    override var memberType: Number
        get() {
            return _tRG(__v_raw, "memberType", __v_raw.memberType, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("memberType")) {
                return
            }
            val oldValue = __v_raw.memberType
            __v_raw.memberType = value
            _tRS(__v_raw, "memberType", oldValue, value)
        }
}
val GenPagesMineIndexClass = CreateVueComponent(GenPagesMineIndex::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesMineIndex.inheritAttrs, inject = GenPagesMineIndex.inject, props = GenPagesMineIndex.props, propsNeedCastKeys = GenPagesMineIndex.propsNeedCastKeys, emits = GenPagesMineIndex.emits, components = GenPagesMineIndex.components, styles = GenPagesMineIndex.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesMineIndex.setup(props as GenPagesMineIndex)
    }
    )
}
, fun(instance, renderer): GenPagesMineIndex {
    return GenPagesMineIndex(instance, renderer)
}
)
val GenPagesAuthLoginClass = CreateVueComponent(GenPagesAuthLogin::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesAuthLogin.inheritAttrs, inject = GenPagesAuthLogin.inject, props = GenPagesAuthLogin.props, propsNeedCastKeys = GenPagesAuthLogin.propsNeedCastKeys, emits = GenPagesAuthLogin.emits, components = GenPagesAuthLogin.components, styles = GenPagesAuthLogin.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesAuthLogin.setup(props as GenPagesAuthLogin)
    }
    )
}
, fun(instance, renderer): GenPagesAuthLogin {
    return GenPagesAuthLogin(instance, renderer)
}
)
val GenPagesAuthRegisterClass = CreateVueComponent(GenPagesAuthRegister::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesAuthRegister.inheritAttrs, inject = GenPagesAuthRegister.inject, props = GenPagesAuthRegister.props, propsNeedCastKeys = GenPagesAuthRegister.propsNeedCastKeys, emits = GenPagesAuthRegister.emits, components = GenPagesAuthRegister.components, styles = GenPagesAuthRegister.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesAuthRegister.setup(props as GenPagesAuthRegister)
    }
    )
}
, fun(instance, renderer): GenPagesAuthRegister {
    return GenPagesAuthRegister(instance, renderer)
}
)
open class PlanData (
    @JsonNotNull
    open var id: Number,
    @JsonNotNull
    open var name: String,
    @JsonNotNull
    open var price: Number,
    @JsonNotNull
    open var durationDays: Number,
    @JsonNotNull
    open var petLimit: Number,
) : UTSReactiveObject(), IUTSSourceMap {
    override fun `__$getOriginalPosition`(): UTSSourceMapPosition? {
        return UTSSourceMapPosition("PlanData", "pages/subscription/index.uvue", 20, 6)
    }
    override fun __v_create(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): UTSReactiveObject {
        return PlanDataReactiveObject(this, __v_isReadonly, __v_isShallow, __v_skip)
    }
}
class PlanDataReactiveObject : PlanData, IUTSReactive<PlanData> {
    override var __v_raw: PlanData
    override var __v_isReadonly: Boolean
    override var __v_isShallow: Boolean
    override var __v_skip: Boolean
    constructor(__v_raw: PlanData, __v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean) : super(id = __v_raw.id, name = __v_raw.name, price = __v_raw.price, durationDays = __v_raw.durationDays, petLimit = __v_raw.petLimit) {
        this.__v_raw = __v_raw
        this.__v_isReadonly = __v_isReadonly
        this.__v_isShallow = __v_isShallow
        this.__v_skip = __v_skip
    }
    override fun __v_clone(__v_isReadonly: Boolean, __v_isShallow: Boolean, __v_skip: Boolean): PlanDataReactiveObject {
        return PlanDataReactiveObject(this.__v_raw, __v_isReadonly, __v_isShallow, __v_skip)
    }
    override var id: Number
        get() {
            return _tRG(__v_raw, "id", __v_raw.id, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("id")) {
                return
            }
            val oldValue = __v_raw.id
            __v_raw.id = value
            _tRS(__v_raw, "id", oldValue, value)
        }
    override var name: String
        get() {
            return _tRG(__v_raw, "name", __v_raw.name, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("name")) {
                return
            }
            val oldValue = __v_raw.name
            __v_raw.name = value
            _tRS(__v_raw, "name", oldValue, value)
        }
    override var price: Number
        get() {
            return _tRG(__v_raw, "price", __v_raw.price, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("price")) {
                return
            }
            val oldValue = __v_raw.price
            __v_raw.price = value
            _tRS(__v_raw, "price", oldValue, value)
        }
    override var durationDays: Number
        get() {
            return _tRG(__v_raw, "durationDays", __v_raw.durationDays, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("durationDays")) {
                return
            }
            val oldValue = __v_raw.durationDays
            __v_raw.durationDays = value
            _tRS(__v_raw, "durationDays", oldValue, value)
        }
    override var petLimit: Number
        get() {
            return _tRG(__v_raw, "petLimit", __v_raw.petLimit, __v_isReadonly, __v_isShallow)
        }
        set(value) {
            if (!__v_canSet("petLimit")) {
                return
            }
            val oldValue = __v_raw.petLimit
            __v_raw.petLimit = value
            _tRS(__v_raw, "petLimit", oldValue, value)
        }
}
val GenPagesSubscriptionIndexClass = CreateVueComponent(GenPagesSubscriptionIndex::class.java, fun(): VueComponentOptions {
    return VueComponentOptions(type = "page", name = "", inheritAttrs = GenPagesSubscriptionIndex.inheritAttrs, inject = GenPagesSubscriptionIndex.inject, props = GenPagesSubscriptionIndex.props, propsNeedCastKeys = GenPagesSubscriptionIndex.propsNeedCastKeys, emits = GenPagesSubscriptionIndex.emits, components = GenPagesSubscriptionIndex.components, styles = GenPagesSubscriptionIndex.styles, setup = fun(props: ComponentPublicInstance): Any? {
        return GenPagesSubscriptionIndex.setup(props as GenPagesSubscriptionIndex)
    }
    )
}
, fun(instance, renderer): GenPagesSubscriptionIndex {
    return GenPagesSubscriptionIndex(instance, renderer)
}
)
fun createApp(): UTSJSONObject {
    val app = createSSRApp(GenAppClass)
    return _uO("app" to app)
}
fun main(app: IApp) {
    enableStyleIsolation()
    definePageRoutes()
    defineAppConfig()
    (createApp()["app"] as VueApp).mount(app, GenUniApp())
}
open class UniAppConfig : io.dcloud.uniapp.appframe.AppConfig {
    override var name: String = "喵汪"
    override var appid: String = "__UNI__uniappx"
    override var versionName: String = "1.0.0"
    override var versionCode: String = "100"
    override var uniCompilerVersion: String = "5.15"
    constructor() : super() {}
}
fun definePageRoutes() {
    __uniRoutes.push(UniPageRoute(path = "pages/index/index", component = GenPagesIndexIndexClass, meta = UniPageMeta(isQuit = true), style = _uM("navigationBarTitleText" to "喵汪")))
    __uniRoutes.push(UniPageRoute(path = "pages/knowledge/list", component = GenPagesKnowledgeListClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "知识库")))
    __uniRoutes.push(UniPageRoute(path = "pages/knowledge/detail", component = GenPagesKnowledgeDetailClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "详情")))
    __uniRoutes.push(UniPageRoute(path = "pages/pets/list", component = GenPagesPetsListClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "我的萌宠")))
    __uniRoutes.push(UniPageRoute(path = "pages/pets/add", component = GenPagesPetsAddClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "添加宠物")))
    __uniRoutes.push(UniPageRoute(path = "pages/reminder/index", component = GenPagesReminderIndexClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "护理日历")))
    __uniRoutes.push(UniPageRoute(path = "pages/favorites/index", component = GenPagesFavoritesIndexClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "我的收藏")))
    __uniRoutes.push(UniPageRoute(path = "pages/about/index", component = GenPagesAboutIndexClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "关于喵汪")))
    __uniRoutes.push(UniPageRoute(path = "pages/mine/index", component = GenPagesMineIndexClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "我的")))
    __uniRoutes.push(UniPageRoute(path = "pages/auth/login", component = GenPagesAuthLoginClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "登录")))
    __uniRoutes.push(UniPageRoute(path = "pages/auth/register", component = GenPagesAuthRegisterClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "注册")))
    __uniRoutes.push(UniPageRoute(path = "pages/subscription/index", component = GenPagesSubscriptionIndexClass, meta = UniPageMeta(isQuit = false), style = _uM("navigationBarTitleText" to "订阅中心")))
}
val __uniTabBar: Map<String, Any?>? = _uM("color" to "#999999", "selectedColor" to "#FF8C42", "backgroundColor" to "#FFFFFF", "borderStyle" to "white", "list" to _uA(
    _uM("pagePath" to "pages/index/index", "text" to "喵汪"),
    _uM("pagePath" to "pages/knowledge/list", "text" to "知识库"),
    _uM("pagePath" to "pages/pets/list", "text" to "萌宠"),
    _uM("pagePath" to "pages/mine/index", "text" to "我的")
))
val __uniLaunchPage: Map<String, Any?> = _uM("url" to "pages/index/index", "style" to _uM("navigationBarTitleText" to "喵汪"))
fun defineAppConfig() {
    __uniConfig.entryPagePath = "/pages/index/index"
    __uniConfig.globalStyle = _uM("navigationBarTextStyle" to "white", "navigationBarTitleText" to "喵汪", "navigationBarBackgroundColor" to "#FF8C42", "backgroundColor" to "#FFF8F0")
    __uniConfig.getTabBarConfig = fun(): Map<String, Any>? {
        return _uM("color" to "#999999", "selectedColor" to "#FF8C42", "backgroundColor" to "#FFFFFF", "borderStyle" to "white", "list" to _uA(
            _uM("pagePath" to "pages/index/index", "text" to "喵汪"),
            _uM("pagePath" to "pages/knowledge/list", "text" to "知识库"),
            _uM("pagePath" to "pages/pets/list", "text" to "萌宠"),
            _uM("pagePath" to "pages/mine/index", "text" to "我的")
        ))
    }
    __uniConfig.tabBar = __uniConfig.getTabBarConfig()
    __uniConfig.conditionUrl = ""
    __uniConfig.uniIdRouter = Map()
    __uniConfig.ready = true
}
open class GenUniApp : UniAppImpl() {
    open val vm: GenApp?
        get() {
            return getAppVm() as GenApp?
        }
    open val `$vm`: GenApp?
        get() {
            return getAppVm() as GenApp?
        }
}
fun getApp(): GenUniApp {
    return getUniApp() as GenUniApp
}
