import SwiftUI

struct SettingsView: View {
    let saved: Settings
    let onSave: (Settings) -> Void
    let onClose: () -> Void

    @State private var draft: Settings
    @State private var confirmDiscard = false

    init(saved: Settings, onSave: @escaping (Settings) -> Void, onClose: @escaping () -> Void) {
        self.saved = saved
        self.onSave = onSave
        self.onClose = onClose
        _draft = State(initialValue: saved)
    }

    private var dirty: Bool { draft != saved }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Settings").font(.title2).foregroundColor(.white)
                    Spacer()
                    Button(action: requestClose) {
                        Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).foregroundColor(.white)
                            .padding(10)
                    }
                    .accessibilityLabel("Close")
                }
                toggle("Filled cards (off = digits only, best for OLED)", $draft.filledCards)
                toggle("24-hour", $draft.is24h)
                toggle("Show date", $draft.showDate)
                toggle("Show battery", $draft.showBattery)
                toggle("Pixel shift", $draft.pixelShift)
                toggle("Auto dim", $draft.autoDim)
                Text("Idle brightness: \(String(format: "%.1f", draft.brightness * 100))% (lowest on the left)")
                    .foregroundColor(.white)
                // Cubic scale: most of the slider is spent on the very dim levels that matter here.
                Slider(value: Binding(
                    get: { cbrt(draft.brightness) },
                    set: { draft.brightness = max(0.004, $0 * $0 * $0) }
                ), in: 0...1)
                stepper("Focus (min)", $draft.focusMin)
                stepper("Break (min)", $draft.breakMin)
                stepper("Long break (min)", $draft.longBreakMin)
                toggle("Show now playing", $draft.showMedia)
                if draft.showMedia {
                    Text("Now playing reads the Apple Music track on this device. iOS does not allow reading other players such as Spotify.")
                        .font(.footnote).foregroundColor(.gray)
                }
                Button("Save") { onSave(draft); onClose() }
                    .buttonStyle(.borderedProminent)
                    .disabled(!dirty)
                about
            }
            .padding(16)
        }
        .background(Color.black)
        .alert("Save settings?", isPresented: $confirmDiscard) {
            Button("Save") { onSave(draft); onClose() }
            Button("Discard", role: .destructive) { onClose() }
            Button("Keep editing", role: .cancel) {}
        } message: {
            Text("You have unsaved changes.")
        }
    }

    private var about: some View {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let links: [(String, String)] = [
            ("GitHub", AppInfoConfig.githubURL),
            ("Source code", AppInfoConfig.sourceURL),
            ("Website", AppInfoConfig.websiteURL),
            ("Contact", AppInfoConfig.contactEmail.isEmpty ? "" : "mailto:\(AppInfoConfig.contactEmail)"),
        ].filter { !$0.1.isEmpty }
        return VStack(alignment: .leading, spacing: 4) {
            Text("About").font(.headline).foregroundColor(.white)
            Text("\(AppInfoConfig.appName) \(version)").foregroundColor(.white)
            Text(AppInfoConfig.appDescription).foregroundColor(.gray)
            Text("\(AppInfoConfig.developerRole): \(AppInfoConfig.developerName)")
                .foregroundColor(.white).padding(.top, 8)
            ForEach(links, id: \.0) { label, url in
                if let u = URL(string: url) { Link(label, destination: u).padding(.vertical, 4) }
            }
            Text(AppInfoConfig.copyright).foregroundColor(.gray).padding(.top, 8)
        }
        .padding(.top, 16)
    }

    private func requestClose() {
        if dirty { confirmDiscard = true } else { onClose() }
    }

    private func toggle(_ label: String, _ value: Binding<Bool>) -> some View {
        Toggle(isOn: value) { Text(label).foregroundColor(.white) }
    }

    private func stepper(_ label: String, _ value: Binding<Int>) -> some View {
        Stepper(value: value, in: 1...180) { Text("\(label): \(value.wrappedValue)").foregroundColor(.white) }
    }
}
