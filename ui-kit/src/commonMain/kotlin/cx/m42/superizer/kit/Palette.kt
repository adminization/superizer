package cx.m42.superizer.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The accent a person picks in the host's Settings: the colour of the main button, the selected
 * navigation item, a chosen option, a chart. [Graphite] is the kit's own near-black, the look before
 * accents existed. [id] is what a host stores.
 */
public enum class KitAccent(public val id: String, internal val light: Color?, internal val dark: Color?) {
    Graphite("graphite", null, null),
    Violet("violet", Color(0xFF6E56CF), Color(0xFF7C66DC)),
    Blue("blue", Color(0xFF2563EB), Color(0xFF3B82F6)),
    Green("green", Color(0xFF16A34A), Color(0xFF22C55E)),
    Orange("orange", Color(0xFFEA580C), Color(0xFFF97316)),
    Pink("pink", Color(0xFFDB2777), Color(0xFFEC4899)),
    ;

    /** The colour itself, for a swatch in a picker. */
    public fun swatch(dark: Boolean): Color = (if (dark) this.dark else light) ?: if (dark) DarkKitColors.primary else LightKitColors.primary

    public companion object {
        /** The accent stored as [id], or null for an id this build does not know. */
        public fun byId(id: String?): KitAccent? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The colours [KitColors] has no slot for, kept apart so that type never changes: the page behind
 * the cards, the side navigation, the accent in its strong and soft forms, and the series of a chart.
 *
 * Read as `Kit.palette`. Derived from one [KitAccent] by [kitPalette]; a screen never mixes its own.
 */
@Immutable
public data class KitPalette(
    /** Behind cards and panels; `KitColors.background` stays the fill of fields and buttons. */
    val canvas: Color,
    /** A side navigation, one step off the canvas. */
    val sidebar: Color,
    /** The accent: a selected item's icon, a link, a chart's main series. */
    val brand: Color,
    val onBrand: Color,
    /** A tinted fill: the selected navigation item, a chosen option, an icon tile. */
    val brandSoft: Color,
    /** Text and icons on [brandSoft] — the accent, made readable at small sizes. */
    val onBrandSoft: Color,
    /** The border of a chosen option. */
    val brandBorder: Color,
    /** Series of a chart, the accent first. Six, then they repeat. */
    val chart: List<Color>,
)

/** The palette of [accent] in the light or the dark set. */
public fun kitPalette(accent: KitAccent, dark: Boolean): KitPalette {
    val base = if (dark) DarkKitColors else LightKitColors
    val brand = accent.swatch(dark)
    val graphite = accent == KitAccent.Graphite
    val others = listOf(Color(0xFF12A594), Color(0xFFF2A20C), Color(0xFFE5578A), Color(0xFF3E8BF0), Color(0xFF8B8D98))
    return KitPalette(
        canvas = if (dark) Color(0xFF09090B) else Color(0xFFF7F7F8),
        sidebar = if (dark) Color(0xFF0D0D10) else Color(0xFFFBFBFC),
        brand = brand,
        onBrand = if (graphite) base.onPrimary else Color.White,
        brandSoft = when {
            graphite -> base.secondary
            dark -> lerp(brand, Color.Black, 0.72f)
            else -> lerp(brand, Color.White, 0.9f)
        },
        onBrandSoft = when {
            graphite -> base.foreground
            dark -> lerp(brand, Color.White, 0.45f)
            else -> lerp(brand, Color.Black, 0.18f)
        },
        brandBorder = when {
            graphite -> base.input
            dark -> lerp(brand, Color.Black, 0.45f)
            else -> lerp(brand, Color.White, 0.65f)
        },
        chart = listOf(if (graphite) Color(0xFF6E56CF) else brand) + others,
    )
}

/**
 * [base] with the accent as its primary: the main button and a switch that is on take the accent.
 * [KitAccent.Graphite] leaves [base] as it is.
 */
public fun KitColors.withAccent(accent: KitAccent): KitColors {
    if (accent == KitAccent.Graphite) return this
    val brand = accent.swatch(isDark)
    return copy(primary = brand, onPrimary = Color.White, ring = lerp(brand, if (isDark) Color.Black else Color.White, 0.35f))
}

public val LocalKitPalette: ProvidableCompositionLocal<KitPalette> = staticCompositionLocalOf { kitPalette(KitAccent.Graphite, dark = false) }

/** `Kit.palette`, beside `Kit.colors`. */
public val Kit.palette: KitPalette
    @Composable @ReadOnlyComposable get() = LocalKitPalette.current

/**
 * [KitTheme] in [accent]: the colours with the accent as primary ([withAccent]) and the matching
 * [KitPalette]. A host calls it with the person's choice from Settings.
 */
@Composable
public fun KitTheme(
    dark: Boolean,
    accent: KitAccent,
    typography: KitTypography = kitTypography(),
    spacing: KitSpacing = KitSpacing(),
    shapes: KitShapes = KitShapes(),
    content: @Composable () -> Unit,
) {
    KitThemeOf(
        colors = (if (dark) DarkKitColors else LightKitColors).withAccent(accent),
        palette = kitPalette(accent, dark),
        typography = typography,
        spacing = spacing,
        shapes = shapes,
        content = content,
    )
}
