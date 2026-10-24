import SwiftUI
import UIKit

struct RootView: View {
    @StateObject private var store = SettingsStore()
    @StateObject private var timer = TimerController.shared
    @StateObject private var nowPlaying = NowPlayingMonitor()
    @Environment(\.scenePhase) private var scenePhase

    @State private var mode: Mode = .clock
    @State private var showSettings = false
    @State private var chrome = true
    @State private var lastTouch = Date()
    private let originalBrightness = UIScreen.main.brightness

    private var settings: Settings { store.settings }
    private var dimmed: Bool { settings.autoDim && !chrome }
    private var dim: Double { dimmed ? max(settings.brightness, 0.2) : 1 }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if showSettings {
                SettingsView(saved: settings, onSave: store.save) { showSettings = false }
            } else {
                PixelShift(enabled: settings.pixelShift) { main }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { touch() }
        .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in touch() })
        .task(id: lastTouch) {
            chrome = true
            try? await Task.sleep(nanoseconds: 15_000_000_000)
            if !Task.isCancelled { chrome = false }
        }
        .onChange(of: dimmed) { _ in applyBrightness() }
        .onChange(of: settings.brightness) { _ in applyBrightness() }
        .onChange(of: scenePhase) { phase in
            // Brightness is system-wide on iOS, so give it back whenever we leave the foreground.
            if phase == .active { applyBrightness() } else { UIScreen.main.brightness = originalBrightness }
        }
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true // keep screen on
            timer.requestNotificationPermission()
            nowPlaying.start()
        }
        .modifier(OrientationFollower())
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .preferredColorScheme(.dark)
    }

    private var main: some View {
        GeometryReader { geo in
            let landscape = geo.size.width > geo.size.height
            let content = Group {
                if mode == .clock {
                    ClockScreen(settings: settings, dim: dim, nowPlaying: nowPlaying)
                } else {
                    TimerScreen(mode: mode, settings: settings, dim: dim, timer: timer)
                }
            }
            // Space is only taken while the bar is visible; once it hides, the clock re-centers.
            let bar = ModeBar(vertical: landscape, mode: mode,
                              onMode: { mode = $0; touch() }, onSettings: { showSettings = true })
                .opacity(chrome ? 1 : 0)
                .allowsHitTesting(chrome)
            if landscape {
                HStack(spacing: 0) {
                    content
                    bar.frame(width: chrome ? 104 : 0).clipped()
                }
            } else {
                VStack(spacing: 0) {
                    content
                    bar.frame(height: chrome ? 56 : 0).clipped()
                }
            }
        }
        .animation(.easeInOut(duration: 0.3), value: chrome)
    }

    private func touch() { lastTouch = Date() }

    private func applyBrightness() {
        UIScreen.main.brightness = dimmed ? CGFloat(settings.brightness) : originalBrightness
    }
}

/// Follows the physical device orientation even when the system rotation lock is on.
private struct OrientationFollower: ViewModifier {
    func body(content: Content) -> some View {
        content
            .onAppear { UIDevice.current.beginGeneratingDeviceOrientationNotifications() }
            .onReceive(NotificationCenter.default.publisher(for: UIDevice.orientationDidChangeNotification)) { _ in
                let mask: UIInterfaceOrientationMask
                switch UIDevice.current.orientation {
                case .portrait: mask = .portrait
                case .portraitUpsideDown: mask = .portraitUpsideDown
                case .landscapeLeft: mask = .landscapeRight // device and interface landscape are mirrored
                case .landscapeRight: mask = .landscapeLeft
                default: return
                }
                guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
                scene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { _ in }
            }
    }
}
