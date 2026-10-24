import SwiftUI

struct ClockScreen: View {
    let settings: Settings
    let dim: Double
    @ObservedObject var nowPlaying: NowPlayingMonitor

    private let digitGray = Color(red: 0xB0 / 255, green: 0xB0 / 255, blue: 0xB0 / 255)
    private var fg: Color { Color(red: 0xB0 / 255, green: 0xB0 / 255, blue: 0xB0 / 255).opacity(dim) }

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { timeline in
            GeometryReader { geo in
                let groups = makeGroups(timeline.date)
                if geo.size.width > geo.size.height {
                    // Landscape: cards stay centered; battery and now-playing float in the corners.
                    ZStack {
                        FlipGroups(groups: groups, filled: settings.filledCards, color: digitGray, dim: dim)
                        if settings.showBattery {
                            BatteryIndicator(fg: fg, dim: dim)
                                .padding(.horizontal, 32).padding(.vertical, 16)
                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                        }
                        if settings.showMedia {
                            NowPlayingBar(monitor: nowPlaying, fg: fg, compact: true)
                                .padding(.leading, 32).padding(.bottom, 24)
                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                        }
                    }
                } else {
                    VStack(spacing: 0) {
                        if settings.showBattery {
                            HStack { Spacer(); BatteryIndicator(fg: fg, dim: dim) }
                                .padding(.horizontal, 24).padding(.vertical, 8)
                        }
                        FlipGroups(groups: groups, filled: settings.filledCards, color: digitGray, dim: dim)
                        if settings.showMedia { NowPlayingBar(monitor: nowPlaying, fg: fg) }
                    }
                }
            }
        }
    }

    private func makeGroups(_ date: Date) -> [FlipGroup] {
        let cal = Calendar.current
        let h24 = cal.component(.hour, from: date)
        let minute = cal.component(.minute, from: date)
        let hour = settings.is24h ? h24 : (h24 % 12 == 0 ? 12 : h24 % 12)
        let dateLabel = settings.showDate ? Self.format(date, "MM/dd/yy") : nil
        let weekday = settings.showDate ? Self.format(date, "EEEE") : nil
        let ampm: String? = settings.is24h ? nil : (h24 < 12 ? "AM" : "PM")
        return [
            FlipGroup(text: String(format: "%02d", hour), topLabel: dateLabel, bottomLabel: ampm),
            FlipGroup(text: String(format: "%02d", minute), topLabel: weekday),
        ]
    }

    private static func format(_ date: Date, _ pattern: String) -> String {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate(pattern)
        f.dateFormat = pattern
        return f.string(from: date)
    }
}
