import SwiftUI

private let cardColor = Color(red: 11 / 255, green: 11 / 255, blue: 11 / 255)

// Barlow Semi Condensed: DIN-like, flat-topped digits that suit the split-flap look.
private func digitFont(_ size: CGFloat) -> Font { .custom("BarlowSemiCondensed-ExtraBold", fixedSize: size) }
func labelFont(_ size: CGFloat) -> Font { .custom("BarlowSemiCondensed-SemiBold", fixedSize: size) }

/// One half of a card showing `text`; `top` picks which half is visible.
private struct Half: View {
    let text: String
    let top: Bool
    let bg: Color
    let fontSize: CGFloat
    let color: Color
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        ZStack(alignment: top ? .top : .bottom) {
            bg
            // Barlow's line box is 1.2em with caps 0.7em tall on a 1.0em baseline, so the glyphs
            // sit 0.05em below the box center; lift them so the center line splits digits evenly.
            Text(text)
                .font(digitFont(fontSize))
                .foregroundColor(color)
                .lineLimit(1)
                .fixedSize()
                .frame(width: width, height: height)
                .offset(y: -fontSize * 0.05)
        }
        .frame(width: width, height: height / 2, alignment: top ? .top : .bottom)
        .clipped()
    }
}

/// Flip card: static top shows the new value, static bottom the old one, and a flap
/// folds the old top down, then unfolds the new bottom.
struct FlipCard: View {
    let text: String
    let width: CGFloat
    let height: CGFloat
    let filled: Bool
    let color: Color
    var topLabel: String?
    var bottomLabel: String?

    @State private var shown: String
    @State private var previous: String
    @State private var phase = 0 // 0 idle, 1 old top folding down, 2 new bottom unfolding
    @State private var angle: Double = 0

    init(text: String, width: CGFloat, height: CGFloat, filled: Bool, color: Color,
         topLabel: String? = nil, bottomLabel: String? = nil) {
        self.text = text
        self.width = width
        self.height = height
        self.filled = filled
        self.color = color
        self.topLabel = topLabel
        self.bottomLabel = bottomLabel
        _shown = State(initialValue: text)
        _previous = State(initialValue: text)
    }

    private var fontSize: CGFloat { min(height * 0.72, width / (CGFloat(max(text.count, 1)) * 0.56)) }
    private var bg: Color { filled ? cardColor : .clear }

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                Half(text: shown, top: true, bg: bg, fontSize: fontSize, color: color, width: width, height: height)
                Half(text: phase == 0 || phase == 2 ? shown : previous, top: false, bg: bg,
                     fontSize: fontSize, color: color, width: width, height: height)
            }
            if phase == 1 {
                Half(text: previous, top: true, bg: bg, fontSize: fontSize, color: color, width: width, height: height)
                    .rotation3DEffect(.degrees(angle), axis: (1, 0, 0), anchor: .bottom, perspective: 0.4)
                    .frame(width: width, height: height, alignment: .top)
            }
            if phase == 2 {
                Half(text: shown, top: false, bg: bg, fontSize: fontSize, color: color, width: width, height: height)
                    .rotation3DEffect(.degrees(angle), axis: (1, 0, 0), anchor: .top, perspective: 0.4)
                    .frame(width: width, height: height, alignment: .bottom)
            }
            Rectangle().fill(Color.black).frame(width: width, height: 3)
            if let topLabel {
                Text(topLabel)
                    .font(labelFont(height * 0.065))
                    .foregroundColor(color)
                    .frame(width: width, height: height, alignment: .top)
                    .padding(.top, height * 0.04)
                    .frame(width: width, height: height, alignment: .top)
            }
            if let bottomLabel {
                Text(bottomLabel)
                    .font(labelFont(height * 0.065))
                    .foregroundColor(color)
                    .padding(.trailing, width * 0.07)
                    .padding(.bottom, height * 0.05)
                    .frame(width: width, height: height, alignment: .bottomTrailing)
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: height * 0.13))
        .onChange(of: text) { new in flip(to: new) }
    }

    private func flip(to new: String) {
        guard new != shown else { return }
        previous = shown
        shown = new
        phase = 1
        angle = 0
        withAnimation(.easeIn(duration: 0.25)) { angle = -90 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            phase = 2
            angle = 90
            withAnimation(.easeOut(duration: 0.25)) { angle = 0 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                previous = shown
                phase = 0
            }
        }
    }
}
