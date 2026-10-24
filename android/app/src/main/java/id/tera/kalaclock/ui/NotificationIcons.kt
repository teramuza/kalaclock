package id.tera.kalaclock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import id.tera.kalaclock.media.NotificationIconConfig
import id.tera.kalaclock.media.NotificationState
import id.tera.kalaclock.ui.flip.CardColor

/** Icons of messaging/email apps with notifications, newest first, each on a small rounded card. */
@Composable
fun NotificationIcons(dim: Float, modifier: Modifier = Modifier) {
    val packages by NotificationState.packages.collectAsState()
    if (packages.isEmpty()) return
    val ctx = LocalContext.current
    Row(modifier.alpha(dim), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        packages.take(NotificationIconConfig.MAX_ICONS).forEach { pkg ->
            val icon = remember(pkg) {
                runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap() }.getOrNull()
            }
            if (icon != null) {
                Box(
                    Modifier.size(30.dp).background(CardColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
