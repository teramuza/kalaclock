package id.tera.kalaclock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val filledCards: Boolean = true,
    val brightness: Float = 0.3f,
    val autoDim: Boolean = true,
    val pixelShift: Boolean = false,
    val is24h: Boolean = true,
    val showDate: Boolean = true,
    val showBattery: Boolean = true,
    val showMedia: Boolean = true,
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val longBreakMin: Int = 15,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val filled = booleanPreferencesKey("filled")
        val brightness = floatPreferencesKey("brightness")
        val autoDim = booleanPreferencesKey("auto_dim")
        val shift = booleanPreferencesKey("pixel_shift_v2")
        val h24 = booleanPreferencesKey("h24")
        val date = booleanPreferencesKey("date")
        val battery = booleanPreferencesKey("battery")
        val media = booleanPreferencesKey("media")
        val focus = intPreferencesKey("focus")
        val brk = intPreferencesKey("break")
        val longBrk = intPreferencesKey("long_break")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            filledCards = p[K.filled] ?: d.filledCards,
            brightness = p[K.brightness] ?: d.brightness,
            autoDim = p[K.autoDim] ?: d.autoDim,
            pixelShift = p[K.shift] ?: d.pixelShift,
            is24h = p[K.h24] ?: d.is24h,
            showDate = p[K.date] ?: d.showDate,
            showBattery = p[K.battery] ?: d.showBattery,
            showMedia = p[K.media] ?: d.showMedia,
            focusMin = p[K.focus] ?: d.focusMin,
            breakMin = p[K.brk] ?: d.breakMin,
            longBreakMin = p[K.longBrk] ?: d.longBreakMin,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(
                Settings(
                    p[K.filled] ?: true, p[K.brightness] ?: 0.3f, p[K.autoDim] ?: true,
                    p[K.shift] ?: false, p[K.h24] ?: true, p[K.date] ?: true,
                    p[K.battery] ?: true, p[K.media] ?: true,
                    p[K.focus] ?: 25, p[K.brk] ?: 5, p[K.longBrk] ?: 15,
                )
            )
            p[K.filled] = s.filledCards
            p[K.brightness] = s.brightness
            p[K.autoDim] = s.autoDim
            p[K.shift] = s.pixelShift
            p[K.h24] = s.is24h
            p[K.date] = s.showDate
            p[K.battery] = s.showBattery
            p[K.media] = s.showMedia
            p[K.focus] = s.focusMin
            p[K.brk] = s.breakMin
            p[K.longBrk] = s.longBreakMin
        }
    }
}
