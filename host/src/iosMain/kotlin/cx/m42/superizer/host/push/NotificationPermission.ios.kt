package cx.m42.superizer.host.push

import cx.m42.superizer.push.PushPermission
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The system's notification question with nothing of APNs in it (0.9.0): push asks it and then
 * registers for remote notifications, local reminders ask it and register nothing.
 */
internal object NotificationPermission {

    /** The one-time dialog; asked again, iOS answers from the stored decision without one. */
    suspend fun request(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
        ) { granted, _ ->
            if (continuation.isActive) continuation.resume(granted)
        }
    }

    /** The stored answer, without a dialog. */
    suspend fun state(): PushPermission {
        val status = suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
                if (continuation.isActive) continuation.resume(settings?.authorizationStatus)
            }
        }
        return when (status) {
            UNAuthorizationStatusNotDetermined -> PushPermission.Off
            UNAuthorizationStatusDenied -> PushPermission.Blocked
            null -> PushPermission.Off
            else -> PushPermission.Granted
        }
    }
}
