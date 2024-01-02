package edu.moravian.markdowneditor.android.render

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import edu.moravian.markdowneditor.android.markup.MarkupNode
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalFoundationApi::class)
data class MarkupParams(
    val coroutineScope: CoroutineScope,
    val baseTextStyle: TextStyle,
    val density: Density,
    val screenSize: DpSize,
    val colors: ColorScheme,

    internal val viewRequestersRaw: MutableMap<MarkupNode, BringIntoViewRequester> = mutableMapOf(),
    val viewRequesters: MutableMap<String, ViewRequester>,
    val onURLClick: (url: String) -> Unit,

    internal val blockStyles: Map<String, MarkupBlockScope.() -> TextStyle>,
    internal val blockMargins: Map<String, MarkupBlockScope.() -> MarginValues>,
    internal val blockModifiers: Map<String, MarkupBlockScope.() -> Modifier>,
    internal val blockRenderers: Map<String, MarkupBlockRenderer>,
    internal val inlineStyles: Map<String, MarkupInlineScope.() -> SpanStyle>,
    internal val inlineRenderers: Map<String, MarkupInlineScope.() -> Unit>,

    internal val blockStyleCache: CacheValid<TextStyle, TextStyle> = mutableMapOf(),
    internal val blockMarginsRawCache: CacheValid<TextUnit, PaddingValues> = mutableMapOf(),
    internal val blockMarginsCache: CacheValid<TextUnit, PaddingValues> = mutableMapOf(),
    internal val blockModifierCache: Cache<Modifier> = mutableMapOf(),
)

@OptIn(ExperimentalFoundationApi::class)
typealias ViewRequester = Pair<BringIntoViewRequester, (() -> Rect?)?>
typealias MarkupBlockRenderer = @Composable MarkupBlockScope.(Modifier) -> Unit

private typealias Cache<T> = MutableMap<MarkupNode, T>
private typealias CacheValid<K, T> = MutableMap<MarkupNode, Pair<K, T>>

sealed class MarkupScope {
    abstract val params: MarkupParams
    abstract val node: MarkupNode

    // Simple forwarding properties to params
    inline val baseTextStyle get() = params.baseTextStyle
    inline val density get() = params.density
    inline val screenSize get() = params.screenSize
    inline val colors get() = params.colors

    // Simple forwarding properties to node
    inline val tag get() = node.tag
    inline val children get() = node.children
    inline val attributes get() = node.attributes
}

@OptIn(ExperimentalFoundationApi::class)
data class MarkupBlockScope internal constructor(
    override val params: MarkupParams,
    override val node: MarkupNode,
): MarkupScope() {
    val viewRequester get() = params.viewRequestersRaw.getValue(node)

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun MarkupNode.scoped() = this@MarkupBlockScope.copy(node=this)

    /**
     * Get the block text style for a node. This style has em resolved and the
     * parent text style and CSS style attribute merged in.
     */
    val style: TextStyle get() =
        if (node.parent === null) { params.baseTextStyle }
        else {
            val parentStyle = node.parent.scoped().style
            params.blockStyleCache.getOrPut(node, parentStyle) {
                val raw = params.blockStyles[node.tag]?.invoke(this) ?: TextStyle.Default
                // TODO: merge node.attributes["style"] into raw
                parentStyle.mergeWithEm(raw)
            }
        }

    /**
     * Get the block margins for a node. This has text units resolved to dp but
     * does not have collapsed margins processed.
     */
    internal val marginsRaw: PaddingValues get() {
        val fontSize = style.fontSize
        return params.blockMarginsRawCache.getOrPut(node, fontSize) {
            val raw = params.blockMargins[node.tag]?.invoke(this) ?: MarginValues.Zero
            // TODO: merge node.attributes["style"]["margin*"] into raw
            raw.asPadding(params.density, fontSize)
        }
    }

    /**
     * Get the block margins for a node. This has text units resolved to dp,
     * includes CSS style information, and has dealt with collapsed margins.
     * These are returned as padding values that can be applied to a modifier.
     */
    val margins get() = params.blockMarginsCache.getOrPut(node, style.fontSize) { collapseMargins() }

    /** Get the modifier for a block node. This will not include the margins. */
    val modifierBase get() = params.blockModifierCache.getOrPut(node) {
        val raw = params.blockModifiers[node.tag]?.invoke(this) ?: Modifier
        // TODO: merge node.attributes["style"] into raw
        raw
    }

    /** Get the renderer for a block node */
    val renderer get() = params.blockRenderers[node.tag]

    /** Convert a TextUnit (either sp or em) to dp. */
    fun TextUnit.toDp() =
        if (isSp) { with (params.density) { this@toDp.toDp() } }
        else if (!isEm) { Dp.Unspecified }
        else {
            val fs = style.fontSize
            val sp = if (fs.isSp) fs else if (fs.isUnspecified) 16.sp else { 16.sp * fs.value }
            with (params.density) { (sp * value).toDp() }
        }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun MarkupBlockScope(params: MarkupParams, node: MarkupNode, viewRequester: BringIntoViewRequester): MarkupBlockScope {
    params.viewRequestersRaw[node] = viewRequester
    return MarkupBlockScope(params, node)
}


/**
 * Like regular getOrPut() but has a second "key" to check if the value is still
 * valid. This second key is the first item in the pair.
 */
private inline fun <K: Any, K2: Any, V: Any> MutableMap<K, Pair<K2, V>>.getOrPut(key: K, key2: K2, defaultValue: () -> V): V {
    val value = get(key)
    return if (value == null || value.first != key2) {
        val answer = defaultValue()
        put(key, key2 to answer)
        answer
    } else {
        value.second
    }
}
