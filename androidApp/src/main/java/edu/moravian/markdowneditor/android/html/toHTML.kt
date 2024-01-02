package edu.moravian.markdowneditor.android.html

import edu.moravian.markdowneditor.android.markup.MarkupElement
import edu.moravian.markdowneditor.android.markup.MarkupNode

// This is just MarkupElement.toString() but it filters out tags that start with "*"
// Eventually this will be more complicated, but for now it is fine
fun MarkupNode.toHTML() = if (this is MarkupElement) {
    val open = if (attributes.isEmpty()) "<$tag>"
        else "<$tag ${attributes.entries.joinToString(" ") { it.toAttribute() }}>"
    val children = this.children.filter { !it.tag.startsWith("*") }
    if (children.isEmpty()) open
        else if (children.size == 1) "$open${children[0]}</$tag>"
        else "$open\n${children.joinToString("\n").prependIndent("  ")}\n</$tag>"
} else {
    this.toString()
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
        else -> "$k=\"$v\"" // TODO
    }
}
