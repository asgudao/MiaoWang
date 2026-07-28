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
open class GenPagesAboutIndex : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {}
    @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
    override fun `$render`(): Any? {
        val _cache = this.`$`.renderCache
        return _cE("view", _uM("class" to "ab-page"), _uA(
            _cE("scroll-view", _uM("scroll-y" to "true", "class" to "ab-scroll"), _uA(
                _cE("view", _uM("class" to "ab-logo"), _uA(
                    _cE("text", _uM("class" to "ab-logo-icon"), "🐱🐶"),
                    _cE("text", _uM("class" to "ab-logo-text"), "喵汪"),
                    _cE("text", _uM("class" to "ab-version"), "v1.0.0")
                )),
                _cE("view", _uM("class" to "ab-card"), _uA(
                    _cE("text", _uM("class" to "ab-card-title"), "关于喵汪"),
                    _cE("text", _uM("class" to "ab-card-desc"), "喵汪是一款专为爱宠人士打造的宠物养护助手，帮助你科学管理萌宠的日常生活。")
                )),
                _cE("view", _uM("class" to "ab-card"), _uA(
                    _cE("text", _uM("class" to "ab-card-title"), "主要功能"),
                    _cE("view", _uM("class" to "ab-feature"), _uA(
                        _cE("text", _uM("class" to "ab-feature-icon"), "🐾"),
                        _cE("view", _uM("class" to "ab-feature-info"), _uA(
                            _cE("text", _uM("class" to "ab-feature-name"), "萌宠管理"),
                            _cE("text", _uM("class" to "ab-feature-desc"), "添加和管理你的爱宠信息，记录成长点滴")
                        ))
                    )),
                    _cE("view", _uM("class" to "ab-feature"), _uA(
                        _cE("text", _uM("class" to "ab-feature-icon"), "📅"),
                        _cE("view", _uM("class" to "ab-feature-info"), _uA(
                            _cE("text", _uM("class" to "ab-feature-name"), "护理日历"),
                            _cE("text", _uM("class" to "ab-feature-desc"), "疫苗、驱虫、洗澡等护理提醒，不再遗忘")
                        ))
                    )),
                    _cE("view", _uM("class" to "ab-feature"), _uA(
                        _cE("text", _uM("class" to "ab-feature-icon"), "📚"),
                        _cE("view", _uM("class" to "ab-feature-info"), _uA(
                            _cE("text", _uM("class" to "ab-feature-name"), "知识库"),
                            _cE("text", _uM("class" to "ab-feature-desc"), "丰富的养宠知识，科学养护每一天")
                        ))
                    )),
                    _cE("view", _uM("class" to "ab-feature"), _uA(
                        _cE("text", _uM("class" to "ab-feature-icon"), "⭐"),
                        _cE("view", _uM("class" to "ab-feature-info"), _uA(
                            _cE("text", _uM("class" to "ab-feature-name"), "收藏夹"),
                            _cE("text", _uM("class" to "ab-feature-desc"), "收藏有用的知识文章，随时回顾")
                        ))
                    ))
                )),
                _cE("view", _uM("class" to "ab-card"), _uA(
                    _cE("text", _uM("class" to "ab-card-title"), "联系我们"),
                    _cE("text", _uM("class" to "ab-card-desc"), "如有问题或建议，欢迎通过以下方式联系："),
                    _cE("text", _uM("class" to "ab-contact"), "📧 邮箱：喵汪@pet.com"),
                    _cE("text", _uM("class" to "ab-contact"), "🌐 官网：www.喵汪.com")
                )),
                _cE("view", _uM("class" to "ab-footer"), _uA(
                    _cE("text", _uM("class" to "ab-footer-text"), "Made with ❤️ for pets"),
                    _cE("text", _uM("class" to "ab-footer-copy"), "© 2026 喵汪 All Rights Reserved")
                ))
            ))
        ))
    }
    companion object {
        val styles: Map<String, Map<String, Map<String, Any>>> by lazy {
            _nCS(_uA(
                styles0
            ))
        }
        val styles0: Map<String, Map<String, Map<String, Any>>>
            get() {
                return _uM("ab-page" to _pS(_uM("backgroundColor" to "#FFF8F0", "flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "ab-scroll" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "paddingTop" to "24rpx", "paddingRight" to "24rpx", "paddingBottom" to "24rpx", "paddingLeft" to "24rpx")), "ab-logo" to _pS(_uM("alignItems" to "center", "paddingTop" to "48rpx", "paddingRight" to 0, "paddingBottom" to "32rpx", "paddingLeft" to 0)), "ab-logo-icon" to _pS(_uM("fontSize" to "72rpx")), "ab-logo-text" to _pS(_uM("fontSize" to "48rpx", "fontWeight" to 700, "color" to "#FF8C42", "marginTop" to "12rpx")), "ab-version" to _pS(_uM("fontSize" to "24rpx", "color" to "#CCCCCC", "marginTop" to "6rpx")), "ab-card" to _pS(_uM("marginBottom" to "20rpx", "paddingTop" to "28rpx", "paddingRight" to "28rpx", "paddingBottom" to "28rpx", "paddingLeft" to "28rpx", "backgroundColor" to "#FFFFFF", "borderTopLeftRadius" to "16rpx", "borderTopRightRadius" to "16rpx", "borderBottomRightRadius" to "16rpx", "borderBottomLeftRadius" to "16rpx")), "ab-card-title" to _pS(_uM("fontSize" to "30rpx", "fontWeight" to 700, "color" to "#333333", "marginBottom" to "14rpx")), "ab-card-desc" to _pS(_uM("fontSize" to "26rpx", "color" to "#666666")), "ab-feature" to _pS(_uM("flexDirection" to "row", "alignItems" to "flex-start", "marginBottom" to "18rpx")), "ab-feature-icon" to _pS(_uM("fontSize" to "36rpx", "marginRight" to "16rpx", "marginTop" to "4rpx")), "ab-feature-info" to _pS(_uM("flexGrow" to 1, "flexShrink" to 1, "flexBasis" to "0%", "flexDirection" to "column")), "ab-feature-name" to _pS(_uM("fontSize" to "26rpx", "fontWeight" to 600, "color" to "#333333")), "ab-feature-desc" to _pS(_uM("fontSize" to "22rpx", "color" to "#999999", "marginTop" to "4rpx")), "ab-contact" to _pS(_uM("fontSize" to "24rpx", "color" to "#666666", "marginTop" to "8rpx")), "ab-footer" to _pS(_uM("alignItems" to "center", "paddingTop" to "40rpx", "paddingRight" to 0, "paddingBottom" to "40rpx", "paddingLeft" to 0)), "ab-footer-text" to _pS(_uM("fontSize" to "24rpx", "color" to "#FF8C42", "fontWeight" to 600)), "ab-footer-copy" to _pS(_uM("fontSize" to "20rpx", "color" to "#CCCCCC", "marginTop" to "8rpx")))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
