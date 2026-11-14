package cx.m42.superizer.host.reminders

import cx.m42.superizer.app.AppId
import cx.m42.superizer.push.PushPermission
import cx.m42.superizer.runtime.PendingReminder
import cx.m42.superizer.runtime.Reminders
import kotlinx.serialization.Serializable

/**
 * One reminder as the platform keeps it (0.9.0). [key] is `<app id>:<the app's id>`, so two apps'
 * ids never meet; [link] is where a tap goes, `<scheme>://app/<app id>`.
 */
@Serializable
internal data class StoredReminder(
    val key: String,
    val appId: String,
    val atEpochMillis: Long,
    val title: String,
    val body: String,
    val link: String,
) {
    /**
     * What a tap hands to `PushTransport.deliverTap`: the shape of a push payload, so the router
     * that opens a tapped push opens a tapped reminder too, with the same rules (D25, idea/09).
     */
    fun tapData(): Map<String, String> = mapOf(
        "schemaVersion" to "1",
        "appId" to appId,
        "link" to link,
    )
}

/** What a platform does with local notifications; the host's per-app [Reminders] sit on top. */
internal interface ReminderPlatform {
    suspend fun permission(): PushPermission

    /** True when notifications can be shown after the question. */
    suspend fun requestPermission(): Boolean

    fun openSettings(): Boolean

    /** Replaces one with the same key; a time already past shows it at once. */
    suspend fun schedule(reminder: StoredReminder)

    suspend fun cancel(key: String)

    /** Every reminder still waiting whose key starts with [prefix]. */
    suspend fun pending(prefix: String): List<StoredReminder>
}

/** This platform's reminders; null where there are none (desktop, the web). */
internal expect fun platformReminders(): ReminderPlatform?

/** [Reminders] for one app, its ids under its own prefix. */
internal class HostReminders(
    private val appId: AppId,
    private val platform: ReminderPlatform,
    private val scheme: String,
) : Reminders {

    private val prefix = "${appId.value}:"

    override suspend fun permission(): PushPermission = platform.permission()

    override suspend fun enable(): PushPermission {
        when (platform.permission()) {
            PushPermission.Off -> platform.requestPermission()
            PushPermission.Blocked -> platform.openSettings()
            else -> Unit
        }
        return platform.permission()
    }

    override suspend fun schedule(id: String, atEpochMillis: Long, title: String, body: String): Boolean {
        require(id.isNotEmpty()) { "A reminder id is not empty" }
        if (platform.permission() != PushPermission.Granted) return false
        platform.schedule(
            StoredReminder(
                key = prefix + id,
                appId = appId.value,
                atEpochMillis = atEpochMillis,
                title = title,
                body = body,
                link = "$scheme://app/${appId.value}",
            ),
        )
        return true
    }

    override suspend fun cancel(id: String) {
        platform.cancel(prefix + id)
    }

    override suspend fun pending(): List<PendingReminder> =
        platform.pending(prefix)
            .map { PendingReminder(it.key.removePrefix(prefix), it.atEpochMillis) }
            .sortedBy { it.atEpochMillis }
}
