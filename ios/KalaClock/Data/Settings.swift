import Foundation

struct Settings: Equatable, Codable {
    var filledCards = true
    var brightness: Double = 0.3
    var autoDim = true
    var pixelShift = false
    var is24h = true
    var showDate = true
    var showBattery = true
    var showMedia = true
    var focusMin = 25
    var breakMin = 5
    var longBreakMin = 15

    var pomodoro: PomodoroConfig {
        PomodoroConfig(focusMin: focusMin, breakMin: breakMin, longBreakMin: longBreakMin)
    }
}

/// Persists settings in UserDefaults.
final class SettingsStore: ObservableObject {
    private static let key = "settings.v1"
    @Published private(set) var settings: Settings

    init() {
        if let data = UserDefaults.standard.data(forKey: Self.key),
           let s = try? JSONDecoder().decode(Settings.self, from: data) {
            settings = s
        } else {
            settings = Settings()
        }
    }

    func save(_ new: Settings) {
        settings = new
        if let data = try? JSONEncoder().encode(new) {
            UserDefaults.standard.set(data, forKey: Self.key)
        }
    }
}
