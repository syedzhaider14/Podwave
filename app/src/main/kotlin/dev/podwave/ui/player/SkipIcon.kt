package dev.podwave.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A circular arrow with the number of seconds inside. Drawn rather than picked from the icon set
 * because the intervals are configurable and the icon set only covers a few of them.
 */
@Composable
fun SkipIcon(seconds: Int, forward: Boolean, contentDescription: String?, size: Dp = 24.dp) {
    val fontSize = with(LocalDensity.current) { (size * 0.3f).toSp() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Replay,
            contentDescription = contentDescription,
            modifier = Modifier.size(size).scale(scaleX = if (forward) -1f else 1f, scaleY = 1f),
        )
        Text(
            text = seconds.toString(),
            style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold, color = LocalContentColor.current),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
