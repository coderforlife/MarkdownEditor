package edu.moravian.markdowneditor.android.css

import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import kotlin.math.min


private const val COMMA = "\\s*,\\s*"
private const val SLASH = "\\s*/\\s*"
private const val RE_NUM_OR_NONE = "($RE_NUM_CORE|none)"
private const val RE_PERC_OR_NONE = "($RE_PERC_CORE|none)"
private const val RE_ALPHA = "($RE_NUM_CORE%?)"  // number or percentage
private const val RE_ALPHA_OR_NONE = "($RE_NUM_CORE%?|none)"  // number or percentage or none
private const val RE_HUE = RE_NUM_OR_ANGLE
private const val RE_HUE_OR_NONE = "($RE_NUM_OR_ANGLE_CORE|none)"

@Suppress("RegExpRedundantEscape")
private val reHex = Regex("\\#([A-Fa-f0-9]{3}|[A-Fa-f0-9]{4}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})")
private val reRGB = Regex("((?:rgb|hsl)a?)\\(([^)]*)\\)")
private val reRGBNum = Regex("$RE_NUM$COMMA$RE_NUM$COMMA$RE_NUM(?:$COMMA$RE_ALPHA)?")
private val reRGBPercent = Regex("$RE_PERC$COMMA$RE_PERC$COMMA$RE_PERC(?:$COMMA$RE_ALPHA)?")
private val reRGBNum2 = Regex("$RE_NUM_OR_NONE\\s+$RE_NUM_OR_NONE\\s+$RE_NUM_OR_NONE(?:$SLASH$RE_ALPHA_OR_NONE)?")
private val reRGBPercent2 = Regex("$RE_PERC_OR_NONE\\s+$RE_PERC_OR_NONE\\s+$RE_PERC_OR_NONE(?:$SLASH$RE_ALPHA_OR_NONE)?")

private val reHSL = Regex("$RE_HUE$COMMA$RE_PERC$COMMA$RE_PERC(?:$COMMA$RE_ALPHA)?")
private val reHSL2 = Regex("$RE_HUE_OR_NONE\\s+$RE_PERC_OR_NONE\\s+$RE_PERC_OR_NONE(?:$SLASH$RE_ALPHA_OR_NONE)?")

/**
 * Parse a CSS color value. Supports all colors names, hex colors, rgb, rgba,
 * hsl, and hsla. Invalid/unknown colors return null.
 *
 * Does not support inherit, initial, revert, revert-layer, unset, currentcolor,
 * color-mix(), color(), hwb(), lab(), lch(), oklab(), oklch(), ...
 */
fun parseColorValue(value: String): Color? {
    val style = value.lowercase().trim()
    if (style in Colors.lookup) return Color(Colors.lookup.getValue(style) or 0xFF000000.toInt())

    if (reHex.matches(style)) { // hex color
        val hex = reHex.matchEntire(style)!!.groupValues[1]
        return if (hex.length == 3 || hex.length == 4) Color( // #f00 and #f00a
            hex.substring(0, 1).toInt(16) * 17,
            hex.substring(1, 2).toInt(16) * 17,
            hex.substring(2, 3).toInt(16) * 17,
            if (hex.length == 3) 255 else hex.substring(3, 4).toInt(16) * 17)
        else Color( // #ff0000 and #ff0000aa
            hex.substring(0, 2).toInt(16),
            hex.substring(2, 4).toInt(16),
            hex.substring(4, 6).toInt(16),
            if (hex.length == 6) 255 else hex.substring(6, 8).toInt(16))
    } else if (reRGB.matches(style)) { // rgb / hsl
        // TODO: support arbitrary colorspaces from CSS (hwb, lab, lch, oklab, oklch, and generic)
        //  srgb, srgb-linear, display-p3, a98-rgb, prophoto-rgb, rec2020, xyz, xyz-d50, and xyz-d65
        //  Compose names these: Srgb, LinearSrgb, DisplayP3, CieXyz, CieLab, Oklab
        val (name, components) = reRGB.matchEntire(style)!!.destructured
        when (name) {
            "rgb", "rgba" -> {
                val rgbNumMatch = reRGBNum.matchEntire(components) ?: reRGBNum2.matchEntire(components)
                val rgbPercMatch = rgbNumMatch ?: reRGBPercent.matchEntire(components) ?: reRGBPercent2.matchEntire(components)
                if (rgbNumMatch !== null) { // rgb(255,0,0) rgba(255,0,0,0.5) rgb(255 0 0) rgba(255 0 0 / 0.5)
                    val (r, g, b, a) = rgbNumMatch.destructured
                    return Color(
                        min(1f, r.toFloat() / 255f),
                        min(1f, g.toFloat() / 255f),
                        min(1f, b.toFloat() / 255f),
                        parseAlpha(a),
                    )
                } else if (rgbPercMatch !== null) { // rgb(100%,0%,0%) rgba(100%,0%,0%,0.5) rgb(100% 0% 0%) rgba(100% 0% 0% / 0.5)
                    val (r, g, b, a) = rgbPercMatch.destructured
                    return Color(parseColorPerc(r), parseColorPerc(g), parseColorPerc(b), parseAlpha(a))
                }
            }
            "hsl", "hsla" -> {
                val rgbHSLMatch = reHSL.matchEntire(components) ?: reHSL2.matchEntire(components)
                if (rgbHSLMatch !== null) { // hsl(120,50%,50%) hsla(120,50%,50%,0.5) hsl(120 50% 50%) hsla(120 50% 50% / 0.5)
                    val (h, s, l, a) = rgbHSLMatch.destructured
                    return Color.hsl(parseHue(h), parseColorPerc(s), parseColorPerc(l), parseAlpha(a))
                }
            }
        }
    }

    return warn("ignoring unsupported CSS color: $value")
}

/**
 * Parse the value of the color: property. Supports everything that cssColor()
 * along with additional keywords:
 *   - initial, revert, revert-layer: returns initial (default black)
 *   - inherit, unset, currentcolor: returns inherit (default Color.Unspecified)
 * Invalid/unknown colors return inherit.
 */
fun parseColor(
    value: String,
    initial: Color = Color.Black,
    inherit: Color = Color.Unspecified,
): Color {
    val style = value.lowercase().trim()
    if (style in setOf("initial", "revert", "revert-layer")) { return initial }
    if (style in setOf("inherit", "currentcolor", "unset")) { return inherit }
    return parseColorValue(style) ?: inherit
}

/**
 * Parse the value of the background-color: property. Supports everything that
 * cssColor() along with additional keywords:
 *   - initial, revert, revert-layer: returns initial (default transparent)
 *   - inherit, unset: returns inherit (default Color.Unspecified)
 *   - currentcolor: returns currentcolor (default Color.Unspecified)
 * Invalid/unknown colors return inherit.
 */
fun parseBackgroundColor(
    value: String,
    initial: Color = Color.Transparent,
    inherit: Color = Color.Unspecified,
    currentcolor: Color = Color.Unspecified,
): Color {
    val style = value.lowercase().trim()
    if (style in setOf("initial", "revert", "revert-layer")) { return initial }
    if (style in setOf("inherit", "unset")) { return inherit }
    if (style == "currentcolor") { return currentcolor }
    return parseColorValue(style) ?: inherit
}

private fun parseHue(x: String) = if (x == "none") 0f else parseAngle(x) % 360f
private fun parseColorPerc(x: String) = if (x == "none") 0f else parsePerc(x).coerceIn(0f, 1f)
private fun parseAlpha(a: String) =
    if (a.isEmpty()) 1f
    else if (a == "none") 0f
    else if (a.last() == '%') parsePerc(a).coerceIn(0f, 1f)
    else a.toFloat().coerceIn(0f, 1f)

enum class Colors(val hex: Int) {
    AliceBlue(0xF0F8FF),
    AntiqueWhite(0xFAEBD7),
    Aqua(0x00FFFF),
    Aquamarine(0x7FFFD4),
    Azure(0xF0FFFF),
    Beige(0xF5F5DC),
    Bisque(0xFFE4C4),
    Black(0x000000),
    BlanchedAlmond(0xFFEBCD),
    Blue(0x0000FF),
    BlueViolet(0x8A2BE2),
    Brown(0xA52A2A),
    Burlywood(0xDEB887),
    CadetBlue(0x5F9EA0),
    Chartreuse(0x7FFF00),
    Chocolate(0xD2691E),
    Coral(0xFF7F50),
    CornflowerBlue(0x6495ED),
    Cornsilk(0xFFF8DC),
    Crimson(0xDC143C),
    Cyan(0x00FFFF),
    DarkBlue(0x00008B),
    DarkCyan(0x008B8B),
    DarkGoldenrod(0xB8860B),
    DarkGray(0xA9A9A9),
    DarkGreen(0x006400),
    DarkGrey(0xA9A9A9),
    DarkKhaki(0xBDB76B),
    DarkMagenta(0x8B008B),
    DarkOliveGreen(0x556B2F),
    DarkOrange(0xFF8C00),
    DarkOrchid(0x9932CC),
    DarkRed(0x8B0000),
    DarkSalmon(0xE9967A),
    DarkSeaGreen(0x8FBC8F),
    DarkSlateBlue(0x483D8B),
    DarkSlateGray(0x2F4F4F),
    DarkSlateGrey(0x2F4F4F),
    DarkTurquoise(0x00CED1),
    DarkViolet(0x9400D3),
    DeepPink(0xFF1493),
    DeepSkyBlue(0x00BFFF),
    DimGray(0x696969),
    DimGrey(0x696969),
    DodgerBlue(0x1E90FF),
    Firebrick(0xB22222),
    FloralWhite(0xFFFAF0),
    ForestGreen(0x228B22),
    Fuchsia(0xFF00FF),
    Gainsboro(0xDCDCDC),
    GhostWhite(0xF8F8FF),
    Gold(0xFFD700),
    Goldenrod(0xDAA520),
    Gray(0x808080),
    Green(0x008000),
    GreenYellow(0xADFF2F),
    Grey(0x808080),
    Honeydew(0xF0FFF0),
    HotPink(0xFF69B4),
    IndianRed(0xCD5C5C),
    Indigo(0x4B0082),
    Ivory(0xFFFFF0),
    Khaki(0xF0E68C),
    Lavender(0xE6E6FA),
    LavenderBlush(0xFFF0F5),
    LawnGreen(0x7CFC00),
    LemonChiffon(0xFFFACD),
    LightBlue(0xADD8E6),
    LightCoral(0xF08080),
    LightCyan(0xE0FFFF),
    LightGoldenrodYellow(0xFAFAD2),
    LightGray(0xD3D3D3),
    LightGreen(0x90EE90),
    LightGrey(0xD3D3D3),
    LightPink(0xFFB6C1),
    LightSalmon(0xFFA07A),
    LightSeaGreen(0x20B2AA),
    LightSkyBlue(0x87CEFA),
    LightSlateGray(0x778899),
    LightSlateGrey(0x778899),
    LightSteelBlue(0xB0C4DE),
    LightYellow(0xFFFFE0),
    Lime(0x00FF00),
    LimeGreen(0x32CD32),
    Linen(0xFAF0E6),
    Magenta(0xFF00FF),
    Maroon(0x800000),
    MediumAquamarine(0x66CDAA),
    MediumBlue(0x0000CD),
    MediumOrchid(0xBA55D3),
    MediumPurple(0x9370DB),
    MediumSeaGreen(0x3CB371),
    MediumSlateBlue(0x7B68EE),
    MediumSpringGreen(0x00FA9A),
    MediumTurquoise(0x48D1CC),
    MediumVioletRed(0xC71585),
    MidnightBlue(0x191970),
    MintCream(0xF5FFFA),
    MistyRose(0xFFE4E1),
    Moccasin(0xFFE4B5),
    NavajoWhite(0xFFDEAD),
    Navy(0x000080),
    OldLace(0xFDF5E6),
    Olive(0x808000),
    OliveDrab(0x6B8E23),
    Orange(0xFFA500),
    OrangeRed(0xFF4500),
    Orchid(0xDA70D6),
    PaleGoldenrod(0xEEE8AA),
    PaleGreen(0x98FB98),
    PaleTurquoise(0xAFEEEE),
    PaleVioletRed(0xDB7093),
    PapayaWhip(0xFFEFD5),
    PeachPuff(0xFFDAB9),
    Peru(0xCD853F),
    Pink(0xFFC0CB),
    Plum(0xDDA0DD),
    PowderBlue(0xB0E0E6),
    Purple(0x800080),
    RebeccaPurple(0x663399),
    Red(0xFF0000),
    RosyBrown(0xBC8F8F),
    RoyalBlue(0x4169E1),
    SaddleBrown(0x8B4513),
    Salmon(0xFA8072),
    SandyBrown(0xF4A460),
    SeaGreen(0x2E8B57),
    Seashell(0xFFF5EE),
    Sienna(0xA0522D),
    Silver(0xC0C0C0),
    SkyBlue(0x87CEEB),
    SlateBlue(0x6A5ACD),
    SlateGray(0x708090),
    SlateGrey(0x708090),
    Snow(0xFFFAFA),
    SpringGreen(0x00FF7F),
    SteelBlue(0x4682B4),
    Tan(0xD2B48C),
    Teal(0x008080),
    Thistle(0xD8BFD8),
    Tomato(0xFF6347),
    Turquoise(0x40E0D0),
    Violet(0xEE82EE),
    Wheat(0xF5DEB3),
    White(0xFFFFFF),
    WhiteSmoke(0xF5F5F5),
    Yellow(0xFFFF00),
    YellowGreen(0x9ACD32);

    val color: Color; get() = Color(this.hex)

    companion object {
        val lookup = values().associate { it.name.lowercase() to it.hex }
    }
}
