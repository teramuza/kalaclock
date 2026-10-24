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
                Text("Brightness \(Int(draft.brightness * 100))%").foregroundColor(.white)
                Slider(value: $draft.brightness, in: 0.05...1)
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
