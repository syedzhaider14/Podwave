package dev.podwave.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/** Square cover art with a neutral placeholder while loading or when a feed has none. */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}
