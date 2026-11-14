package cx.m42.superizer.host.reminders

import cx.m42.superizer.host.push.NotificationPermission
import cx.m42.superizer.host.push.PushTransport
import cx.m42.superizer.push.PushPermission
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

internal actual fun platformReminders(): ReminderPlatform? = IosReminders

/**
 * Local reminders on iOS (0.9.0): requests in `UNUserNotificationCenter`, which keeps them through
 * the app being killed and the phone restarting. The whole [StoredReminder] rides in `userInfo`,
 * so [pending] needs no list of its own; a tap reaches the app delegate's notification response,
 * which hands `userInfo` to `PushTransport.deliverTap` — the push router opens the app.
 *
 * iOS keeps at most 64 waiting per app, the soonest; more is the app's mistake to avoid.
 */
internal object IosReminders : ReminderPlatform {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun permission(): PushPermission = NotificationPermission.state()

    override suspend fun requestPermission(): Boolean = NotificationPermission.request()

    override fun openSettings(): Boolean = PushTransport.openSettings()

    override suspend fun schedule(reminder: StoredReminder) {
        val content = UNMutableNotificationContent().apply {
            // UNMutableNotificationContent redeclares these readwrite; Kotlin sees the parent's vals.
            setTitle(reminder.title)
            setBody(reminder.body)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(
                reminder.tapData() +
                    mapOf(AT to reminder.atEpochMillis.toString(), TITLE to reminder.title, BODY to reminder.body),
            )
        }
        val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
        // A trigger of zero seconds is refused; one second is "now" to a person.
        val seconds = ((reminder.atEpochMillis - now) / 1000.0).coerceAtLeast(1.0)
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(reminder.key, content, trigger)
        suspendCancellableCoroutine { continuation ->
            center.addNotificationRequest(request) { _ -> if (continuation.isActive) continuation.resume(Unit) }
        }
    }

    override suspend fun cancel(key: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(key))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(key))
    }

    override suspend fun pending(prefix: String): List<StoredReminder> {
        val requests = suspendCancellableCoroutine { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { list ->
                if (continuation.isActive) continuation.resume(list.orEmpty())
            }
        }
        return requests.mapNotNull { item ->
            val request = item as? UNNotificationRequest ?: return@mapNotNull null
            if (!request.identifier.startsWith(prefix)) return@mapNotNull null
            val info = request.content.userInfo
            StoredReminder(
                key = request.identifier,
                appId = info["appId"] as? String ?: return@mapNotNull null,
                atEpochMillis = (info[AT] as? String)?.toLongOrNull() ?: return@mapNotNull null,
                title = info[TITLE] as? String ?: "",
                body = info[BODY] as? String ?: "",
                link = info["link"] as? String ?: "",
            )
        }
    }

    private const val AT = "reminderAt"
    private const val TITLE = "reminderTitle"
    private const val BODY = "reminderBody"
}
