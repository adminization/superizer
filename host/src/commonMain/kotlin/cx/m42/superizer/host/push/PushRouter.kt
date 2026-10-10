package cx.m42.superizer.host.push

import cx.m42.superizer.RoutePort
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.PushUse
import cx.m42.superizer.host.activation.Sha256
import cx.m42.superizer.push.PushDeviceStatus
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.PushMessage
import cx.m42.superizer.runtime.PushService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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

    /** What [appId] is subscribed to, read without replacing the flow [of] handed out. */
    public fun current(appId: AppId): Set<String> = _topics.value[appId.value].orEmpty().toSet()

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
 *
 * A subscription is kept by the host either way, but reaches the transport only while the person
 * uses the app ([live], push-opt-in D423): `subscribe` in `setup()` runs on every start for every
 * app, and an app nobody opened must not make this device known to Google.
 */
public class RoutedPush(
    private val appId: AppId,
    private val declared: Set<String>,
    private val topics: TopicStore,
    private val logger: Logger,
    private val use: PushUse = PushUse.Alerts,
    private val control: PushControl = PlatformPushControl,
    private val live: (AppId) -> Boolean = { true },
    override val enabled: StateFlow<Boolean> = MutableStateFlow(PushTransport.available).asStateFlow(),
    private val ask: suspend () -> Boolean = { control.requestPermission() },
) : PushService {

    private val _messages = MutableSharedFlow<PushMessage>(replay = 0, extraBufferCapacity = 16)
    override val messages: SharedFlow<PushMessage> get() = _messages.asSharedFlow()

    override val subscriptions: StateFlow<Set<String>> = topics.of(appId)

    /** D425: only for an app that said it draws notifications. */
    override suspend fun requestPermission(): Boolean {
        if (use != PushUse.Alerts) {
            logger.warn("requestPermission() ignored: the manifest says push = $use, not Alerts")
            return false
        }
        return ask()
    }

    override suspend fun subscribe(topic: String) {
        if (topic !in declared) {
            logger.warn("refusing to subscribe to '$topic': not in manifest.pushTopics")
            return
        }
        topics.add(appId, topic)
        if (live(appId)) control.subscribeTopic(topics.qualified(appId, topic))
    }

    override suspend fun unsubscribe(topic: String) {
        topics.remove(appId, topic)
        if (live(appId)) control.unsubscribeTopic(topics.qualified(appId, topic))
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
    /**
     * Whether the person uses the app (push-opt-in, D430). Every registered app is enabled, so
     * without this a data message for an app nobody opened would still reach it and draw.
     */
    private val used: (AppId) -> Boolean = { true },
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
        // A secret app takes no pushes (its manifest is refused if it asks), and one aimed at it
        // anyway is dropped as though the app did not exist — not as `Locked` (idea/09).
        if (appId == null || registry.get(appId) == null || registry.isSecret(appId)) {
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

        // The data road only (push-opt-in, D430): a tap is someone opening the app, through the
        // same door a link uses — and the system may have drawn that notification itself, from the
        // payload's `notification` block, without this router ever seeing it arrive.
        if (!used(appId)) {
            drop(appId, "NotUsed")
            return
        }

        // The data road: onto the *app-level* runtime, so an app with no screen open still hears it.
        val payload = data["data"]?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())
        pushOf(appId)?.deliver(PushMessage(data["topic"], payload, link, clock.now()))
        events.tryEmit(SuperizerEvent.PushReceived(appId, data["topic"]))

        // An app that said Silent draws nothing, whatever the server put in the payload (D417).
        if (data["silent"] != "true" && registry.pushUse(appId) != PushUse.Silent) {
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

/** What the phone keeps about its row on the server. Not backed up: a new phone is a new device. */
@Serializable
internal data class StoredDevice(
    val id: String? = null,
    /** `ds_…`, sealed by the host's vault when it has one. */
    val secret: String? = null,
    /** What the server was last told, so a start with nothing new sends nothing new. */
    val apps: List<String> = emptyList(),
    val tokenHash: String? = null,
    val syncedAt: Long? = null,
)

/**
 * The device row on the push server (push-opt-in, D422, D427), and only while the person uses a
 * push app: before that there is no token and no secret, and nothing is sent.
 *
 *  * first time — a `ds_…` secret is born here, sealed, and POST `/devices` carries its SHA-256;
 *  * every start, a new token, a new set of apps — PUT `/devices/me`;
 *  * 401 — the server forgot this device: POST again, with the same secret;
 *  * the last push app gone — one PUT with `apps: []`. The row, the id and the token stay, and the
 *    server sends nothing (D427).
 *
 * With no [api] (no `pushServer` in the builder) it logs what it would have sent, as it always did.
 */
public class DeviceRegistrar(
    private val used: StateFlow<Set<AppId>>,
    private val control: PushControl,
    private val api: DeviceApi?,
    private val seal: suspend (String) -> String,
    private val open: suspend (String) -> String,
    private val appVersion: String,
    private val locale: () -> String,
    private val clock: Clock,
    private val logger: Logger,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val retryMs: Long = 30_000,
) {
    private val _status = MutableStateFlow(statusOf(load(), null, null))
    public val status: StateFlow<PushDeviceStatus> get() = _status.asStateFlow()

    /** A PUT on every start (D422), but one: after that only a change sends anything. */
    private var updatedThisRun = false

    public fun attach(scope: CoroutineScope) {
        scope.launch {
            combine(control.token, used) { token, apps -> token to apps.map { it.value }.sorted() }
                .distinctUntilChanged()
                .collectLatest { (token, apps) ->
                    var attempt = 0
                    while (!sync(token, apps)) {
                        attempt++
                        delay(minOf(retryMs * attempt, MAX_RETRY_MS))
                    }
                }
        }
    }

    /** True when there is nothing left to do for this token and these apps. */
    internal suspend fun sync(token: PushRegistration?, apps: List<String>): Boolean {
        val api = api
        if (api == null) {
            if (token != null && apps.isNotEmpty()) {
                logger.info(
                    "device registration: token=${token.token.take(12)}… platform=${token.platform} " +
                        "apps=$apps — no server configured",
                )
            }
            return true
        }
        val stored = load()
        if (apps.isEmpty()) {
            if (stored.id == null || stored.apps.isEmpty()) return true
            return update(api, stored, DeviceBody(apps = emptyList()), tokenHash = stored.tokenHash, apps = apps)
        }
        // Waits for activate(): the gate wakes FCM as the first push app is used.
        if (token == null) return true
        val tokenHash = Sha256Hex(token.token)
        if (stored.id == null) return register(api, stored, token, apps)
        if (updatedThisRun && stored.apps == apps && stored.tokenHash == tokenHash) return true
        return update(api, stored, body(token, apps), tokenHash, apps, token)
    }

    private suspend fun register(api: DeviceApi, stored: StoredDevice, token: PushRegistration, apps: List<String>): Boolean {
        val secret = stored.secret?.let { sealed -> runCatching { open(sealed) }.getOrNull() } ?: newSecret()
        val kept = stored.copy(id = null, secret = seal(secret))
        // Written before the call: a POST that succeeds and a process that dies before the answer
        // is read must leave the same secret behind for the next try.
        save(kept)
        return when (val result = api.register(body(token, apps).copy(secretHash = Sha256Hex(secret)))) {
            is DeviceCall.Ok -> {
                updatedThisRun = true
                done(kept.copy(id = result.value, apps = apps, tokenHash = Sha256Hex(token.token), syncedAt = clock.now()), token)
                logger.info("device registered: ${result.value}, apps=$apps")
                true
            }
            DeviceCall.Unauthorized -> failed(kept, token, "unauthorized")
            is DeviceCall.Failed -> failed(kept, token, result.reason)
        }
    }

    private suspend fun update(
        api: DeviceApi,
        stored: StoredDevice,
        body: DeviceBody,
        tokenHash: String?,
        apps: List<String>,
        token: PushRegistration? = null,
    ): Boolean {
        val id = stored.id ?: return true
        val secret = stored.secret?.let { sealed -> runCatching { open(sealed) }.getOrNull() }
        if (secret == null) {
            // The vault changed under it: a new secret is a new device, which is all a lost one costs.
            logger.warn("device secret unreadable; registering again")
            save(StoredDevice())
            return token == null || register(api, StoredDevice(), token, apps)
        }
        return when (val result = api.update("$id.$secret", body)) {
            is DeviceCall.Ok -> {
                updatedThisRun = true
                done(stored.copy(apps = apps, tokenHash = tokenHash, syncedAt = clock.now()), token)
                true
            }
            DeviceCall.Unauthorized -> {
                logger.info("device $id unknown to the server; registering again")
                val forgotten = stored.copy(id = null, apps = emptyList())
                save(forgotten)
                // Nothing to register with when the last app went: the next one will.
                token == null || register(api, forgotten, token, apps)
            }
            is DeviceCall.Failed -> failed(stored, token, result.reason)
        }
    }

    private fun body(token: PushRegistration, apps: List<String>) = DeviceBody(
        platform = token.platform,
        pushToken = token.token,
        apps = apps,
        appVersion = appVersion,
        locale = locale(),
    )

    private fun done(device: StoredDevice, token: PushRegistration?) {
        save(device)
        _status.value = statusOf(device, token, null)
    }

    private fun failed(device: StoredDevice, token: PushRegistration?, reason: String): Boolean {
        logger.warn("device registration failed: $reason")
        _status.value = statusOf(device, token, reason)
        return false
    }

    private fun statusOf(device: StoredDevice, token: PushRegistration?, error: String?) = PushDeviceStatus(
        deviceId = device.id,
        tokenHead = token?.token?.take(12),
        apps = device.apps,
        syncedAt = device.syncedAt,
        error = error,
    )

    private fun load(): StoredDevice {
        val raw = SafePrefs.get(HostKeys.PUSH_DEVICE) ?: return StoredDevice()
        return runCatching { json.decodeFromString(StoredDevice.serializer(), raw) }.getOrDefault(StoredDevice())
    }

    private fun save(device: StoredDevice) {
        SafePrefs.put(HostKeys.PUSH_DEVICE, json.encodeToString(StoredDevice.serializer(), device))
    }

    private companion object {
        const val MAX_RETRY_MS = 10L * 60 * 1000
        const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        /** `ds_` + 32 base58 (remote-progress 01 §1); bytes ≥ 232 (= 58 × 4) are dropped, not wrapped. */
        fun newSecret(): String {
            val out = StringBuilder("ds_")
            while (out.length < 3 + 32) {
                for (byte in secureRandomBytes(64)) {
                    val value = byte.toInt() and 0xff
                    if (value >= 232) continue
                    out.append(BASE58[value % 58])
                    if (out.length == 3 + 32) break
                }
            }
            return out.toString()
        }

        @Suppress("FunctionName")
        fun Sha256Hex(value: String): String = Sha256.hex(value.encodeToByteArray())
    }
}
