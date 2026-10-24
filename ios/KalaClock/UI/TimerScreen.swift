import SwiftUI

struct TimerScreen: View {
    let mode: Mode
    let settings: Settings
    let dim: Double
    @ObservedObject var timer: TimerController
    @State private var countdownMin: Int64 = 10
    @State private var now = monotonicNowMs()
    private let frame = Timer.publish(every: 0.2, on: .main, in: .common).autoconnect()

    private let digitGray = Color(red: 0xB0 / 255, green: 0xB0 / 255, blue: 0xB0 / 255)
    private var fg: Color { Color(red: 0xB0 / 255, green: 0xB0 / 255, blue: 0xB0 / 255).opacity(dim) }
    private var state: TimerState { timer.state }

    var body: some View {
        GeometryReader { geo in
            Group {
                if geo.size.width > geo.size.height {
                    // Landscape: controls in a left column so the cards keep the full height.
                    HStack(spacing: 0) {
                        VStack(spacing: 16) {
                            phaseLabel
                            adjusters(vertical: true)
                            buttons(vertical: true)
                        }
                        .frame(width: 132).padding(.leading, 16)
                        FlipGroups(groups: groups, filled: settings.filledCards, color: digitGray, dim: dim)
                    }
                } else {
                    VStack(spacing: 0) {
                        phaseLabel
                        adjusters(vertical: false)
                        FlipGroups(groups: groups, filled: settings.filledCards, color: digitGray, dim: dim)
                        buttons(vertical: false).padding(16)
                    }
                }
            }
        }
        .onAppear { syncEngine() }
        .onChange(of: mode) { _ in syncEngine() }
        .onChange(of: countdownMin) { _ in syncEngine() }
        .onChange(of: settings.pomodoro) { _ in syncEngine() }
        .onReceive(frame) { _ in
            now = monotonicNowMs()
            timer.tick()
        }
    }

    // Switch engine state when the mode tab changes (only if idle).
    private func syncEngine() {
        let cur = timer.state
        if cur.running { return }
        let wrongMode = cur.mode != mode
        let staleCountdown = mode == .countdown && cur.targetMs != countdownMin * 60_000 && cur.accumulatedMs == 0
        guard wrongMode || staleCountdown else { return }
        switch mode {
        case .stopwatch: timer.set(TimerEngine.stopwatch())
        case .countdown: timer.set(TimerEngine.countdown(durationMs: countdownMin * 60_000))
        case .pomodoro: timer.set(TimerEngine.pomodoro(settings.pomodoro))
        case .clock: break
        }
    }

    private var groups: [FlipGroup] {
        let total = Int(state.displayMs(now) / 1000)
        let h = total / 3600
        let f = { (v: Int) in FlipGroup(text: String(format: "%02d", v)) }
        return h > 0 ? [f(h), f((total % 3600) / 60), f(total % 60)] : [f(total / 60), f(total % 60)]
    }

    @ViewBuilder private var phaseLabel: some View {
        if mode == .pomodoro {
            let label: String = {
                switch state.phase {
                case .focus: return "Focus"
                case .shortBreak: return "Break"
                case .longBreak: return "Long break"
                }
            }()
            Text("\(label) · \(state.completedFocus) done")
                .font(.system(size: 18)).foregroundColor(fg).padding(8)
        }
    }

    @ViewBuilder private func adjusters(vertical: Bool) -> some View {
        if mode == .countdown && !state.running {
            let items = Group {
                Button("−1m") { countdownMin = max(1, countdownMin - 1) }
                Button("+1m") { countdownMin += 1 }
                Button("+5m") { countdownMin += 5 }
            }.buttonStyle(.bordered).tint(.white)
            if vertical { VStack(spacing: 8) { items } } else { HStack(spacing: 8) { items }.padding(8) }
        }
    }

    @ViewBuilder private func buttons(vertical: Bool) -> some View {
        let items = Group {
            if state.finished && mode == .pomodoro {
                Button("Next") { timer.nextPhase(settings.pomodoro) }.buttonStyle(.borderedProminent)
            } else {
                Button(state.running ? "Pause" : "Start") {
                    if state.running { timer.pause() } else { timer.start() }
                }
                .buttonStyle(.borderedProminent).tint(Color(white: 0.2))
            }
            Button("Reset") { timer.reset() }.buttonStyle(.bordered).tint(.white)
        }
        if vertical { VStack(spacing: 12) { items } } else { HStack(spacing: 12) { items } }
    }
}
