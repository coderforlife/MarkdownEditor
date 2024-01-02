package edu.moravian.markdowneditor.android.html

/** Encode necessary HTML entities in the string (&"'<>). */
fun encodeHTMLEntities(str: String) =
    htmlEntitiesToEncode.entries.fold(str) { s, (old, new) -> s.replace(old, new) }

private val htmlEntitiesToEncode = mapOf("&" to "&amp;", "\"" to "&quot;", "'" to "&#39;", "<" to "&lt;", ">" to "&gt;")

// Decoding HTML entities is done with KsoupEntities.decodeHtml(string)
// Encoding is not done with KsoupEntities.encodeHtml(string) since it encodes more than we want
