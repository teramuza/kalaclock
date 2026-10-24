import SwiftUI

private let cardColor = Color(red: 11 / 255, green: 11 / 255, blue: 11 / 255)

// Roboto Condensed: a narrow neo-grotesque with flat-cut terminals, close to the classic flip clock look.
private func digitFont(_ size: CGFloat) -> Font { .custom("RobotoCondensed-Bold", fixedSize: size) }
func labelFont(_ size: CGFloat) -> Font { .custom("RobotoCondensed-SemiBold", fixedSize: size) }

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
            // Roboto Condensed's line box is 1.17em with the baseline 0.93em down and digits 0.71em tall,
            // so the glyphs sit 0.014em above the box center; nudge them down so the center line
            // splits the digits evenly.
            Text(text)
                .font(digitFont(fontSize))
                .foregroundColor(color)
                .lineLimit(1)
                .fixedSize()
                .frame(width: width, height: height)
                .offset(y: fontSize * 0.014)
        }
        .frame(width: width, height: height / 2, alignment: top ? .top : .bottom)
        .clipped()
    }
}

/// One flipping surface: static top shows the new value, static bottom the old one, and a flap
/// folds the old top down, then unfolds the new bottom. Flips only when its own `text` changes.
private struct FlipUnit: View {
    let text: String
    let width: CGFloat
    let height: CGFloat
    let bg: Color
    let fontSize: CGFloat
    let color: Color

    @State private var shown: String
    @State private var previous: String
    @State private var phase = 0 // 0 idle, 1 old top folding down, 2 new bottom unfolding
    @State private var angle: Double = 0

    init(text: String, width: CGFloat, height: CGFloat, bg: Color, fontSize: CGFloat, color: Color) {
        self.text = text
        self.width = width
        self.height = height
        self.bg = bg
        self.fontSize = fontSize
        self.color = color
        _shown = State(initialValue: text)
        _previous = State(initialValue: text)
    }

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
        }
        .frame(width: width, height: height)
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

/// Flip card. With a card (`filled`) the whole group flips as one. Without a card (digits only)
/// each digit flips on its own, so in "XY" an unchanged X stays still while Y flips.
struct FlipCard: View {
    let text: String
    let width: CGFloat
    let height: CGFloat
    let filled: Bool
    let color: Color
    var topLabel: String?
    var bottomLabel: String?

    private var fontSize: CGFloat { min(height * 1.08, width * 0.86 / (CGFloat(max(text.count, 1)) * 0.505)) }
    private var bg: Color { filled ? cardColor : .black } // opaque, so a flap hides what is behind it

    var body: some View {
        ZStack {
            if filled {
                FlipUnit(text: text, width: width, height: height, bg: bg, fontSize: fontSize, color: color)
            } else {
                HStack(spacing: 0) {
                    ForEach(Array(text.enumerated()), id: \.offset) { _, ch in
                        FlipUnit(text: String(ch), width: fontSize * 0.52, height: height, bg: bg,
                                 fontSize: fontSize, color: color)
                    }
                }
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
    }
}
