package cx.m42.superizer.runtime

import cx.m42.superizer.push.PushPermission

/**
 * Notifications the device shows by itself at a time the app names (0.9.0): no server, no FCM
 * token, nothing leaves the phone. An optional service (D19), bound per app — ids are the app's
 * own, and the host keeps two apps' ids apart.
 *
 * | Platform | How | Survives |
 * |---|---|---|
 * | iOS | `UNUserNotificationCenter`, a time-interval trigger | the app being killed, a reboot |
 * | Android | `AlarmManager.setAndAllowWhileIdle`, drawn by the host's receiver | the app being killed; a reboot, re-armed at boot |
 * | Desktop, web | none: the service is absent | |
 *
 * Android is not exact to the minute: in Doze the system holds the alarm for its next maintenance
 * window, usually a few minutes. Nothing here asks for `SCHEDULE_EXACT_ALARM`.
 *
 * Asking is the system's notification question — the one push asks — but it registers nothing
 * for push: an app that only sets reminders makes this device known to no server (push-opt-in).
 *
 * Tapping a reminder opens the app that set it, through the same door a push tap does.
 */
public interface Reminders {

    /** What the system says now; never [PushPermission.Unavailable] — then the service is absent. */
    public suspend fun permission(): PushPermission

    /** The system's question while it can still be asked, its settings once it cannot. The answer after. */
    public suspend fun enable(): PushPermission

    /**
     * Shows [title] and [body] at [atEpochMillis]; a time already past shows it now. The same [id]
     * replaces the one set before. False when notifications are not allowed: nothing is kept then.
     */
    public suspend fun schedule(id: String, atEpochMillis: Long, title: String, body: String): Boolean

    /** Nothing happens for an id that is not waiting. */
    public suspend fun cancel(id: String)

    /** This app's reminders still waiting, soonest first. */
    public suspend fun pending(): List<PendingReminder>

    public companion object {
        public val Key: ServiceKey<Reminders> = ServiceKey("reminders")
    }
}

public data class PendingReminder(val id: String, val atEpochMillis: Long)
