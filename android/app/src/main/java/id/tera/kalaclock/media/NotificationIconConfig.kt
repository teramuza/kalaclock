package id.tera.kalaclock.media

import android.app.Notification
import android.service.notification.StatusBarNotification

/** Which notifications show up as icons in the corner of the clock. Edit the values here. */
object NotificationIconConfig {
    const val MAX_ICONS = 3

    /** Apps always shown, even when their notifications do not declare a message/email category. */
    val ALLOWED_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms", // Signal
        "com.google.android.gm", // Gmail
        "com.google.android.apps.dynamite", // Google Chat
        "com.google.android.apps.tachyon", // Google Meet / Duo
        "com.google.android.apps.messaging", // Google Messages
        "com.microsoft.office.outlook",
        "com.slack",
        "com.discord",
        "jp.naver.line.android",
    )

    /** Message and email notifications from any app qualify too. */
    private val CATEGORIES = setOf(Notification.CATEGORY_MESSAGE, Notification.CATEGORY_EMAIL)

    fun matches(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        return sbn.packageName in ALLOWED_PACKAGES ||
            n.category in CATEGORIES ||
            n.extras?.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MessagingStyle") == true
    }
}
