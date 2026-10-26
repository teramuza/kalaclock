package id.tera.kalaclock.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * Dims by changing the device's own brightness setting (needs the "modify system settings"
 * permission, granted once by the user) and puts the previous value and mode back afterwards.
 * The original values are kept in SharedPreferences so a killed process can still restore them.
 */
object SystemBrightness {
    private const val PREFS = "system_brightness"
    private const val KEY_VALUE = "value"
    private const val KEY_MODE = "mode"

    fun canWrite(c: Context): Boolean = Settings.System.canWrite(c)

    fun permissionIntent(c: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}"))

    /** Returns false when there is no permission, so the caller can fall back to the window brightness. */
    fun dim(c: Context, level: Float): Boolean {
        if (!canWrite(c)) return false
        val resolver = c.contentResolver
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_VALUE)) {
            prefs.edit()
                .putInt(KEY_VALUE, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128))
                .putInt(KEY_MODE, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
                .apply()
        }
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (level * 255).roundToInt().coerceIn(1, 255))
        return true
    }

    fun restore(c: Context) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_VALUE) || !canWrite(c)) return
        val resolver = c.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, prefs.getInt(KEY_VALUE, 128))
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, prefs.getInt(KEY_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
        prefs.edit().clear().apply()
    }
}
