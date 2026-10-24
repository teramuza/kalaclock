package id.tera.kalaclock.ui.flip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

// Barlow Semi Condensed: DIN-like, flat-topped digits that suit the split-flap look.
private val DigitFont = FontFamily(Font(R.font.barlow_semi_condensed_extrabold, FontWeight.ExtraBold))
private val LabelFont = FontFamily(Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold))

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
        // Barlow's line box is 1.2em with caps 0.7em tall on a 1.0em baseline, so the glyphs
        // sit 0.05em below the box center; lift them so the center line splits the digits evenly.
        val lift = with(LocalDensity.current) { (fontSize.toPx() * 0.05f).toDp() }
        Text(
            text,
            modifier = Modifier.offset(y = -lift),
            style = TextStyle(fontSize = fontSize, fontFamily = DigitFont, fontWeight = FontWeight.ExtraBold, color = color),
        )
    }
}

/**
 * Flip card: static top shows the new value, static bottom shows the old one,
 * and a rotating half-card sweeps from old to new.
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

    val fontSize = with(LocalDensity.current) {
        minOf(height.toPx() * 0.72f, width.toPx() / (text.length.coerceAtLeast(1) * 0.56f)).toSp()
    }
    val labelSize = with(LocalDensity.current) { (height.toPx() * 0.065f).toSp() }
    val camera = with(LocalDensity.current) { 16f * density }
    val radius = height * 0.13f
    val bg = if (filled) CardColor else Color.Transparent
    val flipping = angle.isRunning || angle.value in 0.1f..179.9f

    Box(
        modifier
            .size(width, height)
            // Dim the finished card as one offscreen layer: the flap overlaps the static halves,
            // and per-text alpha there would add up and flash brighter mid-flip.
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(RoundedCornerShape(radius)),
    ) {
        // static top: new value
        Box(Modifier.fillMaxSize()) { Half(shown, true, bg, fontSize, color, Modifier.fillMaxSize()) }
        // static bottom: old value until the flap lands
        val bottomText = if (angle.value >= 90f) shown else previous
        Box(Modifier.fillMaxSize()) { Half(bottomText, false, bg, fontSize, color, Modifier.fillMaxSize()) }
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
                modifier = Modifier.align(Alignment.TopCenter).padding(top = height * 0.04f),
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
