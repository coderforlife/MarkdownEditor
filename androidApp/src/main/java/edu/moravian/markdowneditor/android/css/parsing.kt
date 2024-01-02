@file:Suppress("RegExpUnnecessaryNonCapturingGroup")

package edu.moravian.markdowneditor.android.css

import kotlin.math.PI

// From https://www.w3.org/TR/CSS21/grammar.html  (except that -- is allowed as a start of a property)
// TODO: should these use \\f or \u000C  ?
private const val MAX_UNICODE = "\udbff\udfff" // U+10FFFF
private const val NON_ASCII = "[\u00A0-$MAX_UNICODE]"
private const val ESCAPE = "(?:\\[0-9a-fA-F]{1,6}(?:\\r\\n|[ \\t\\r\\n\u000C])?|\\\\[^\\r\\n\u000C0-9a-fA-F])"
private val reEscape = Regex(ESCAPE)
private const val NAME_START = "(?:[_a-zA-Z]|$NON_ASCII|$ESCAPE)"
private const val NAME_CHAR = "(?:[_a-zA-Z0-9-]|$NON_ASCII|$ESCAPE)"
private val reProperty = Regex("-{0,2}$NAME_START$NAME_CHAR*")

private const val STRING_CONTENT = "(?:[^\\\"\\n\\r\\f]|$ESCAPE)*"  // TODO: |\{nl}
private val reString = Regex("(?:\"$STRING_CONTENT\"|\'$STRING_CONTENT\')")

/**
 * Parses a CSS style string into a map of property names to values. The values
 * are "raw" and have been minimally processed (comments have been removed, but
 * escape sequences have not been resolved).
 *
 * NOTE: this only returns the last value for a property which
 * non-conforming. This means if the last value is invalid, the prior (good)
 * value will not be retrievable.
 */
fun parseInlineCSS(style: String): Map<String, String> {
    if (style.isEmpty()) { return emptyMap() }
    val declarations = mutableMapOf<String, String>()
    var declaration = declaration(style, 0)
    while (declaration.property.isNotEmpty()) {
        declarations[declaration.property] = declaration.value
        declaration = declaration(style, declaration.index)
    }
    return declarations
}

/**
 * Information about a CSS declaration with a property name and value.
 * Additionally, the final index of the declaration is provided.
 */
private data class Declaration(
    val index: Int,
    val property: String = "",
    val value: String = "",
)

/**
 * Parses a CSS declaration starting at the given start index.
 */
private fun declaration(string: String, start: Int): Declaration {
    // Find start (skip comments and empty declarations)
    var index = comments(string, start)
    while (index < string.length && string[index] == ';') { index = comments(string, index + 1) }

    // Property name
    val property = reProperty.matchAt(string, index)?.value?.lowercase()
    if (property === null) {
        if (index != string.length) throw IllegalArgumentException("property missing name")
        return Declaration(index)
    }
    index = comments(string, index + property.length)
    if (index == string.length || string[index] != ':') throw IllegalArgumentException("property missing ':'")
    index = comments(string, index + 1)
    if (index == string.length) throw IllegalArgumentException("property missing value")

    // Property value
    val value = extractValue(string, index)
    index = comments(string, index + value.length)
    if (index != string.length) {
        if (string[index] != ';') throw IllegalArgumentException("property missing ';'")
        index = comments(string, index + 1)
    }

    return Declaration(index, decodeEscape(property), cleanupValue(value))
}

/** Possible brackets in a value */
private val brackets = mapOf('(' to ')', '[' to ']', '{' to '}')

/**
 * Extract the raw value after a property name, ensuring brackets and quotes
 * are balanced. The returned value will not include the optional semicolon
 * at the end.
 */
private fun extractValue(string: String, start: Int): String {
    var index = start
    val stack = mutableListOf<Char>()
    while (index < string.length && string[index] != ';') {
        when (val ch = string[index]) {
            '\\' -> index += 1 // skip next character
            '\'', '"' -> index += string(string, index, true).length - 1 // quoted string
            '/' -> if (string[index+1] == '*') { index = comment(string, index) - 1 } // comment
            in brackets -> stack += brackets.getValue(ch)
            in brackets.values ->
                if (stack.removeLastOrNull() != ch) throw IllegalArgumentException("value missing closing bracket")
        }
        index += 1
    }
    return string.substring(start, index)
}

/** Parses a CSS style string value. The given index must refer to a " or '. */
private fun string(string: String, index: Int = 0, includeQuotes: Boolean = false): String {
    val str = reString.matchAt(string, index)?.value
    if (str === null) throw IllegalArgumentException("string missing closing quote")
    return if (includeQuotes) str else str.substring(1, str.length - 1) // remove quotes
}

private fun cleanupValue(value: String): String {
    val output = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        when (val ch = value[index]) {
            '\\' -> { // escaped character
                output.append(value.substring(index, index + 2))
                index += 1
            }
            '\'', '"' -> { // quoted string
                val s = string(value, index, true)
                output.append(s)
                index += s.length - 1
            }
            '/' -> if (value[index+1] == '*') index = comment(value, index) - 1 else output.append(ch)
            ' ' -> if (value[index+1] != ' ') output.append(ch) // condense whitespace
            else -> output.append(ch)
        }
        index += 1
    }
    return output.toString().trim()
}

/** Decode CSS escape sequences in a string. */
private fun decodeEscape(string: String) =
    reEscape.replace(string) {
        val s = it.value.substring(1)
        if (s.length > 1 || s[0] in "0123456789abcdefABCDEF") {
            val x = s.trim().toInt(16)
            if (Character.charCount(x) == 1) x.toChar().toString()
            else String(Character.toChars(x))
        } else s
    }

/**
 * Skips a comment, starting at the given start index. The comment must begin
 * at start to be detected. Returns a value >= start and <= string.length.
 */
private fun comment(string: String, start: Int): Int {
    if (start >= string.length-1 || string.substring(start, start + 2) != "/*") { return start }
    val end = string.indexOf("*/", start + 2)
    if (end == -1) { throw IllegalArgumentException("end of comment missing") }
    return end + 2
}

/**
 * Skips all whitespace and comments starting at the given start index.
 * Returns a value >= start and <= string.length.
 */
private fun comments(string: String, start: Int): Int {
    var last = whitespace(string, start)
    var index = whitespace(string, comment(string, last))
    while (index != last) {
        last = index
        index = whitespace(string, comment(string, last))
    }
    return index
}

/**
 * Skips all whitespace starting at the given start index.
 * Returns a value >= start and <= string.length.
 */
private fun whitespace(string: String, start: Int): Int {
    for (index in start until string.length) {
        if (!string[index].isWhitespace()) { return index }
    }
    return string.length
}


//////////////////// General Value Parsing Utilities ////////////////////

internal const val RE_NUM_CORE = "(?:\\d*\\.?\\d+|\\d+\\.)"
internal const val RE_NUM = "($RE_NUM_CORE)"
internal const val RE_PERC_CORE = "(?:$RE_NUM_CORE%)"
internal const val RE_PERC = "($RE_PERC_CORE)"

internal val reNum = Regex(RE_NUM_CORE)
internal val rePerc = Regex(RE_PERC_CORE)
internal val reWhitespace = Regex("\\s+")

internal val standardOpts = setOf("inherit", "initial", "revert", "revert-layer", "unset")

// General units
/** Split the unit from a number. Assumes it is valid. */
internal fun splitUnit(value: String): Pair<Float, String> {
    val num = value.takeWhile { it in ".-0123456789" }
    return num.toFloat() to value.substring(num.length)
}

/** Split the unit from a number. Returns null if invalid. */
internal fun maybeSplitUnit(value: String, unitsAllowed: Set<String>): Pair<Float, String>? {
    val num = reNum.matchAt(value, 0)?.value ?: return null
    val unit = value.substring(num.length)
    return if (unit in unitsAllowed) num.toFloat() to unit else null
}

// Percentages
/** Parses a CSS percentage, returning 1.0f for 100%. The percentage is assumed to be valid with a trailing %. */
internal fun parsePerc(value: String) = value.removeSuffix("%").toFloat() / 100f

/** Parses a CSS percentage, returning 1.0f for 100%. Returns null if invalid. */
internal fun maybeParsePerc(value: String): Float? {
    return parsePerc(rePerc.matchEntire(value)?.value ?: return null)
}

// Angles
internal const val RE_ANGLE = "($RE_NUM_CORE(?:deg|grad|rad|turn))"
internal const val RE_NUM_OR_ANGLE_CORE = "$RE_NUM_CORE(?:deg|grad|rad|turn)?"
internal const val RE_NUM_OR_ANGLE = "($RE_NUM_OR_ANGLE_CORE)"
internal val angleUnits = mapOf(
    "deg" to 1f,
    "grad" to 360f / 400f,
    "rad" to 360f / (2f * Math.PI).toFloat(),
    "turn" to 360f,
)

/**
 * Parses a CSS angle, returning a value in degrees.
 * This assumes that the unit is valid.
 */
internal fun parseAngle(x: String): Float {
    val (num, unit) = splitUnit(x)
    return num * (angleUnits[unit] ?: 1f)
}

/**
 * Parses a CSS angle, returning a value in degrees.
 * If invalid, returns null.
 */
internal fun maybeParseAngle(x: String): Float? {
    val (num, unit) = maybeSplitUnit(x, angleUnits.keys) ?: return null
    return num * angleUnits.getValue(unit)
}

internal const val deg2rad = (PI / 180.0).toFloat()
