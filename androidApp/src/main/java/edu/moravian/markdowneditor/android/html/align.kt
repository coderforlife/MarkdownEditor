package edu.moravian.markdowneditor.android.html

import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign

val htmlAlignToTextAlign = mapOf(
    "left" to TextAlign.Left,
    "right" to TextAlign.Right,
    "center" to TextAlign.Center,
    "justify" to TextAlign.Justify,
)

val htmlAlignToAlignment = mapOf(
    "left" to AbsoluteAlignment.Left,
    "right" to AbsoluteAlignment.Right,
    "center" to Alignment.CenterHorizontally,
)

val htmlVAlignToAlignment = mapOf(
    "top" to Alignment.Top,
    "bottom" to Alignment.Bottom,
    "middle" to Alignment.CenterVertically,
    // TODO: "baseline" to Alignment.Baseline,
)
