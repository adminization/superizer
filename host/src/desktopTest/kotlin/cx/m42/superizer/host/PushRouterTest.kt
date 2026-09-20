package cx.m42.superizer.host

import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.push.IncomingPush
import cx.m42.superizer.host.push.Notifier
import cx.m42.superizer.host.push.PendingRoute
import cx.m42.superizer.host.push.PushRouter
import cx.m42.superizer.host.push.RoutedPush
import cx.m42.superizer.host.push.TopicStore
import cx.m42.superizer.host.storage.UnlockStore
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.testing.RecordingLogger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The rules table of 13 §4, line by line.
 *
 * Two of these are the reason the router exists as a separate object rather than as a branch inside
 * a Firebase callback: a push must never unlock a hidden app (D25), and a `link` that names a
 * different app than the payload is a malformed message rather than a redirect.
 */
class PushRouterTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private class Fixture(unlockedIds: Set<AppId> = emptySet(), val disabled: Set<AppId> = emptySet()) {
        val events = MutableSharedFlow<SuperizerEvent>(replay = 64, extraBufferCapacity = 64)
        val registry = AppRegistry(testHostInfo(), events).apply {
            register(StubApp("chat", topics = setOf("news")))
            register(StubApp("secret", hidden = true))
        }
        val unlocks = UnlockStore(events).also { store -> unlockedIds.forEach { store.unlock(it) } }
        val topics = TopicStore()
        val logger = RecordingLogger()
        val pushes = mutableMapOf<AppId, RoutedPush>()
        val pending = PendingRoute(events)
        val notified = mutableListOf<Triple<String?, String?, String>>()

        val router = PushRouter(
            registry = registry,
            unlocked = { unlocks.unlocked.value },
            isEnabled = { it !in disabled },
            pushOf = { id ->
                pushes.getOrPut(id) {
                    RoutedPush(id, registry.get(id)?.manifest?.pushTopics.orEmpty(), topics, logger)
                }
            },
            pending = pending,
            notifier = Notifier { title, body, link -> notified += Triple(title, body, link) },
            events = events,
            clock = Clock { 1_000 },
            scheme = "unitool",
        )

        fun dropped(): String? = events.replayCache
            .filterIsInstance<SuperizerEvent.PushDropped>()
            .lastOrNull()
            ?.reason

        fun names(): List<String> = events.replayCache.map { it::class.simpleName!! }
    }

    private fun payload(vararg pairs: Pair<String, String>) = mapOf("schemaVersion" to "1", *pairs)

    @Test
    fun aPayloadFromANewerSchemaIsDropped() {
        val f = Fixture()
        f.router.route(IncomingPush(mapOf("schemaVersion" to "2", "appId" to "chat"), tapped = false))
        assertEquals("UnsupportedSchema", f.dropped())
    }

    @Test
    fun aPayloadWithNoAppIdOrAnUnknownOneIsDropped() {
        val f = Fixture()
        f.router.route(IncomingPush(payload(), tapped = false))
        assertEquals("UnknownApp", f.dropped())

        f.router.route(IncomingPush(payload("appId" to "weather"), tapped = false))
        assertEquals("UnknownApp", f.dropped())

        f.router.route(IncomingPush(payload("appId" to "Chat"), tapped = false))
        assertEquals("UnknownApp", f.dropped())
    }

    @Test
    fun aPushNeverRevealsAHiddenApp() {
        // D25. A notification is something anyone who knows an app id can cause; unlocking is
        // something only a QR code or a promo code does, because those carry a secret.
        val f = Fixture()
        f.router.route(IncomingPush(payload("appId" to "secret"), tapped = true))
        assertEquals("Locked", f.dropped())
        assertNull(f.pending.route.value)
    }

    @Test
    fun anUnlockedHiddenAppReceivesItsPushesLikeAnyOther() {
        val f = Fixture(unlockedIds = setOf(AppId("secret")))
        f.router.route(IncomingPush(payload("appId" to "secret"), tapped = true))
        assertEquals("unitool://app/secret", f.pending.route.value)
    }

    @Test
    fun anAppTurnedOffInTheServiceMenuGetsNothing() {
        val f = Fixture(disabled = setOf(AppId("chat")))
        f.router.route(IncomingPush(payload("appId" to "chat"), tapped = false))
        assertEquals("Disabled", f.dropped())
    }

    @Test
    fun aLinkThatNamesADifferentAppIsAMalformedMessage() {
        val f = Fixture()
        f.router.route(
            IncomingPush(payload("appId" to "chat", "link" to "unitool://app/secret/x"), tapped = true),
        )
        assertEquals("LinkMismatch", f.dropped())
    }

    @Test
    fun aTapBecomesARouteAndNothingElse() {
        // D26: the screen road goes through the same door as a QR code, so there is one set of
        // unlock rules and one parser to reason about.
        val f = Fixture()
        f.router.route(
            IncomingPush(payload("appId" to "chat", "link" to "unitool://app/chat/thread?id=7"), tapped = true),
        )
        assertEquals("unitool://app/chat/thread?id=7", f.pending.route.value)
        assertTrue(f.notified.isEmpty())
    }

    @Test
    fun aTapWithNoLinkStillOpensTheApp() {
        val f = Fixture()
        f.router.route(IncomingPush(payload("appId" to "chat"), tapped = true))
        assertEquals("unitool://app/chat", f.pending.route.value)
    }

    @Test
    fun aDataMessageReachesTheAppAndDrawsANotification() {
        val f = Fixture()
        f.router.route(
            IncomingPush(
                payload(
                    "appId" to "chat",
                    "topic" to "news",
                    "title" to "Курс обновлён",
                    "body" to "USD/RUB 92.10",
                    "data" to """{"rate":92.1}""",
                ),
                tapped = false,
            ),
        )
        assertTrue("PushReceived" in f.names())
        assertEquals(1, f.notified.size)
        assertEquals("Курс обновлён", f.notified.single().first)
    }

    @Test
    fun aSilentMessageUpdatesTheAppWithoutInterruptingAnybody() {
        val f = Fixture()
        f.router.route(
            IncomingPush(payload("appId" to "chat", "silent" to "true", "data" to """{"n":1}"""), tapped = false),
        )
        assertTrue("PushReceived" in f.names())
        assertTrue(f.notified.isEmpty())
    }

    @Test
    fun aMalformedDataBlobIsAnEmptyObjectRatherThanADroppedMessage() {
        val f = Fixture()
        f.router.route(IncomingPush(payload("appId" to "chat", "data" to "not json"), tapped = false))
        assertTrue("PushReceived" in f.names())
    }
}
