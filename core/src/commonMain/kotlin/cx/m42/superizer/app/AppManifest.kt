package cx.m42.superizer.app

import cx.m42.superizer.backup.BackupPolicy
import cx.m42.superizer.runtime.ServiceKey
import cx.m42.superizer.runtime.ServiceKeySerializer
import kotlin.jvm.JvmInline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stable, URL-safe identifier: what QR payloads, promo codes, deep links and push messages name.
 *
 * Kebab-case is checked in the constructor rather than by a linter, because every one of those
 * sources is a string arriving from outside the process — an id that could be `Calculator` in one
 * payload and `calculator` in the next is an id two of them disagree about.
 */
@Serializable
@JvmInline
public value class AppId(public val value: String) {
    init {
        require(value.matches(PATTERN)) { "AppId must be kebab-case: $value" }
    }

    override fun toString(): String = value

    public companion object {
        private val PATTERN = Regex("[a-z0-9][a-z0-9-]*")

        /** Null rather than a throw, for the parsers that meet untrusted text (06, 13). */
        public fun parseOrNull(value: String?): AppId? =
            value?.takeIf { it.matches(PATTERN) }?.let { AppId(it) }
    }
}

/**
 * A string that depends on the language. Apps ship their own tables; the host only needs the tag.
 *
 * A serializable table rather than the `fun interface` the sketch in 03 had: [AppManifest] has to
 * survive a round trip through JSON (D47) — the Service Menu shows it, and an extension manifest
 * is this, serialized — and a lambda does not. Resolution is on the primary subtag, so `ru-RU`
 * finds the `ru` entry.
 */
@Serializable
public class Localized(
    private val entries: Map<String, String>,
    /** What a language with no entry gets. The first entry, unless stated otherwise. */
    private val fallback: String,
) {
    public fun resolve(langTag: String): String {
        val primary = langTag.substringBefore('-').substringBefore('_').lowercase()
        return entries[primary] ?: entries[langTag] ?: fallback
    }

    override fun toString(): String = fallback

    override fun equals(other: Any?): Boolean =
        other is Localized && other.entries == entries && other.fallback == fallback

    override fun hashCode(): Int = entries.hashCode() * 31 + fallback.hashCode()
}

/** `localized("en" to "Calculator", "ru" to "Калькулятор")` — the first entry is the fallback. */
public fun localized(vararg entries: Pair<String, String>): Localized {
    require(entries.isNotEmpty()) { "localized() needs at least one entry" }
    return Localized(entries.toMap(), entries.first().second)
}

/**
 * How All Apps draws the tile. Core *describes* an icon; `ui` draws it — core has no
 * `compose.ui` dependency, and an icon that were a `Painter` could not travel in a manifest.
 */
@Serializable
public sealed interface AppIcon {

    /** The fallback everywhere: one letter in the app's own tile. */
    @Serializable
    @SerialName("letter")
    public data class Letter(val char: Char) : AppIcon

    /** A glyph from the host's hand-drawn pack ("calculator", "apps"). Unknown names fall back to [Letter]. */
    @Serializable
    @SerialName("named")
    public data class Named(val name: String) : AppIcon

    /**
     * The app's own icon as an SVG path (the `d` attribute over a 24×24 viewBox), drawn by `ui`
     * (D29). A string, not a painter: this is what lets an app ship an icon the host has never
     * seen without anybody editing the host's glyph pack — §24 in one field.
     */
    @Serializable
    @SerialName("path")
    public data class Path(val svgPath: String) : AppIcon
}

/** What the host shows about an app before launching it. */
@Serializable
public data class AppMetadata(
    val title: Localized,
    val description: Localized? = null,
    val icon: AppIcon = AppIcon.Letter('?'),
    /** Hidden apps are not listed on All Apps until an activation unlocks them (06, D8). */
    val hidden: Boolean = false,
    /** Free-form grouping for All Apps ("Tools", "Finance"). Null → ungrouped. */
    val category: String? = null,
)

/**
 * Everything static the host may want to know before running a line of the app (D47).
 *
 * One serializable object rather than a scattering of fields: the registry validates it whole, the
 * Service Menu shows it before launch, and the manifest of a downloaded extension (10) is this,
 * serialized. Declaration and behaviour are deliberately split — the manifest says *what*,
 * [SuperizerApp.setup] says *how*, and the registry checks the two agree.
 */
@Serializable
public data class AppManifest(
    val id: AppId,
    val version: String,
    /** Oldest host contract this app compiles against (D14). Newer than the host → the app is rejected, not crashed. */
    val minHostContract: Int = 1,
    val metadata: AppMetadata,
    /**
     * Optional services this app cannot work without (D19), checked at registration against
     * `HostInfo.services`. A host that lacks one rejects the app rather than letting it meet a
     * null on first use.
     */
    val requires: Set<@Serializable(ServiceKeySerializer::class) ServiceKey<*>> = emptySet(),
    /**
     * Deep-link paths under `unitool://app/<id>/…` this app serves. `setup()` must register a
     * handler for each — and for nothing else.
     */
    val deepLinks: Set<String> = emptySet(),
    /** Push topics it subscribes to. The host's TopicStore refuses a subscribe to an undeclared one (13). */
    val pushTopics: Set<String> = emptySet(),
    /** Hosts it talks to. Informational here; the allowlist of a Tier 2 bridge later (10). */
    val networkHosts: Set<String> = emptySet(),
    /**
     * The lock over its screen and the window flag (06). Anything but the default needs
     * `minHostContract = 2`: a host of contract 1 has never heard of this field and would show the
     * app with no lock at all, so the registry turns such a manifest away as inconsistent (D138).
     */
    val protection: AppProtection = AppProtection(),
    /**
     * What of this app the host's backup carries (05 §3.3): everything, by default. Anything else
     * needs `minHostContract = 3`, for the same reason as [protection] (D138).
     */
    val backup: BackupPolicy = BackupPolicy.All,
)
