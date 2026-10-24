import SwiftUI
import UIKit

/// Reads the battery level and charging state and publishes changes as they happen.
final class BatteryMonitor: ObservableObject {
    @Published private(set) var percent: Int?
    @Published private(set) var charging = false

    init() {
        UIDevice.current.isBatteryMonitoringEnabled = true
        update()
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(update), name: UIDevice.batteryLevelDidChangeNotification, object: nil)
        nc.addObserver(self, selector: #selector(update), name: UIDevice.batteryStateDidChangeNotification, object: nil)
    }

    @objc private func update() {
        let level = UIDevice.current.batteryLevel // -1 when unavailable (e.g. simulator)
        percent = level < 0 ? nil : Int((level * 100).rounded())
        let state = UIDevice.current.batteryState
        charging = state == .charging || state == .full
    }
}

private let lowBattery = Color(red: 0xC6 / 255, green: 0x28 / 255, blue: 0x28 / 255)

/// System-style battery icon filled proportionally to the level, plus percent. Red at 20% or less.
struct BatteryIndicator: View {
    let fg: Color
    let dim: Double
    @StateObject private var battery = BatteryMonitor()

    var body: some View {
        if let pct = battery.percent {
            let low = pct <= 20 && !battery.charging
            let tint = low ? lowBattery.opacity(max(dim, 0.6)) : fg
            HStack(spacing: 6) {
                Text("\(pct)%").font(.system(size: 16)).foregroundColor(tint)
                BatteryIcon(percent: pct, charging: battery.charging, tint: tint)
                    .frame(width: 30, height: 14)
            }
        }
    }
}

private struct BatteryIcon: View {
    let percent: Int
    let charging: Bool
    let tint: Color

    var body: some View {
        Canvas { ctx, size in
            let tip = size.width * 0.08
            let body = size.width - tip
            let stroke: CGFloat = 1.5
            ctx.stroke(
                Path(roundedRect: CGRect(x: stroke / 2, y: stroke / 2, width: body - stroke, height: size.height - stroke), cornerRadius: 3),
                with: .color(tint), lineWidth: stroke)
            ctx.fill(
                Path(roundedRect: CGRect(x: body, y: size.height * 0.3, width: tip, height: size.height * 0.4), cornerRadius: 1),
                with: .color(tint))
            let pad = stroke + 1.5
            let fillW = (body - pad * 2) * CGFloat(min(max(percent, 0), 100)) / 100
            if fillW > 0 {
                ctx.fill(Path(roundedRect: CGRect(x: pad, y: pad, width: fillW, height: size.height - pad * 2), cornerRadius: 1.5),
                         with: .color(tint))
            }
            if charging {
                let cx = body / 2, h = size.height
                var bolt = Path()
                bolt.move(to: CGPoint(x: cx + h * 0.10, y: h * 0.05))
                bolt.addLine(to: CGPoint(x: cx - h * 0.28, y: h * 0.55))
                bolt.addLine(to: CGPoint(x: cx - h * 0.02, y: h * 0.55))
                bolt.addLine(to: CGPoint(x: cx - h * 0.10, y: h * 0.95))
                bolt.addLine(to: CGPoint(x: cx + h * 0.28, y: h * 0.42))
                bolt.addLine(to: CGPoint(x: cx + h * 0.02, y: h * 0.42))
                bolt.closeSubpath()
                ctx.stroke(bolt, with: .color(.black), lineWidth: 2)
                ctx.fill(bolt, with: .color(tint))
            }
        }
    }
}
