package cx.m42.superizer.kit

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The colours of the kit, named as shadcn/ui names them: a surface and the colour drawn on it come in
 * pairs (`card` / `onCard`), so a component never has to guess which text colour goes on which fill.
 *
 * Colour is for state, not for chrome: `primary` is near-black in the light set, and the only hues
 * are the four states at the bottom.
 */
@Immutable
public data class KitColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val onCard: Color,
    val popover: Color,
    val onPopover: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    /** A quiet fill: a skeleton, a track, the inactive part of tabs. */
    val muted: Color,
    /** Secondary text: hints, captions, placeholders. */
    val mutedForeground: Color,
    /** The fill of a hovered or pressed row. */
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val onDestructive: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    /** "This needs a person": the blue Superizer keeps apart from `primary`. */
    val info: Color,
    val onInfo: Color,
    val border: Color,
    /** The border of a field. */
    val input: Color,
    /** The focus ring. */
    val ring: Color,
    /** What a modal dims the screen with. */
    val scrim: Color,
    val isDark: Boolean,
)

/** Zinc, light. The greys are the ones `ui-theme`'s `LightTokens` uses, so both look like one app. */
public val LightKitColors: KitColors = KitColors(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF09090B),
    card = Color(0xFFFFFFFF),
    onCard = Color(0xFF09090B),
    popover = Color(0xFFFFFFFF),
    onPopover = Color(0xFF09090B),
    primary = Color(0xFF18181B),
    onPrimary = Color(0xFFFAFAFA),
    secondary = Color(0xFFF4F4F5),
    onSecondary = Color(0xFF18181B),
    muted = Color(0xFFF4F4F5),
    mutedForeground = Color(0xFF71717A),
    accent = Color(0xFFF4F4F5),
    onAccent = Color(0xFF18181B),
    destructive = Color(0xFFDC2626),
    onDestructive = Color(0xFFFFFFFF),
    success = Color(0xFF16A34A),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFFF59E0B),
    onWarning = Color(0xFF18181B),
    info = Color(0xFF0969DA),
    onInfo = Color(0xFFFFFFFF),
    border = Color(0xFFE4E4E7),
    input = Color(0xFFE4E4E7),
    ring = Color(0xFFA1A1AA),
    scrim = Color(0x66000000),
    isDark = false,
)

/** Zinc, dark. */
public val DarkKitColors: KitColors = KitColors(
    background = Color(0xFF09090B),
    foreground = Color(0xFFFAFAFA),
    card = Color(0xFF18181B),
    onCard = Color(0xFFFAFAFA),
    popover = Color(0xFF18181B),
    onPopover = Color(0xFFFAFAFA),
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF18181B),
    secondary = Color(0xFF27272A),
    onSecondary = Color(0xFFFAFAFA),
    muted = Color(0xFF27272A),
    mutedForeground = Color(0xFFA1A1AA),
    accent = Color(0xFF27272A),
    onAccent = Color(0xFFFAFAFA),
    destructive = Color(0xFFEF4444),
    onDestructive = Color(0xFFFFFFFF),
    success = Color(0xFF22C55E),
    onSuccess = Color(0xFF052E16),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF18181B),
    info = Color(0xFF3B82F6),
    onInfo = Color(0xFFFFFFFF),
    border = Color(0xFF27272A),
    input = Color(0xFF3F3F46),
    ring = Color(0xFF71717A),
    scrim = Color(0x99000000),
    isDark = true,
)

/** The type scale of shadcn/ui's typography page, in one family. */
@Immutable
public data class KitTypography(
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    /** Body text. */
    val p: TextStyle,
    /** An introduction under a heading. */
    val lead: TextStyle,
    /** Emphasised body: a row's title, a total. */
    val large: TextStyle,
    /** A label, a caption, a button's text. */
    val small: TextStyle,
    /** Secondary text; [Text] gives it the muted colour. */
    val muted: TextStyle,
    /** Tables, logs, codes. */
    val mono: TextStyle,
)

/**
 * The scale in [fontFamily].
 *
 * On the web Compose has no system fonts, so an app whose text is not Latin passes a family that has
 * its script (for Russian, e.g. Inter from its resources); elsewhere the default is fine.
 */
public fun kitTypography(fontFamily: FontFamily = FontFamily.Default): KitTypography = KitTypography(
    h1 = TextStyle(fontFamily = fontFamily, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold),
    h2 = TextStyle(fontFamily = fontFamily, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    h3 = TextStyle(fontFamily = fontFamily, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    h4 = TextStyle(fontFamily = fontFamily, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    p = TextStyle(fontFamily = fontFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    lead = TextStyle(fontFamily = fontFamily, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    large = TextStyle(fontFamily = fontFamily, fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    small = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    muted = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
)

/** Tailwind's 1, 2, 3, 4, 6, 8: every gap and padding in the kit is one of these. */
@Immutable
public data class KitSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** Corner radii, from [radius] the way shadcn/ui derives them from `--radius`. */
@Immutable
public data class KitShapes(val radius: Dp = 10.dp) {
    val sm: Shape get() = RoundedCornerShape(radius - 4.dp)
    val md: Shape get() = RoundedCornerShape(radius - 2.dp)
    val lg: Shape get() = RoundedCornerShape(radius)
    val xl: Shape get() = RoundedCornerShape(radius + 4.dp)
    val full: Shape get() = RoundedCornerShape(percent = 50)
}

public val LocalKitColors: ProvidableCompositionLocal<KitColors> = staticCompositionLocalOf { LightKitColors }
public val LocalKitTypography: ProvidableCompositionLocal<KitTypography> = staticCompositionLocalOf { kitTypography() }
public val LocalKitSpacing: ProvidableCompositionLocal<KitSpacing> = staticCompositionLocalOf { KitSpacing() }
public val LocalKitShapes: ProvidableCompositionLocal<KitShapes> = staticCompositionLocalOf { KitShapes() }

/**
 * The colour text and icons take when nobody names one: a component that paints a fill sets it to
 * the matching `on…` colour, so a [Text] or an [Icon] inside it is right without being told.
 */
public val LocalContentColor: ProvidableCompositionLocal<Color> = compositionLocalOf { Color.Unspecified }

/**
 * Wraps a tree in the kit's theme. A host or an app calls it once, at the root; without it the kit
 * draws in the light set with the default font.
 */
@Composable
public fun KitTheme(
    dark: Boolean = false,
    colors: KitColors = if (dark) DarkKitColors else LightKitColors,
    typography: KitTypography = kitTypography(),
    spacing: KitSpacing = KitSpacing(),
    shapes: KitShapes = KitShapes(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKitColors provides colors,
        LocalKitTypography provides typography,
        LocalKitSpacing provides spacing,
        LocalKitShapes provides shapes,
        LocalContentColor provides colors.foreground,
        content = content,
    )
}

/** Read inside any composable: `Kit.colors.primary`, `Kit.spacing.lg`. */
public object Kit {
    public val colors: KitColors
        @Composable @ReadOnlyComposable get() = LocalKitColors.current
    public val typography: KitTypography
        @Composable @ReadOnlyComposable get() = LocalKitTypography.current
    public val spacing: KitSpacing
        @Composable @ReadOnlyComposable get() = LocalKitSpacing.current
    public val shapes: KitShapes
        @Composable @ReadOnlyComposable get() = LocalKitShapes.current
}

/** The content colour in effect, falling back to the theme's foreground outside any component. */
internal val currentContentColor: Color
    @Composable @ReadOnlyComposable get() = LocalContentColor.current.takeOrElse { LocalKitColors.current.foreground }

private inline fun Color.takeOrElse(block: () -> Color): Color = if (this != Color.Unspecified) this else block()
