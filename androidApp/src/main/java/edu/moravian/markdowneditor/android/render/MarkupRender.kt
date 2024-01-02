@file:Suppress("PrivatePropertyName")

package edu.moravian.markdowneditor.android.render

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import edu.moravian.markdowneditor.android.css.*
import edu.moravian.markdowneditor.android.markup.MarkupNode
import edu.moravian.markdowneditor.android.markup.attrAsBoolean
import edu.moravian.markdowneditor.android.markup.attrAsInt
import edu.moravian.markdowneditor.android.markup.innerText
import edu.moravian.markdowneditor.android.markup.parents
import edu.moravian.markdowneditor.android.markup.rendered
import kotlinx.coroutines.launch
import kotlin.math.min

//    h1: TextStyle = MaterialTheme.typography.displayLarge,
//    h2: TextStyle = MaterialTheme.typography.displayMedium,
//    h3: TextStyle = MaterialTheme.typography.displaySmall,
//    h4: TextStyle = MaterialTheme.typography.headlineMedium,
//    h5: TextStyle = MaterialTheme.typography.headlineSmall,
//    h6: TextStyle = MaterialTheme.typography.titleLarge,
//    p: TextStyle = MaterialTheme.typography.bodyMedium,

interface BlockRenderer {
    fun MarkupBlockScope.textStyle() = TextStyle.Default
    fun MarkupBlockScope.margins() = MarginValues.Zero
    fun MarkupBlockScope.modifier() = Modifier
    @Composable
    fun MarkupBlockScope.Render(modifier: Modifier = Modifier) { }
}

val DEFAULT_BLOCK_STYLES = mapOf<String, MarkupBlockScope.() -> TextStyle>(
    "body" to { TextStyle(lineBreak = LineBreak.Paragraph) },
    //"p" to { TextStyle() },
    "h1" to { TextStyle(fontSize = 2.00.em, lineBreak = LineBreak.Heading, fontWeight = FontWeight.Bold) },
    "h2" to { TextStyle(fontSize = 1.50.em, lineBreak = LineBreak.Heading, fontWeight = FontWeight.Bold) },
    "h3" to { TextStyle(fontSize = 1.17.em, lineBreak = LineBreak.Heading, fontWeight = FontWeight.Bold) },
    "h4" to { TextStyle(fontSize = 1.00.em, lineBreak = LineBreak.Heading, fontWeight = FontWeight.Bold) },
    "h5" to { TextStyle(fontSize = 0.83.em, lineBreak = LineBreak.Heading, fontWeight = FontWeight.Bold) },
    "h6" to { TextStyle(fontSize = 0.67.em, lineBreak = LineBreak.Heading, fontWeight = FontWeight.Bold) },
    "blockquote" to { TextStyle(color = colors.onSurfaceVariant) },
    //"ul" to { TextStyle() },
    //"ol" to { TextStyle() },
    //"li" to { TextStyle() },
    "pre" to { TextStyle(fontFamily = FontFamily.Monospace, lineBreak = LineBreak.Simple, hyphens = Hyphens.None) },
    //"table" to { TextStyle() },
    //"thead" to { TextStyle() },
    //"tr" to { TextStyle() },
    "th" to { TextStyle(color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold,
        textAlign = getTableCellTextAlign(node, TextAlign.Center)
    ) },
    "td" to { TextStyle(textAlign = getTableCellTextAlign(node)) },
)

val DEFAULT_BLOCK_MARGINS = mapOf<String, MarkupBlockScope.() -> MarginValues>(
    "p" to { MarginValues(vertical = 1.00.em) },
    "h1" to { MarginValues(vertical = 0.67.em) },
    "h2" to { MarginValues(vertical = 0.83.em) },
    "h3" to { MarginValues(vertical = 1.00.em) },
    "h4" to { MarginValues(vertical = 1.33.em) },
    "h5" to { MarginValues(vertical = 1.67.em) },
    "h6" to { MarginValues(vertical = 2.33.em) },
    "blockquote" to { MarginValues(vertical = 1.00.em) },
    "hr" to { MarginValues(vertical = 0.50.em) },
    "ul" to { MarginValues(vertical = 1.00.em) },
    "ol" to { MarginValues(vertical = 1.00.em) },
    //"li" to { MarginValues(vertical = 0.em) },
    "pre" to { MarginValues(vertical = 1.00.em) },
    //"table" to { MarginValues(vertical = 0.em) },
    //"thead" to { MarginValues(vertical = 0.em) },
    //"tr" to { MarginValues(vertical = 0.em) },
    //"th" to { MarginValues(vertical = 0.em) },
    //"td" to { MarginValues(vertical = 0.em) },
)

val DEFAULT_BLOCK_MODIFIERS = mapOf<String, MarkupBlockScope.() -> Modifier>(
    "blockquote" to {
        val em = 1.em.toDp()
        Modifier
            .border(1.dp, colors.outline, RoundedCornerShape(em))
            .background(colors.surfaceVariant, RoundedCornerShape(em))
            .padding(em)
    },
    "hr" to { Modifier.doNotCollapse() }, // make sure the margin isn't collapsed even though this looks like an empty element
    "thead" to { Modifier.background(colors.surfaceVariant) },
    "th" to {
        Modifier
            .tableCell(node)
            .background(colors.surfaceVariant)
            .border(Dp.Hairline, colors.outline)
            .padding(vertical = 2.dp, horizontal = 4.dp)
    },
    "td" to {
        Modifier
            .tableCell(node)
            .border(Dp.Hairline, colors.outline)
            .padding(vertical = 2.dp, horizontal = 4.dp)
    },
)

val DEFAULT_BLOCK_RENDERERS = mapOf<String, MarkupBlockRenderer>(
    "hr" to { modifier -> HorizontalDivider(modifier) },
    "pre" to { modifier ->
        Box(modifier.horizontalScroll(rememberScrollState())) {
            if (node.text !== null) {
                Text(node.text, softWrap = false)
            } else {
                Block()
            }
        }
    },
    "li" to { modifier ->
        val parent = node.parent
        when (val tag = parent?.tag) {
            "ol", "ul" -> {
                val marker = if (tag == "ol")
                    "${(parent.attrAsInt("start") ?: 1) + node.index}."
                else
                    bullets[min(parent.parents.count { it.tag in listTags }, bullets.lastIndex)]
                val first = node.children.firstOrNull()
                val checkbox = first?.tag == "input" && first.attributes["type"] == "checkbox"
                Row(modifier, verticalAlignment = Alignment.Top) {
                    if (checkbox) {
                        val style = LocalTextStyle.current
                        val measurer = rememberTextMeasurer()
                        val heightPx = remember(style, measurer) { measurer.measure("X", style).size.height }
                        val height = with(LocalDensity.current) { heightPx.toDp() }
                        val pad = (48.dp - height) / 2
                        Checkbox(
                            checked = first!!.attrAsBoolean("checked"),
                            onCheckedChange = {},
                            enabled = false,
                            modifier = Modifier.padding(end = 8.sp.toDp())
                        )
                        Block(modifier = Modifier.padding(top = pad), skip = 1)
                    } else {
                        Text(
                            marker,
                            modifier = Modifier
                                .width(48.sp.toDp())
                                .padding(end = 8.sp.toDp()),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                        )
                        BlockOrText()
                    }
                }
            }
            else -> BlockOrText()
        }
    },
    "table" to { modifier -> Table(modifier = modifier) },
    "tr" to { modifier -> TableRow(modifier = modifier) },
    "th" to { modifier -> TableCell(modifier = modifier, defaultAlignment = Alignment.CenterHorizontally) },
    "td" to { modifier -> TableCell(modifier = modifier) },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarkupText(
    markupTree: MarkupNode,
    modifier: Modifier = Modifier,
    baseTextStyle: TextStyle = LocalTextStyle.current,
    colors: ColorScheme = MaterialTheme.colorScheme,
    blockStyles: Map<String, MarkupBlockScope.() -> TextStyle>? = null,
    blockMargins: Map<String, MarkupBlockScope.() -> MarginValues>? = null,
    blockModifiers: Map<String, @Composable MarkupBlockScope.() -> Modifier>? = null,
    blockRenderers: Map<String, MarkupBlockRenderer>? = null,
    inlineStyles: Map<String, MarkupInlineScope.() -> SpanStyle>? = null,
    inlineRenderers: Map<String, MarkupInlineScope.() -> Unit>? = null,
    onURLClick: (url: String) -> Unit = {},
) {
    println(markupTree) //.toHTML()
    val coroutineScope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    val newBlockStyles = remember(blockStyles) { DEFAULT_BLOCK_STYLES + blockStyles }
    val newBlockMargins = remember(blockMargins) { DEFAULT_BLOCK_MARGINS + blockMargins }
    val newBlockModifiers = remember(blockModifiers) { DEFAULT_BLOCK_MODIFIERS + blockModifiers }
    val newBlockRenderers = remember(blockRenderers) { DEFAULT_BLOCK_RENDERERS + blockRenderers }
    val newInlineStyles = remember(inlineStyles) { DEFAULT_INLINE_STYLES + inlineStyles }
    val newInlineRenderers = remember(blockRenderers) { DEFAULT_INLINE_RENDERERS + inlineRenderers }

    val params = remember(
        markupTree, onURLClick,
        newBlockStyles, newBlockModifiers, newBlockRenderers,
        newInlineStyles, newInlineRenderers,
    ) {
        val viewRequesters = mutableMapOf<String, ViewRequester>()
        MarkupParams(
            coroutineScope = coroutineScope,
            baseTextStyle = baseTextStyle,
            density = density,
            screenSize = DpSize(config.screenWidthDp.dp, config.screenHeightDp.dp),
            colors = colors,

            blockStyles = newBlockStyles,
            blockMargins = newBlockMargins,
            blockModifiers = newBlockModifiers,
            blockRenderers = newBlockRenderers,
            inlineStyles = newInlineStyles,
            inlineRenderers = newInlineRenderers,

            viewRequesters = viewRequesters,
            onURLClick = {
                if (it.startsWith("#")) {
                    viewRequesters[it.removePrefix("#")]?.let { (requester, getRect) ->
                        coroutineScope.launch { requester.bringIntoView(getRect?.invoke()) }
                    }
                } else {
                    onURLClick(it)
                }
            },
        )
    }

    RenderNode(node = markupTree, params = params, modifier = modifier)
}

private operator fun <T: Any> Map<String, T>.plus(values: Map<String, T>?): Map<String, T> =
    values?.let { this + it } ?: this

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RenderNode(
    node: MarkupNode,
    params: MarkupParams,
    modifier: Modifier = Modifier,
) {
    if (!node.rendered) return

    val text = remember(node) { node.innerText }

    // Auto-generate anchors for headers
    val viewRequester = remember(node) { BringIntoViewRequester() }
    val scope = remember(node, params) { MarkupBlockScope(params, node, viewRequester) }
    if (node.tag in headers && text.isNotEmpty()) {
        LaunchedEffect(node) {
            val id = makeId(text)
            params.viewRequesters[id] = viewRequester to null
            params.viewRequesters["user-content-$id"] = viewRequester to null // github style
            params.viewRequesters["markdown-header-$id"] = viewRequester to null // bitbucket style
        }
    }

    ProvideTextStyle(scope.style) {
        val margins = remember(node, params) { scope.margins }
        val mod = remember(node, params) {
            Modifier
                //.fillMaxWidth()
                .padding(margins)
                .bringIntoViewRequester(viewRequester)
                .then(modifier)
                .then(scope.modifierBase)
        }
        val renderer = remember(node, params) { scope.renderer }
        if (renderer !== null) {
            scope.renderer(mod)
            // TODO
//        } else if (node.text !== null) {
//            scope.Text(mod)
        } else if (node.children.isNotEmpty()) {
            scope.Block(mod)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarkupBlockScope.Text(
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    TitleTooltipBox(modifier) { showTooltip ->
        val style = LocalTextStyle.current.toSpanStyle() // TODO: use in the scope
        val inlineContent = remember { mutableStateMapOf<String, InlineTextContent>() }
        val txt = remember { buildAnnotatedString {
            MarkupInlineScope(params, node, this, inlineContent).renderNode()
        } }
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        ClickableText(
            txt,
            inlineContent = inlineContent,
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            minLines = minLines,
            onLongPress = { offset ->
                txt.annotationAt("title", offset)?.let {
                    showTooltip(it.item, layoutResult?.getBoundingBox(it))
                }
            },
            onClick = { offset ->
                txt.urlAnnotationAt(offset)?.let { params.onURLClick(it) }
            },
            onTextLayout = { layout ->
                layoutResult = layout
                txt.annotations("id").forEach {
                    params.viewRequesters[it.item] =
                        viewRequester to { layout.getBoundingBox(it) }
                }
                onTextLayout(layout)
            },
        )
    }
}

@Composable
fun MarkupBlockScope.Block(modifier: Modifier = Modifier, skip: Int = 0) {
    val children = node.children
    if (children.size <= skip) return
    Column(modifier = modifier) {
        children.subList(skip, children.size).forEach {
                child -> RenderNode(node = child, params = params)
        }
    }
}

@Composable
fun MarkupBlockScope.BlockOrText(modifier: Modifier = Modifier) {
    /* TODO: if (node.text !== null) {
        Text(modifier)
    } else*/ if (node.children.isNotEmpty()) {
        Block(modifier)
    }
}

private val headers = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private fun makeId(text: String) =
    text.lowercase().replace(Regex("\\W+"), "-").trim('-')

private val listTags = setOf("ol", "ul", "menu", "dir")
private val bullets = listOf("•", "◦", "▪")
