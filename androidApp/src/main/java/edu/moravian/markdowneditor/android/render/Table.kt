package edu.moravian.markdowneditor.android.render

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import edu.moravian.markdowneditor.android.css.parseTextAlignToAlignment
import edu.moravian.markdowneditor.android.css.parseTextAlignToTextAlign
import edu.moravian.markdowneditor.android.css.parseVerticalAlignToAlignment
import edu.moravian.markdowneditor.android.html.htmlAlignToAlignment
import edu.moravian.markdowneditor.android.html.htmlAlignToTextAlign
import edu.moravian.markdowneditor.android.html.htmlVAlignToAlignment
import edu.moravian.markdowneditor.android.markup.MarkupNode
import edu.moravian.markdowneditor.android.markup.parents
import edu.moravian.markdowneditor.android.markup.styles
import kotlin.math.min

/**
 * A table. This is a composable that can be used to render a table. The node
 * must be a table node (i.e. tag == "table"). The table must be well-formed
 * for predictable results (the table must contain optional thead, tbody, tfoot
 * nodes along with tr nodes which contain td or th nodes). The td and th nodes
 * must be direct children of the tr nodes and must use the [TableCell]
 * composable. The rows should use the [TableRow] composable. The other table
 * elements can be rendered as normal blocks.
 */
@Composable
fun MarkupBlockScope.Table(modifier: Modifier = Modifier) {
    require(node.tag == "table") { "Table must be used on a table node" }

    // Find all of the rows and cells in the table
    val rows = remember(node) { node.children.flatMap { n ->
        when (n.tag) {
            "thead", "tbody", "tfoot" -> n.children.filter { it.tag == "tr" }
            "tr" -> listOf(n)
            else -> emptyList()
        }
    } }
    val cells = remember(node) { rows.flatMap { row ->
        row.children.filter { it.tag == "td" || it.tag == "th" }
    } }
    val table = remember(node) { cells.groupBy { it.parent!! } }

    println(table)

    BoxWithConstraints {
        val maxCellWidth = maxWidth * 0.9f
        Layout(
            content = {
                // Table cell needed for measuring but will NOT be placed here
                cells.forEach { child -> RenderNode(node = child, params = params) }
                // The children to be measured and placed
                node.children.forEach { child -> RenderNode(node = child, params = params) }
            },
            modifier = modifier.horizontalScroll(rememberScrollState()),
        ) {measurables, constraints ->
            // Measure all of the cells
            val cellConstraints = constraints.copy(maxWidth = maxCellWidth.roundToPx())
            val infos = measurables.take(cells.size).mapNotNull {
                measureCell(it, cellConstraints, rows, table)
            }

            // Find the width/height of each column/row
            val heights = infos.groupBy { it.row }.mapValues { (_, cells) -> cells.maxOf { it.height } }
            val widths = infos.groupBy { it.column }.mapValues { (_, cells) -> cells.maxOf { it.width } }

            // Apply the width/height to the cells
            infos.forEach {
                it.height = heights[it.row] ?: -1
                it.width = widths[it.column] ?: -1
            }

            // Now do the actual layout of the children (i.e. the rows)
            val placeables = measurables.takeLast(node.children.size).map { it.measure(constraints) }
            layout(placeables.maxOf { it.width }, placeables.sumOf { it.height }) {
                var y = 0
                placeables.forEach { row ->
                    row.placeRelative(x = 0, y = y)
                    y += row.height
                }
            }
        }
    }
}

@Composable
fun MarkupBlockScope.TableRow(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        node.children.forEach { child ->
            RenderNode(node = child, params = params)
        }
    }
}

/**
 * Render a table cell. Must be used in a [Table] composable.
 */
@Composable
fun MarkupBlockScope.TableCell(modifier: Modifier = Modifier, defaultAlignment: Alignment.Horizontal = AbsoluteAlignment.Left) {
    val align = getTableCellAlign(node, defaultAlignment)
    val valign = getTableCellVerticalAlign(node)
    println("TableCell")

    Box(modifier = modifier.layout { measurable, constraints ->
        val cellData = measurable.parentData as? TableCellInfo
        if (cellData !== null && cellData.width > 0 && cellData.height > 0) {
            // Once the table cell knows it size, we enforce it
            println("good")
            val w = cellData.width
            val h = cellData.height
            val placeable = measurable.measure(constraints.copy(
                maxWidth = min(constraints.maxWidth, w),
                maxHeight = min(constraints.maxHeight, h)
            ))
            layout(width = w, height = h) {
                placeable.placeRelative(
                    align.align(placeable.width, w, layoutDirection),
                    valign.align(placeable.height, h)
                )
            }
        } else {
            // Default processing
            println("fallback $cellData")
            val placeable = measurable.measure(constraints)
            layout(width = placeable.width, height = placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
    ) { BlockOrText() }
}


/**
 * Modifier that marks a composable as a table cell.
 * This has no effect if not a td or th node or the table is malformed.
 * This must be cached in some way to be used by the [Table] and [TableCell]
 * composables.
 */
fun Modifier.tableCell(node: MarkupNode): Modifier {
    val tr = node.parent
    val table = tr?.parents?.firstOrNull { it.tag == "table" }
    return if (node.tag != "td" && node.tag != "th" || tr?.tag != "tr" || table === null) this
    else then(TableCellInfo(node))
}

/**
 * Information about a single table cell. This is used to measure the cell.
 * It is the data used by the [tableCell] modifier.
 */
private data class TableCellInfo(
    val node: MarkupNode,
    var column: Int = -1,
    var row: Int = -1,
    var width: Int = -1,
    var height: Int = -1,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) = this@TableCellInfo
}

/**
 * Measure the size of a cell and return the [TableCellInfo] if it is a valid cell
 * after filling in its row, column, width, and height.
 */
private fun measureCell(
    measurable: Measurable,
    constraints: Constraints,
    rows: List<MarkupNode>,
    table: Map<MarkupNode, List<MarkupNode>>,
) = (measurable.parentData as? TableCellInfo)?.let {
    val tr = it.node.parent
    it.row = rows.indexOf(tr)
    it.column = table[tr]?.indexOf(it.node) ?: -1
    if (it.row == -1 || it.column == -1) null
    else {
        println("setting width/height ${it.row} ${it.column}")
        val placeable = measurable.measure(constraints)
        it.width = placeable.width
        it.height = placeable.height
        it
    }
}

/**
 * Find the value of the align attribute for a table cell. This searches the
 * cell, row, tbody/thead?tfoot, and table nodes for the align attribute.
 */
fun getTableCellTextAlign(node: MarkupNode, default: TextAlign = TextAlign.Left) =
    getAlignValue(node, "align", "text-align", default, htmlAlignToTextAlign, ::parseTextAlignToTextAlign)

/**
 * Find the value of the align attribute for a table cell. This searches the
 * cell, row, tbody/thead/tfoot, and table nodes for the align attribute.
 */
fun getTableCellAlign(node: MarkupNode, default: Alignment.Horizontal = AbsoluteAlignment.Left) =
    // HTML: left, center, right, justify
    // CSS adds: start, end, justify-all, match-parent, a string (for aligning to), and a string + keyword
    getAlignValue(node, "align", "text-align", default, htmlAlignToAlignment, ::parseTextAlignToAlignment)

/**
 * Find the value of the valign attribute for a table cell. This searches the
 * cell, row, tbody/thead/tfoot, and table nodes for the valign attribute.
 */
fun getTableCellVerticalAlign(node: MarkupNode, default: Alignment.Vertical = Alignment.CenterVertically) =
    // HTML: top, middle, bottom, baseline
    // CSS adds: sub, super, text-top, text-bottom, length, and percentage
    getAlignValue(node, "valign", "vertical-align", default, htmlVAlignToAlignment, ::parseVerticalAlignToAlignment)

private val tableTags = setOf("td", "th", "tr", "thead", "tbody", "tfoot", "table")

private fun <T: Any> getAlignValue(
    cell: MarkupNode,
    attribute: String,
    property: String,
    default: T,
    htmlValues: Map<String, T>,
    cssParse: (value: String) -> T?,
): T = if (cell.tag in tableTags) {
    getAlignValue(cell, attribute, property, htmlValues, cssParse) ?:
        if (cell.tag == "table") default
        else getAlignValue(cell.parent!!, attribute, property, default, htmlValues, cssParse)
} else default

private inline fun <T: Any> getAlignValue(
    cell: MarkupNode,
    attribute: String, // HTML
    property: String, // CSS
    htmlValues: Map<String, T>,
    cssParse: (value: String) -> T?,
): T? = cell.styles[property]?.let { cssParse(it) } ?: htmlValues[cell.attributes[attribute]]
