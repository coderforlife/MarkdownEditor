package edu.moravian.markdowneditor.android.render


import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max
import kotlin.math.min


/** Gets the bounding box for a range of characters in a text layout result */
inline fun TextLayoutResult.getBoundingBox(range: IntRange): Rect? =
    multiParagraph.getBoundingBox(range)

/** Gets the bounding box for a range of characters in a text layout result */
inline fun TextLayoutResult.getBoundingBox(range: AnnotatedString.Range<*>): Rect? =
    multiParagraph.getBoundingBox(range.start until range.end)

/** Gets the bounding box for a range of characters in a multi-paragraph */
fun MultiParagraph.getBoundingBox(range: IntRange): Rect? {
    if (range.first == range.last) { return null }
    // TODO: or return getPathForRange(start, end).getBounds()?
    val lineA = getLineForOffset(range.first)
    val lineB = getLineForOffset(range.last)
    return if (lineA == lineB) getBoundingBox(range.first).union(getBoundingBox(range.last))
    else if (lineA < lineB) getLinesBoundingBox(lineA, lineB)
    else getLinesBoundingBox(lineB, lineA)
}

/** Get the bounding box for a line */
private fun MultiParagraph.getLinesBoundingBox(start: Int, end: Int) =
    (start+1..end).fold(getLineBoundingBox(start)) { r, i -> r.union(getLineBoundingBox(i)) }

/** Get the bounding box for a line */
private fun MultiParagraph.getLineBoundingBox(lineIndex: Int) = Rect(
    getLineLeft(lineIndex), getLineTop(lineIndex),
    getLineRight(lineIndex), getLineBottom(lineIndex),
)

/** Take the rectangle encompassing both this rectangle and the other rectangle */
private fun Rect.union(other: Rect) = Rect(
    min(left, other.left),
    min(top, other.top),
    max(right, other.right),
    max(bottom, other.bottom),
)
