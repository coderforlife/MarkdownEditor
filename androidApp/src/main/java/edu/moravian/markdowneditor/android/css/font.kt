package edu.moravian.markdowneditor.android.css

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import kotlin.math.tan

fun parseFontWeight(value: String, base: FontWeight = FontWeight.Normal): FontWeight? {
    return when (val weight = value.lowercase().trim()) {
        in standardOpts -> warn("unsupported CSS standard operation: $value") // TODO
        "normal" -> FontWeight.Normal
        "bold" -> FontWeight.Bold
        "lighter" -> FontWeight(if (base.weight < 350) 100 else if (base.weight < 750) 400 else 700)
        "bolder" -> FontWeight(if (base.weight < 350) 400 else if (base.weight < 550) 700 else 900)
        else -> FontWeight(weight.toIntOrNull() ?: return warn("unsupported CSS font-weight: $value"))
    }
}

fun parseFontStyle(value: String): SpanStyle? {
    return when (val fs = value.lowercase().trim()) {
        in standardOpts -> warn("unsupported CSS standard operation: $value") // TODO
        "normal" -> SpanStyle(fontStyle = FontStyle.Normal)
        "italic", "oblique" -> SpanStyle(fontStyle = FontStyle.Italic) // TODO: distinguish between italic and oblique?
        else -> {
            if (!fs.startsWith("oblique ")) return warn("unsupported CSS font-style: $value")
            val angle = maybeParseAngle(fs.substring(8).trim()) ?: return warn("bad angle in CSS font-style: $value")
            if (angle !in -90f .. 90f) return warn("angle not in range -90 to 90 within CSS font-style: $value")
            // TODO: if current font-synthesis does not include italic, do nothing
            // TODO: merge with current textGeometricTransform
            SpanStyle(textGeometricTransform = TextGeometricTransform(skewX=-tan(angle*deg2rad)))
        }
    }
}

private val fontStretchOpts = mapOf(
    "ultra-condensed" to 0.5f,
    "extra-condensed" to 0.625f,
    "condensed" to 0.75f,
    "semi-condensed" to 0.875f,
    "normal" to 1.0f,
    "semi-expanded" to 0.1125f,
    "expanded" to 0.125f,
    "extra-expanded" to 0.15f,
    "ultra-expanded" to 0.2f,
)

fun parseFontStretch(value: String): TextGeometricTransform? {
    val stretch = value.lowercase().trim()
    if (stretch in standardOpts) return warn("unsupported CSS standard operation: $value") // TODO
    val perc = fontStretchOpts[stretch] ?: maybeParsePerc(stretch) ?: return warn("unsupported CSS font-stretch: $value")
    val percChecked = perc.takeIf { it in 0.5f..2f } ?: return warn("percent not in range 50% to 200% within CSS font-stretch: $value")
    // TODO: merge with current textGeometricTransform
    return TextGeometricTransform(scaleX = percChecked)
}

private val fontSynthesisOpts = mapOf(
    "weight" to FontSynthesis.Weight,
    "style" to FontSynthesis.Style,
    // TODO: "small-caps" to FontSynthesis.SmallCaps,
)

fun parseFontSynthesis(value: String): FontSynthesis? {
    val synthesis = value.lowercase().trim()
    if (synthesis in standardOpts) return warn("unsupported CSS standard operation: $value") // TODO
    if (synthesis == "none") return FontSynthesis.None
    val options = synthesis.split(reWhitespace).mapNotNull { fontSynthesisOpts[it] ?: warn("unsupported CSS font-synthesis: $value") }
    return if (options.size == fontSynthesisOpts.size) FontSynthesis.All else options[0]
}
