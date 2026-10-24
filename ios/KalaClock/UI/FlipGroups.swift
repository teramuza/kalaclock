import SwiftUI

/// One flip card holding `text` (e.g. "02"), with optional labels drawn inside the card.
struct FlipGroup: Equatable {
    var text: String
    var topLabel: String?
    var bottomLabel: String?
}

private let cardAspect: CGFloat = 0.89 // width / height

/// One card per group, with a gap between cards (hour | minute).
/// Landscape: cards side by side. Portrait: cards stacked.
struct FlipGroups: View {
    let groups: [FlipGroup]
    let filled: Bool
    let color: Color
    var dim: Double = 1

    var body: some View {
        GeometryReader { geo in
            let landscape = geo.size.width > geo.size.height
            let n = CGFloat(groups.count)
            let cols = landscape ? n : 1
            let rows = landscape ? 1 : n
            let gap: CGFloat = 24
            // Outer margin also leaves room for the pixel-shift drift (±12pt).
            let margin: CGFloat = landscape ? 24 : 32
            let availW = geo.size.width - margin * 2
            let availH = geo.size.height - margin * 2
            let cardW = max(48, min((availW - gap * (cols - 1)) / cols,
                                    ((availH - gap * (rows - 1)) / rows) * cardAspect))
            let cardH = cardW / cardAspect

            Group {
                if landscape {
                    HStack(spacing: gap) { cards(cardW, cardH) }
                } else {
                    VStack(spacing: gap) { cards(cardW, cardH) }
                }
            }
            // Dim the cards as one group so the flip layers never stack translucently mid-flip.
            .compositingGroup()
            .opacity(dim)
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }

    @ViewBuilder
    private func cards(_ w: CGFloat, _ h: CGFloat) -> some View {
        ForEach(Array(groups.enumerated()), id: \.offset) { _, g in
            FlipCard(text: g.text, width: w, height: h, filled: filled, color: color,
                     topLabel: g.topLabel, bottomLabel: g.bottomLabel)
        }
    }
}
