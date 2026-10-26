package id.tera.kalaclock

import android.Manifest
import id.tera.kalaclock.media.NowPlayingState
import id.tera.kalaclock.media.NowPlayingListener
import android.service.notification.NotificationListenerService
import android.content.ComponentName
import id.tera.kalaclock.service.DeviceLock
import id.tera.kalaclock.service.FocusMode
import id.tera.kalaclock.service.SystemBrightness
import id.tera.kalaclock.service.TimerController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.core.content.ContextCompat
import android.content.IntentFilter
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.app.KeyguardManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.mutableIntStateOf
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
import id.tera.kalaclock.ui.NotificationIcons
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
        // Undo a brightness left dimmed by a killed process.
        SystemBrightness.restore(this)
        // Undo a Do Not Disturb left on by a focus session whose process was killed.
        FocusMode.sync(this, TimerController.state.value, enabled = false)
        // Stay visible over the lock screen so the clock keeps showing while the phone is locked.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
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

    /** True while the keyguard is up. Touches are ignored then; the user unlocks with fingerprint or PIN. */
    private val locked = mutableStateOf(false)

    private val keyguardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshLocked()
            // The keyguard state can lag behind the screen broadcasts.
            window.decorView.postDelayed({ refreshLocked() }, 500)
        }
    }

    private fun refreshLocked() {
        locked.value = getSystemService(KeyguardManager::class.java).isKeyguardLocked
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, keyguardReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshLocked()
        // Android does not rebind the notification listener after a force-stop or reinstall.
        if (NowPlayingState.hasAccess(this)) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(this, NowPlayingListener::class.java))
            }
        }
    }

    override fun onStop() {
        unregisterReceiver(keyguardReceiver)
        // Give the device brightness back whenever the app leaves the screen.
        SystemBrightness.restore(this)
        super.onStop()
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
        // Dimming changes the device brightness itself, so the UI keeps its normal colors.
        val dim = 1f
        var resumes by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
        LaunchedEffect(settings.autoDim, settings.brightness, dimmed, resumes) {
            if (dimmed) {
                // Without the permission, fall back to this window's own brightness.
                if (!SystemBrightness.dim(this@MainActivity, settings.brightness)) applyBrightness(settings.brightness)
            } else {
                SystemBrightness.restore(this@MainActivity)
                applyBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
        }
        // Tapping the clock toggles the bar; while it is shown it hides itself after 15s.
        LaunchedEffect(chrome, lastTouch) {
            if (chrome) {
                delay(15_000)
                chrome = false
            }
        }

        // Idle lock: after 5 minutes without a touch, lock the phone; the clock stays on screen.
        var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
        val isLocked by locked
        var lockHint by remember { mutableStateOf(false) }
        LaunchedEffect(lockHint, isLocked) {
            if (lockHint) {
                delay(2_500)
                lockHint = false
            }
        }
        LaunchedEffect(lastInteraction, settings.autoLock, isLocked) {
            if (!settings.autoLock || isLocked || !DeviceLock.isAdmin(this@MainActivity)) return@LaunchedEffect
            delay(5 * 60_000L)
            DeviceLock.lockAndWake(this@MainActivity)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    // Observe every touch without consuming it, to restart the idle timer.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            lastInteraction = System.currentTimeMillis()
                        }
                    }
                }
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                    chrome = !chrome
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
                                // The lock icon takes this corner while the phone is locked.
                                if (mode == Mode.CLOCK && settings.showNotifIcons && !isLocked) {
                                    NotificationIcons(dim, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp))
                                }
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
            if (isLocked) {
                // Swallow all touches while locked; the lock icon only shows a hint.
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    },
                )
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Unlock",
                    tint = Color.Gray.copy(alpha = dim),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .size(28.dp)
                        .clickable { lockHint = true },
                )
                if (lockHint) {
                    // In-app message: asking the system to unlock would force the lock screen.
                    Text(
                        "Unlock device to access the app features",
                        color = Color.White.copy(alpha = dim),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 72.dp)
                            .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
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
