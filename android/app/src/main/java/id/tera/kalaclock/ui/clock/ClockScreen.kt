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
import id.tera.kalaclock.ui.flip.CardColor
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.mutableIntStateOf
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
import id.tera.kalaclock.ui.flip.DigitGray
import id.tera.kalaclock.ui.flip.FlipGroups
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ClockScreen(settings: Settings, dim: Float, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // The face has no seconds, so wake once per minute. Delays do not count time spent asleep,
    // so restart the loop (and re-read the time) every time the app comes back to the foreground.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    LaunchedEffect(resumes) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val fg = Color(0xFFB0B0B0).copy(alpha = dim)
    val hour = if (settings.is24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    val date = if (settings.showDate) now.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) else null
    val weekday = if (settings.showDate) now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())) else null
    val ampm = if (settings.is24h) null else if (now.hour < 12) "AM" else "PM"
    val groups = listOf(
        FlipGroup("%02d".format(hour), topLabel = date, bottomLabel = ampm),
        FlipGroup("%02d".format(now.minute), topLabel = weekday),
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: cards stay centered; battery and now-playing float in the corners.
            FlipGroups(groups, settings.filledCards, DigitGray, Modifier.fillMaxSize(), dim)
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
                    FlipGroups(groups, settings.filledCards, DigitGray, Modifier.fillMaxSize(), dim)
                }
                if (settings.showMedia) {
                    // Bottom-left, with room on the right for the notification / lock icons.
                    Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 140.dp, bottom = 24.dp)) {
                        NowPlayingBar(fg, scale = 0.9f, startAligned = true)
                    }
                }
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
internal fun NowPlayingBar(fg: Color, compact: Boolean = false, scale: Float = 1f, startAligned: Boolean = compact) {
    val np by NowPlayingState.value.collectAsState()
    val n = np ?: return
    Row(
        Modifier.fillMaxWidth().padding(if (startAligned) 0.dp else 16.dp),
        horizontalArrangement = if (startAligned) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val artSize = (if (compact) 40.dp else 56.dp) * scale
        val ctx = LocalContext.current
        val sourceIcon = remember(n.packageName) {
            n.packageName?.let { pkg ->
                runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap() }.getOrNull()
            }
        }
        if (n.art != null || sourceIcon != null) {
            Box(Modifier.size(artSize)) {
                val gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                if (n.art != null) {
                    Image(n.art.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), colorFilter = gray, alpha = fg.alpha * 0.8f)
                } else {
                    Box(Modifier.fillMaxSize().background(CardColor, RoundedCornerShape(8.dp)))
                }
                // Small logo of the player the track comes from, on the artwork's bottom-right corner.
                if (sourceIcon != null) {
                    val badge = (if (compact) 16.dp else 20.dp) * scale
                    Image(
                        sourceIcon, null,
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(4.dp, 4.dp)
                            .size(badge)
                            .background(CardColor, RoundedCornerShape(5.dp))
                            .padding(1.5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        alpha = fg.alpha,
                    )
                }
            }
        }
        Column(Modifier.padding(start = if (compact) 8.dp else 12.dp)) {
            Text(n.title, color = fg, fontSize = ((if (compact) 13f else 16f) * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.artist.isNotBlank()) Text(n.artist, color = fg.copy(alpha = fg.alpha * 0.7f), fontSize = ((if (compact) 11f else 14f) * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Current local time, shown on screens whose main digits are something else (the Pomodoro timer). */
@Composable
internal fun CurrentTimeLabel(is24h: Boolean, fg: Color, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    LaunchedEffect(resumes) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val pattern = if (is24h) "HH:mm" else "h:mm a"
    Text(now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())), color = fg, fontSize = 22.sp)
}
