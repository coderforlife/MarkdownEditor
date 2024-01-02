package edu.moravian.markdowneditor.android.html

import co.touchlab.kermit.Logger
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import edu.moravian.markdowneditor.android.markup.MarkupCDATASection
import edu.moravian.markdowneditor.android.markup.MarkupComment
import edu.moravian.markdowneditor.android.markup.MarkupDeclaration
import edu.moravian.markdowneditor.android.markup.MarkupNode
import edu.moravian.markdowneditor.android.markup.MarkupProcessingInstruction

/**
 * Parse a single HTML element tag including the <>. Detects self-closing tags.
 */
fun parseHTMLTag(element: String): HTMLTagInfo? {
    val contents = contents(element) ?: return warn("tag", element)

    // Parse tag
    val tag = contents.takeWhile { it.isLetterOrDigit() }.lowercase()
    val selfClosing = contents.endsWith("/")
    val off = if (selfClosing) 1 else 0
    if (contents.length != tag.length+off && contents[tag.length] != ' ') return warn("tag", element)

    // Parse attributes
    val attrStr = contents.substring(tag.length, contents.length-off)
    val attributes = mutableMapOf<String, String>()
    var lastEnd = 0
    for (match in htmlAttrMatch.findAll(attrStr)) {
        if (lastEnd == match.range.first || attrStr.substring(lastEnd, match.range.first).isNotBlank()) return warn("tag", element)
        val (name, value) = match.destructured
        attributes[name.lowercase()] = KsoupEntities.decodeHtml(
            if (value.isNotEmpty() && value[0] in "\"'") value.substring(1, value.length-1) else value
        )
        lastEnd = match.range.last + 1
    }
    if (attrStr.substring(lastEnd).isNotBlank()) return warn("tag", element)

    // Return the tag information
    return HTMLTagInfo(tag, attributes, selfClosing)
}

/**
 * Information about an HTML tag. The name and attribute names are always
 * lowercased.
 */
data class HTMLTagInfo(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val selfClosing: Boolean = false,
)

/**
 * Parse a single HTML element closing tag including the <> and return the tag
 * name (without the /). Returns null if the tag is not a closing tag.
 */
fun parseHTMLCloseTag(element: String) =
    contents(element)?.takeIf { it.startsWith("/") }?.substring(1)?.trimEnd()?.
        takeIf {it.isNotEmpty() && it.all { ch -> ch.isLetterOrDigit() } } ?: warn("closing tag", element)

/** Check if an HTML node is possibly a closing tag. */
fun isPossiblyHTMLCloseTag(element: String) = element.length > 3 && element.startsWith("</")

/**
 * Parse a single HTML special 'node', which may be an comment, CDATA,
 * processing instruction, or declaration. The first two characters must be
 * "<!" or "<?" and the last character must be ">" (along with several other
 * requirements).
 */
fun parseHTMLSpecialNode(node: String): MarkupNode? =
    contents(node)?.let { c -> specialNodes.firstOrNull { it.matches(c) }?.create(c) } ?: warn("node", node)

/** Check if an HTML node is possibly a special node. */
fun isPossiblyHTMLSpecialNode(node: String) =
    node.length >= 3 && node[0] == '<' && (node[1] == '!' || node[1] == '?')

/** Information about parsing and creating a special HTML node. */
private data class SpecialNodeInfo(
    val start: String,
    val end: String,
    val ctor: (String) -> MarkupNode,
) {
    @Suppress("NOTHING_TO_INLINE")
    inline fun matches(contents: String) = contents.startsWith(start) && contents.endsWith(end)
    @Suppress("NOTHING_TO_INLINE")
    inline fun create(contents: String) = ctor(contents.substring(start.length, contents.length-end.length))
}

/** Special HTML nodes. */
private val specialNodes = listOf(
    SpecialNodeInfo("!--", "--", ::MarkupComment),
    SpecialNodeInfo("![CDATA[", "]]", ::MarkupCDATASection),
    SpecialNodeInfo("!", "", ::MarkupDeclaration),
    SpecialNodeInfo("?", "?", ::MarkupProcessingInstruction),
)

/** Removes the < > brackets from the tag and returns the contents. Returns null if invalid. */
@Suppress("NOTHING_TO_INLINE")
private inline fun contents(text: String) =
    if (text.length < 3 || text.first() != '<' || text.last() != '>') null
    else text.substring(1, text.length-1)

/** Print a warning and return null. */
@Suppress("NOTHING_TO_INLINE")
private inline fun warn(type: String, text: String): Nothing? {
    Logger.w("invalid HTML $type: $text")
    return null
}

/** Regular expression that matches a name and optional value for an attribute */
private val htmlAttrMatch = Regex(
    "([^ \"'>/=\u0000-\u001F\u007F]+)" + // attribute name
            "(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^ \"'=<>`]+))?" // optional attribute value
)

/** HTML tags that never use a close tag */
val voidTags = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr"
)

