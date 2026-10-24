package id.tera.kalaclock.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import id.tera.kalaclock.MainActivity
import id.tera.kalaclock.domain.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps a running timer alive and schedules the finish alarm. Stops itself when idle. */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Timer", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL, "Timer finished", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(System.currentTimeMillis(), false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        job?.cancel()
        job = scope.launch {
            TimerController.state.collectLatest { s ->
                if (!s.running || s.mode == Mode.CLOCK) {
                    cancelAlarm(this@TimerService)
                    stopSelf()
                    return@collectLatest
                }
                // The system draws the running time itself (chronometer), so nothing here wakes every second.
                val now = SystemClock.elapsedRealtime()
                val countDown = s.mode != Mode.STOPWATCH
                val base = if (countDown) System.currentTimeMillis() + s.remainingMs(now)
                else System.currentTimeMillis() - s.elapsedMs(now)
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(base, countDown))
                if (countDown) {
                    scheduleAlarm(this@TimerService, now + s.remainingMs(now))
                    delay(s.remainingMs(now))
                    TimerController.tick()
                } else {
                    awaitCancellation()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(whenMs: Long, countDown: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("KalaClock")
            .setShowWhen(true)
            .setWhen(whenMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(countDown)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "timer"
        const val ALARM_CHANNEL = "timer_done"
        const val NOTIF_ID = 1
        const val DONE_ID = 2

        private fun alarmIntent(c: Context) = PendingIntent.getBroadcast(
            c, 0, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun scheduleAlarm(c: Context, triggerElapsed: Long) {
            c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, alarmIntent(c)
            )
        }

        fun cancelAlarm(c: Context) {
            c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c))
        }

        fun start(c: Context) {
            c.startForegroundService(Intent(c, TimerService::class.java))
        }
    }
}
