package id.tera.kalaclock.ui.flip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/** One flip card holding [text] (e.g. "02"), with optional labels drawn inside the card. */
data class FlipGroup(val text: String, val topLabel: String? = null, val bottomLabel: String? = null)

/** Digit color before dimming; dimming is applied to the whole card so flip layers never stack translucently. */
val DigitGray = Color(0xFFB0B0B0)

private const val CARD_ASPECT = 0.89f // width / height

/**
 * One card per group, with a gap between cards (hour | minute).
 * Landscape: cards side by side. Portrait: cards stacked.
 */
@Composable
fun FlipGroups(
    groups: List<FlipGroup>,
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    dim: Float = 1f,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val n = groups.size
        val cols = if (landscape) n else 1
        val rows = if (landscape) 1 else n
        val gap = 24.dp
        // Outer margin also leaves room for the pixel-shift drift (±12dp).
        val margin = if (landscape) 24.dp else 32.dp
        val availW = maxWidth - margin * 2
        val availH = maxHeight - margin * 2
        val cardW = min(
            (availW - gap * (cols - 1)) / cols,
            ((availH - gap * (rows - 1)) / rows) * CARD_ASPECT,
        ).coerceAtLeast(48.dp)
        val cardH = cardW / CARD_ASPECT

        val card: @Composable (FlipGroup) -> Unit = { g ->
            FlipCard(g.text, cardW, cardH, filled, color, g.topLabel, g.bottomLabel, alpha = dim)
        }
        if (landscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                groups.forEach { card(it) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                groups.forEach { card(it) }
            }
        }
    }
}
