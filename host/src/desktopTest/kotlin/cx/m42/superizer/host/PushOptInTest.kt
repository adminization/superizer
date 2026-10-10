package cx.m42.superizer.host

import cx.m42.superizer.Superizer
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.PushUse
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.push.DeviceApi
import cx.m42.superizer.host.push.DeviceBody
import cx.m42.superizer.host.push.DeviceCall
import cx.m42.superizer.host.push.DeviceRegistrar
import cx.m42.superizer.host.push.IncomingPush
import cx.m42.superizer.host.push.Notifier
import cx.m42.superizer.host.push.PendingRoute
import cx.m42.superizer.host.push.PushControl
import cx.m42.superizer.host.push.PushRegistration
import cx.m42.superizer.host.push.PushRouter
import cx.m42.superizer.host.push.RoutedPush
import cx.m42.superizer.host.push.TopicStore
import cx.m42.superizer.push.PushPermission
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.testing.FakeClock
import cx.m42.superizer.testing.FakeDeviceAuthenticator
import cx.m42.superizer.testing.FakeNetwork
import cx.m42.superizer.testing.RecordingLogger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/** The platform, as the gate and the registrar see it, with every call written down. */
internal class FakePushControl(
    var state: PushPermission = PushPermission.Off,
    /** What the person answers the system's dialog. */
    var answer: Boolean = true,
) : PushControl {
    override val token = MutableStateFlow<PushRegistration?>(null)
    override val available: Boolean = true
    val calls = mutableListOf<String>()

    override suspend fun permission(): PushPermission = state

    override suspend fun requestPermission(): Boolean {
        calls += "request"
        if (answer) state = PushPermission.Granted
        return answer
    }

    override suspend fun activate() {
        calls += "activate"
        token.value = PushRegistration("fcm-token-0123456789", "android")
    }

    override suspend fun subscribeTopic(topic: String) {
        calls += "sub:$topic"
    }

    override suspend fun unsubscribeTopic(topic: String) {
        calls += "unsub:$topic"
    }

    override fun openSettings(): Boolean {
        calls += "settings"
        return true
    }
}

/**
 * Unitool push-opt-in against a whole host: the question comes when the person starts using a push
 * app — added from the catalog, opened, restored — and never at start, never for a reveal, never
 * for an app that takes no push (D418–D421, D423).
 */
class PushOptInTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private val control = FakePushControl()

    private fun host(defaultHome: List<String> = emptyList()): Superizer =
        Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(testHostInfo())
            network(FakeNetwork())
            clock(FakeClock())
            lifecycle(MutableStateFlow(HostLifecycle.Foreground))
            deviceAuthenticator(FakeDeviceAuthenticator())
            register(StubApp("chat", topics = setOf("news")))
            register(StubApp("mail", push = PushUse.Alerts))
            register(StubApp("sync", push = PushUse.Silent))
            register(StubApp("plain"))
            register(StubApp("bench", hidden = true, push = PushUse.Alerts))
            home(defaultHome.map(::AppId))
            pushControl = control
        }

    @Test
    fun aFreshStartAsksNothingAndWakesNothingEvenWithAPushAppOnHome() {
        val superizer = host(defaultHome = listOf("chat", "plain"))
        assertEquals(emptyList(), control.calls)
        assertEquals(emptySet(), superizer.push.used.value)
        assertEquals(null, control.token.value)
    }

    @Test
    fun addingFromTheCatalogAsksAtOnceAndHandsTheTopicsOver() = runBlocking {
        val superizer = host()
        // `setup()` subscribes on every start; with nobody using the app it stays in the store.
        superizer.handler.appRuntime(AppId("chat"))!!.push.subscribe("news")
        assertEquals(emptyList(), control.calls)

        superizer.addToHome(AppId("chat"))
        assertEquals(listOf("activate", "sub:chat.news", "request"), control.calls)
        assertEquals(setOf(AppId("chat")), superizer.push.used.value)
        assertEquals(PushPermission.Granted, superizer.push.permission.value)
    }

    @Test
    fun theFirstOpeningAsksForAnAppThatWasOnHomeFromTheStart() = runBlocking {
        val superizer = host(defaultHome = listOf("chat"))
        assertTrue(superizer.handler.launch(AppId("chat")).isSuccess)
        assertEquals(listOf("activate", "request"), control.calls)
        // Opening it again is not starting to use it again.
        superizer.handler.close(force = true)
        superizer.handler.launch(AppId("chat"))
        assertEquals(listOf("activate", "request"), control.calls)
    }

    @Test
    fun aRevealAsksNothingAndTheHiddenAppsFirstVisitDoes() = runBlocking {
        val superizer = host()
        superizer.diagnostics.unlock(AppId("bench"))
        assertTrue(AppId("bench") in superizer.home.value)
        assertEquals(emptyList(), control.calls)

        assertTrue(superizer.handler.launch(AppId("bench")).isSuccess)
        assertEquals(listOf("activate", "request"), control.calls)
    }

    @Test
    fun aSilentAppWakesFcmAndAsksNothing() = runBlocking {
        val superizer = host()
        superizer.addToHome(AppId("sync"))
        assertEquals(listOf("activate"), control.calls)
        superizer.addToHome(AppId("plain"))
        assertEquals(listOf("activate"), control.calls)
    }

    @Test
    fun whatTheSystemAllowedAlreadyIsNotAskedAgain() = runBlocking {
        control.state = PushPermission.Granted
        val superizer = host()
        superizer.addToHome(AppId("chat"))
        assertEquals(listOf("activate"), control.calls)
    }

    @Test
    fun eachNewAlertsAppAsksAgainAfterARefusal() = runBlocking {
        control.answer = false
        val superizer = host()
        superizer.addToHome(AppId("chat"))
        superizer.addToHome(AppId("mail"))
        assertEquals(listOf("activate", "request", "request"), control.calls)
        assertEquals(PushPermission.Off, superizer.push.permission.value)
    }

    @Test
    fun takingItOffHomeUnsubscribesAndKeepsTheTopicsForLater() = runBlocking {
        val superizer = host()
        val push = superizer.handler.appRuntime(AppId("chat"))!!.push
        push.subscribe("news")
        superizer.addToHome(AppId("chat"))
        control.calls.clear()

        superizer.removeFromHome(AppId("chat"))
        assertEquals(listOf("unsub:chat.news"), control.calls)
        assertEquals(emptySet(), superizer.push.used.value)
        assertEquals(setOf("news"), push.subscriptions.value)

        superizer.addToHome(AppId("chat"))
        assertTrue("sub:chat.news" in control.calls)
    }

    @Test
    fun theSetOutlivesTheProcessAndWakesFcmOnTheNextStart() = runBlocking {
        host().addToHome(AppId("chat"))
        control.calls.clear()
        val next = host()
        assertEquals(setOf(AppId("chat")), next.push.used.value)
        assertEquals(listOf("activate"), control.calls)
    }

    @Test
    fun turningOnFromSettingsAsksWhileItCanAndOpensSettingsOnceItCannot() = runBlocking {
        control.answer = false
        val superizer = host()
        superizer.addToHome(AppId("chat"))
        control.calls.clear()

        superizer.push.enable()
        assertEquals(listOf("request"), control.calls)

        control.state = PushPermission.Blocked
        superizer.push.enable()
        assertEquals(listOf("request", "settings"), control.calls)
    }

    @Test
    fun anAppMayAskAgainOnlyIfItSaidAlerts() = runBlocking {
        control.answer = false
        val superizer = host()
        superizer.addToHome(AppId("sync"))
        assertFalse(superizer.handler.appRuntime(AppId("sync"))!!.push.requestPermission())
        assertEquals(listOf("activate"), control.calls)
    }
}

/** The registrar against a fake server (D422, D427). */
class DeviceRegistrarTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private class FakeApi : DeviceApi {
        val calls = mutableListOf<Pair<String, DeviceBody>>()
        var next: DeviceCall<Unit> = DeviceCall.Ok(Unit)
        var registered = 0

        override suspend fun register(body: DeviceBody): DeviceCall<String> {
            calls += "POST" to body
            registered++
            return DeviceCall.Ok("d_Test0000000$registered")
        }

        override suspend fun update(credential: String, body: DeviceBody): DeviceCall<Unit> {
            calls += "PUT $credential" to body
            return next.also { next = DeviceCall.Ok(Unit) }
        }
    }

    private val token = PushRegistration("fcm-token-0123456789", "android")

    private fun registrar(api: DeviceApi?, logger: RecordingLogger = RecordingLogger()) = DeviceRegistrar(
        used = MutableStateFlow(emptySet()),
        control = FakePushControl(),
        api = api,
        seal = { "sealed:$it" },
        open = { it.removePrefix("sealed:") },
        appVersion = "1.2.3",
        locale = { "ru" },
        clock = Clock { 42 },
        logger = logger,
    )

    @Test
    fun nothingIsSentWithoutAPushAppOrWithoutAToken() = runBlocking {
        val api = FakeApi()
        val registrar = registrar(api)
        assertTrue(registrar.sync(token, emptyList()))
        assertTrue(registrar.sync(null, listOf("chat")))
        assertEquals(emptyList(), api.calls)
    }

    @Test
    fun registersOnceThenUpdatesOnlyWhatChanged() = runBlocking {
        val api = FakeApi()
        val registrar = registrar(api)
        assertTrue(registrar.sync(token, listOf("chat")))
        val (method, body) = api.calls.single()
        assertEquals("POST", method)
        assertEquals(listOf("chat"), body.apps)
        assertEquals("android", body.platform)
        assertEquals(token.token, body.pushToken)
        assertEquals(64, body.secretHash!!.length)
        assertEquals("d_Test00000001", registrar.status.value.deviceId)

        // The same token and apps once more: nothing to say.
        registrar.sync(token, listOf("chat"))
        assertEquals(1, api.calls.size)

        registrar.sync(token.copy(token = "fcm-token-rotated"), listOf("chat"))
        val put = api.calls.last()
        assertTrue(put.first.startsWith("PUT d_Test00000001.ds_"))
        assertEquals("fcm-token-rotated", put.second.pushToken)
        assertEquals(null, put.second.secretHash)
    }

    @Test
    fun aStartPutsOnceEvenWithNothingNew() = runBlocking {
        val api = FakeApi()
        registrar(api).sync(token, listOf("chat"))
        val next = registrar(api)
        next.sync(token, listOf("chat"))
        next.sync(token, listOf("chat"))
        assertEquals(listOf("POST", "PUT"), api.calls.map { it.first.substringBefore(' ') })
    }

    @Test
    fun aForgottenDeviceRegistersAgainWithTheSameSecret() = runBlocking {
        val api = FakeApi()
        val registrar = registrar(api)
        registrar.sync(token, listOf("chat"))
        val firstHash = api.calls.single().second.secretHash

        api.next = DeviceCall.Unauthorized
        assertTrue(registrar.sync(token, listOf("chat", "mail")))
        val post = api.calls.last()
        assertEquals("POST", post.first)
        assertEquals(firstHash, post.second.secretHash)
        assertEquals("d_Test00000002", registrar.status.value.deviceId)
    }

    @Test
    fun theLastAppGoneIsOnePutWithNoAppsAndTheRowStays() = runBlocking {
        val api = FakeApi()
        val registrar = registrar(api)
        registrar.sync(token, listOf("chat"))
        registrar.sync(token, emptyList())
        registrar.sync(token, emptyList())
        val put = api.calls.drop(1).single()
        assertTrue(put.first.startsWith("PUT d_Test00000001."))
        assertEquals(emptyList(), put.second.apps)
        // Left out, so the server keeps the token.
        assertEquals(null, put.second.pushToken)
        assertEquals("d_Test00000001", registrar.status.value.deviceId)

        registrar.sync(token, listOf("chat"))
        assertEquals("PUT", api.calls.last().first.substringBefore(' '))
        assertEquals(listOf("chat"), api.calls.last().second.apps)
    }

    @Test
    fun aFailureIsReportedAndRetried() = runBlocking {
        val api = FakeApi()
        val registrar = registrar(api)
        registrar.sync(token, listOf("chat"))
        api.next = DeviceCall.Failed("http-503")
        assertFalse(registrar.sync(token, listOf("chat", "mail")))
        assertEquals("http-503", registrar.status.value.error)
        assertTrue(registrar.sync(token, listOf("chat", "mail")))
        assertEquals(null, registrar.status.value.error)
        assertEquals(listOf("chat", "mail"), registrar.status.value.apps)
    }

    @Test
    fun withNoServerItOnlyLogs() = runBlocking {
        val logger = RecordingLogger()
        assertTrue(registrar(null, logger).sync(token, listOf("chat")))
        val line = logger.lines.single { "device registration" in it }
        assertTrue("no server configured" in line)
        assertFalse(token.token in line)
    }
}

/** The router's two push-opt-in rules (D417, D430). */
class PushRouterOptInTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private val events = MutableSharedFlow<SuperizerEvent>(replay = 64, extraBufferCapacity = 64)
    private val registry = AppRegistry(testHostInfo(), events).apply {
        register(StubApp("chat", topics = setOf("news")))
        register(StubApp("sync", push = PushUse.Silent))
    }
    private val topics = TopicStore()
    private val pushes = mutableMapOf<AppId, RoutedPush>()
    private val notified = mutableListOf<String>()
    private val pending = PendingRoute(events)
    private var used = setOf(AppId("chat"), AppId("sync"))

    private val router = PushRouter(
        registry = registry,
        unlocked = { emptySet() },
        isEnabled = { true },
        pushOf = { id -> pushes.getOrPut(id) { RoutedPush(id, emptySet(), topics, RecordingLogger()) } },
        pending = pending,
        notifier = Notifier { _, _, link -> notified += link },
        events = events,
        clock = Clock { 1 },
        scheme = "unitool",
        used = { it in used },
    )

    private fun dropped(): String? = events.replayCache.filterIsInstance<SuperizerEvent.PushDropped>().lastOrNull()?.reason

    @Test
    fun aSilentAppDrawsNothingWhateverThePayloadSays() {
        router.route(IncomingPush(mapOf("schemaVersion" to "1", "appId" to "sync", "title" to "Hi"), tapped = false))
        assertEquals(emptyList(), notified)
        assertNotNull(events.replayCache.filterIsInstance<SuperizerEvent.PushReceived>().singleOrNull())

        router.route(IncomingPush(mapOf("schemaVersion" to "1", "appId" to "chat", "title" to "Hi"), tapped = false))
        assertEquals(listOf("unitool://app/chat"), notified)
    }

    @Test
    fun dataForAnAppNobodyUsesIsDroppedButATapStillOpensIt() {
        used = emptySet()
        router.route(IncomingPush(mapOf("schemaVersion" to "1", "appId" to "chat", "title" to "Hi"), tapped = false))
        assertEquals("NotUsed", dropped())
        assertEquals(emptyList(), notified)

        router.route(IncomingPush(mapOf("schemaVersion" to "1", "appId" to "chat"), tapped = true))
        assertEquals("unitool://app/chat", pending.consume())
    }
}
