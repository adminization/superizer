package cx.m42.superizer.host.reminders

/** No notification here outlives the process, so an app gets no `Reminders`. */
internal actual fun platformReminders(): ReminderPlatform? = null
