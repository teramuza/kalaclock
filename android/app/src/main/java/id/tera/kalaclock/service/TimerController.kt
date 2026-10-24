package id.tera.kalaclock.service

import android.os.SystemClock
import id.tera.kalaclock.domain.PomodoroConfig
import id.tera.kalaclock.domain.TimerEngine
import id.tera.kalaclock.domain.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide timer state shared by the UI, foreground service and alarm receiver. */
object TimerController {
    private val _state = MutableStateFlow(TimerEngine.stopwatch())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private fun now() = SystemClock.elapsedRealtime()

    fun set(s: TimerState) { _state.value = s }
    fun start() = _state.update { TimerEngine.start(it, now()) }
    fun pause() = _state.update { TimerEngine.pause(it, now()) }
    fun reset() = _state.update { TimerEngine.reset(it) }
    fun tick() = _state.update { TimerEngine.tick(it, now()) }
    fun nextPhase(cfg: PomodoroConfig) = _state.update { TimerEngine.nextPhase(it, cfg) }
}
