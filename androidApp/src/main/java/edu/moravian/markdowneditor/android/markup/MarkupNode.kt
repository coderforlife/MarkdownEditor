@file:Suppress("NOTHING_TO_INLINE")

package edu.moravian.markdowneditor.android.markup

import edu.moravian.markdowneditor.android.css.parseInlineCSS
import edu.moravian.markdowneditor.android.html.encodeHTMLEntities

/**
 * A node in a markup tree. Depending on the type of node, additional features
 * are available. In the default implementation, there are no children or
 * attributes. However, every node can have metadata and a parent (except if it
 * is the root, then the parent is null).
 */
sealed class MarkupNode(
    val metadata: Map<String, Any> = emptyMap(),
    val parent: MarkupNode? = null,
) {
    abstract val tag: String
    open val children = emptyList<MarkupNode>()
    open val attributes = emptyMap<String, Any>()

    /** Index of this node within it parent, or -1 if this is the root node */
    // cached for performance reasons
    val index by lazy { parent?.children?.indexOf(this) ?: -1 }

    // Convenience operators to get a child (by index) or attribute (by name)
    inline operator fun get(index: Int) = children[index]
    inline operator fun get(attributeName: String) = attributes[attributeName]

    /**
     * Visits all nodes in a depth-first-search order, calling the
     * function for each node visited.
     */
    fun visit(visit: (MarkupNode) -> Unit) {
        visit(this)
        children.forEach { it.visit(visit) }
    }

    /** Depth-first-search list of nodes in the tree. */
    fun dfsList(): List<MarkupNode> {
        val nodes = mutableListOf<MarkupNode>()
        visit { nodes.add(it) }
        return nodes
    }
}

inline val MarkupNode.siblings get() = parent?.children
inline val MarkupNode.isFirstOfParent get() = siblings?.firstOrNull() === this
inline val MarkupNode.isLastOfParent get() = siblings?.lastOrNull() === this
inline val MarkupNode.prevSibling get() = siblings?.let { s -> index.takeIf { it > 0 }?.let { s[it-1] } }
inline val MarkupNode.nextSibling get() = siblings?.let { s -> index.takeIf { it >= 0 && it < s.lastIndex }?.let { s[it+1] } }
inline val MarkupNode.parents get() = generateSequence(parent) { it.parent }
//inline val MarkupNode.indexInParentOfTag get() = siblings?.filter { it.tag == tag }?.indexOf(this) ?: -1
//inline fun MarkupNode.indexInParentOfTags(vararg tags: String) = siblings?.filter { it.tag in tags }?.indexOf(this) ?: -1

val MarkupNode.innerText: String get() = innerTextRecursive.trim(' ')
private val MarkupNode.innerTextRecursive: String get() = when {
    this is MarkupText -> this.data.replace(whitespaceRE, " ")
    this is MarkupCharacterData -> "" // comments and similar
    this.tag == "br" -> "\n"
    else -> children.joinToString("") { it.innerTextRecursive }
        .replace(spaceRE, " ").replace(newlineRE, "\n")
}

val MarkupNode.rendered: Boolean get() =
    (this is MarkupText || this is MarkupElement) && !this.tag.startsWith("*")

/**
 * A markup element, i.e. a tag. An element has a meaningful tag, 0 or more
 * children, and 0 or more attributes.
 */
class MarkupElement(
    override val tag: String,
    override val children: List<MarkupNode> = emptyList(),
    override val attributes: Map<String, Any> = emptyMap(),
    metadata: Map<String, Any> = emptyMap(),
    parent: MarkupNode? = null,
): MarkupNode(metadata, parent) {
    override fun toString(): String {
        val open = if (attributes.isEmpty()) "<$tag>"
            else "<$tag ${attributes.entries.joinToString(" ") { it.toAttribute() }}>"
        return if (children.isEmpty()) open
            else if (children.size == 1) "$open${children[0]}</$tag>"
            else "$open\n${children.joinToString("\n").prependIndent("  ")}\n</$tag>"
    }

    private fun Map.Entry<String, Any>.toAttribute(): String {
        val (key, value) = this
        val k = encodeHTMLEntities(key)
        val v = encodeHTMLEntities(value.toString())
        return when (value) {
            false -> ""
            true, "" -> k
            is String -> "$k=\"$v\""
            is Number -> "$k=$v"
            else -> "$k=\"$v\"" // TODO: handle other types (the style attribute could be a map, maybe others could be enums)
        }
    }

    internal fun copy(
        tag: String = this.tag,
        children: List<MarkupNode> = this.children,
        attributes: Map<String, Any> = this.attributes,
        metadata: Map<String, Any> = this.metadata,
        parent: MarkupNode? = this.parent,
    ) = MarkupElement(tag, children, attributes, metadata, parent)

    override fun hashCode() = hash(tag, children, attributes, metadata) // adding parent would cause infinite recursion
    override fun equals(other: Any?) =
        this === other || other is MarkupElement &&
        tag == other.tag && children == other.children &&
        attributes == other.attributes && metadata == other.metadata //&& parent == other.parent  // would cause infinite recursion
}

/**
 * A markup chunk of character data. Has no attribute or children, but does
 * have character data. All of the tags for these begin with #. The main type
 * of character data is for text.
 */
sealed class MarkupCharacterData(
    val data: String = "",
    metadata: Map<String, Any> = emptyMap(),
    parent: MarkupNode? = null,
): MarkupNode(metadata, parent) {
    val length = data.length
    internal abstract fun copy(
        data: String = this.data,
        metadata: Map<String, Any> = this.metadata,
        parent: MarkupNode? = this.parent,
    ): MarkupCharacterData
    override fun hashCode() = hash(tag, data, metadata)
    override fun equals(other: Any?) =
        this === other || other is MarkupCharacterData &&
                tag == other.tag && data == other.data && metadata == other.metadata
}

open class MarkupText(
    data: String = "",
    metadata: Map<String, Any> = emptyMap(),
    parent: MarkupNode? = null,
): MarkupCharacterData(data, metadata, parent) {
    override val tag = "#text"
    override fun toString() = encodeHTMLEntities(data)
    override fun copy(data: String, metadata: Map<String, Any>, parent: MarkupNode?) =
        MarkupText(data, metadata, parent)
}

class MarkupCDATASection(
    data: String = "",
    metadata: Map<String, Any> = emptyMap(),
    parent: MarkupNode? = null,
): MarkupText(data, metadata, parent) {
    init { require("]]>" !in data) { "CDATA sections cannot contain ']]>'" } }
    override val tag = "#cdata-section"
    override fun toString() = "<![CDATA[$data]]>"
    override fun copy(data: String, metadata: Map<String, Any>, parent: MarkupNode?) =
        MarkupCDATASection(data, metadata, parent)
}

class MarkupComment(
    data: String = "",
    metadata: Map<String, Any> = emptyMap(),
    parent: MarkupNode? = null,
): MarkupCharacterData(data, metadata, parent) {
    init { require("--" !in data) { "comments cannot contain '--'" } }
    override val tag = "#comment"
    override fun toString() = "<!--$data-->"
    override fun copy(data: String, metadata: Map<String, Any>, parent: MarkupNode?) =
        MarkupComment(data, metadata, parent)
}

class MarkupDeclaration(
    data: String = "",
    metadata: Map<String, Any> = emptyMap(),
    parent: MarkupNode? = null,
): MarkupCharacterData(data, metadata, parent) {
    init { require(">" !in data) { "declarations cannot contain '>'" } }
    override val tag = "#declaration"
    override fun toString() = "<!$data>"
    override fun copy(data: String, metadata: Map<String, Any>, parent: MarkupNode?) =
        MarkupDeclaration(data, metadata, parent)
}

class MarkupProcessingInstruction(
    data: String = "",
    metadata: Map<String, Any> = emptyMap(),
    parent: MarkupNode? = null,
): MarkupCharacterData(data, metadata, parent) {
    init { require("--" !in data) { "processing instructions cannot contain '?>'" } }
    override val tag = "#processing-instruction"
    override fun toString() = "<?$data?>"
    override fun copy(data: String, metadata: Map<String, Any>, parent: MarkupNode?) =
        MarkupProcessingInstruction(data, metadata, parent)
}

@Suppress("UNCHECKED_CAST")
val MarkupNode.style: Map<String, String> get() {
    // TODO: have this "built in"
    val style = attributes["style"]
    return if (style is String) { parseInlineCSS(style) }
        else { style as? Map<String, String> } ?: emptyMap()
}

fun MarkupNode.attrAsInt(attr: String) = when (val raw = attributes[attr]) {
    is String -> raw.toIntOrNull()
    is Int? -> raw
    else -> null
}

fun MarkupNode.attrAsBoolean(attr: String) = when (val raw = attributes[attr]) {
    is String -> true // existence is true
    is Boolean -> raw
    else -> false
}

private fun hash(vararg values: Any?) = values.fold(0) { acc, v -> 31 * acc + (v?.hashCode() ?: 0) }

private val spaceRE = Regex(" +")
private val newlineRE = Regex(" *\n *")
private val whitespaceRE = Regex("[ \t\n\r]+")
private val whitespaceChars = charArrayOf(' ', '\t', '\n', '\r')
private val whitespaceCharsSet = setOf(' ', '\t', '\n', '\r')
