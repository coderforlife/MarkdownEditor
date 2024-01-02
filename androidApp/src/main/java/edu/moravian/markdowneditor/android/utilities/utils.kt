@file:Suppress("NOTHING_TO_INLINE")

package edu.moravian.markdowneditor.android.utilities


/** Brackets match a string if they are the first and last characters. */
private inline fun String.hasBrackets(left: Char, right: Char = left) =
    first() == left && last() == right

/** Remove first and last characters from a string then trim. */
private inline fun String.trimBrackets() = substring(1, length-1).trim()

/** Remove the brackets from a string if they are the first and last characters. */
fun String.removeBrackets(left: Char, right: Char = left) =
    if (hasBrackets(left, right)) trimBrackets() else this

/** Remove a single set of brackets from a string if they are the first and last characters. */
fun String.removeSingleBrackets(brackets: List<Pair<Char, Char>>) =
    brackets.firstOrNull { (l, r) -> hasBrackets(l, r) }?.let { trimBrackets() } ?: this

private val randomChars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

/** Create a random string of letters and digits. */
fun randomId(n: Int = 16) = (1..n).map { randomChars.random() }.joinToString("")


/**
 * Trim the indents of a string and report the amount of indent that was trimmed.
 * This is equivalent to [String.trimIndent] except that it also reports the
 * amount of indent trimmed.
 */
fun String.trimAndReportIndent(): Pair<String, Int> {
    val lines = lines().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
    val indent = lines.filter(String::isNotBlank)
        .minOfOrNull { indexOfFirst { !it.isWhitespace() }.let { if (it == -1) length else it } }
        ?: 0
    return if (indent == 0) {
        lines.joinTo(StringBuilder(length), "\n")
    } else {
        lines.joinTo(StringBuilder(length - indent*lines.size), "\n") { it.drop(indent) }
    }.toString() to indent
}


@Suppress("UNCHECKED_CAST")
fun <T: Any> Array<out Pair<String, T?>>.toNonNullMap(): Map<String, T> =
    filter { it.second != null }.toMap() as Map<String, T>

@Suppress("UNCHECKED_CAST")
fun <T: Any> mapOfNonNull(vararg pairs: Pair<String, T?>): Map<String, T> =
    pairs.filter { it.second != null }.toMap() as Map<String, T>
