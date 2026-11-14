package cx.m42.superizer.host.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import cx.m42.superizer.host.platform.AndroidHost
import cx.m42.superizer.host.push.NotificationPermission
import cx.m42.superizer.host.push.PushTransport
import cx.m42.superizer.push.PushPermission
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal actual fun platformReminders(): ReminderPlatform? = AndroidReminders

/**
 * Local reminders on Android (0.9.0): `AlarmManager.setAndAllowWhileIdle`, a list in the host's
 * own preferences, and [ReminderReceiver] to draw them and to re-arm them after a reboot (alarms
 * do not survive one).
 *
 * Not `setExact…`: that needs `SCHEDULE_EXACT_ALARM`, off by default from Android 14 and refused by
 * Play for anything but alarm clocks and calendars. In Doze the alarm waits for the next
 * maintenance window — usually minutes.
 */
internal object AndroidReminders : ReminderPlatform {

    override suspend fun permission(): PushPermission = NotificationPermission.state()

    override suspend fun requestPermission(): Boolean = NotificationPermission.request()

    override fun openSettings(): Boolean = PushTransport.openSettings()

    override suspend fun schedule(reminder: StoredReminder) {
        val context = AndroidHost.appContext ?: return
        withContext(Dispatchers.IO) {
            ReminderStore.update(context) { list -> list.filter { it.key != reminder.key } + reminder }
            arm(context, reminder)
        }
    }

    override suspend fun cancel(key: String) {
        val context = AndroidHost.appContext ?: return
        withContext(Dispatchers.IO) {
            ReminderStore.update(context) { list -> list.filter { it.key != key } }
            alarms(context)?.cancel(alarmIntent(context, key))
            notifications(context)?.cancel(key, NOTIFICATION_ID)
        }
    }

    override suspend fun pending(prefix: String): List<StoredReminder> {
        val context = AndroidHost.appContext ?: return emptyList()
        return withContext(Dispatchers.IO) { ReminderStore.load(context).filter { it.key.startsWith(prefix) } }
    }

    /** Sets the alarm, or shows it now when its time is already past. */
    fun arm(context: Context, reminder: StoredReminder) {
        if (reminder.atEpochMillis <= System.currentTimeMillis()) {
            fire(context, reminder.key)
            return
        }
        alarms(context)?.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.atEpochMillis,
            alarmIntent(context, reminder.key),
        )
    }

    /** Draws the reminder and forgets it. One whose key is gone was cancelled after the alarm went off. */
    fun fire(context: Context, key: String) {
        var due: StoredReminder? = null
        ReminderStore.update(context) { list ->
            due = list.firstOrNull { it.key == key }
            list.filter { it.key != key }
        }
        val reminder = due ?: return
        val manager = notifications(context) ?: return
        ensureChannel(context, manager)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Extras in a push payload's shape: the host activity hands them to PushTransport.deliverTap,
        // and the router opens the app as it would for a tapped push.
        reminder.tapData().forEach { (name, value) -> launch.putExtra(name, value) }
        val tap = PendingIntent.getActivity(
            context,
            key.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(icon(context))
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setStyle(Notification.BigTextStyle().bigText(reminder.body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        manager.notify(key, NOTIFICATION_ID, notification)
    }

    /** After a reboot or an update: every alarm again, and the ones whose time passed meanwhile now. */
    fun rearm(context: Context) {
        ReminderStore.load(context).forEach { arm(context, it) }
    }

    private fun alarmIntent(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        key.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * The status-bar icon has to be a monochrome drawable the library cannot ship for every host, so
     * the host names it in its manifest, as Firebase's default icon is named:
     * `<meta-data android:name="cx.m42.superizer.notification_icon" android:resource="@drawable/…"/>`.
     * Without it, the launcher icon.
     */
    private fun icon(context: Context): Int {
        val meta = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        }.getOrNull()
        val named = meta?.getInt(ICON_META, 0) ?: 0
        return if (named != 0) named else context.applicationInfo.icon
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        // The receiver runs where the host's language setting is not loaded; the system's language
        // is what the channel list in Settings is in anyway.
        val name = if (Locale.getDefault().language == "ru") "Напоминания" else "Reminders"
        manager.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun alarms(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun notifications(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    const val ACTION_FIRE: String = "cx.m42.superizer.REMINDER"
    const val EXTRA_KEY: String = "cx.m42.superizer.reminder.key"
    private const val CHANNEL = "superizer.reminders"
    private const val NOTIFICATION_ID = 0x5265 // 'R','e'
    private const val ICON_META = "cx.m42.superizer.notification_icon"
}

/** The list, in preferences of its own: read by a receiver in a process where no host was built. */
internal object ReminderStore {
    private val serializer = ListSerializer(StoredReminder.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<StoredReminder> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun update(context: Context, change: (List<StoredReminder>) -> List<StoredReminder>) {
        val next = change(load(context))
        prefs(context).edit().putString(KEY, json.encodeToString(serializer, next)).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val FILE = "superizer.reminders"
    private const val KEY = "list"
}

/**
 * The alarm going off, and the boot that wiped every alarm. Named in this library's manifest, so
 * it is public and must keep its name.
 */
public class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AndroidReminders.ACTION_FIRE ->
                intent.getStringExtra(AndroidReminders.EXTRA_KEY)?.let { AndroidReminders.fire(context, it) }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                AndroidReminders.rearm(context)
        }
    }
}
