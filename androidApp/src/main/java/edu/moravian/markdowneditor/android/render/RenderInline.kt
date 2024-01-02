package edu.moravian.markdowneditor.android.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.em
import co.touchlab.kermit.Logger
import edu.moravian.markdowneditor.android.css.parseBackgroundColor
import edu.moravian.markdowneditor.android.css.parseColor
import edu.moravian.markdowneditor.android.css.parseFontStretch
import edu.moravian.markdowneditor.android.css.parseFontStyle
import edu.moravian.markdowneditor.android.css.parseFontSynthesis
import edu.moravian.markdowneditor.android.css.parseFontWeight
import edu.moravian.markdowneditor.android.css.parseTextDecoration
import edu.moravian.markdowneditor.android.markup.MarkupNode
import edu.moravian.markdowneditor.android.markup.MarkupText
import edu.moravian.markdowneditor.android.markup.rendered
import edu.moravian.markdowneditor.android.markup.style
import edu.moravian.markdowneditor.android.utilities.randomId


data class MarkupInlineScope internal constructor(
    override val params: MarkupParams,
    override val node: MarkupNode,
    val builder: AnnotatedString.Builder,
    val inlineContent: MutableMap<String, InlineTextContent>,
): MarkupScope() {
    @Suppress("NOTHING_TO_INLINE")
    private inline fun MarkupNode.scoped() = this@MarkupInlineScope.copy(node=this)

    fun renderContents() { children.forEach { it.scoped().renderNode() } }

    fun renderNode() { builder.renderNode() }
    fun AnnotatedString.Builder.renderNode() {
        if (node is MarkupText) { append(node.data) }
        else if (node.tag == "br") { append("\n") }
        else if (node.rendered) {
            withAnnotation("title", node["title"] as? String?) {
                withAnnotation("id", node["id"] as? String?) {
                    // TODO: keep track of cascading styles for relative sizes and font-weights
                    withStyle(applyCSS(params.inlineStyles[node.tag]?.invoke(), node.style)) {
                        (params.inlineRenderers[node.tag] ?: ::renderContents).invoke()
                    }
                }
            }
        }
    }
}

private const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

val DEFAULT_INLINE_STYLES = mapOf<String, MarkupInlineScope.() -> SpanStyle>(
    // Markdown elements
    "em" to { SpanStyle(fontStyle = FontStyle.Italic) },
    "strong" to { SpanStyle(fontWeight = FontWeight.Bold) },
    "code" to { SpanStyle(fontFamily = FontFamily.Monospace) },  // TODO: use attributes["*lang"] to perform syntax highlighting
    "a" to { SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline) },
    //"img" to { SpanStyle() },
    //"br" to { SpanStyle() },

    // Non-Semantic HTML Elements
    "b" to { SpanStyle(fontWeight = FontWeight.Bold) },
    "i" to { SpanStyle(fontStyle = FontStyle.Italic) },
    "s" to { SpanStyle(textDecoration = TextDecoration.LineThrough) },
    "u" to { SpanStyle(textDecoration = TextDecoration.Underline) },
    "small" to { SpanStyle(fontSize = (1/1.2).em) },
    "big" to { SpanStyle(fontSize = 1.2.em) },
    "sub" to { SpanStyle(fontSize = (1/1.2).em, baselineShift = BaselineShift.Subscript) },
    "sup" to { SpanStyle(fontSize = (1/1.2).em, baselineShift = BaselineShift.Superscript) },
    //"span" to { SpanStyle() },

    // Semantic HTML Elements
    "del" to { SpanStyle(textDecoration = TextDecoration.LineThrough) },
    "ins" to { SpanStyle(textDecoration = TextDecoration.Underline) },
    "mark" to { SpanStyle(background = Color.Yellow) },
    //"abbr" to { SpanStyle() },
    "cite" to { SpanStyle(fontStyle = FontStyle.Italic) },
    "dfn" to { SpanStyle(fontStyle = FontStyle.Italic) },
    "kbd" to { SpanStyle(fontFamily = FontFamily.Monospace) },
    "samp" to { SpanStyle(fontFamily = FontFamily.Monospace) },
    "var" to { SpanStyle(fontStyle = FontStyle.Italic) },
)

@OptIn(ExperimentalTextApi::class)
val DEFAULT_INLINE_RENDERERS = mapOf<String, MarkupInlineScope.() -> Unit>(
    "a" to {
        node["href"]?.also { url ->
            builder.withAnnotation(UrlAnnotation(url.toString())) { renderContents() }
        } ?: renderContents()
    },
    "img" to {
        // title and id are handled automatically
        val src = node["src"] ?: return@to
        val widthAttr = node["width"]?.toIntOrNull()?.toFloat()
        val heightAttr = node["height"]?.toIntOrNull()?.toFloat()

        val painter = rememberImagePainter(src)
        val imgSize = painter.intrinsicSize.takeOrElse { Size(50f, 50f) }
        val size = Size(widthAttr ?: imgSize.width, heightAttr ?: imgSize.height)

        // TODO: we could look at the style attribute for width and height as well though...
        //  along with vertical alignment

        println("$size")

        val dpSize = with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
        val widthSp = with(LocalDensity.current) { dpSize.width.toSp() }
        val heightSp = with(LocalDensity.current) { dpSize.height.toSp() }

        val id = remember { "img-$src-${randomId()}" }
        // alt text is covered by the inline content annotation
        addStringAnnotation(INLINE_CONTENT_TAG, id, annotation.start, annotation.end)
        inlineContent += id to InlineTextContent(
            placeholder = Placeholder(
                width = widthSp, height = heightSp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
                //"sub"/"super" -> null
                //"baseline" -> AboveBaseline,  (default)
                //"top" -> Top
                //"middle" -> Center
                //"bottom" -> Bottom
                //"text-top" -> TextTop
                //"text-bottom" -> TextBottom
            )
        ) {
            Image(
                painter = painter,
                contentDescription = it,
                modifier = Modifier.size(dpSize)
            )
        }
    },
)

private fun applyCSS(style: SpanStyle?, css: Map<String, String>) =
    applyCSS(style ?: SpanStyle(), css)

private fun applyCSS(style: SpanStyle, css: Map<String, String>): SpanStyle {
    return css.entries.fold(style) { s, (name, value) ->
        when (name) {
            //"font" -> combines: font-family, font-size, font-stretch, font-style, *font-variant, font-weight, *line-height
            //"font-family" -> s.copy(fontFamily = ... || null)
            //"font-size" -> s.copy(fontSize = ... || TextUnit.Unspecified)
            //  xx-small (=0.5625*medium), x-small (=0.625*medium), small (=0.8125*medium)
            //  medium (=baseTextStyle.fontSize)
            //  large = (=1.125*medium), x-large (=1.5*medium), xx-large (=2*medium), xxx-large (=3*medium)
            //  larger (=1.2.em), smaller  (=(1/1.2).em)
            //  <number><unit> with unit: %, ch, em, ex, lh, rem, rlh, vh, vw, vi, vb, vmin, vmax, cqw, cqh, cqi, cqb, cqmin, cqmax, px, pt, pc, cm, mm, in, and q
            "font-stretch" -> s.copy(textGeometricTransform = parseFontStretch(value))
            "font-style" -> s.merge(parseFontStyle(value))
            "font-weight" -> s.copy(fontWeight = parseFontWeight(value))
            "font-synthesis" -> s.copy(fontSynthesis = parseFontSynthesis(value))
            "font-feature-settings" -> s.copy(fontFeatureSettings = value) // TODO: handle inherit, initial, unset, revert, revert-layer
            "text-decoration" -> s.copy(textDecoration = parseTextDecoration(value))
            //"letter-spacing" -> s.copy(letterSpacing = || TextUnit.Unspecified) // TODO
            "color" -> s.copy(color = parseColor(value))
            "background-color" -> s.copy(background = parseBackgroundColor(value)) // TODO: shorthand property as well
            //"vertical-align" -> s.copy(baselineShift = parseVerticalAlign(value))
            //shadow: Shadow? = null
            else -> {
                Logger.w("ignoring unsupported CSS property: $name=$value")
                s
            }
        }
    }
}
