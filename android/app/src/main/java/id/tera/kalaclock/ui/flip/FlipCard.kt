package id.tera.kalaclock.ui.flip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import id.tera.kalaclock.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardColor = Color(0xFF0B0B0B)

// Anton for the digits: a very tall, narrow grotesque. Archivo Condensed for the small labels.
private val DigitFont = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))
/** Height of the digits' vertical center above the baseline, in em (Anton: ink from -0.008 to 0.867). */
private const val DIGIT_INK_CENTER = 0.43f

private val LabelFont = FontFamily(Font(R.font.archivo_condensed_semibold, FontWeight.SemiBold))

/** One half of a card showing [text]; [top] picks which half is visible. */
@Composable
private fun Half(
    text: String,
    top: Boolean,
    bg: Color,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .drawWithContent {
                val half = size.height / 2f
                val r = if (top) Rect(0f, 0f, size.width, half) else Rect(0f, half, size.width, size.height)
                clipRect(r.left, r.top, r.right, r.bottom, ClipOp.Intersect) {
                    drawRect(bg)
                    this@drawWithContent.drawContent()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Line box metrics differ per platform and per font, so place the digits from the glyph
        // ink instead: Anton's digits are centered 0.43em above the baseline. Shift the text so
        // that point lands on the middle of the card, where the center line is.
        val fontSizePx = with(LocalDensity.current) { fontSize.toPx() }
        var shiftPx by remember { mutableFloatStateOf(0f) }
        Text(
            text,
            modifier = Modifier.offset { IntOffset(0, shiftPx.roundToInt()) },
            onTextLayout = { r ->
                shiftPx = DIGIT_INK_CENTER * fontSizePx + r.size.height / 2f - r.firstBaseline
            },
            style = TextStyle(fontSize = fontSize, fontFamily = DigitFont, fontWeight = FontWeight.Normal, color = color),
        )
    }
}

/**
 * One flipping surface: static top shows the new value, static bottom shows the old one,
 * and a rotating half sweeps from old to new. Flips only when its own [text] changes.
 */
@Composable
private fun FlipUnit(text: String, width: Dp, height: Dp, bg: Color, fontSize: TextUnit, color: Color) {
    var shown by remember { mutableStateOf(text) }
    var previous by remember { mutableStateOf(text) }
    val angle = remember { Animatable(0f) }

    LaunchedEffect(text) {
        if (text != shown) {
            previous = shown
            shown = text
            angle.snapTo(0f)
            angle.animateTo(180f, tween(500))
            previous = shown
        }
    }

    val camera = with(LocalDensity.current) { 16f * density }
    val flipping = angle.isRunning || angle.value in 0.1f..179.9f

    Box(Modifier.size(width, height)) {
        // static top: new value
        Half(shown, true, bg, fontSize, color, Modifier.fillMaxSize())
        // static bottom: old value until the flap lands
        val bottomText = if (angle.value >= 90f) shown else previous
        Half(bottomText, false, bg, fontSize, color, Modifier.fillMaxSize())
        // moving flap: old top folds down, then new bottom unfolds
        if (flipping) {
            val firstHalf = angle.value < 90f
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationX = if (firstHalf) -angle.value else 180f - angle.value
                        cameraDistance = camera
                    },
            ) {
                if (firstHalf) Half(previous, true, bg, fontSize, color, Modifier.fillMaxSize())
                else Half(shown, false, bg, fontSize, color, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Flip card. With a card ([filled]) the whole group flips as one. Without a card (digits only)
 * each digit flips on its own, so in "XY" an unchanged X stays still while Y flips.
 */
@Composable
fun FlipCard(
    text: String,
    width: Dp,
    height: Dp,
    filled: Boolean,
    color: Color = Color.White,
    topLabel: String? = null,
    bottomLabel: String? = null,
    alpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) {
        // Digits are 0.494em wide and 0.86em tall. Keep them under about 56% of the card height so
        // there is clear space between the clock and the date / weekday labels.
        minOf(height.toPx() * 0.65f, width.toPx() * 0.78f / (text.length.coerceAtLeast(1) * 0.494f)).toSp()
    }
    val labelSize = with(LocalDensity.current) { (height.toPx() * 0.065f).toSp() }
    val digitWidth = with(LocalDensity.current) { (fontSize.toPx() * 0.52f).toDp() }
    val radius = height * 0.13f
    // Digits-only still needs an opaque surface (black on black) so a flap hides what is behind it.
    val bg = if (filled) CardColor else Color.Black

    Box(
        modifier
            .size(width, height)
            // Dim the finished card as one offscreen layer: the flap overlaps the static halves,
            // and per-text alpha there would add up and flash brighter mid-flip.
            .graphicsLayer {
                this.alpha = alpha
                // An offscreen buffer is only needed while dimmed; at full brightness it would just cost memory.
                compositingStrategy = if (alpha < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .clip(RoundedCornerShape(radius)),
    ) {
        if (filled) {
            FlipUnit(text, width, height, bg, fontSize, color)
        } else {
            Row(Modifier.align(Alignment.Center)) {
                text.forEach { ch -> FlipUnit(ch.toString(), digitWidth, height, bg, fontSize, color) }
            }
        }
        // center line
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.Black),
        )
        topLabel?.let {
            Text(
                it,
                color = color,
                fontSize = labelSize,
                fontFamily = LabelFont,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = height * 0.05f),
            )
        }
        bottomLabel?.let {
            Text(
                it,
                color = color,
                fontSize = labelSize,
                fontFamily = LabelFont,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = width * 0.07f, bottom = height * 0.05f),
            )
        }
    }
}
