package id.tera.kalaclock

import id.tera.kalaclock.domain.*
import org.junit.Assert.*
import org.junit.Test

class TimerEngineTest {
    private val cfg = PomodoroConfig(25, 5, 15)

    @Test fun stopwatchCountsFromTimestamps() {
        var s = TimerEngine.start(TimerEngine.stopwatch(), 1_000)
        assertEquals(4_000, s.elapsedMs(5_000))
        s = TimerEngine.pause(s, 5_000)
        assertEquals(4_000, s.elapsedMs(99_000))
        s = TimerEngine.start(s, 100_000)
        assertEquals(6_000, s.elapsedMs(102_000))
    }

    @Test fun countdownFinishes() {
        var s = TimerEngine.start(TimerEngine.countdown(10_000), 0)
        assertEquals(4_000, s.displayMs(6_000))
        s = TimerEngine.tick(s, 10_000)
        assertTrue(s.finished)
        assertFalse(s.running)
        assertEquals(0, s.displayMs(20_000))
    }

    @Test fun pomodoroLongBreakAfterFour() {
        var s = TimerEngine.pomodoro(cfg)
        repeat(3) {
            s = TimerEngine.nextPhase(s, cfg)
            assertEquals(PomodoroPhase.BREAK, s.phase)
            s = TimerEngine.nextPhase(s, cfg)
            assertEquals(PomodoroPhase.FOCUS, s.phase)
        }
        s = TimerEngine.nextPhase(s, cfg)
        assertEquals(PomodoroPhase.LONG_BREAK, s.phase)
        assertEquals(15 * 60_000L, s.targetMs)
    }
}
