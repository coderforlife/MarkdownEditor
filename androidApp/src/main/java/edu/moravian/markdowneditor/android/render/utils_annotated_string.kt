@file:Suppress("NOTHING_TO_INLINE")

package edu.moravian.markdowneditor.android.render

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withAnnotation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


/** Get the first annotation found at the given offset with the given tag, or null. */
inline fun AnnotatedString.annotationAt(tag: String, offset: Int) =
    getStringAnnotations(tag, offset, offset).firstOrNull()

/** Get all annotations found at the given offset with the given tag. */
inline fun AnnotatedString.annotationsAt(tag: String, offset: Int) =
    getStringAnnotations(tag, offset, offset).map { it.item }

/** Get the first URL annotation found at the given offset, or null. */
@OptIn(ExperimentalTextApi::class)
inline fun AnnotatedString.urlAnnotationAt(offset: Int) =
    getUrlAnnotations(offset, offset).firstOrNull()?.item?.url

/** Check if there are any string annotations with the given tag. */
inline fun AnnotatedString.hasStringAnnotations(tag: String) =
    hasStringAnnotations(tag, 0, length)

/** Get all string annotations with the given tag. */
inline fun AnnotatedString.annotations(tag: String) =
    getStringAnnotations(tag, 0, length)

/** Add a string annotation from a JSON convertible object. */
@OptIn(ExperimentalTextApi::class)
inline fun <R : Any> AnnotatedString.Builder.withAnnotation(
    tag: String, attributes: Map<String, String> = emptyMap(),
    crossinline block: AnnotatedString.Builder.() -> R
): R = withAnnotation(tag, if (attributes.isEmpty()) "" else Json.encodeToString(attributes), block)

inline fun <R> AnnotatedString.Builder.withAnnotation(
    tag: String, value: String?,
    crossinline block: AnnotatedString.Builder.() -> R
): R =
    if (value === null) { this.block() }
    else {
        val index = pushStringAnnotation(tag, value)
        try { this.block() } finally { pop(index) }
    }

inline fun <R> AnnotatedString.Builder.withStyle(
    style: SpanStyle?,
    crossinline block: AnnotatedString.Builder.() -> R
): R =
    if (style === null) { this.block() }
    else {
        val index = pushStyle(style)
        try { this.block() } finally { pop(index) }
    }

/** Add a string annotation at a single location. */
inline fun AnnotatedString.Builder.addInstantAnnotation(
    tag: String, annotation: String
) = addStringAnnotation(tag, annotation, length-1, length)

/** Add a string annotation from a JSON convertible object. */
inline fun AnnotatedString.Builder.addAnnotation(
    tag: String, attributes: Map<String, String>, start: Int, end: Int
) = addStringAnnotation(tag, Json.encodeToString(attributes), start, end)

/** Add a string annotation from a JSON convertible object at a single location. */
inline fun AnnotatedString.Builder.addInstantAnnotation(
    tag: String, annotation: Map<String, String>
) = addAnnotation(tag, annotation, length, length)

/**
 * Process all string annotations, generating a new annotated string.
 *
 * The new annotated string will not have any annotations or styles except for
 * those added by the process function.
 */
inline fun AnnotatedString.processStringAnnotations(
    process: AnnotatedString.Builder.(AnnotatedString.Range<String>) -> Unit
) = AnnotatedString.Builder(length).apply {
    append(text)
    getStringAnnotations(0, length).forEach { process(it) }
}.toAnnotatedString()
