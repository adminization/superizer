package cx.m42.superizer.runtime

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.registry.AppState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/*
 * The platform, as an app is allowed to see it.
 *
 * Two rules hold this file together, and both are cheap now and impossible later:
 *
 *  - **Nothing here takes a type that cannot be JSON.** No `KSerializer`, no `Painter`, no
 *    platform class. That is what keeps a bridge to a non-Kotlin app (10) possible, and it is also
 *    what makes the fakes in `testing` twenty lines each.
 *  - **Every suspending call is main-safe.** The implementation goes to IO itself; an app calls it
 *    from the main dispatcher without a `withContext`. A fake that broke this would behave
 *    differently from the real thing, which is the one thing a fake must not do.
 *
 * A new service is *not* added as a field here. It gets a [ServiceKey] (D19). The fields below are
 * the frozen MVP set; changing them means raising `SuperizerContract.VERSION`.
 */

/**
 * Key/value that survives a restart. Namespaced by app id inside the host, so an app sees plain
 * keys and two apps cannot collide.
 */
public interface StorageService {
    public suspend fun get(key: String): String?
    public suspend fun set(key: String, value: String)
    public suspend fun remove(key: String)
    public suspend fun keys(): Set<String>
}

/** Typed sugar over the JSON strings above, here so no app rewrites it. */
public suspend inline fun <reified T> StorageService.getJson(key: String, json: Json = Json): T? =
    get(key)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

public suspend inline fun <reified T> StorageService.setJson(key: String, value: T, json: Json = Json) {
    set(key, json.encodeToString(value))
}

/**
 * Strings in, strings out.
 *
 * No credentials are injected (D32): an app that needs a token reads `auth.session` and sets the
 * header itself, so the host never has to guess which of an app's several hosts should be sent
 * one. Fifteen-second timeout, no retries — whether a call is safe to repeat is knowledge the app
 * has and the host does not.
 */
public interface NetworkService {
    /**
     * Best-effort connectivity (D32): `ConnectivityManager`, `navigator.onLine`, assumed true on
     * desktop. Show "offline" from this rather than from the first failure, so the user is told
     * before waiting for a timeout.
     */
    public val online: StateFlow<Boolean>

    public suspend fun request(request: NetworkRequest): NetworkResponse

    public suspend fun get(url: String, headers: Map<String, String> = emptyMap()): NetworkResponse =
        request(NetworkRequest("GET", url, headers))

    public suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse = request(NetworkRequest("POST", url, headers, body, contentType))
}

public data class NetworkRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
)

public data class NetworkResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, List<String>> = emptyMap(),
) {
    public val isSuccess: Boolean get() = status in 200..299
}

/** A transport failure, or a status the caller asked to be told about. */
public class NetworkException(
    public val status: Int?,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** `response.decode<Rates>()` — apps never import Ktor, or any HTTP library at all. */
public inline fun <reified T> NetworkResponse.decode(json: Json = LenientJson): T =
    json.decodeFromString(body)

/** Unknown keys are a server that grew a field, not a reason to fail. */
public val LenientJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Tagged `app:<id>` by the host (D33). In a release build the console gets `warn` and `error` only,
 * while the ring buffer behind the Service Menu keeps every level.
 */
public interface Logger {
    public fun debug(message: String, throwable: Throwable? = null)
    public fun info(message: String, throwable: Throwable? = null)
    public fun warn(message: String, throwable: Throwable? = null)
    public fun error(message: String, throwable: Throwable? = null)
}

/**
 * Names are the app's own — `"error"`. The host prefixes the app id before anything leaves the
 * process (D33), because two apps both reporting `error` are two apps nobody can tell apart.
 */
public interface AnalyticsService {
    public fun event(name: String, params: Map<String, String> = emptyMap())
    public fun screen(name: String)
}

/**
 * Everything an app may ask the host to do about navigation, and nothing more. Only on
 * [InstanceRuntime]: navigating from a background listener is not a thing an app gets to do (D27).
 */
public interface NavigationService {
    /**
     * Opens another app; the current one is closed first (D4) and its snapshot kept (D16).
     *
     * Typed failure rather than a boolean (D22). `AppResult.payload` is always null for now — the
     * shape is reserved for "open X and get a value back" so the signature never has to change.
     */
    public suspend fun openApp(id: AppId, config: AppConfig = AppConfig.Empty): Result<AppResult>

    /** Closes this app and returns to wherever it was opened from. */
    public suspend fun close()

    public fun openSettings()

    /** Opens an external URL in the platform's browser. False when the platform refused. */
    public fun openUrl(url: String): Boolean
}

public data class AppResult(val payload: JsonObject? = null)

public sealed class NavigationError(message: String) : Exception(message) {
    public class UnknownApp(public val id: AppId) : NavigationError("unknown app $id")
    public class Locked(public val id: AppId) : NavigationError("hidden app $id is not unlocked")
    public class Vetoed(public val id: AppId) : NavigationError("the open app refused to close")
}

public interface LocaleService {
    /** The BCP-47 tag in effect. Observable: switching language in Settings repaints the app. */
    public val langTag: StateFlow<String>

    /** Picks a table by primary subtag — the one place `startsWith("ru")` is written (D11). */
    public fun <S> pick(tables: Map<String, S>, fallback: S): S
}

/** A no-op when the host's haptics setting is off (D38). The app never checks the setting. */
public fun interface HapticsService {
    public fun tick()
}

/**
 * What an app sees of push (13): a topic to subscribe to and a flow of payloads.
 *
 * Deliberately missing: the device token (the host's business and the server's), drawing a
 * notification (global UI, §14), and navigating from a message handler (that is what the `link` in
 * the payload is for, and it goes through the same deep-link door as a QR code).
 */
public interface PushService {
    /** Transport present and permission granted. False on desktop and in the browser for now. */
    public val enabled: StateFlow<Boolean>

    /** The system dialog where one exists (Android 13+); a no-op true elsewhere. */
    public suspend fun requestPermission(): Boolean

    /** Persisted by the host and re-applied after a token refresh, so `setup()` subscribes once. */
    public suspend fun subscribe(topic: String)

    public suspend fun unsubscribe(topic: String)

    public val subscriptions: StateFlow<Set<String>>

    /** Payloads addressed to this app id. Replay 0: state belongs in storage, not in a flow. */
    public val messages: SharedFlow<PushMessage>
}

public data class PushMessage(
    val topic: String?,
    val data: JsonObject,
    val link: String?,
    val receivedAt: Long,
)

/** Stubbed for now — always anonymous. The shape is fixed so apps can be written against it. */
public interface AuthService {
    public val session: StateFlow<AuthSession?>
    public suspend fun signIn(): AuthSession?
    public suspend fun signOut()
}

public data class AuthSession(val userId: String, val displayName: String?, val token: String?)

/** A read-only view of the registry. No register or unregister here — §19. */
public interface AppsService {
    public fun list(): List<AppSummary>
    public fun get(id: AppId): AppSummary?
}

public data class AppSummary(
    val id: AppId,
    val version: String,
    val metadata: AppMetadata,
    val state: AppState,
    val unlocked: Boolean,
)
