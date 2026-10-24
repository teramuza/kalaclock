import Foundation

enum Mode: String, CaseIterable {
    case clock, stopwatch, countdown, pomodoro

    var title: String { rawValue.prefix(1).uppercased() + rawValue.dropFirst() }
}

enum PomodoroPhase { case focus, shortBreak, longBreak }

/// Pure timer state. Time is derived from monotonic timestamps, never from tick counting,
/// so it does not drift.
struct TimerState: Equatable {
    var mode: Mode = .stopwatch
    var running = false
    var startedAt: Int64 = 0
    var accumulatedMs: Int64 = 0
    var targetMs: Int64 = 0
    var phase: PomodoroPhase = .focus
    var completedFocus = 0
    var finished = false

    func elapsedMs(_ now: Int64) -> Int64 { accumulatedMs + (running ? now - startedAt : 0) }

    func remainingMs(_ now: Int64) -> Int64 { max(0, targetMs - elapsedMs(now)) }

    /// Value shown on the clock face: elapsed for stopwatch, remaining otherwise.
    func displayMs(_ now: Int64) -> Int64 { mode == .stopwatch ? elapsedMs(now) : remainingMs(now) }
}

struct PomodoroConfig: Equatable {
    var focusMin: Int
    var breakMin: Int
    var longBreakMin: Int

    func durationMs(_ phase: PomodoroPhase) -> Int64 {
        let minutes: Int
        switch phase {
        case .focus: minutes = focusMin
        case .shortBreak: minutes = breakMin
        case .longBreak: minutes = longBreakMin
        }
        return Int64(minutes) * 60_000
    }
}

enum TimerEngine {
    static let longBreakEvery = 4

    static func start(_ s: TimerState, now: Int64) -> TimerState {
        guard !s.running, !s.finished else { return s }
        var r = s
        r.running = true
        r.startedAt = now
        return r
    }

    static func pause(_ s: TimerState, now: Int64) -> TimerState {
        guard s.running else { return s }
        var r = s
        r.running = false
        r.accumulatedMs = s.elapsedMs(now)
        return r
    }

    static func reset(_ s: TimerState) -> TimerState {
        var r = s
        r.running = false
        r.startedAt = 0
        r.accumulatedMs = 0
        r.finished = false
        return r
    }

    static func stopwatch() -> TimerState { TimerState(mode: .stopwatch) }

    static func countdown(durationMs: Int64) -> TimerState {
        TimerState(mode: .countdown, targetMs: durationMs)
    }

    static func pomodoro(_ cfg: PomodoroConfig) -> TimerState {
        TimerState(mode: .pomodoro, targetMs: cfg.durationMs(.focus))
    }

    /// Marks countdown/pomodoro phase as finished when remaining hits zero.
    static func tick(_ s: TimerState, now: Int64) -> TimerState {
        guard s.running, s.mode != .stopwatch, s.remainingMs(now) == 0 else { return s }
        var r = s
        r.running = false
        r.accumulatedMs = s.targetMs
        r.finished = true
        return r
    }

    /// Moves a finished pomodoro to the next phase (not started).
    static func nextPhase(_ s: TimerState, cfg: PomodoroConfig) -> TimerState {
        var r = s
        switch s.phase {
        case .focus:
            let n = s.completedFocus + 1
            r.phase = n % longBreakEvery == 0 ? .longBreak : .shortBreak
            r.completedFocus = n
        default:
            r.phase = .focus
        }
        r.targetMs = cfg.durationMs(r.phase)
        r.accumulatedMs = 0
        r.running = false
        r.finished = false
        return r
    }
}

/// Monotonic milliseconds that keep counting while the device sleeps (like Android's elapsedRealtime).
func monotonicNowMs() -> Int64 {
    Int64(clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW) / 1_000_000)
}
