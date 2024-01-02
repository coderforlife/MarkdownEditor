package edu.moravian.markdowneditor.android.css

import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration

//fun parseVerticalAlign(value: String): BaselineShift? {
//    return when (val v = value.lowercase().trim()) {
//        in standardOpts -> warn("unsupported CSS standard operation: $value") // TODO
//        "baseline" -> BaselineShift.None
//        "sub" -> BaselineShift.Subscript
//        "super" -> BaselineShift.Superscript
//        // TODO: "text-top", "text-bottom", "middle", "top", "bottom"
//        else -> {
//            val length = maybeSplitUnit(v)
//            if (length !== null) {
//                val (num, unit) = length
//                if (unit == "%") BaselineShift(num / 100f)
//                else null // TODO: length units
//            } else warn("unsupported CSS vertical-align: $value")
//        }
//    }
//}

private val textDecorationOpts = mapOf(
    "underline" to TextDecoration.Underline,
    "line-through" to TextDecoration.LineThrough,
    // TODO: "overline" to TextDecoration.Overline,
)

fun parseTextDecoration(value: String): TextDecoration? {
    val decor = value.lowercase().trim()
    if (decor in standardOpts) return warn("unsupported CSS standard operation: $value") // TODO
    if (decor == "none") return TextDecoration.None
    val options = decor.split(reWhitespace).mapNotNull { textDecorationOpts[it] ?: warn("unsupported CSS text-decoration: $value") }
    return TextDecoration.combine(options)
}
