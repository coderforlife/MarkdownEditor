package edu.moravian.markdowneditor.android.css

import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign


private val textAlignToTextAlign = mapOf(
    "left" to TextAlign.Left,
    "right" to TextAlign.Right,
    "center" to TextAlign.Center,
    "start" to TextAlign.Start,
    "end" to TextAlign.End,
    "justify" to TextAlign.Justify,
    "justify-all" to TextAlign.Justify, // TODO
)

fun parseTextAlignToTextAlign(value: String) =
    when (val ta = value.lowercase().trim()) {
        in standardOpts -> warn("unsupported CSS standard operation: $value") // TODO
        in textAlignToTextAlign -> textAlignToTextAlign[ta]
        "match-parent" -> warn("unsupported CSS text-align: $value") // TODO
        else -> {
            // TODO: string or string + keyword
            warn("unsupported CSS text-align: $value")
        }
    }

private val cssTextAlignToAlignment = mapOf(
    "left" to AbsoluteAlignment.Left,
    "right" to AbsoluteAlignment.Right,
    "center" to Alignment.CenterHorizontally,
    "start" to Alignment.Start,
    "end" to Alignment.End,
    "justify" to Alignment.Start, // since this isn't applying to text, justify doesn't really make sense
    "justify-all" to Alignment.Start,
)

fun parseTextAlignToAlignment(value: String) =
    when (val ta = value.lowercase().trim()) {
        in standardOpts -> warn("unsupported CSS standard operation: $value") // TODO
        in cssTextAlignToAlignment -> cssTextAlignToAlignment[ta]
        "match-parent" -> warn("unsupported CSS text-align: $value") // TODO
        else -> {
            // TODO: string or string + keyword
            warn("unsupported CSS text-align: $value")
        }
    }

private val cssVerticalAlignToAlignment = mapOf(
    "top" to Alignment.Top,
    "bottom" to Alignment.Bottom,
    "middle" to Alignment.CenterVertically,
    // TODO: "baseline" to Alignment.Baseline,
    // TODO: "sub" to Alignment.Sub,
    // TODO: "super" to Alignment.Super,
    // TODO: "text-top" to Alignment.TextTop,
    // TODO: "text-bottom" to Alignment.TextBottom,
)

fun parseVerticalAlignToAlignment(value: String): Alignment.Vertical? =
    when (val va = value.lowercase().trim()) {
        in standardOpts -> warn("unsupported CSS standard operation: $value") // TODO
        in cssVerticalAlignToAlignment -> cssVerticalAlignToAlignment[va]
        else -> {
            // TODO: length or percentage
            warn("unsupported CSS vertical-align: $value")
        }
    }
