package edu.moravian.markdowneditor.android.render

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

// Based on Compose's ClickableText but supports long press and uses Text()
// instead of BasicText().

@Composable
fun ClickableText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onLongPress: (Int) -> Unit = {},
    onClick: (Int) -> Unit
) {
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    fun getOffset(positionOffset: Offset): Int? = layoutResult.value
        ?.multiParagraph
        ?.takeIf { it.containsWithinBounds(positionOffset) }
        ?.getOffsetForPosition(positionOffset)

    val pointerInputModifier = Modifier.pointerInput(onClick, onLongPress) {
        detectTapGestures(
            onLongPress = { pos -> getOffset(pos)?.let(onLongPress) },
            onTap = { pos -> getOffset(pos)?.let(onClick) },
        )
    }

    Text(
        text = text,
        modifier = modifier.then(pointerInputModifier),
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = {
            layoutResult.value = it
            onTextLayout(it)
        }
    )
}

private fun MultiParagraph.containsWithinBounds(positionOffset: Offset): Boolean =
    positionOffset.let { (x, y) -> x > 0 && y >= 0 && x <= width && y <= height }
