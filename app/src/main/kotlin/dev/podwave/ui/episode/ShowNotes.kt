package dev.podwave.ui.episode

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import dev.podwave.ui.common.Html

/**
 * Renders show notes: HTML becomes styled text with working links, and timestamps become links
 * that seek within the episode.
 */
@Composable
fun ShowNotes(
    notes: String,
    durationMs: Long?,
    onTimestampClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val onTimestamp by rememberUpdatedState(onTimestampClick)
    val text = remember(notes, durationMs, linkColor) {
        buildShowNotes(notes, durationMs, linkColor) { onTimestamp(it) }
    }
    SelectionContainer(modifier) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun buildShowNotes(
    notes: String,
    durationMs: Long?,
    linkColor: Color,
    onTimestamp: (Long) -> Unit,
): AnnotatedString {
    val linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    val base = if (Html.looksLikeHtml(notes)) {
        AnnotatedString.fromHtml(notes.trim(), linkStyles = linkStyles)
    } else {
        AnnotatedString(notes.trim())
    }
    val timestamps = Timestamps.find(base.text, maxMs = durationMs)
    if (timestamps.isEmpty()) return base

    return buildAnnotatedString {
        append(base)
        timestamps.forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            // A timestamp inside an existing link (e.g. a YouTube URL) keeps the original link.
            if (base.getLinkAnnotations(start, end).isEmpty()) {
                addLink(
                    LinkAnnotation.Clickable(tag = "t=${match.positionMs}", styles = linkStyles) {
                        onTimestamp(match.positionMs)
                    },
                    start,
                    end,
                )
            }
        }
    }
}
