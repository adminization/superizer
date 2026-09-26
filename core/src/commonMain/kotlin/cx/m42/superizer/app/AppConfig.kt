package cx.m42.superizer.app

import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Raw config as it travels: out of a QR payload, a promo code, a deep link, the Service Menu.
 *
 * JSON and not a typed object (D2) because the host must never learn an app's shape and the app
 * must never learn its config's source. Both halves of §11–13 fall out of this one decision.
 */
@Serializable
@JvmInline
public value class AppConfig(public val json: JsonObject) {
    public companion object {
        public val Empty: AppConfig = AppConfig(JsonObject(emptyMap()))

        /**
         * What an event says a protected app was configured with (D134). The shape of a config and
         * not an empty one, so a reader of the log can tell "had a config, not shown" from "had none".
         */
        public val Redacted: AppConfig = AppConfig(JsonObject(mapOf("…" to JsonPrimitive("redacted"))))
    }
}

/** Where a config came from stops mattering the moment it is decoded — this is the whole of it. */
public fun JsonObject.asConfig(): AppConfig = AppConfig(this)

/**
 * How an app reads its own config: typed inside the app, JSON outside it.
 *
 * @property schemaVersion bumped when the shape changes incompatibly; [migrate] brings older
 *   payloads up to date. A payload without the field is assumed to be current.
 * @property fallbackToDefault whether a broken payload may still launch the app on [default].
 *   False for an app where a wrong config is worse than no launch (the bench app sets it so, to
 *   keep that path exercised).
 */
public class AppConfigSpec<C : Any>(
    public val serializer: KSerializer<C>,
    public val default: C,
    public val schemaVersion: Int = 1,
    public val fallbackToDefault: Boolean = true,
    private val migrate: (fromVersion: Int, raw: JsonObject) -> JsonObject = { _, raw -> raw },
    private val json: Json = DefaultJson,
) {
    /**
     * Missing keys keep the default; a *broken* payload is a failure, not a silent default (D18).
     *
     * The handler turns the failure into a `ConfigRejected` event the Service Menu shows, and only
     * then applies [fallbackToDefault]. Quietly opening a plain calculator from a QR that said
     * "scientific" is the bug this exists to prevent.
     */
    public fun decode(config: AppConfig): Result<C> = runCatching {
        val from = config.json[SCHEMA_VERSION]?.jsonPrimitive?.intOrNull ?: schemaVersion
        val migrated = if (from == schemaVersion) config.json else migrate(from, config.json)
        json.decodeFromJsonElement(serializer, mergeWithDefault(migrated))
    }

    /** The inverse, stamped with [schemaVersion] so a payload written today is readable tomorrow. */
    public fun encode(value: C): AppConfig {
        val encoded = json.encodeToJsonElement(serializer, value).jsonObject
        return AppConfig(JsonObject(encoded + (SCHEMA_VERSION to JsonPrimitive(schemaVersion))))
    }

    /**
     * A shallow merge of the default's JSON under the incoming object, so `{"mode":"scientific"}`
     * sets one field instead of resetting every other one to whatever the serializer's own default
     * happens to be. Shallow on purpose: a deep merge would make a nested object impossible to
     * *clear*, and no MVP config has one.
     */
    private fun mergeWithDefault(incoming: JsonObject): JsonObject {
        val defaults = json.encodeToJsonElement(serializer, default).jsonObject
        return JsonObject(defaults + incoming.filterKeys { it != SCHEMA_VERSION })
    }

    public companion object {
        /** Reserved key: the version of the *shape*, which no app declares as a field of its own. */
        public const val SCHEMA_VERSION: String = "schemaVersion"

        /**
         * Lenient by design. A config arrives from a QR someone printed a year ago and from a
         * Service Menu text field; an unknown key there is a payload from a newer build, not a
         * reason to refuse to open.
         */
        public val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /** For apps that genuinely have no config. */
        public val None: AppConfigSpec<Unit> = AppConfigSpec(Unit.serializer(), Unit)
    }
}

/** The version stamped on a stored snapshot or config, or null when it carries none. */
public fun JsonObject.schemaVersionOrNull(): Int? =
    this[AppConfigSpec.SCHEMA_VERSION]?.jsonPrimitive?.intOrNull

/** Reads a schema version, defaulting to [ifAbsent] — what [AppInstance.restore] needs to migrate. */
public fun JsonObject.schemaVersion(ifAbsent: Int): Int =
    this[AppConfigSpec.SCHEMA_VERSION]?.jsonPrimitive?.int ?: ifAbsent
