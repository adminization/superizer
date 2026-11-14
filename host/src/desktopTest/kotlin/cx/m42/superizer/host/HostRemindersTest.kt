package cx.m42.superizer.host

import cx.m42.superizer.Superizer
import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.reminders.HostReminders
import cx.m42.superizer.host.reminders.ReminderPlatform
import cx.m42.superizer.host.reminders.StoredReminder
import cx.m42.superizer.push.PushPermission
import cx.m42.superizer.runtime.PendingReminder
import cx.m42.superizer.runtime.Reminders
import cx.m42.superizer.testing.FakeNetwork
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** 0.9.0: local reminders, one app's ids under its own prefix, nothing kept without permission. */
class HostRemindersTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private class FakePlatform(var state: PushPermission = PushPermission.Granted, val grant: Boolean = true) : ReminderPlatform {
        val stored = LinkedHashMap<String, StoredReminder>()
        var asked = 0
        var settingsOpened = 0

        override suspend fun permission(): PushPermission = state
        override suspend fun requestPermission(): Boolean {
            asked++
            state = if (grant) PushPermission.Granted else PushPermission.Blocked
            return grant
        }
        override fun openSettings(): Boolean {
            settingsOpened++
            return true
        }
        override suspend fun schedule(reminder: StoredReminder) {
            stored[reminder.key] = reminder
        }
        override suspend fun cancel(key: String) {
            stored.remove(key)
        }
        override suspend fun pending(prefix: String): List<StoredReminder> = stored.values.filter { it.key.startsWith(prefix) }
    }

    @Test
    fun twoAppsWithTheSameIdDoNotMeet() = runBlocking {
        val platform = FakePlatform()
        val alpha = HostReminders(AppId("alpha"), platform, "unitool")
        val beta = HostReminders(AppId("beta"), platform, "unitool")
        assertTrue(alpha.schedule("end", 2_000, "A", "a"))
        assertTrue(beta.schedule("end", 1_000, "B", "b"))
        assertEquals(setOf("alpha:end", "beta:end"), platform.stored.keys)
        assertEquals(listOf(PendingReminder("end", 2_000)), alpha.pending())

        beta.cancel("end")
        assertEquals(setOf("alpha:end"), platform.stored.keys)
    }

    @Test
    fun theSameIdReplacesAndPendingIsSoonestFirst() = runBlocking {
        val platform = FakePlatform()
        val app = HostReminders(AppId("alpha"), platform, "unitool")
        app.schedule("end", 5_000, "t", "b")
        app.schedule("before", 4_000, "t", "b")
        app.schedule("end", 6_000, "t", "b")
        assertEquals(listOf(PendingReminder("before", 4_000), PendingReminder("end", 6_000)), app.pending())
    }

    @Test
    fun aTapIsAPushTapForTheSameApp() = runBlocking {
        val platform = FakePlatform()
        HostReminders(AppId("alpha"), platform, "unitool").schedule("end", 1, "t", "b")
        val data = platform.stored.getValue("alpha:end").tapData()
        assertEquals(mapOf("schemaVersion" to "1", "appId" to "alpha", "link" to "unitool://app/alpha"), data)
    }

    @Test
    fun nothingIsKeptWithoutPermission() = runBlocking {
        val platform = FakePlatform(state = PushPermission.Off)
        val app = HostReminders(AppId("alpha"), platform, "unitool")
        assertFalse(app.schedule("end", 1, "t", "b"))
        assertTrue(platform.stored.isEmpty())
    }

    @Test
    fun enableAsksWhileItCanAndOpensSettingsOnceItCannot() = runBlocking {
        val off = FakePlatform(state = PushPermission.Off)
        assertEquals(PushPermission.Granted, HostReminders(AppId("alpha"), off, "unitool").enable())
        assertEquals(1, off.asked)

        val blocked = FakePlatform(state = PushPermission.Blocked)
        assertEquals(PushPermission.Blocked, HostReminders(AppId("alpha"), blocked, "unitool").enable())
        assertEquals(0, blocked.asked)
        assertEquals(1, blocked.settingsOpened)

        val granted = FakePlatform()
        HostReminders(AppId("alpha"), granted, "unitool").enable()
        assertEquals(0, granted.asked + granted.settingsOpened)
    }

    @Test
    fun desktopOffersNoReminders() {
        val superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(testHostInfo())
            network(FakeNetwork())
            register(StubApp("alpha"))
        }
        assertFalse(Reminders.Key in superizer.hostInfo.services)
        assertNull(superizer.handler.appRuntime(AppId("alpha"))?.service(Reminders.Key))
    }
}
