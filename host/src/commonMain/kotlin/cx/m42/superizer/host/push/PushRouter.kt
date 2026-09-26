package cx.m42.superizer.host.push

import cx.m42.superizer.RoutePort
import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.runtime.AuthService
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.PushMessage
import cx.m42.superizer.runtime.PushService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A link that arrived before anything could act on it (13 §5): a notification tap into a dead
 * process, an `onNewIntent`, `?link=` in the browser's query, `--link=` on the command line.
 *
 * Consume-once, exactly as `Push.consumeRoute()` is in agentiz. A route is an instruction; a
 * `StateFlow` that kept handing it back would reopen the app every time the shell recomposed.
 */
public class PendingRoute(
    private val events: MutableSharedFlow<SuperizerEvent>? = null,
    /**
     * Apps whose links are secrets (D134): the 2FA app's `add?uri=otpauth://…secret=…`. Their
     * discarded links go into the log without the query.
     */
    private val sensitive: (AppId) -> Boolean = { false },
) : RoutePort {
    private val _route = MutableStateFlow<String?>(null)
    override val route: StateFlow<String?> get() = _route.asStateFlow()

    override fun deliver(link: String) {
        _route.value = link
    }

    override fun consume(): String? = _route.value.also { _route.value = null }

    override fun discard() {
        val link = _route.value ?: return
        _route.value = null
        AppId.parseOrNull(link.substringAfter("app/", "").substringBefore('/').substringBefore('?'))
            ?.let { id ->
                val shown = if (sensitive(id)) link.substringBefore('?').substringBefore('#') else link
                events?.tryEmit(SuperizerEvent.RouteDiscarded(id, shown))
            }
    }
}

/**
 * Which topics each app is subscribed to, kept by the host.
 *
 * Persisted so one `subscribe()` in `setup()` survives a token refresh — the app is not there to
 * re-subscribe, and asking it to be would leak the existence of tokens into the contract. Prefixed
 * with the app id before it reaches the transport, so two apps cannot claim one topic name.
 */
public class TopicStore(private val json: Json = Json) {

    private val _topics = MutableStateFlow(load())

    public fun of(appId: AppId): StateFlow<Set<String>> {
        // Derived on read rather than stored per app: the whole map is a handful of strings, and
        // one key means one thing to migrate later.
        val flow = MutableStateFlow(_topics.value[appId.value].orEmpty().toSet())
        perApp[appId] = flow
        return flow.asStateFlow()
    }

    private val perApp = mutableMapOf<AppId, MutableStateFlow<Set<String>>>()

    public fun add(appId: AppId, topic: String) {
        val current = _topics.value[appId.value].orEmpty()
        if (topic in current) return
        _topics.value = _topics.value + (appId.value to (current + topic))
        publish(appId)
    }

    public fun remove(appId: AppId, topic: String) {
        val current = _topics.value[appId.value].orEmpty()
        if (topic !in current) return
        _topics.value = _topics.value + (appId.value to (current - topic))
        publish(appId)
    }

    /** D35: an app that was reset is an app with no subscriptions. */
    public fun clear(appId: AppId) {
        _topics.value = _topics.value - appId.value
        publish(appId)
    }

    public fun all(): Map<String, List<String>> = _topics.value.mapValues { it.value.toList() }

    /** What the transport is told: the app id is the namespace, so `rates` cannot collide. */
    public fun qualified(appId: AppId, topic: String): String = "${appId.value}.$topic"

    private fun publish(appId: AppId) {
        perApp[appId]?.value = _topics.value[appId.value].orEmpty().toSet()
        SafePrefs.put(HostKeys.TOPICS, json.encodeToString(SERIALIZER, _topics.value))
    }

    private fun load(): Map<String, List<String>> {
        val raw = SafePrefs.get(HostKeys.TOPICS) ?: return emptyMap()
        return runCatching { json.decodeFromString(SERIALIZER, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        val SERIALIZER = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    }
}

/**
 * What an app sees of push: its own topics, and the messages addressed to it.
 *
 * `subscribe` refuses a topic the manifest does not declare (D47). The alternative — letting it
 * through — means the Service Menu's list of what an app listens to is a guess, and a manifest
 * that can be wrong is a manifest nobody trusts.
 */
public class RoutedPush(
    private val appId: AppId,
    private val declared: Set<String>,
    private val topics: TopicStore,
    private val logger: Logger,
) : PushService {

    override val enabled: StateFlow<Boolean> = MutableStateFlow(PushTransport.available).asStateFlow()

    private val _messages = MutableSharedFlow<PushMessage>(replay = 0, extraBufferCapacity = 16)
    override val messages: SharedFlow<PushMessage> get() = _messages.asSharedFlow()

    override val subscriptions: StateFlow<Set<String>> = topics.of(appId)

    override suspend fun requestPermission(): Boolean = PushTransport.requestPermission()

    override suspend fun subscribe(topic: String) {
        if (topic !in declared) {
            logger.warn("refusing to subscribe to '$topic': not in manifest.pushTopics")
            return
        }
        topics.add(appId, topic)
        PushTransport.subscribeTopic(topics.qualified(appId, topic))
    }

    override suspend fun unsubscribe(topic: String) {
        topics.remove(appId, topic)
        PushTransport.unsubscribeTopic(topics.qualified(appId, topic))
    }

    internal fun deliver(message: PushMessage) {
        _messages.tryEmit(message)
    }
}

/** Draws the notification a non-silent data message asks for. Nothing does, on these three targets. */
public fun interface Notifier {
    public fun show(title: String?, body: String?, link: String)
}

/**
 * Where a push message becomes either data or a screen (13 §1).
 *
 * The rules in 13 §4 are this class, line for line, and each is a test. The one worth restating:
 * a push **never** unlocks a hidden app (D25). A notification is something anyone with the app id
 * can cause; unlocking is something a QR code or a promo code does, because those carry a secret
 * and an app id does not.
 */
public class PushRouter(
    private val registry: AppRegistry,
    private val unlocked: () -> Set<AppId>,
    private val isEnabled: (AppId) -> Boolean,
    private val pushOf: (AppId) -> RoutedPush?,
    private val pending: PendingRoute,
    private val notifier: Notifier?,
    private val events: MutableSharedFlow<SuperizerEvent>,
    private val clock: Clock,
    private val scheme: String,
    private val json: Json = Json,
) {
    /** Collects the transport for the life of the host. */
    public fun attach(scope: CoroutineScope) {
        scope.launch { PushTransport.incoming.collect { route(it) } }
    }

    public fun route(push: IncomingPush) {
        val data = push.data

        if (data["schemaVersion"] != SCHEMA) {
            drop(null, "UnsupportedSchema")
            return
        }
        val appId = AppId.parseOrNull(data["appId"])
        if (appId == null || registry.get(appId) == null) {
            drop(appId, "UnknownApp")
            return
        }
        val app = registry.get(appId)!!
        if (app.metadata.hidden && appId !in unlocked()) {
            drop(appId, "Locked")
            return
        }
        if (!isEnabled(appId)) {
            drop(appId, "Disabled")
            return
        }
        val link = data["link"]
        if (link != null && !link.startsWith("$scheme://app/${appId.value}")) {
            drop(appId, "LinkMismatch")
            return
        }

        if (push.tapped) {
            // The screen road: one door for every link there is (D26), so a tap and a QR code go
            // through the same parser, the same unlock rules and the same handler.
            pending.deliver(link ?: "$scheme://app/${appId.value}")
            return
        }

        // The data road: onto the *app-level* runtime, so an app with no screen open still hears it.
        val payload = data["data"]?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())
        pushOf(appId)?.deliver(PushMessage(data["topic"], payload, link, clock.now()))
        events.tryEmit(SuperizerEvent.PushReceived(appId, data["topic"]))

        if (data["silent"] != "true") {
            notifier?.show(data["title"], data["body"], link ?: "$scheme://app/${appId.value}")
        }
    }

    private fun drop(appId: AppId?, reason: String) {
        events.tryEmit(SuperizerEvent.PushDropped(appId, reason))
    }

    private companion object {
        /** FCM `data` is `Map<String, String>`, so the version is a string too (13 §2). */
        const val SCHEMA = "1"
    }
}

/**
 * Token × session → the server. There is no server (question 18 in 09), so it logs — and the
 * Service Menu shows "token: …, registered: no server", which is the honest state of it.
 *
 * `appIds` goes in the registration so a server never sends an app that this build does not have.
 */
public class DeviceRegistrar(
    private val auth: AuthService,
    private val logger: Logger,
    private val appIds: () -> List<AppId>,
) {
    public fun attach(scope: CoroutineScope) {
        scope.launch {
            PushTransport.token.collect { registration ->
                if (registration == null) return@collect
                logger.info(
                    "device registration: token=${registration.token.take(12)}… " +
                        "platform=${registration.platform} user=${auth.session.value?.userId ?: "anonymous"} " +
                        "apps=${appIds().map { it.value }} — no server configured",
                )
            }
        }
    }
}
