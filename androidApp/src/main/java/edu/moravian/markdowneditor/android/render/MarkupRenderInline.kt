@file:Suppress("PrivatePropertyName")

package edu.moravian.markdowneditor.android.render

//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.text.InlineTextContent
//import androidx.compose.foundation.text.appendInlineContent
//import androidx.compose.material.Divider
//import androidx.compose.material.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.text.AnnotatedString
//import androidx.compose.ui.text.ParagraphStyle
//import androidx.compose.ui.text.Placeholder
//import androidx.compose.ui.text.PlaceholderVerticalAlign
//import androidx.compose.ui.text.PlatformTextStyle
//import androidx.compose.ui.text.SpanStyle
//import androidx.compose.ui.text.buildAnnotatedString
//import androidx.compose.ui.text.font.FontStyle
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.Hyphens
//import androidx.compose.ui.text.style.LineBreak
//import androidx.compose.ui.text.style.LineHeightStyle
//import androidx.compose.ui.text.style.TextIndent
//import androidx.compose.ui.unit.TextUnit
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.em
//import androidx.compose.ui.unit.sp
//import org.intellij.markdown.IElementType
//import org.intellij.markdown.MarkdownElementType
//import org.intellij.markdown.MarkdownElementTypes
//import org.intellij.markdown.MarkdownTokenTypes
//import org.intellij.markdown.ast.ASTNode
//import org.intellij.markdown.ast.acceptChildren
//import org.intellij.markdown.ast.getTextInNode
//import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
//import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
//import org.intellij.markdown.html.HtmlGenerator
//import org.intellij.markdown.parser.MarkdownParser
//import kotlin.random.Random
//
//// Paragraph Styles (these are based on HTML)
//private val ATX_1 = ParagraphInfo(ParagraphStyle(lineBreak = LineBreak.Heading), SpanStyle(fontSize = 2.00.em, fontWeight = FontWeight.Bold), 0.67.em)
//private val ATX_2 = ParagraphInfo(ParagraphStyle(lineBreak = LineBreak.Heading), SpanStyle(fontSize = 1.50.em, fontWeight = FontWeight.Bold), 0.83.em)
//private val ATX_3 = ParagraphInfo(ParagraphStyle(lineBreak = LineBreak.Heading), SpanStyle(fontSize = 1.17.em, fontWeight = FontWeight.Bold), 1.00.em)
//private val ATX_4 = ParagraphInfo(ParagraphStyle(lineBreak = LineBreak.Heading), SpanStyle(fontSize = 1.00.em, fontWeight = FontWeight.Bold), 1.33.em)
//private val ATX_5 = ParagraphInfo(ParagraphStyle(lineBreak = LineBreak.Heading), SpanStyle(fontSize = 0.83.em, fontWeight = FontWeight.Bold), 1.67.em)
//private val ATX_6 = ParagraphInfo(ParagraphStyle(lineBreak = LineBreak.Heading), SpanStyle(fontSize = 0.67.em, fontWeight = FontWeight.Bold), 2.33.em)
//private val SETEXT_1 = ATX_1
//private val SETEXT_2 = ATX_2
//private val PARAGRAPH = ParagraphInfo()
//private val BLOCK_QUOTE = ParagraphInfo(
//    ParagraphStyle(textIndent = TextIndent(27.sp, 27.sp), lineBreak = LineBreak.Paragraph), // TODO: 40px == 27.sp?,
//    SpanStyle(background = Color.LightGray), // TODO: background color fills in indent as well?
//)
//
//private val INLINE_BLOCK = ParagraphStyle(lineBreak = LineBreak.Simple, hyphens = Hyphens.None, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
//
//// Span Styles
//private val EMPH = SpanStyle(fontStyle = FontStyle.Italic)
//private val STRONG = SpanStyle(fontWeight = FontWeight.Bold)
//
//private data class ParagraphInfo(
//    val style: ParagraphStyle = ParagraphStyle(lineBreak = LineBreak.Paragraph),
//    val span: SpanStyle? = null,
//    val top: TextUnit = 1.00.em,
//    val bottom: TextUnit = top,
//)
//
//private val characters : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
//private fun randomId(n: Int = 8): String =
//    (1..n).map { characters.random() }.joinToString("")
//
//
//@Composable
//fun MarkdownText(
//    text: String,
//    modifier: Modifier = Modifier,
//    parser: MarkdownParser = MarkdownParser(CommonMarkFlavourDescriptor()),
//) {
//    val inlineContent = mutableMapOf<String, InlineTextContent>()
//    Text(
//        text = buildAnnotatedString {
//            markdown(text, inlineContent, parser)
//        },
//        modifier = modifier,
//        inlineContent = inlineContent,
//    )
//}
//
//fun AnnotatedString.Builder.markdown(
//    text: String,
//    inlineContent: MutableMap<String, InlineTextContent>,
//    parser: MarkdownParser = MarkdownParser(CommonMarkFlavourDescriptor()),
//) {
//    val parsedTree = parser.buildMarkdownTreeFromString(text)
//    processNode(parsedTree, State(text, inlineContent))
//}
//
//private data class State(
//    val text: String,
//    val inlineContent: MutableMap<String, InlineTextContent>,
//    val indent: String = "",
//    val blockQuoteDepth: Int = 0,
//    val listDepth: Int = 0,
//)
//
//
//private val ASTNode.isFirstOfParent get() = parent?.children?.get(0) == this
//
//private fun AnnotatedString.Builder.processNode(node: ASTNode, state: State) {
//    if (node.type !in listOf(MarkdownElementTypes.MARKDOWN_FILE, MarkdownTokenTypes.EOL)) {
//        println("${state.indent}${node.type} ${node.startOffset} ${node.endOffset} '${state.text.substring(node.startOffset, node.endOffset)}'")
//    }
//
//    // simple tokens (isToken == true and node.type.name.length == 1) should be preserved when the
//    // parent is a paragraph (MarkdownElementTypes.PARAGRAPH) but in many other cases as well...
//
//    when (node.type) {
//        // Delimiting whitespace is ignored
//        MarkdownTokenTypes.EOL -> assert(node.children.isEmpty())
//
//        // Append raw text
//        MarkdownTokenTypes.TEXT -> {
//            appendRange(state.text, node.startOffset, node.endOffset)
//            assert(node.children.isEmpty())
//        }
//        MarkdownTokenTypes.WHITE_SPACE -> {
//            // Remove whitespace that is not at the beginning of a paragraph or header
//            if (node.parent?.type != MarkdownTokenTypes.ATX_CONTENT &&
//                node.parent?.type != MarkdownTokenTypes.BLOCK_QUOTE && // has the extra > as white-space
//                !(node.parent?.type == MarkdownElementTypes.PARAGRAPH && node.isFirstOfParent)) {
//                appendRange(state.text, node.startOffset, node.endOffset)
//            }
//            assert(node.children.isEmpty())
//        }
//        MarkdownTokenTypes.HARD_LINE_BREAK -> {
//            append("\n")
//            assert(node.children.isEmpty())
//        }
//
//        // Horizontal Rule
//        MarkdownTokenTypes.HORIZONTAL_RULE -> {
//            horizontalRule(state)
//            assert(node.children.isEmpty())
//        }
//
//        // Paragraph Styles
//        MarkdownElementTypes.MARKDOWN_FILE -> processChildren(node, state)
//        MarkdownElementTypes.PARAGRAPH -> {
//            // PARAGRAPH is nested inside BLOCK_QUOTE and LIST_ITEM but shouldn't start actual new paragraphs
//            if (state.blockQuoteDepth > 0 || state.listDepth > 0) {
//                processChildren(node, state)
//            } else {
//                paragraph(PARAGRAPH, node, state)
//            }
//        }
//        MarkdownElementTypes.BLOCK_QUOTE -> {
//            val depth = state.blockQuoteDepth
//            if (depth == 0) {
//                paragraph(BLOCK_QUOTE, node, state.copy(blockQuoteDepth = depth + 1))
//            } else {
//                // TODO
//            }
//        }
//        MarkdownElementTypes.ATX_1 -> paragraph(ATX_1, node, state)
//        MarkdownElementTypes.ATX_2 -> paragraph(ATX_2, node, state)
//        MarkdownElementTypes.ATX_3 -> paragraph(ATX_3, node, state)
//        MarkdownElementTypes.ATX_4 -> paragraph(ATX_4, node, state)
//        MarkdownElementTypes.ATX_5 -> paragraph(ATX_5, node, state)
//        MarkdownElementTypes.ATX_6 -> paragraph(ATX_6, node, state)
//        MarkdownElementTypes.SETEXT_1 -> paragraph(SETEXT_1, node, state)
//        MarkdownElementTypes.SETEXT_2 -> paragraph(SETEXT_2, node, state)
//
//        // Span Styles
//        MarkdownElementTypes.EMPH -> span(EMPH, node, state)
//        MarkdownElementTypes.STRONG -> span(STRONG, node, state)
//
//        // Tokens the are ignored based on their parent element
//        MarkdownTokenTypes.EMPH -> {
//            if (node.parent?.type !in listOf(MarkdownElementTypes.EMPH, MarkdownElementTypes.STRONG)) {
//                appendRange(state.text, node.startOffset, node.endOffset)
//            }
//            assert(node.children.isEmpty())
//        }
//
//        // Always ignored tokens (they are handled as their parent elements instead)
//        MarkdownTokenTypes.ATX_HEADER, MarkdownTokenTypes.SETEXT_1, MarkdownTokenTypes.SETEXT_2,
//        MarkdownTokenTypes.BLOCK_QUOTE -> assert(node.children.isEmpty())
//
//        // Always ignored tokens (but have children that need processing)
//        MarkdownTokenTypes.ATX_CONTENT, MarkdownTokenTypes.SETEXT_CONTENT -> processChildren(node, state)
//
//        else -> {
//            println("${state.indent} ** Ignoring ${node.type} ${if ((node.type as? MarkdownElementType)?.isToken == true) "<token>" else ""}")
//            processChildren(node, state)
//        }
//    }
//}
//
//private fun AnnotatedString.Builder.processChildren(node: ASTNode, state: State) {
//    val newState = state.copy(indent = state.indent + "  ")
//    for (child in node.children) { processNode(child, newState) }
//}
//
//private fun AnnotatedString.Builder.span(style: SpanStyle, node: ASTNode, state: State) {
//    val id = pushStyle(style)
//    processChildren(node, state)
//    pop(id)
//}
//
//private fun AnnotatedString.Builder.paragraph(info: ParagraphInfo, node: ASTNode, state: State) {
//    spacer(info.top, state)
//    val id = pushStyle(info.style)
//    if (info.span !== null) pushStyle(info.span)
//    processChildren(node, state)
//    pop(id)
//    spacer(info.top, state)
//}
//
//private fun AnnotatedString.Builder.spacer(height: TextUnit, state: State) {
//    appendInlineBlock(height, "spacer", " ", state) {
//        with(LocalDensity.current) { Spacer(modifier = Modifier.height(1.dp)) }
//    }
//}
//
//private fun AnnotatedString.Builder.horizontalRule(state: State, alt: String = "------") {
//    appendInlineBlock(1.em, "hr", alt, state) { Divider(Modifier.fillMaxWidth()) }
//}
//
//private fun AnnotatedString.Builder.appendInlineBlock(height: TextUnit, name: String, alt: String, state: State, content: @Composable (String) -> Unit) {
//    pushStyle(INLINE_BLOCK.copy(lineHeight = height))
//    if (height.value != 0f) {
//        var id = "${name}_${randomId()}"
//        while (id in state.inlineContent) { id = "${name}_${randomId()}" }
//        state.inlineContent[id] = InlineTextContent(
//            // TODO: use content width instead of 10.em
//            Placeholder(10.em, height, PlaceholderVerticalAlign.Center),
//            content
//        )
//        appendInlineContent(id, alt)
//    }
//    pop()
//}
