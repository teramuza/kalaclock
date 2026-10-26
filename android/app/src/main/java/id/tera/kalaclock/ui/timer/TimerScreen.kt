package id.tera.kalaclock.ui.timer

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import id.tera.kalaclock.ui.clock.NowPlayingBar
import id.tera.kalaclock.ui.clock.CurrentTimeLabel
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tera.kalaclock.data.Settings
import id.tera.kalaclock.domain.Mode
import id.tera.kalaclock.domain.PomodoroConfig
import id.tera.kalaclock.domain.PomodoroPhase
import id.tera.kalaclock.domain.TimerEngine
import id.tera.kalaclock.service.TimerController
import id.tera.kalaclock.service.TimerService
import id.tera.kalaclock.ui.flip.FlipGroup
import id.tera.kalaclock.ui.flip.DigitGray
import id.tera.kalaclock.ui.flip.FlipGroups
import kotlinx.coroutines.delay

@Composable
fun TimerScreen(mode: Mode, settings: Settings, dim: Float, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val state by TimerController.state.collectAsState()
    val cfg = PomodoroConfig(settings.focusMin, settings.breakMin, settings.longBreakMin)
    var countdownMin by remember { mutableLongStateOf(10L) }

    // Switch engine state when the mode tab changes (only if idle).
    LaunchedEffect(mode, countdownMin, cfg) {
        val cur = TimerController.state.value
        if (cur.running) return@LaunchedEffect
        if (cur.mode != mode || (mode == Mode.COUNTDOWN && cur.targetMs != countdownMin * 60_000L && cur.accumulatedMs == 0L)) {
            TimerController.set(
                when (mode) {
                    Mode.STOPWATCH -> TimerEngine.stopwatch()
                    Mode.COUNTDOWN -> TimerEngine.countdown(countdownMin * 60_000L)
                    Mode.POMODORO -> TimerEngine.pomodoro(cfg)
                    Mode.CLOCK -> cur
                }
            )
        }
    }

    // UI ticker, independent of the service. Idle when paused; while running it wakes once per
    // displayed second, right when the digits change, instead of polling.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.running) {
        now = SystemClock.elapsedRealtime()
        while (TimerController.state.value.running) {
            val s = TimerController.state.value
            val shown = s.displayMs(now)
            val wait = if (s.mode == Mode.STOPWATCH) 1000 - shown % 1000 else (shown % 1000).let { if (it == 0L) 1000 else it + 1 }
            delay(wait.coerceAtLeast(16))
            now = SystemClock.elapsedRealtime()
            TimerController.tick()
        }
    }

    val ms = state.displayMs(now)
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val groups = if (h > 0) listOf(FlipGroup("%02d".format(h)), FlipGroup("%02d".format((totalSec % 3600) / 60)), FlipGroup("%02d".format(totalSec % 60)))
    else listOf(FlipGroup("%02d".format(totalSec / 60)), FlipGroup("%02d".format(totalSec % 60)))
    val fg = Color(0xFFB0B0B0).copy(alpha = dim)

    val phaseLabel: @Composable () -> Unit = {
        if (mode == Mode.POMODORO) {
            val label = when (state.phase) {
                PomodoroPhase.FOCUS -> "Focus"
                PomodoroPhase.BREAK -> "Break"
                PomodoroPhase.LONG_BREAK -> "Long break"
            }
            Text("$label · ${state.completedFocus} done", color = fg, fontSize = 18.sp, modifier = Modifier.padding(8.dp))
        }
    }
    val adjusters: @Composable (Boolean) -> Unit = { vertical ->
        if (mode == Mode.COUNTDOWN && !state.running) {
            val items: @Composable () -> Unit = {
                OutlinedButton({ countdownMin = (countdownMin - 1).coerceAtLeast(1) }) { Text("−1m") }
                OutlinedButton({ countdownMin += 1 }) { Text("+1m") }
                OutlinedButton({ countdownMin += 5 }) { Text("+5m") }
            }
            if (vertical) Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { items() }
            else Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items() }
        }
    }
    val buttons: @Composable (Boolean) -> Unit = { vertical ->
        val items: @Composable () -> Unit = {
            if (state.finished && mode == Mode.POMODORO) {
                Button({ TimerController.nextPhase(cfg) }) { Text("Next") }
            } else {
                Button(
                    onClick = {
                        if (state.running) TimerController.pause()
                        else { TimerController.start(); TimerService.start(ctx) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.White),
                ) { Text(if (state.running) "Pause" else "Start") }
            }
            OutlinedButton({ TimerController.reset(); TimerService.cancelAlarm(ctx) }) { Text("Reset", color = Color.White) }
        }
        if (vertical) Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { items() }
        else Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items() }
    }

    val pomodoro = mode == Mode.POMODORO

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: controls in a left column so the cards keep the full height.
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.width(132.dp).fillMaxHeight().padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        phaseLabel()
                        adjusters(true)
                        buttons(true)
                    }
                    FlipGroups(groups, settings.filledCards, DigitGray, Modifier.weight(1f).fillMaxHeight(), dim)
                }
                // Pomodoro also shows the time and what is playing, in the corners.
                if (pomodoro) {
                    CurrentTimeLabel(settings.is24h, fg, Modifier.align(Alignment.TopEnd).padding(horizontal = 32.dp, vertical = 16.dp))
                    if (settings.showMedia) {
                        Box(Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 24.dp).widthIn(max = 170.dp)) {
                            NowPlayingBar(fg, compact = true)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pomodoro) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                        CurrentTimeLabel(settings.is24h, fg)
                    }
                }
                phaseLabel()
                adjusters(false)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    FlipGroups(groups, settings.filledCards, DigitGray, Modifier.fillMaxSize(), dim)
                }
                if (pomodoro && settings.showMedia) {
                    Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
                        NowPlayingBar(fg, scale = 0.9f, startAligned = true)
                    }
                }
                buttons(false)
            }
        }
    }
}
