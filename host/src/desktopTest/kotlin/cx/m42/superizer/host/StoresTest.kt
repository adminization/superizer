package cx.m42.superizer.host

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.push.PendingRoute
import cx.m42.superizer.host.push.TopicStore
import cx.m42.superizer.host.storage.PrefsSnapshotStore
import cx.m42.superizer.host.storage.HomeStore
import cx.m42.superizer.host.storage.PrefsStorageService
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.host.storage.UnlockStore
import cx.m42.superizer.registry.SessionSnapshot
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The four things the host stores, and the one operation that erases them all (D35). */
class StoresTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    @Test
    fun anAppSeesPlainKeysAndCannotReachAnother() = runTest {
        val mine = PrefsStorageService(AppId("calculator"))
        val theirs = PrefsStorageService(AppId("currency-converter"))

        mine.set("last", "42")
        theirs.set("last", "7")

        assertEquals("42", mine.get("last"))
        assertEquals("7", theirs.get("last"))
        assertEquals(setOf("last"), mine.keys())
    }

    @Test
    fun erasingAnAppLeavesEveryOtherAppAlone() = runTest {
        val mine = PrefsStorageService(AppId("calculator"))
        val theirs = PrefsStorageService(AppId("currency-converter"))
        mine.set("last", "42")
        theirs.set("last", "7")

        PrefsStorageService.erase(AppId("calculator"))

        assertNull(mine.get("last"))
        assertEquals("7", theirs.get("last"))
    }

    @Test
    fun unlocksSurviveARestartBecauseTheyAreStoredNotDerived() {
        UnlockStore().unlock(AppId("secret"))
        // A second store on the same preferences — which is what the next process is.
        assertTrue(AppId("secret") in UnlockStore().unlocked.value)
    }

    @Test
    fun lockingAnAppTakesItBackOutOfTheSet() {
        val store = UnlockStore()
        store.unlock(AppId("secret"))
        store.lock(AppId("secret"))
        assertTrue(UnlockStore().unlocked.value.isEmpty())
    }

    @Test
    fun aFreshInstallStartsWithTheHostsDefaultsOnHome() {
        val defaults = listOf(AppId("calculator"))
        assertEquals(defaults, HomeStore(defaults).home.value)
    }

    @Test
    fun onceTheUserHasTouchedHomeTheDefaultsNoLongerApply() {
        // Present-but-empty is a choice, and a later build with a longer default list must not
        // undo it: "nothing on Home" is what the user asked for.
        val store = HomeStore(listOf(AppId("calculator")))
        store.remove(AppId("calculator"))
        assertEquals(emptyList(), HomeStore(listOf(AppId("calculator"), AppId("converter"))).home.value)
    }

    @Test
    fun homeKeepsTheOrderAppsWereAddedInAcrossARestart() {
        val store = HomeStore(emptyList())
        store.add(AppId("converter"))
        store.add(AppId("calculator"))
        store.add(AppId("converter"))
        assertEquals(listOf(AppId("converter"), AppId("calculator")), HomeStore(emptyList()).home.value)
    }

    @Test
    fun aCorruptHomeListFallsBackToTheDefaults() {
        SafePrefs.put("host.home", "{not json")
        assertEquals(listOf(AppId("calculator")), HomeStore(listOf(AppId("calculator"))).home.value)
    }

    @Test
    fun aCorruptUnlockListMeansNoUnlocksRatherThanNoStartup() {
        SafePrefs.put("host.unlocked", "{not json")
        assertTrue(UnlockStore().unlocked.value.isEmpty())
    }

    @Test
    fun aSnapshotRoundTripsAndClearsCleanly() = runTest {
        val store = PrefsSnapshotStore()
        store.save(SessionSnapshot(AppId("calculator"), AppConfig.Empty, null, 5))

        val loaded = PrefsSnapshotStore().load()
        assertEquals(AppId("calculator"), loaded?.appId)
        assertEquals(5, loaded?.savedAt)

        store.clear()
        assertNull(PrefsSnapshotStore().load())
    }

    @Test
    fun aSnapshotFromAnOlderBuildMeansNoSnapshot() = runTest {
        // Not an error worth showing anyone: it means the same thing as having none.
        SafePrefs.put("host.session", """{"appId":"calculator"}""")
        assertNull(PrefsSnapshotStore().load())
    }

    @Test
    fun topicsArePrefixedWithTheAppIdSoTwoAppsCannotCollide() {
        val topics = TopicStore()
        assertEquals("chat.news", topics.qualified(AppId("chat"), "news"))
    }

    @Test
    fun topicsSurviveARestartSoOneSubscribeInSetupIsEnough() {
        TopicStore().add(AppId("chat"), "news")
        assertEquals(listOf("news"), TopicStore().all()["chat"])
    }

    @Test
    fun resettingAnAppLeavesItWithNoSubscriptions() {
        val topics = TopicStore()
        topics.add(AppId("chat"), "news")
        topics.clear(AppId("chat"))
        assertTrue(TopicStore().all()["chat"].isNullOrEmpty())
    }

    @Test
    fun aRouteIsConsumedExactlyOnce() {
        // A route is an instruction. A StateFlow that kept handing it back would reopen the app
        // every time the shell recomposed.
        val route = PendingRoute()
        route.deliver("unitool://app/chat")
        assertEquals("unitool://app/chat", route.consume())
        assertNull(route.consume())
        assertNull(route.route.value)
    }
}
