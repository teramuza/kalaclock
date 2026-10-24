package id.tera.kalaclock.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import id.tera.kalaclock.data.Settings
import id.tera.kalaclock.AppInfoConfig
import id.tera.kalaclock.media.NowPlayingState
import id.tera.kalaclock.service.DeviceLock
import id.tera.kalaclock.service.FocusMode

@Composable
fun SettingsScreen(saved: Settings, onSave: (Settings) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(saved) }
    val onChange: ((Settings) -> Settings) -> Unit = { t -> settings = t(settings) }
    val dirty = settings != saved
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestClose = { if (dirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = requestClose)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Save settings?") },
            text = { Text("You have unsaved changes.") },
            confirmButton = { TextButton({ onSave(settings); onClose() }) { Text("Save") } },
            dismissButton = { TextButton({ onClose() }) { Text("Discard") } },
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Settings", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            IconButton(requestClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
        }
        Toggle("Filled cards (off = digits only, best for OLED)", settings.filledCards) { v -> onChange { it.copy(filledCards = v) } }
        Toggle("24-hour", settings.is24h) { v -> onChange { it.copy(is24h = v) } }
        Toggle("Show date", settings.showDate) { v -> onChange { it.copy(showDate = v) } }
        Toggle("Show battery", settings.showBattery) { v -> onChange { it.copy(showBattery = v) } }
        Toggle("Pixel shift", settings.pixelShift) { v -> onChange { it.copy(pixelShift = v) } }
        Toggle("Auto dim", settings.autoDim) { v -> onChange { it.copy(autoDim = v) } }
        Text("Brightness ${(settings.brightness * 100).toInt()}%", color = Color.White)
        Slider(settings.brightness, { v -> onChange { it.copy(brightness = v.coerceIn(0.05f, 1f)) } }, valueRange = 0.05f..1f)
        Stepper("Focus (min)", settings.focusMin) { v -> onChange { it.copy(focusMin = v) } }
        Stepper("Break (min)", settings.breakMin) { v -> onChange { it.copy(breakMin = v) } }
        Stepper("Long break (min)", settings.longBreakMin) { v -> onChange { it.copy(longBreakMin = v) } }
        Toggle("Lock phone after 5 min idle (clock stays on screen)", settings.autoLock) { v -> onChange { it.copy(autoLock = v) } }
        if (settings.autoLock && !DeviceLock.isAdmin(ctx)) {
            Text("Locking needs device admin access. It can only lock the screen.", color = Color.LightGray)
            Button({ ctx.startActivity(DeviceLock.requestIntent(ctx)) }) { Text("Grant device admin") }
        }
        Toggle("Do Not Disturb during Pomodoro focus", settings.pomodoroDnd) { v -> onChange { it.copy(pomodoroDnd = v) } }
        if (settings.pomodoroDnd && !FocusMode.hasAccess(ctx)) {
            Text("Needs Do Not Disturb access. Alarms and priority contacts still come through.", color = Color.LightGray)
            Button({ ctx.startActivity(FocusMode.accessIntent()) }) { Text("Grant Do Not Disturb access") }
        }
        Toggle("Show now playing", settings.showMedia) { v -> onChange { it.copy(showMedia = v) } }
        Toggle("Show message/email app icons (max 3)", settings.showNotifIcons) { v -> onChange { it.copy(showNotifIcons = v) } }
        if ((settings.showMedia || settings.showNotifIcons) && !NowPlayingState.hasAccess(ctx)) {
            Text("Now playing and notification icons need Notification access. Nothing leaves your device.", color = Color.LightGray)
            Button({ ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                Text("Grant access")
            }
        }
        Button({ onSave(settings); onClose() }, enabled = dirty) { Text("Save") }
        AboutSection()
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$label: $value", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ onChange((value - 1).coerceAtLeast(1)) }) { Text("−", color = Color.White) }
            OutlinedButton({ onChange((value + 1).coerceAtMost(180)) }) { Text("+", color = Color.White) }
        }
    }
}

@Composable
private fun AboutSection() {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("About", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text("${AppInfoConfig.APP_NAME} $version".trim(), color = Color.White)
        Text(AppInfoConfig.APP_DESCRIPTION, color = Color.Gray)
        Text(
            "${AppInfoConfig.DEVELOPER_ROLE}: ${AppInfoConfig.DEVELOPER_NAME}",
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp),
        )
        listOf(
            "GitHub" to AppInfoConfig.GITHUB_URL,
            "Source code" to AppInfoConfig.SOURCE_URL,
            "Website" to AppInfoConfig.WEBSITE_URL,
            "Contact" to AppInfoConfig.CONTACT_EMAIL.takeIf { it.isNotBlank() }?.let { "mailto:$it" }.orEmpty(),
        ).filter { it.second.isNotBlank() }.forEach { (label, url) ->
            TextButton({ runCatching { uriHandler.openUri(url) } }) { Text(label) }
        }
        Text(AppInfoConfig.COPYRIGHT, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    }
}
