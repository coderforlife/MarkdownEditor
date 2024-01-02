@file:Suppress("NOTHING_TO_INLINE")

package edu.moravian.markdowneditor.android.render

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.FontScalable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp


///** Convert a TextUnit (either sp or em) to dp. */
//@Composable
//fun TextUnit.toDp() = TextUnit.toDp(LocalDensity.current, LocalTextStyle.current.fontSize)

/** Convert a TextUnit (either sp or em) to dp. */
fun TextUnit.toDp(scalable: FontScalable, fontSize: TextUnit) =
    if (isSp) { with (scalable) { this@toDp.toDp() } }
    else if (!isEm) { Dp.Unspecified }
    else {
        val sp = if (fontSize.isSp) fontSize else if (fontSize.isUnspecified) 16.sp else { 16.sp * fontSize.value }
        with (scalable) { (sp * value).toDp() }
    }

/** Resolves the current text size if it is in em units based on the base text size. */
private inline fun TextUnit.resolveEm(base: TextUnit) = if (!isEm) this else base * value

/** Resolves the em text units in the current text style based on the base text size. */
internal fun TextStyle.resolveEm(base: TextStyle) =
    if (fontSize.isEm || letterSpacing.isEm || lineHeight.isEm) {
        val fs = fontSize.resolveEm(base.fontSize)
        copy(
            fontSize = fs,
            letterSpacing = letterSpacing.resolveEm(fs),
            lineHeight = lineHeight.resolveEm(fs)
        )
    } else this

/** Merge the base text style with this text style after resolving em units. */
internal inline fun TextStyle.mergeWithEm(other: TextStyle) = merge(other.resolveEm(this))

// Convenience attributes for zero values
internal val zeroDp = 0.dp
internal val zeroTU = 0.sp
internal inline val Dp.Companion.Zero inline get() = zeroDp
internal inline val TextUnit.Companion.Zero inline get() = zeroTU
