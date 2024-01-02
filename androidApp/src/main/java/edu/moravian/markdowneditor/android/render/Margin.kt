@file:Suppress("NOTHING_TO_INLINE")

package edu.moravian.markdowneditor.android.render

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.ValueElement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.FontScalable
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import edu.moravian.markdowneditor.android.markup.nextSibling
import edu.moravian.markdowneditor.android.markup.prevSibling

///**
// * Apply browser-like margin to a composable, providing for collapse of
// * vertical margins of siblings. Ultimately this becomes a padding value.
// * See https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_box_model/Mastering_margin_collapsing
// */
//fun Modifier.margin(
//    node: MarkupNode,
//    margins: MarginValues
//): Modifier = composed {
//    val pad = padding.toDp()
//    val first = node.isFirstOfParent
//    val last = node.isLastOfParent
//    if (node.parent?.tag == "li" || first && last) { this }
//    else if (first) { this.padding(bottom = pad) }
//    else if (last) { this.padding(top = pad) }
//    else { this.padding(vertical = pad) }
//    Modifier
//}


fun MarkupBlockScope.collapseMargins(): PaddingValues {
    val margins = marginsRaw

    val empty = node.children.isEmpty() // TODO: && node.text.isNullOrEmpty() && !isNotEmptyVertical()
    val prev = node.prevSibling?.scoped()
    val next = node.nextSibling?.scoped()

    // Compute top margin
    val top = if (prev !== null) {
        // If the node has a previous sibling, its top margin is collapsed into the
        // sibling's bottom margin
        Dp.Zero
    } else if (node.parent !== null && !hasTopBorderOrPadding()) {
        // If the node is the first sibling, and the node has no top border or top padding
        // the node's top margin is collapsed into its parent's top margin
        Dp.Zero
    } else if (empty) {
        // Empty nodes collapse top margin into bottom margin
        Dp.Zero
    } else if (node.children.isNotEmpty()) {
        // If the node has children, its top margin is collapsed from the top margin of
        // all the first children
        collapse(firstTopMargins())
    } else {
        // Margin not collapsed
        marginsRaw.top
    }

    // Compute bottom margin
    val bottom = if (next !== null) {
        // If the node has a next sibling, its bottom margin is collapsed from the
        // sibling's top margin (that top margin may be collapsed from its children)
        collapse(next.firstTopMargins() + lastBottomMargins())
    } else if (node.parent !== null && !hasBottomBorderOrPadding()) {
        // If the node is the last child of its parent, and the node has no bottom border or
        // bottom padding the node's bottom margin is collapsed into its parent's bottom margin
        Dp.Zero
    } else if (empty) {
        // Empty nodes collapse bottom margin from top margin
        margins.bottom + margins.top
    } else if (node.children.isNotEmpty()) {
        // If the node has children, its bottom margin is collapsed from the bottom margin of
        // all the last children
        collapse(lastBottomMargins())
    } else {
        // Margin not collapsed
        margins.bottom
    }

    return PaddingValuesWithOverrides(margins, top = top, bottom = bottom)
}

private class DoNotCollapseNode : Modifier.Node()

@SuppressLint("ModifierNodeInspectableProperties")
private object DoNotCollapseModifier : ModifierNodeElement<DoNotCollapseNode>() {
    override fun create() = DoNotCollapseNode()
    override fun update(node: DoNotCollapseNode) { }
    override fun equals(other: Any?) = other is DoNotCollapseModifier
    override fun hashCode() = 1.hashCode()
}

fun Modifier.doNotCollapse(): Modifier = this then DoNotCollapseModifier

private fun MarkupBlockScope.hasBorder() = modifierBase.any { (it as? InspectableValue)?.nameFallback == "border" }
private fun MarkupBlockScope.hasTopPadding() = modifierBase.has("padding", "absolutePadding") {
    (it["vertical"] as? Dp) ?: (it["paddingValues"] as? PaddingValues)?.top ?: (it["top"] as? Dp)
}
private fun MarkupBlockScope.hasTopBorderOrPadding() = hasBorder() || hasTopPadding()
private fun MarkupBlockScope.hasBottomPadding() = modifierBase.has("padding", "absolutePadding") {
    (it["vertical"] as? Dp) ?: (it["paddingValues"] as? PaddingValues)?.bottom ?: (it["bottom"] as? Dp)
}
private fun MarkupBlockScope.hasBottomBorderOrPadding() = hasBorder() || hasBottomPadding()
private fun MarkupBlockScope.hasVerticalPadding() = hasTopPadding() || hasBottomPadding()
private fun MarkupBlockScope.hasHeightSpecified() =
    // TODO: could use LayoutModifierNode.minIntrinsicHeight() or LayoutModifierNode.measure() to get if any of the modifiers force a height
    modifierBase.any { it is DoNotCollapseModifier } ||
    modifierBase.has("height", "size", "heightIn", "sizeIn", "defaultMinSize", "requiredHeight", "requiredSize", "requiredHeightIn", "requiredSizeIn") {
        // TODO: these all seem to only be supported in debug mode (except defaultMinSize)
        (it["height"] as? Dp) ?: (it["min"] as? Dp) ?: (it["minHeight"] as? Dp)
    } || modifierBase.any {
        it is InspectableValue && (it.nameFallback in arrayOf("fillMaxHeight", "fillMaxSize")) &&
            (it.valueOverride as? Float)?.let { value -> value > 0f } ?: false
    }
private fun MarkupBlockScope.isNotEmptyVertical() = hasBorder() || hasVerticalPadding() || hasHeightSpecified()

private fun Modifier.has(vararg names: String, dp: (elems: Map<String, Any?>) -> Dp?) = any {
    it is InspectableValue && it.nameFallback in names &&
            ((it.valueOverride as? Dp) ?: dp(it.inspectableElements.toMap())).isPositive()
}
private inline fun Dp?.isPositive() = this != null && isSpecified && value > 0f
private fun Sequence<ValueElement>.toMap() = associate { it.name to it.value }

/**
 * Gather the top margins of all the first children of a node. This always
 * includes the top margin of the starting node, and then continues to include
 * the top margin of each child until a child with a top border or top padding
 * is encountered.
 */
private fun MarkupBlockScope.firstTopMargins(): List<Dp> {
    val margins = mutableListOf(marginsRaw.top)
    var child = node.children.firstOrNull()
    while (child !== null) {
        with (child.scoped()) {
            if (hasTopBorderOrPadding()) return margins
            marginsRaw.top
        }
        child = child.children.firstOrNull()
    }
    return margins
}

/**
 * Gather the bottom margins of all the last children of a node. This always
 * includes the bottom margin of the starting node, and then continues to
 * include the bottom margin of each child until a child with a bottom border
 * or bottom padding is encountered.
 */
private fun MarkupBlockScope.lastBottomMargins(): List<Dp> {
    val margins = mutableListOf(marginsRaw.bottom)
    var child = node.children.lastOrNull()
    while (child !== null) {
        with (child.scoped()) {
            if (hasBottomBorderOrPadding()) return margins
            marginsRaw.bottom
        }
        child = child.children.lastOrNull()
    }
    return margins
}

/**
 * Size of a collapsed margin is:
 *  * if all are positive: largest of all margins
 *  * if any are negative: sum of the most positive and most negative margin
 *  * if all are negative: most negative margin
 */
private inline fun collapse(values: Collection<Dp>) =
    (values.filter { it > Dp.Zero }.maxOrNull() ?: Dp.Zero) +
    (values.filter { it < Dp.Zero }.minOrNull() ?: Dp.Zero)

/**
 * Describes margins to be applied along the edges inside a box. See the
 * MarginValues factories and Absolute for convenient ways to build MarginValues.
 */
interface MarginValues {
    fun calculateTopMargin(): TextUnit
    fun calculateBottomMargin(): TextUnit
    fun calculateLeftMargin(layoutDirection: LayoutDirection): TextUnit
    fun calculateRightMargin(layoutDirection: LayoutDirection): TextUnit
    fun asPadding(fontScalable: FontScalable, fontSize: TextUnit = 16.sp): PaddingValues

    /** Describes an absolute (RTL unaware) margin to be applied along the edges inside a box. */
    data class Absolute(
        val left: TextUnit = TextUnit.Zero,
        val top: TextUnit = TextUnit.Zero,
        val right: TextUnit = TextUnit.Zero,
        val bottom: TextUnit = TextUnit.Zero
    ) : MarginValues {
        override fun calculateTopMargin() = top
        override fun calculateBottomMargin() = bottom
        override fun calculateRightMargin(layoutDirection: LayoutDirection) = right
        override fun calculateLeftMargin(layoutDirection: LayoutDirection) = left
        override fun asPadding(fontScalable: FontScalable, fontSize: TextUnit) =
            PaddingValues.Absolute(
                left.toDp(fontScalable, fontSize),
                top.toDp(fontScalable, fontSize),
                right.toDp(fontScalable, fontSize),
                bottom.toDp(fontScalable, fontSize),
            )
    }

    companion object { val Zero = Absolute(TextUnit.Zero) }
}

/** Creates a margin of [all] along all 4 edges. */
fun MarginValues(all: TextUnit): MarginValues = MarginValuesImpl(all, all, all, all)

/**
 * Creates a margin of [horizontal] along the left and right edges, and of [vertical]
 * along the top and bottom edges.
 */
fun MarginValues(horizontal: TextUnit = TextUnit.Zero, vertical: TextUnit = TextUnit.Zero): MarginValues =
    MarginValuesImpl(horizontal, vertical, horizontal, vertical)

/**
 * Creates a margin to be applied along the edges inside a box. In LTR contexts [start] will
 * be applied along the left edge and [end] will be applied along the right edge. In RTL contexts,
 * [start] will correspond to the right edge and [end] to the left.
 */
fun MarginValues(
    start: TextUnit = TextUnit.Zero,
    top: TextUnit = TextUnit.Zero,
    end: TextUnit = TextUnit.Zero,
    bottom: TextUnit = TextUnit.Zero,
): MarginValues = MarginValuesImpl(start, top, end, bottom)

private data class MarginValuesImpl(
    val start: TextUnit = TextUnit.Zero,
    val top: TextUnit = TextUnit.Zero,
    val end: TextUnit = TextUnit.Zero,
    val bottom: TextUnit = TextUnit.Zero,
) : MarginValues {
    override fun calculateTopMargin() = top
    override fun calculateBottomMargin() = bottom
    override fun calculateLeftMargin(layoutDirection: LayoutDirection) =
        if (layoutDirection == LayoutDirection.Ltr) start else end
    override fun calculateRightMargin(layoutDirection: LayoutDirection) =
        if (layoutDirection == LayoutDirection.Ltr) end else start
    override fun asPadding(fontScalable: FontScalable, fontSize: TextUnit) =
        PaddingValues(
            start.toDp(fontScalable, fontSize),
            top.toDp(fontScalable, fontSize),
            end.toDp(fontScalable, fontSize),
            bottom.toDp(fontScalable, fontSize)
        )
}

/** Wrap a PaddingValues with new values for top and bottom. */
private data class PaddingValuesWithOverrides(
    val paddingValues: PaddingValues,
    val top: Dp = paddingValues.calculateTopPadding(),
    val bottom: Dp = paddingValues.calculateBottomPadding(),
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        paddingValues.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        paddingValues.calculateRightPadding(layoutDirection)
    override fun calculateTopPadding() = top
    override fun calculateBottomPadding() = bottom
}

// Convenience attributes for PaddingValues
private inline val PaddingValues.top get() = calculateTopPadding()
private inline val PaddingValues.bottom get() = calculateBottomPadding()
