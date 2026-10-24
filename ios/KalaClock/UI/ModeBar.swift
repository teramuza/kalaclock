import SwiftUI

/// Mode tabs plus settings button. Vertical rail in landscape, bottom bar in portrait.
struct ModeBar: View {
    let vertical: Bool
    let mode: Mode
    let onMode: (Mode) -> Void
    let onSettings: () -> Void

    var body: some View {
        if vertical {
            VStack(spacing: 0) {
                ForEach(Mode.allCases, id: \.self) { tab($0).frame(maxWidth: .infinity) }
                gear
            }
            .padding(.trailing, 8)
            .frame(maxHeight: .infinity)
        } else {
            HStack(spacing: 0) {
                ForEach(Mode.allCases, id: \.self) { tab($0).frame(maxWidth: .infinity) }
                gear
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity)
        }
    }

    private func tab(_ m: Mode) -> some View {
        Button { onMode(m) } label: {
            Text(m.title)
                .font(.system(size: 13))
                .lineLimit(1)
                .foregroundColor(m == mode ? .white : .gray)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity)
        }
    }

    private var gear: some View {
        Button(action: onSettings) {
            Image(systemName: "gearshape").font(.system(size: 20)).foregroundColor(.white).padding(12)
        }
        .accessibilityLabel("Settings")
    }
}
