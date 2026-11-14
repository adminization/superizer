package cx.m42.superizer.testing

import cx.m42.superizer.push.PushPermission
import cx.m42.superizer.runtime.PendingReminder
import cx.m42.superizer.runtime.Reminders

/**
 * Reminders in memory (0.9.0). [waiting] is what the system would show later, [shown] what it
 * already did; [advanceTo] moves the clock past some of them, as the hours passing would.
 */
public class FakeReminders(
    /** What the system says; [enable] turns [PushPermission.Off] into [grantOnAsk]'s answer. */
    public var permission: PushPermission = PushPermission.Granted,
    public var grantOnAsk: Boolean = true,
    private var now: Long = 0L,
) : Reminders {

    public data class Reminder(val id: String, val atEpochMillis: Long, val title: String, val body: String)

    public val waiting: LinkedHashMap<String, Reminder> = LinkedHashMap()
    public val shown: MutableList<Reminder> = mutableListOf()

    /** How many times the system's question was asked. */
    public var asked: Int = 0
        private set

    override suspend fun permission(): PushPermission = permission

    override suspend fun enable(): PushPermission {
        if (permission == PushPermission.Off) {
            asked++
            permission = if (grantOnAsk) PushPermission.Granted else PushPermission.Blocked
        }
        return permission
    }

    override suspend fun schedule(id: String, atEpochMillis: Long, title: String, body: String): Boolean {
        require(id.isNotEmpty()) { "A reminder id is not empty" }
        if (permission != PushPermission.Granted) return false
        val reminder = Reminder(id, atEpochMillis, title, body)
        if (atEpochMillis <= now) {
            waiting.remove(id)
            shown += reminder
        } else {
            waiting[id] = reminder
        }
        return true
    }

    override suspend fun cancel(id: String) {
        waiting.remove(id)
    }

    override suspend fun pending(): List<PendingReminder> =
        waiting.values.map { PendingReminder(it.id, it.atEpochMillis) }.sortedBy { it.atEpochMillis }

    /** Shows every reminder due by [epochMillis], soonest first. */
    public fun advanceTo(epochMillis: Long) {
        now = epochMillis
        val due = waiting.values.filter { it.atEpochMillis <= epochMillis }.sortedBy { it.atEpochMillis }
        due.forEach { waiting.remove(it.id) }
        shown += due
    }
}
