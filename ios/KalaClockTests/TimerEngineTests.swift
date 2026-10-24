import XCTest
@testable import KalaClock

final class TimerEngineTests: XCTestCase {
    private let cfg = PomodoroConfig(focusMin: 25, breakMin: 5, longBreakMin: 15)

    func testStopwatchCountsFromTimestamps() {
        var s = TimerEngine.start(TimerEngine.stopwatch(), now: 1_000)
        XCTAssertEqual(s.elapsedMs(5_000), 4_000)
        s = TimerEngine.pause(s, now: 5_000)
        XCTAssertEqual(s.elapsedMs(99_000), 4_000)
        s = TimerEngine.start(s, now: 100_000)
        XCTAssertEqual(s.elapsedMs(102_000), 6_000)
    }

    func testCountdownFinishes() {
        var s = TimerEngine.start(TimerEngine.countdown(durationMs: 10_000), now: 0)
        XCTAssertEqual(s.displayMs(6_000), 4_000)
        s = TimerEngine.tick(s, now: 10_000)
        XCTAssertTrue(s.finished)
        XCTAssertFalse(s.running)
        XCTAssertEqual(s.displayMs(20_000), 0)
    }

    func testPomodoroLongBreakAfterFour() {
        var s = TimerEngine.pomodoro(cfg)
        for _ in 0..<3 {
            s = TimerEngine.nextPhase(s, cfg: cfg)
            XCTAssertEqual(s.phase, .shortBreak)
            s = TimerEngine.nextPhase(s, cfg: cfg)
            XCTAssertEqual(s.phase, .focus)
        }
        s = TimerEngine.nextPhase(s, cfg: cfg)
        XCTAssertEqual(s.phase, .longBreak)
        XCTAssertEqual(s.targetMs, 15 * 60_000)
    }
}
