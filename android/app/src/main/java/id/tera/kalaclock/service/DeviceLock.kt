package id.tera.kalaclock.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/** Holds the device-admin grant (force-lock only) used to lock the phone after idle. */
class LockAdminReceiver : DeviceAdminReceiver()

object DeviceLock {
    private fun admin(c: Context) = ComponentName(c, LockAdminReceiver::class.java)

    fun isAdmin(c: Context): Boolean =
        c.getSystemService(DevicePolicyManager::class.java).isAdminActive(admin(c))

    fun requestIntent(c: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(c))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Lets KalaClock lock the phone after it has been idle. It can only lock the screen.",
            )

    /**
     * Locks the phone, then wakes the screen again. The activity is shown over the lock screen
     * (setShowWhenLocked), so the clock stays visible while the phone is locked.
     */
    @Suppress("DEPRECATION")
    fun lockAndWake(c: Context) {
        if (!isAdmin(c)) return
        c.getSystemService(DevicePolicyManager::class.java).lockNow()
        Handler(Looper.getMainLooper()).postDelayed({
            c.getSystemService(PowerManager::class.java)
                .newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "kalaclock:wake",
                )
                .acquire(3_000)
        }, 800)
    }
}
