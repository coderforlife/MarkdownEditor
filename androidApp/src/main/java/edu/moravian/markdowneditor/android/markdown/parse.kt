@file:Suppress("PrivatePropertyName") @file:JvmName("MarkupRenderInlineKt")

package edu.moravian.markdowneditor.android.markdown

import co.touchlab.kermit.Logger
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import edu.moravian.markdowneditor.android.html.HTMLTagInfo
import edu.moravian.markdowneditor.android.html.isPossiblyHTMLCloseTag
import edu.moravian.markdowneditor.android.html.isPossiblyHTMLSpecialNode
import edu.moravian.markdowneditor.android.html.parseHTMLCloseTag
import edu.moravian.markdowneditor.android.html.voidTags
import edu.moravian.markdowneditor.android.html.parseHTMLSpecialNode
import edu.moravian.markdowneditor.android.html.parseHTMLTag
import edu.moravian.markdowneditor.android.markup.MarkupCharacterData
import edu.moravian.markdowneditor.android.markup.MarkupElement
import edu.moravian.markdowneditor.android.markup.MarkupNode
import edu.moravian.markdowneditor.android.markup.MarkupText
import edu.moravian.markdowneditor.android.utilities.asList
import edu.moravian.markdowneditor.android.utilities.mapOfNonNull
import edu.moravian.markdowneditor.android.utilities.removeBrackets
import edu.moravian.markdowneditor.android.utilities.removeSingleBrackets
import edu.moravian.markdowneditor.android.utilities.trimAndReportIndent
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * Create a markup tree from a markdown string.
 *
 * This fully supports CommonMark and partially supports GitHub Flavored
 * Markdown (GFM) (tables and task lists currently not supported).
 *
 * This supports inline HTML tags and attributes (from an approved list) but
 * currently does not support block-level HTML tags.
 *
 * This adds several additional nodes/tags, annotations, and attributes that
 * start with *md that should not be rendered or converted to HTML. These are
 * used for reconstructing the original markdown text only.
 */
fun markdownMarkupTree(
    text: String,
    parser: MarkdownParser = MarkdownParser(GFMFlavourDescriptor()),
): MarkupNode {
    val state = MDState(text)
    val root = state.processNode(parser.buildMarkdownTreeFromString(text))!!
    return fixNode(root, state.linkDefinitions, null)
}

// Reconstruction annotations are missing for the following:
//  * characters used to prefix a block quote (e.g. >> or > >)
//  * style of symbol used to define titles in links and <> around urls in link definitions
//  * unknown elements and filtered out HTML tags and attributes

/**
 * Fixes up the node (and its children) by removing unnecessary "p" elements,
 * adding the parents, and resolving link references.
 */
private fun fixNode(node: MarkupNode, linkDefs: Map<String, MarkupElement>, parent: MarkupNode?): MarkupNode = when (node) {
    is MarkupCharacterData -> node.copy(parent = parent)
    is MarkupElement -> {
        // Remove single <p> elements // TODO: still? or different solution to this issue?
        val collapse = parent !== null && node.children.size == 1 && node.children[0].tag == "p"
        val content = if (collapse) node.children[0] else node

        // Create a new node with the text adjusted and the parent properly set
        // This also makes sure that the children are a mutable list (though they will appear immutable)
        val children = mutableListOf<MarkupNode>()
        val new = dereference(node.copy(
            parent = parent,
            children = children.asList(),
        ), linkDefs)

        // Recurse
        content.children.collapseTextNodes().mapTo(children) { fixNode(it, linkDefs, new) }

        // Return the new node
        new
    }
}

/** Dereference a link reference by updating the tag name and attributes from the definitions. */
private fun dereference(node: MarkupElement, linkDefs: Map<String, MarkupElement>): MarkupElement {
    if (!node.tag.endsWith("-ref")) return node

    // Get the link definition
    val label = node.metadata["md-label"]
    val def = linkDefs[label as? String]
    if (def === null) {
        // Referenced links without a definition are left (almost) as-is
        // These will render as the text (either the alt text or the link text)
        Logger.w("unknown link reference '$label'")
        return node
    }

    // Change href to src for images
    val attr = if (node.tag != "img-ref") def.attributes
        else def.attributes.mapKeys { (k, _) -> if (k == "href") "src" else k }

    // Update the node
    return node.copy(
        tag = node.tag.dropLast(4),
        attributes = attr + node.attributes,
    )
}

/** Collapse adjacent text nodes into a single node. */
private fun List<MarkupNode>.collapseTextNodes(): List<MarkupNode> {
    if (size < 2) return this
    val output = mutableListOf<MarkupNode>()
    var current = this[0]
    for (next in subList(1, size)) {
        // merge two text nodes or two cdata nodes, but do not mix them
        current = if (current is MarkupText && next is MarkupText && current::class == next::class) {
            MarkupText(
                current.data + next.data,
                current.metadata + next.metadata,  // TODO: or don't merge nodes with differing metadata? depends on the data in them...
                current.parent
            )
        } else {
            output.add(current)
            next
        }
    }
    output.add(current)
    return if (output.size == size) this else output
}

/**
 * Internal state when recursively processing a markdown AST tree.
 */
private data class MDState(
    val text: String,
    val linkDefinitions: MutableMap<String, MarkupElement> = mutableMapOf(), // label -> element w/ attributes and metadata
    val tableAlignments: MutableList<String?> = mutableListOf(), // alignment for each column
) {
    val ASTNode.text: String get() = this@MDState.text.substring(startOffset, endOffset)
    val ASTNode.metadata get() = mapOf("md-content" to text, "md-start-off" to startOffset, "md-end-off" to endOffset)
}

private fun MDState.processNode(node: ASTNode): MarkupNode? {
    //if (node.type !in listOf(MarkdownTokenTypes.EOL, MarkdownElementTypes.MARKDOWN_FILE)) {
    //    println("${node.type} ${node.startOffset} ${node.endOffset} '${node.text}'")
    //}

    return when (node.type) {
        // Markdown file is the root element
        MarkdownElementTypes.MARKDOWN_FILE -> block("body", node)

        // Whitespace not in a paragraph/content is ignored (preserve EOL for reconstructing original though)
        MarkdownTokenTypes.WHITE_SPACE -> { assert(node.children.isEmpty()); null }
        MarkdownTokenTypes.EOL -> {
            assert(node.children.isEmpty())
            if (node.prevSibling?.type == MarkdownTokenTypes.EOL)
                MarkupElement("*md-br", metadata = node.metadata)
            else null
        }

        // Divider / Horizontal Rule
        MarkdownTokenTypes.HORIZONTAL_RULE -> {
            assert(node.children.isEmpty())
            MarkupElement("hr", metadata = node.metadata)
        }

        // Regular paragraph block
        MarkdownElementTypes.PARAGRAPH -> text("p", node)

        // Headers
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
            val closed = node.children.last().type == MarkdownTokenTypes.ATX_HEADER
            val contentNode = node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT)!!
            text(
                "h${node.type.name.last()}", contentNode,
                metadata = mapOfNonNull("md-type" to "atx", "md-style" to if (closed) "closed" else "open"),
            )
        }
        MarkdownElementTypes.SETEXT_1, MarkdownElementTypes.SETEXT_2 -> {
            val underline = node.findChildOfType(
                if (node.type == MarkdownElementTypes.SETEXT_1) MarkdownTokenTypes.SETEXT_1
                else MarkdownTokenTypes.SETEXT_2
            )?.text
            val contentNode = node.findChildOfType(MarkdownTokenTypes.SETEXT_CONTENT)!!
            text(
                "h${node.type.name.last()}", contentNode,
                metadata = mapOfNonNull("md-type" to "setext", "md-underline" to underline),
            )
        }
        // Tokens inside headers that should never be reached
        MarkdownTokenTypes.ATX_CONTENT, MarkdownTokenTypes.SETEXT_CONTENT,
        MarkdownTokenTypes.ATX_HEADER, MarkdownTokenTypes.SETEXT_1, MarkdownTokenTypes.SETEXT_2 ->
            throw IllegalStateException("unexpected ${node.type}")


        // Recursive blocks (they can contain other blocks including themselves)
        MarkdownElementTypes.UNORDERED_LIST -> {
            val indent = node.prevSibling?.takeIf { it.type == MarkdownTokenTypes.WHITE_SPACE }?.text
            block("ul", node, metadata = mapOfNonNull("md-indent" to indent))
        }
        MarkdownElementTypes.ORDERED_LIST -> {
            val listItem = node.findChildOfType(MarkdownElementTypes.LIST_ITEM)
            val listNumber = listItem?.findChildOfType(MarkdownTokenTypes.LIST_NUMBER)
            val start = listNumber?.text?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()?.takeIf { it != 1 }
            val indent = node.prevSibling?.takeIf { it.type == MarkdownTokenTypes.WHITE_SPACE }?.text
            block(
                "ol", node,
                attributes = mapOfNonNull("start" to start),
                metadata = mapOfNonNull("md-indent" to indent)
            )
        }
        MarkdownElementTypes.LIST_ITEM -> {
            val marker = (node.findChildOfType(MarkdownTokenTypes.LIST_BULLET) ?:
                          node.findChildOfType(MarkdownTokenTypes.LIST_NUMBER))?.text
            block("li", node, metadata = mapOfNonNull("md-marker" to marker))
        }
        MarkdownElementTypes.BLOCK_QUOTE -> block("blockquote", node)
        // Ignored tokens inside recursive blocks (check parents though)
        MarkdownTokenTypes.LIST_BULLET, MarkdownTokenTypes.LIST_NUMBER -> checkToken(node, MarkdownElementTypes.LIST_ITEM)
        MarkdownTokenTypes.BLOCK_QUOTE -> checkToken(node, MarkdownElementTypes.BLOCK_QUOTE)

        // Code blocks
        MarkdownElementTypes.CODE_FENCE -> {
            val fence = node.findChildOfType(MarkdownTokenTypes.CODE_FENCE_START)?.text
            val end = node.findChildOfType(MarkdownTokenTypes.CODE_FENCE_END)?.text
            val info = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.text
            val lang = info?.trim()?.substringBefore(' ')?.lowercase()
            val code = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
                .joinToString("\n") { it.text }
            codeBlock(node, code, "fence", lang, "md-info" to info,
                "md-fence" to fence, "md-fence-end" to if (fence == end) null else end)
        }
        MarkdownElementTypes.CODE_BLOCK -> {
            val (code, indent) = node.children.filter { it.type == MarkdownTokenTypes.CODE_LINE }
                .joinToString("\n") { it.text }.trimAndReportIndent()
            codeBlock(node, code, "block", null, "md-indent" to indent)
        }
        // Tokens inside code blocks that should never be reached
        MarkdownTokenTypes.CODE_LINE,
        MarkdownTokenTypes.CODE_FENCE_START, MarkdownTokenTypes.CODE_FENCE_END,
        MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.FENCE_LANG ->
            throw IllegalStateException("unexpected ${node.type}")

        // HTML blocks
        MarkdownElementTypes.HTML_BLOCK -> {
            val html = node.children.filter { it.type == MarkdownTokenTypes.HTML_BLOCK_CONTENT }
                .joinToString("\n") { it.text }
            // TODO: parse HTML
            //  see https://github.com/JetBrains/markdown/blob/master/src/commonMain/kotlin/org/intellij/markdown/flavours/gfm/lexer/gfm.flex#L41 for a list of possible block-level HTML elements
            //  see https://github.github.com/gfm/#disallowed-raw-html-extension- for disallowed elements
            null
        }
        MarkdownTokenTypes.HTML_BLOCK_CONTENT -> throw IllegalStateException("unexpected ${node.type}")

        // Process link definitions
        MarkdownElementTypes.LINK_DEFINITION -> {
            val (_, label) = getLinkLabel(node)
            val destNode = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)!!
            val dest = destNode.text.trim().removeBrackets('<', '>')
            val elem = MarkupElement("*md-link-def",
                attributes = mapOfNonNull("href" to dest, "title" to getLinkTitle(node)),
                metadata = mapOf("md-label" to label) + node.metadata
            )
            if (label !in linkDefinitions)
                linkDefinitions[label] = elem
            else if (label != "//" && dest != "#" && dest.isNotEmpty())  // these are common ways of making comments, don't warn about duplicates
                Logger.w("Duplicate definition for link label '$label', ignoring all but the first")
            elem
        }

        // GFM Tables
        GFMElementTypes.TABLE -> {
            val sep = node.findChildOfType(GFMTokenTypes.TABLE_SEPARATOR)?.text
            val alignments = sep?.trim()?.removeBrackets('|')?.split('|')?.map { it.trim() }?.map {
                if (it.startsWith(":")) {
                    if (it.endsWith(":")) "center" else "left"
                } else if (it.endsWith(":")) "right"
                else null
            }
            tableAlignments.clear()
            alignments?.let { tableAlignments.addAll(it) }
            block("table", node, metadata = mapOfNonNull("md-separator" to sep))
        }
        GFMElementTypes.HEADER -> MarkupElement("thead", listOf(tableRow(node)), metadata = node.metadata)
        GFMElementTypes.ROW -> tableRow(node)
        GFMTokenTypes.TABLE_SEPARATOR -> checkToken(node, GFMElementTypes.TABLE, GFMElementTypes.HEADER, GFMElementTypes.ROW)
        GFMTokenTypes.CELL -> {
            val align = tableAlignments.getOrNull(node.indexInParentOfType(GFMTokenTypes.CELL))
            text(
                if (node.parent?.type == GFMElementTypes.HEADER) "th" else "td", node,
                attributes = mapOfNonNull("align" to align),
            )
        }

        // GFM Task Lists
        GFMTokenTypes.CHECK_BOX -> {
            val char = node.text[1]
            val checked = char.lowercaseChar() == 'x'
            MarkupElement(
                "input",
                attributes = mapOf("type" to "checkbox", "checked" to checked),
                metadata = mapOfNonNull("md-char" to char.takeIf { checked }),
            )
        }

        else -> {
            // Other elements/tokens are ignored but we still explore their children
            Logger.w("** ignoring unknown markdown element type ${node.type}")
            block(node.type.name, node)
        }
    }
}

private inline val ASTNode.siblings get() = parent?.children
private inline val ASTNode.isFirstOfParent get() = siblings?.firstOrNull() == this
private inline val ASTNode.isLastOfParent get() = siblings?.lastOrNull() == this
private inline val ASTNode.indexInParent get() = siblings?.indexOf(this) ?: -1
private fun ASTNode.indexInParentOfType(type: IElementType) = siblings?.filter { it.type == type }?.indexOf(this) ?: -1
private inline val ASTNode.prevSibling get() = siblings?.let { s -> s.indexOf(this).takeIf { it > 0 }?.let { s[it-1] } }
private inline val ASTNode.nextSibling get() = siblings?.let { s -> s.indexOf(this).takeIf { it >= 0 && it < s.lastIndex }?.let { s[it+1] } }

@Suppress("NOTHING_TO_INLINE")
private inline fun checkToken(node: ASTNode, vararg parentTypes: IElementType): Nothing? {
    assert(node.children.isEmpty() && node.parent?.type in parentTypes)
    return null
}

/** Process all children of the node recursively (block mode) */
private fun MDState.block(
    tag: String, node: ASTNode,
    attributes: Map<String, Any> = emptyMap(),
    metadata: Map<String, Any> = emptyMap()
) = MarkupElement(
    tag, node.children.mapNotNull { processNode(it) },
    attributes, metadata + node.metadata
)

/** Create a <pre><code> element */
private fun MDState.codeBlock(
    node: ASTNode, code: String, type: String, lang: String? = null,
    vararg metadata: Pair<String, Any?>
) = MarkupElement("pre",
    listOf(MarkupElement("code", listOf(MarkupText(code)))),
    attributes = mapOfNonNull("class" to lang?.let { "language-$it" }),
    metadata = mapOfNonNull("lang" to lang, "md-type" to type, *metadata) + node.metadata,
)

/** Create a <tr> element */
private fun MDState.tableRow(node: ASTNode): MarkupNode {
    val hasLeadingSep = node.children.firstOrNull()?.type == GFMTokenTypes.TABLE_SEPARATOR
    val hasTrailingSep = node.children.lastOrNull()?.type == GFMTokenTypes.TABLE_SEPARATOR
    return block("tr", node,
        metadata = mapOfNonNull(
            "md-leading-sep" to if (!hasLeadingSep) "none" else null,
            "md-trailing-sep" to if (!hasTrailingSep) "none" else null,
        )
    )
}

/** Process all children of the node recursively (text mode) */
private fun MDState.text(
    tag: String,
    node: ASTNode,
    attributes: Map<String, Any> = emptyMap(),
    metadata: Map<String, Any> = emptyMap()
) = MarkupElement(
    tag, processHTMLTags(node.children.mapNotNull { processText(it) }),
    attributes, metadata + node.metadata
)

/** Process a node in text mode. */
private fun MDState.processText(node: ASTNode): MarkupNode? {
    //if (node.type !in listOf(MarkdownTokenTypes.EOL, MarkdownElementTypes.MARKDOWN_FILE)) {
    //    println("${node.type} ${node.startOffset} ${node.endOffset} '${node.text}'")
    //}

    return when (node.type) {
        // Basic Text
        MarkdownTokenTypes.TEXT -> {
            assert(node.children.isEmpty())
            MarkupText(KsoupEntities.decodeHtml(unescape(node.text)), metadata = node.metadata)
        }
        MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.EOL -> {
            assert(node.children.isEmpty())
            MarkupText(node.text, metadata = node.metadata)
        }
        MarkdownTokenTypes.HARD_LINE_BREAK -> {
            assert(node.children.isEmpty())
            MarkupElement("<br>", metadata = node.metadata)
        }
        MarkdownTokenTypes.ESCAPED_BACKTICKS -> {
            assert(node.children.isEmpty())
            MarkupText(node.text.trimStart('\\'), metadata = node.metadata) // can include multiple `
        }

        // Tokens that are sometimes ignored (based on their parent element)
        MarkdownTokenTypes.EMPH -> maybeAddTokenDouble(node, MarkdownElementTypes.EMPH, MarkdownElementTypes.STRONG)
        MarkdownTokenTypes.BACKTICK -> maybeAddToken(node, MarkdownElementTypes.CODE_SPAN)
        MarkdownTokenTypes.LBRACKET -> maybeAddLeftToken(node, MarkdownElementTypes.LINK_TEXT, MarkdownElementTypes.LINK_LABEL)
        MarkdownTokenTypes.RBRACKET -> maybeAddRightToken(node, MarkdownElementTypes.LINK_TEXT, MarkdownElementTypes.LINK_LABEL)
        MarkdownTokenTypes.LPAREN -> maybeAddLeftToken(node, MarkdownElementTypes.INLINE_LINK)
        MarkdownTokenTypes.RPAREN -> maybeAddRightToken(node, MarkdownElementTypes.INLINE_LINK)
        MarkdownTokenTypes.LT -> maybeAddToken(node, MarkdownElementTypes.AUTOLINK, MarkdownTokenTypes.EMAIL_AUTOLINK) {
            // A bug in the lexer email autolink places the < > outside the element
            !node.isFirstOfParent && node.nextSibling?.type != MarkdownTokenTypes.EMAIL_AUTOLINK
        }
        MarkdownTokenTypes.GT -> maybeAddToken(node, MarkdownElementTypes.AUTOLINK, MarkdownTokenTypes.EMAIL_AUTOLINK) {
            // A bug in the lexer email autolink places the < > outside the element
            !node.isLastOfParent && node.prevSibling?.type != MarkdownTokenTypes.EMAIL_AUTOLINK
        }
        MarkdownTokenTypes.DOUBLE_QUOTE, MarkdownTokenTypes.SINGLE_QUOTE -> maybeAddToken(node, MarkdownElementTypes.LINK_TITLE)
        MarkdownTokenTypes.EXCLAMATION_MARK -> maybeAddLeftToken(node, MarkdownElementTypes.IMAGE)
        MarkdownTokenTypes.COLON -> maybeAddToken(node)
        GFMTokenTypes.TILDE -> maybeAddTokenDouble(node, GFMElementTypes.STRIKETHROUGH)

        // Basic Span Styles
        MarkdownElementTypes.EMPH -> text("em", node, metadata = emphasisType(node))
        MarkdownElementTypes.STRONG -> text("strong", node, metadata = emphasisType(node))
        MarkdownElementTypes.CODE_SPAN -> text("code", node)
        GFMElementTypes.STRIKETHROUGH -> text("s", node)

        // Links
        MarkdownElementTypes.INLINE_LINK -> inlineLink(node)
        MarkdownElementTypes.FULL_REFERENCE_LINK, MarkdownElementTypes.SHORT_REFERENCE_LINK -> refLink(node)
        MarkdownElementTypes.AUTOLINK -> addRawLink(node.findChildOfType(MarkdownTokenTypes.AUTOLINK)!!, "autolink")
        MarkdownTokenTypes.EMAIL_AUTOLINK -> addRawLink(node, "email")
        GFMTokenTypes.GFM_AUTOLINK -> addRawLink(node, "gfm")

        // Images
        MarkdownElementTypes.IMAGE -> {
            node.findChildOfType(MarkdownElementTypes.INLINE_LINK)?.let { inlineLink(it, "img", "src") } ?:
            node.findChildOfType(MarkdownElementTypes.FULL_REFERENCE_LINK)?.let { refLink(it, "img") } ?:
            throw IllegalStateException("unknown image source")
        }

        // HTML Tags
        MarkdownTokenTypes.HTML_TAG -> {
            val text = node.text
            if (isPossiblyHTMLSpecialNode(text)) parseHTMLSpecialNode(text)
            else if (isPossiblyHTMLCloseTag(text)) parseHTMLCloseTag(text)?.let { closeHTMLTag(node, it) }
            else parseHTMLTag(text)?.let { startHTMLTag(node, it) }
        }

        // Tokens that are never used in the source code
        //MarkdownTokenTypes.LINK_ID, MarkdownTokenTypes.LINK_TITLE, MarkdownTokenTypes.URL

        else -> {
            // Other elements/tokens are ignored but we still explore their children
            Logger.w("** ignoring unknown markdown element type ${node.type} in text mode")
            text(node.type.name, node)
        }
    }
}

/** Determine emphasis type (either * or _) */
private fun MDState.emphasisType(node: ASTNode): Map<String, String> =
    node.children.firstOrNull()?.text?.let { mapOf("md-type" to it) } ?: emptyMap()

/** Add the token if it the condition passes or not within one of the given parent elements. */
private inline fun MDState.maybeAddToken(node: ASTNode, vararg parentTypes: IElementType, condition: () -> Boolean): MarkupNode? {
    assert(node.children.isEmpty())
    return if (node.parent?.type !in parentTypes || condition()) MarkupText(node.text, metadata = node.metadata) else null
}

/**
 * Add the token if it isn't the first or last tokens of its parent or not
 * within one of the given parent elements
 */
private fun MDState.maybeAddToken(node: ASTNode, vararg parentTypes: IElementType) =
    maybeAddToken(node, *parentTypes) { !node.isFirstOfParent && !node.isLastOfParent }

/**
 * Add the token if it isn't the first two or last two tokens of its parent or
 * not within one of the given parent elements
 */
private fun MDState.maybeAddTokenDouble(node: ASTNode, vararg parentTypes: IElementType) =
    maybeAddToken(node, *parentTypes) {
        node.indexInParent in 2 until ((node.parent?.children?.size ?: 0) - 2)
    }

/**
 * Add the token if it isn't the first token of its parent or not within one of
 * the given parent elements
 */
private fun MDState.maybeAddLeftToken(node: ASTNode, vararg parentTypes: IElementType) =
    maybeAddToken(node, *parentTypes) { !node.isFirstOfParent }

/**
 * Add the token if it isn't the last token of its parent or not within one of
 * the given parent elements
 */
private fun MDState.maybeAddRightToken(node: ASTNode, vararg parentTypes: IElementType) =
    maybeAddToken(node, *parentTypes) { !node.isLastOfParent }

/** Create an inline link. */
private fun MDState.inlineLink(node: ASTNode, tag: String = "a", destKey: String = "href"): MarkupNode {
    val destNode = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)!!
    val textNode = node.findChildOfType(MarkdownElementTypes.LINK_TEXT) ?: destNode
    return text(tag, textNode, mapOfNonNull(
        destKey to destNode.text.trim(),
        "title" to getLinkTitle(node))
    )
}

/** Create a reference link. */
private fun MDState.refLink(node: ASTNode, tag: String = "a"): MarkupNode {
    val textNode = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
    val (labelNode, label) = getLinkLabel(node)
    return text("$tag-ref", textNode ?: labelNode, metadata = mapOf("md-label" to label))
}

/** Get the title of a link, or null */
private fun MDState.getLinkTitle(node: ASTNode) =
    node.findChildOfType(MarkdownElementTypes.LINK_TITLE)?.text?.trim()?.removeSingleBrackets(
        quotesAndParens
    )
private val quotesAndParens = listOf('(' to ')', '"' to '"', '\'' to '\'')

/** Get the label node and label value of a link reference or definition. */
private fun MDState.getLinkLabel(node: ASTNode): Pair<ASTNode, String> {
    val labelNode = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)!!
    val label = labelNode.text.trim().removeBrackets('[', ']').lowercase()  // can include letters, numbers, spaces, or punctuation
    return labelNode to label
}

/** Add a raw link to the current annotated string builder. */
private fun MDState.addRawLink(node: ASTNode, style: String): MarkupNode {
    assert(node.children.isEmpty())
    val link = node.text
    val url = link.trim()
    return MarkupElement("a", listOf(MarkupText(link)),
        attributes = mapOf("href" to if (style == "email") "mailto:$url" else ensureSchema(url)),
        metadata = mapOf("md-raw" to style) + node.metadata,
    )
}

/** Make sure the link has a schema, if it doesn't, add http:// */
private fun ensureSchema(link: String): String {
    val schemaIndex = link.indexOf("://")
    return if (schemaIndex == -1 || link.indexOf('/') != schemaIndex + 1) "http://$link"
    else link
}

/** Unescape markdown text, processing \`*_{}[]<>()#+-.!| */
private fun unescape(string: String): String {
    val output = StringBuilder(string.length)
    var start = 0
    var end = string.indexOf('\\', start)
    while (end >= 0 && end < string.length - 1) {
        output.appendRange(string, start, end)
        if (string[end + 1] !in "\\`*_{}[]<>()#+-.!|") output.append('\\')
        start = end + 1
        end = string.indexOf('\\', start)
    }
    output.appendRange(string, start, string.length)
    return output.toString()
}

/** HTML inline (text) tags allowed */
private val ALLOWED_HTML_INLINE_TAGS = setOf(
    "code", "em", "strong", "a", "img", "br", // have a markdown equivalent
    "b", "i", "s", "u", "small", "big", "sub", "sup", "span", // non-semantic styling
    "del", "ins", "mark", "abbr", "cite", "dfn", "kbd", "samp", "var", // semantic styling
)

/** Attributes of HTML inline (text) tags allowed */
private val ALLOWED_HTML_INLINE_TAG_ATTRIBUTES = mapOf(
    "a" to setOf("id", "title", "style", "href"),
    "img" to setOf("id", "title", "style", "src", "alt", "width", "height"),
    "br" to setOf(),
)

/** Default attributes allowed for HTML inline (text) tags */
private val DEFAULT_ALLOWED_HTML_INLINE_TAG_ATTRIBUTES = setOf("id", "title", "style")

/**
 * Add a marker for an inline HTML tag. This needs further processing later
 * once all of the children of the current parent have been processed to match
 * with a closing tag and group elements into this element.
 */
private fun MDState.startHTMLTag(node: ASTNode, tag: HTMLTagInfo): MarkupNode? {
    if (tag.name !in ALLOWED_HTML_INLINE_TAGS) return null
    val allowedAttrs = ALLOWED_HTML_INLINE_TAG_ATTRIBUTES[tag.name] ?:
        DEFAULT_ALLOWED_HTML_INLINE_TAG_ATTRIBUTES
    return MarkupElement(
        tag.name,
        attributes = tag.attributes.filterKeys { it in allowedAttrs },
        metadata = mapOf("md-is-html" to true, "md-self-close" to tag.selfClosing) + node.metadata,
    )
}

/** Add a marker for an inline HTML tag being closed. */
private fun MDState.closeHTMLTag(node: ASTNode, tag: String) =
    if (tag in voidTags || tag !in ALLOWED_HTML_INLINE_TAGS) null
    else MarkupElement("/$tag", metadata = mapOf("md-is-html" to true) + node.metadata)

private fun processHTMLTags(nodes: List<MarkupNode>): List<MarkupNode> {
    if (!nodes.any { it.metadata["md-is-html"] == true && it.tag !in voidTags }) return nodes
    val newNodes = mutableListOf<MarkupNode>()
    var i = 0
    while (i < nodes.size) {
        val node = nodes[i]
        if (node is MarkupCharacterData || node.metadata["md-is-html"] != true || node.tag in voidTags || node.metadata["md-self-close"] == true) {
            // non-html or html that is self-closing is added directly
            newNodes.add(node)
        } else if (node.tag.firstOrNull() == '/') {
            // drop closing tags that we haven't matched
        } else {
            // find the matching closing tag and group the elements in between
            val tag = node.tag
            val close = nodes.indexOf(i+1) { it.tag == "/$tag" }
            val end = if (close == -1) { Logger.w("unclosed HTML tag $tag"); nodes.size } else close
            newNodes.add(MarkupElement(
                tag,
                processHTMLTags(nodes.subList(i+1, end)),
                node.attributes,
                node.metadata,
            ))
            i = end
        }
        i++
    }
    return newNodes
}

private fun <T> List<T>.indexOf(start: Int, predicate: (T) -> Boolean): Int {
    val it = listIterator(start)
    for (index in start until size) {
        if (predicate(it.next()))
            return index
    }
    return -1
}
