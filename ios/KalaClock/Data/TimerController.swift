import Foundation
import UserNotifications

/// App-wide timer state. iOS has no foreground service, so the finish alert is a local notification.
final class TimerController: ObservableObject {
    static let shared = TimerController()
    private static let notificationId = "timer.finished"

    @Published private(set) var state = TimerEngine.stopwatch()

    func set(_ s: TimerState) { state = s }

    func start() {
        state = TimerEngine.start(state, now: monotonicNowMs())
        scheduleFinishAlert()
    }

    func pause() {
        state = TimerEngine.pause(state, now: monotonicNowMs())
        cancelFinishAlert()
    }

    func reset() {
        state = TimerEngine.reset(state)
        cancelFinishAlert()
    }

    func tick() {
        let next = TimerEngine.tick(state, now: monotonicNowMs())
        if next != state { state = next }
    }

    func nextPhase(_ cfg: PomodoroConfig) {
        state = TimerEngine.nextPhase(state, cfg: cfg)
    }

    func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    private func scheduleFinishAlert() {
        guard state.mode != .stopwatch, state.running else { return }
        let seconds = Double(state.remainingMs(monotonicNowMs())) / 1000
        guard seconds > 0 else { return }
        let content = UNMutableNotificationContent()
        content.title = "KalaClock"
        content.body = "Time is up"
        content.sound = .defaultCritical
        content.interruptionLevel = .timeSensitive
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: Self.notificationId, content: content, trigger: trigger)
        )
    }

    private func cancelFinishAlert() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [Self.notificationId])
    }
}
