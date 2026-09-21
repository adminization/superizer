package cx.m42.superizer.host.activation

import cx.m42.superizer.ActivationPort
import cx.m42.superizer.activation.Activation
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.storage.UnlockStore
import cx.m42.superizer.registry.AppHandler
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.registry.AppSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the payload a QR code carries, or the URL that stands in for one.
 *
 * Every branch returns a typed rejection and none of them throws: this function's whole input is
 * text somebody else produced — a code printed a year ago, a link pasted by hand, a payload from a
 * newer build — and an exception is not a thing the Activate screen can show anybody.
 */
public class QrPayloadParser(
    private val registry: AppRegistry,
    private val scheme: String,
    private val json: Json = Json,
) {
    public fun parse(text: String): ActivationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return rejected(ActivationResult.Reason.Malformed)
        // One parser for both forms (06): a compact `scheme://activate?…` URL is what a long QR
        // code degrades to, and a deep link is the same thing arriving by another route.
        if (trimmed.startsWith("$scheme://")) return parseUrl(trimmed)
        return parseJson(trimmed)
    }

    private fun parseJson(text: String): ActivationResult {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull()
            ?: return rejected(ActivationResult.Reason.Malformed)
        val obj = (root as? JsonObject) ?: return rejected(ActivationResult.Reason.Malformed)

        val version = obj["schemaVersion"]?.jsonPrimitive?.intOrNull
        if (version != null && version != SCHEMA_VERSION) {
            return rejected(ActivationResult.Reason.UnsupportedSchema)
        }
        if (obj["type"]?.jsonPrimitive?.contentOrNullSafe() != TYPE) {
            return rejected(ActivationResult.Reason.Malformed)
        }
        val appId = AppId.parseOrNull(obj["appId"]?.jsonPrimitive?.contentOrNullSafe())
            ?: return rejected(ActivationResult.Reason.Malformed)
        if (registry.get(appId) == null) return rejected(ActivationResult.Reason.UnknownApp)

        val config = (obj["config"] as? JsonObject)?.let { AppConfig(it) } ?: AppConfig.Empty
        return ActivationResult.Success(Activation(appId, config))
    }

    private fun parseUrl(url: String): ActivationResult {
        val withoutScheme = url.removePrefix("$scheme://")
        val path = withoutScheme.substringBefore('?')
        val params = queryOf(withoutScheme.substringAfter('?', ""))

        if (path == "activate") {
            val appId = AppId.parseOrNull(params["a"] ?: params["appId"])
                ?: return rejected(ActivationResult.Reason.Malformed)
            if (registry.get(appId) == null) return rejected(ActivationResult.Reason.UnknownApp)
            val raw = params["c"] ?: params["config"]
            val config = raw?.let { decodeConfig(it) } ?: AppConfig.Empty
            return ActivationResult.Success(Activation(appId, config))
        }
        return rejected(ActivationResult.Reason.Malformed)
    }

    private fun decodeConfig(raw: String): AppConfig {
        // Base64url first (that is what a compact QR carries), then plain JSON, then give up and
        // hand back an empty config — a broken config inside a *valid* activation is the handler's
        // `ConfigRejected`, not this parser's `Rejected` (06).
        val decoded = runCatching { base64UrlDecode(raw) }.getOrNull() ?: raw
        return runCatching { AppConfig(json.parseToJsonElement(decoded).jsonObject) }
            .recoverCatching { AppConfig(json.parseToJsonElement(raw).jsonObject) }
            .getOrDefault(AppConfig.Empty)
    }

    private fun rejected(reason: ActivationResult.Reason) = ActivationResult.Rejected(reason)

    public companion object {
        public const val SCHEMA_VERSION: Int = 1
        public const val TYPE: String = "app_activation"
    }
}

/**
 * A `JsonPrimitive` that is not a *string* is not an app id.
 *
 * `.content` on the number 7 answers "7", which is valid kebab-case and would turn a malformed
 * payload into an `UnknownApp` rejection — the wrong reason, shown to the wrong person.
 */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (isString) content else null

/** `k=v&k2=v2`, percent-decoded. Repeated keys keep the first, as every URL library does. */
internal fun queryOf(query: String): Map<String, String> = query
    .split('&')
    .filter { it.isNotEmpty() }
    .mapNotNull { pair ->
        val name = pair.substringBefore('=')
        if (name.isEmpty()) return@mapNotNull null
        name to percentDecode(pair.substringAfter('=', ""))
    }
    .reversed()
    .toMap()

internal fun percentDecode(value: String): String {
    if ('%' !in value && '+' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    val plussed = value.replace('+', ' ')
    while (i < plussed.length) {
        val c = plussed[i]
        if (c == '%' && i + 2 < plussed.length) {
            val hex = plussed.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte())
                i += 3
                continue
            }
        }
        c.toString().encodeToByteArray().forEach { bytes.add(it) }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/**
 * Base64url, by hand: `commonMain` has no decoder that is stable across all three targets, and the
 * alternative is a dependency for forty lines.
 */
internal fun base64UrlDecode(value: String): String {
    val clean = value.trimEnd('=')
    require(clean.all { it in BASE64_URL }) { "not base64url" }
    var buffer = 0
    var bits = 0
    val out = ArrayList<Byte>(clean.length * 3 / 4 + 1)
    for (c in clean) {
        buffer = (buffer shl 6) or BASE64_URL.indexOf(c)
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.add(((buffer shr bits) and 0xFF).toByte())
        }
    }
    return out.toByteArray().decodeToString()
}

/** Resolves a code to an activation. The MVP ships a table; a remote one is the same interface. */
public fun interface PromoCodeResolver {
    public suspend fun resolve(code: String): ActivationResult
}

/**
 * A table shipped with the host.
 *
 * Normalised so `test 2026`, `TEST-2026` and `test2026` are one code: these are read off paper and
 * typed by hand, and a code that only works if you guess the punctuation is a code that does not
 * work.
 */
public class LocalPromoCodes(codes: Map<String, ActivationResult>) : PromoCodeResolver {
    private val table = codes.mapKeys { normalise(it.key) }

    override suspend fun resolve(code: String): ActivationResult =
        table[normalise(code)] ?: ActivationResult.Rejected(ActivationResult.Reason.UnknownCode)

    public companion object {
        public fun normalise(code: String): String =
            code.filter { it.isLetterOrDigit() }.uppercase()
    }
}

/**
 * The one place an activation turns into a running app.
 *
 * Every route in 06 — a QR code, a promo code, a deep link, a notification tap, the Service Menu —
 * converges here, which is why an app cannot tell them apart and why there is exactly one set of
 * unlock rules to reason about.
 */
public class ActivationService(
    private val registry: AppRegistry,
    private val promo: PromoCodeResolver,
    private val unlocks: UnlockStore,
    private val handler: AppHandler,
    private val events: MutableSharedFlow<SuperizerEvent>,
    private val scheme: String,
    private val serviceCode: String?,
    private val json: Json = Json,
    /** What "unlock" does to the host: the store, and in a real host Home as well (D48). */
    private val onUnlock: (AppId) -> Unit = { unlocks.unlock(it) },
) : ActivationPort {

    private val parser = QrPayloadParser(registry, scheme, json)

    override suspend fun fromQr(text: String): ActivationResult = parser.parse(text)

    override suspend fun fromPromo(code: String): ActivationResult {
        // A host command, not an app (D20): the shell matches an enum, so there is no reserved app
        // id to collide with a real one — and one reserved name always becomes several.
        if (serviceCode != null && LocalPromoCodes.normalise(code) == LocalPromoCodes.normalise(serviceCode)) {
            return ActivationResult.HostCommand(ActivationResult.Command.OpenServiceMenu)
        }
        return promo.resolve(code)
    }

    /**
     * Links, in the four shapes of 06.
     *
     * `app/<id>/<path>` does **not** unlock (D25): naming an app is not knowing a secret about it.
     * An unknown path opens the app on an empty config and says so as an event — soft degradation,
     * the same choice agentiz made for an unrecognised push `type`.
     */
    override suspend fun fromDeepLink(url: String): ActivationResult {
        val trimmed = url.trim()
        // Not a URL at all — so it is a QR payload, arriving through the same door. The browser's
        // `?activate=` carries one of these and `?link=` carries the other, and neither the shell
        // nor the app should have to know which it was holding.
        if (!trimmed.startsWith("$scheme://")) return parser.parse(trimmed)
        val rest = trimmed.removePrefix("$scheme://")
        val path = rest.substringBefore('?')
        val params = queryOf(rest.substringAfter('?', ""))

        if (path.startsWith("activate")) return parser.parse(trimmed)
        if (!path.startsWith("app/")) return ActivationResult.Rejected(ActivationResult.Reason.Malformed)

        val segments = path.removePrefix("app/").split('/').filter { it.isNotEmpty() }
        val appId = AppId.parseOrNull(segments.firstOrNull())
            ?: return ActivationResult.Rejected(ActivationResult.Reason.Malformed)
        val app = registry.get(appId) ?: return ActivationResult.Rejected(ActivationResult.Reason.UnknownApp)
        if (app.metadata.hidden && appId !in unlocks.unlocked.value) {
            return ActivationResult.Rejected(ActivationResult.Reason.UnknownApp)
        }

        val subPath = segments.drop(1).joinToString("/")
        if (subPath.isEmpty()) return ActivationResult.Success(Activation(appId, AppConfig.Empty, unlock = false))

        val toConfig = handler.deepLinkHandler(appId, subPath)
        if (toConfig == null) {
            events.tryEmit(SuperizerEvent.DeepLinkUnmatched(appId, subPath))
            return ActivationResult.Success(Activation(appId, AppConfig.Empty, unlock = false))
        }
        val config = runCatching { toConfig(params) }.getOrDefault(AppConfig.Empty)
        return ActivationResult.Success(Activation(appId, config, unlock = false))
    }

    override suspend fun apply(activation: Activation, source: String): Result<AppSession> {
        if (activation.unlock) onUnlock(activation.appId)
        events.tryEmit(SuperizerEvent.Activated(activation.appId, source))
        // force, because an activation is the thing that is *allowed* to open a hidden app — that
        // is the entire difference between it and a link that merely names one (D25).
        return handler.launch(activation.appId, activation.config, force = true)
    }
}
