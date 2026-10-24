import SwiftUI

/// Drifts content a few points every ~90s, slowly, to avoid OLED burn-in.
struct PixelShift<Content: View>: View {
    let enabled: Bool
    @ViewBuilder let content: () -> Content
    @State private var offset: CGSize = .zero

    var body: some View {
        content()
            .offset(offset)
            .task(id: enabled) {
                guard enabled else { offset = .zero; return }
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 90_000_000_000)
                    if Task.isCancelled { break }
                    withAnimation(.linear(duration: 20)) {
                        offset = CGSize(width: .random(in: -12...12), height: .random(in: -12...12))
                    }
                }
            }
    }
}
