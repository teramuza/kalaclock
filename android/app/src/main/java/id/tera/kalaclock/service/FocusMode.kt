package id.tera.kalaclock.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import id.tera.kalaclock.domain.Mode
import id.tera.kalaclock.domain.PomodoroPhase
import id.tera.kalaclock.domain.TimerState

/**
 * Turns Do Not Disturb on while a Pomodoro focus phase is running and puts the previous
 * setting back when it stops (pause, reset, break, finish). Needs Do Not Disturb access,
 * which the user grants once in system settings.
 */
object FocusMode {
    private const val PREFS = "focus_mode"
    private const val KEY_PREVIOUS_FILTER = "previous_filter"

    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun hasAccess(c: Context): Boolean = manager(c).isNotificationPolicyAccessGranted

    fun accessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /** Call on every timer state change, and once at app start to undo a DND left on by a killed process. */
    fun sync(c: Context, state: TimerState, enabled: Boolean) {
        if (!hasAccess(c)) return
        val nm = manager(c)
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wantDnd = enabled && state.mode == Mode.POMODORO && state.phase == PomodoroPhase.FOCUS && state.running
        if (wantDnd) {
            val current = nm.currentInterruptionFilter
            // Leave it alone if the user already has some Do Not Disturb mode on.
            if (!prefs.contains(KEY_PREVIOUS_FILTER) && current == NotificationManager.INTERRUPTION_FILTER_ALL) {
                prefs.edit().putInt(KEY_PREVIOUS_FILTER, current).apply()
                // Priority only: alarms and the finish alert still come through.
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        } else if (prefs.contains(KEY_PREVIOUS_FILTER)) {
            nm.setInterruptionFilter(prefs.getInt(KEY_PREVIOUS_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL))
            prefs.edit().remove(KEY_PREVIOUS_FILTER).apply()
        }
    }
}
