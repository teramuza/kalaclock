package id.tera.kalaclock.ui

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Drifts content a few dp every ~90s, slowly, to avoid OLED burn-in. */
@Composable
fun PixelShift(enabled: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var target by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(enabled) {
        if (!enabled) { target = IntOffset.Zero; return@LaunchedEffect }
        while (true) {
            delay(90_000)
            target = IntOffset(Random.nextInt(-12, 13), Random.nextInt(-12, 13))
        }
    }
    val animated by animateIntOffsetAsState(target, tween(20_000), label = "shift")
    Box(modifier.offset(animated.x.dp, animated.y.dp)) { content() }
}
