package id.tera.kalaclock.ui.clock

import android.content.Context
import androidx.compose.ui.graphics.Path
import android.content.BroadcastReceiver
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tera.kalaclock.data.Settings
import id.tera.kalaclock.media.NowPlayingState
import id.tera.kalaclock.ui.flip.FlipGroup
import id.tera.kalaclock.ui.flip.FlipGroups
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ClockScreen(settings: Settings, dim: Float, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val fg = Color(0xFFB0B0B0).copy(alpha = dim)
    val hour = if (settings.is24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    val date = if (settings.showDate) now.format(DateTimeFormatter.ofPattern("MM/dd/yy", Locale.getDefault())) else null
    val weekday = if (settings.showDate) now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())) else null
    val ampm = if (settings.is24h) null else if (now.hour < 12) "AM" else "PM"
    val groups = listOf(
        FlipGroup("%02d".format(hour), topLabel = date, bottomLabel = ampm),
        FlipGroup("%02d".format(now.minute), topLabel = weekday),
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: cards stay centered; battery and now-playing float in the corners.
            FlipGroups(groups, settings.filledCards, fg, Modifier.fillMaxSize())
            if (settings.showBattery) {
                Row(Modifier.align(Alignment.TopEnd).padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BatteryIndicator(fg)
                }
            }
            if (settings.showMedia) {
                Box(Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 24.dp).widthIn(max = 170.dp)) {
                    NowPlayingBar(fg, compact = true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (settings.showBattery) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { BatteryIndicator(fg) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    FlipGroups(groups, settings.filledCards, fg, Modifier.fillMaxSize())
                }
                if (settings.showMedia) NowPlayingBar(fg)
            }
        }
    }
}

private val LowBattery = Color(0xFFC62828)

/** System-style battery icon filled proportionally to the level, plus percent. Red when 20% or less. */
@Composable
private fun BatteryIndicator(fg: Color) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf(readBattery(ctx)) }
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { info = parseBattery(i) }
        }
        ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    val pct = info.percent
    val low = pct <= 20 && !info.charging
    val tint = if (low) LowBattery.copy(alpha = fg.alpha.coerceAtLeast(0.6f)) else fg
    Text("$pct%", color = tint, fontSize = 16.sp)
    Canvas(Modifier.padding(start = 6.dp).size(width = 30.dp, height = 14.dp)) {
        val tip = size.width * 0.08f
        val body = size.width - tip
        val stroke = 1.5.dp.toPx()
        drawRoundRect(tint, Offset(stroke / 2, stroke / 2), Size(body - stroke, size.height - stroke), CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        drawRoundRect(tint, Offset(body, size.height * 0.3f), Size(tip, size.height * 0.4f), CornerRadius(1.dp.toPx()))
        val pad = stroke + 1.5.dp.toPx()
        val fillW = (body - pad * 2) * pct.coerceIn(0, 100) / 100f
        if (fillW > 0f) {
            drawRoundRect(tint, Offset(pad, pad), Size(fillW, size.height - pad * 2), CornerRadius(1.5.dp.toPx()))
        }
        if (info.charging) {
            // lightning bolt knocked out of the segments
            val cx = body / 2f
            val h = size.height
            val bolt = Path().apply {
                moveTo(cx + h * 0.10f, h * 0.05f)
                lineTo(cx - h * 0.28f, h * 0.55f)
                lineTo(cx - h * 0.02f, h * 0.55f)
                lineTo(cx - h * 0.10f, h * 0.95f)
                lineTo(cx + h * 0.28f, h * 0.42f)
                lineTo(cx + h * 0.02f, h * 0.42f)
                close()
            }
            drawPath(bolt, Color.Black, style = Stroke(2.dp.toPx()))
            drawPath(bolt, tint)
        }
    }
}

private data class BatteryInfo(val percent: Int, val charging: Boolean)

private fun readBattery(c: Context): BatteryInfo =
    parseBattery(c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))

private fun parseBattery(i: Intent?): BatteryInfo {
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryInfo(if (level < 0) 0 else level * 100 / scale, charging)
}

@Composable
private fun NowPlayingBar(fg: Color, compact: Boolean = false) {
    val np by NowPlayingState.value.collectAsState()
    val n = np ?: return
    Row(
        Modifier.fillMaxWidth().padding(if (compact) 0.dp else 16.dp),
        horizontalArrangement = if (compact) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        n.art?.let {
            val gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            Image(it.asImageBitmap(), null, Modifier.size(if (compact) 40.dp else 56.dp).clip(RoundedCornerShape(8.dp)), colorFilter = gray, alpha = fg.alpha * 0.8f)
        }
        Column(Modifier.padding(start = if (compact) 8.dp else 12.dp)) {
            Text(n.title, color = fg, fontSize = if (compact) 13.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.artist.isNotBlank()) Text(n.artist, color = fg.copy(alpha = fg.alpha * 0.7f), fontSize = if (compact) 11.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
