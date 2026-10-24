package id.tera.kalaclock

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.tera.kalaclock.data.Settings
import id.tera.kalaclock.data.SettingsRepository
import id.tera.kalaclock.domain.Mode
import id.tera.kalaclock.ui.PixelShift
import id.tera.kalaclock.ui.clock.ClockScreen
import id.tera.kalaclock.ui.settings.SettingsScreen
import id.tera.kalaclock.ui.timer.TimerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                App()
            }
        }
    }

    private fun applyBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun App() {
        val settings by repo.flow.collectAsState(initial = Settings())
        val scope = rememberCoroutineScope()
        var mode by remember { mutableStateOf(Mode.CLOCK) }
        var showSettings by remember { mutableStateOf(false) }
        var chrome by remember { mutableStateOf(true) }
        var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }

        // Back from a timer mode returns to the clock; settings has its own handler.
        BackHandler(enabled = !showSettings && mode != Mode.CLOCK) { mode = Mode.CLOCK }

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Auto dim after 15s of no touch; touch restores full level.
        val dimmed = settings.autoDim && !chrome
        val dim = if (dimmed) settings.brightness.coerceAtLeast(0.2f) else 1f
        LaunchedEffect(settings.autoDim, settings.brightness, dimmed) {
            applyBrightness(if (dimmed) settings.brightness else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
        LaunchedEffect(lastTouch) {
            chrome = true
            delay(15_000)
            chrome = false
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                    lastTouch = System.currentTimeMillis()
                },
        ) {
            if (showSettings) {
                SettingsScreen(settings, { new -> scope.launch { repo.update { new } } }) { showSettings = false }
            } else {
                PixelShift(settings.pixelShift, Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val landscape = maxWidth > maxHeight
                        val content: @Composable (Modifier) -> Unit = { m ->
                            Box(m) {
                                if (mode == Mode.CLOCK) ClockScreen(settings, dim)
                                else TimerScreen(mode, settings, dim)
                            }
                        }
                        // Landscape puts the bar in a side rail so cards keep the full height.
                        val bar: @Composable (Modifier) -> Unit = { m ->
                            Box(m) {
                                if (chrome) {
                                    ModeBar(
                                        vertical = landscape,
                                        mode = mode,
                                        onMode = { mode = it; lastTouch = System.currentTimeMillis() },
                                        onSettings = { showSettings = true },
                                    )
                                }
                            }
                        }
                        // Space is only taken while the bar is visible; once it hides, the clock re-centers.
                        val railWidth by animateDpAsState(if (chrome) 104.dp else 0.dp, label = "rail")
                        val barHeight by animateDpAsState(if (chrome) 56.dp else 0.dp, label = "bar")
                        if (landscape) {
                            Row(Modifier.fillMaxSize()) {
                                content(Modifier.weight(1f).fillMaxHeight())
                                bar(Modifier.width(railWidth).fillMaxHeight())
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                content(Modifier.weight(1f).fillMaxWidth())
                                bar(Modifier.fillMaxWidth().height(barHeight))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeBar(vertical: Boolean, mode: Mode, onMode: (Mode) -> Unit, onSettings: () -> Unit) {
    @Composable
    fun Tab(m: Mode, modifier: Modifier) {
        Text(
            m.name.lowercase().replaceFirstChar { it.uppercase() },
            color = if (m == mode) Color.White else Color.Gray,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = modifier.clickable { onMode(m) }.padding(vertical = 14.dp),
        )
    }
    @Composable
    fun Gear(modifier: Modifier) {
        Text("⚙", color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = modifier.clickable(onClick = onSettings).padding(12.dp))
    }
    if (vertical) {
        Column(Modifier.fillMaxSize().padding(end = 8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Mode.entries.forEach { Tab(it, Modifier.fillMaxWidth()) }
            Gear(Modifier)
        }
    } else {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Mode.entries.forEach { Tab(it, Modifier.weight(1f)) }
            Gear(Modifier)
        }
    }
}
