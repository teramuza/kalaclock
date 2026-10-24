package id.tera.kalaclock.domain

enum class Mode { CLOCK, STOPWATCH, COUNTDOWN, POMODORO }

enum class PomodoroPhase { FOCUS, BREAK, LONG_BREAK }

/**
 * Pure timer state. Time is derived from monotonic timestamps (elapsedRealtime),
 * never from tick counting, so it does not drift.
 */
data class TimerState(
    val mode: Mode = Mode.STOPWATCH,
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val accumulatedMs: Long = 0L,
    val targetMs: Long = 0L,
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val completedFocus: Int = 0,
    val finished: Boolean = false,
) {
    fun elapsedMs(now: Long): Long = accumulatedMs + if (running) now - startedAt else 0L

    /** Value shown on the clock face: elapsed for stopwatch, remaining otherwise. */
    fun displayMs(now: Long): Long = when (mode) {
        Mode.STOPWATCH -> elapsedMs(now)
        else -> (targetMs - elapsedMs(now)).coerceAtLeast(0L)
    }

    fun remainingMs(now: Long): Long = (targetMs - elapsedMs(now)).coerceAtLeast(0L)
}

data class PomodoroConfig(val focusMin: Int, val breakMin: Int, val longBreakMin: Int) {
    fun durationMs(phase: PomodoroPhase): Long = 60_000L * when (phase) {
        PomodoroPhase.FOCUS -> focusMin
        PomodoroPhase.BREAK -> breakMin
        PomodoroPhase.LONG_BREAK -> longBreakMin
    }
}

object TimerEngine {
    const val LONG_BREAK_EVERY = 4

    fun start(s: TimerState, now: Long): TimerState =
        if (s.running || s.finished) s else s.copy(running = true, startedAt = now)

    fun pause(s: TimerState, now: Long): TimerState =
        if (!s.running) s else s.copy(running = false, accumulatedMs = s.elapsedMs(now))

    fun reset(s: TimerState): TimerState =
        s.copy(running = false, startedAt = 0L, accumulatedMs = 0L, finished = false)

    fun stopwatch(): TimerState = TimerState(mode = Mode.STOPWATCH)

    fun countdown(durationMs: Long): TimerState =
        TimerState(mode = Mode.COUNTDOWN, targetMs = durationMs)

    fun pomodoro(cfg: PomodoroConfig): TimerState = TimerState(
        mode = Mode.POMODORO,
        targetMs = cfg.durationMs(PomodoroPhase.FOCUS),
    )

    /** Marks countdown/pomodoro phase as finished when remaining hits zero. */
    fun tick(s: TimerState, now: Long): TimerState =
        if (s.running && s.mode != Mode.STOPWATCH && s.remainingMs(now) == 0L) {
            s.copy(running = false, accumulatedMs = s.targetMs, finished = true)
        } else s

    /** Moves a finished pomodoro to the next phase (not started). */
    fun nextPhase(s: TimerState, cfg: PomodoroConfig): TimerState {
        val (phase, done) = when (s.phase) {
            PomodoroPhase.FOCUS -> {
                val n = s.completedFocus + 1
                (if (n % LONG_BREAK_EVERY == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.BREAK) to n
            }
            else -> PomodoroPhase.FOCUS to s.completedFocus
        }
        return s.copy(
            phase = phase,
            completedFocus = done,
            targetMs = cfg.durationMs(phase),
            accumulatedMs = 0L,
            running = false,
            finished = false,
        )
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
