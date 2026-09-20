package cx.m42.superizer.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal, monochrome design tokens inspired by shadcn/ui, Radix and Tailwind: a white surface,
 * near-black text, a single black primary accent, and colour reserved for *state* — never for
 * chrome.
 *
 * Every screen in `ui`, and every app that wants to look like it belongs, reads its colours and
 * its type scale from here and nowhere else. There is no Material theme underneath, so a value not
 * in this file is a value some screen invented on its own.
 *
 * A class rather than an object (D37) for one reason: a second instance is a dark theme, and it
 * arrives without a single component changing.
 */
@Immutable
public data class Tokens(
    val background: Color,
    val foreground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val muted: Color,
    val border: Color,
    val surface: Color,
    /**
     * The backdrop the slide-out menu sits on. A touch darker than [surface], so the white screen
     * pushed on top of it reads as a card lifted off the page and its rounded corners have
     * something to show against.
     */
    val menuBackground: Color,
    /** The backdrop of a screen built out of card blocks: a light grey page with white cards on it. */
    val pageBackground: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val disabled: Color,
    /** The blue reserved for "this needs a person" — kept apart from [primary], which is chrome. */
    val accent: Color,
    val accentSubtle: Color,
    val dangerSubtle: Color,
    val radius: Dp,
    val title: TextStyle,
    val header: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val buttonLabel: TextStyle,
    /** Metadata scale — a footnote under a row, a build stamp. */
    val footnote: TextStyle,
    /** One line of digits, as large as a phone can carry. */
    val display: TextStyle,
    /** A key's label: big enough to hit by eye, light enough not to shout. */
    val keyLabel: TextStyle,
    /** Fixed-width, for the Service Menu's tables and the event log. */
    val mono: TextStyle,
)

/** The one set that exists today. A dark set is a second value of [Tokens], not a second component. */
public val LightTokens: Tokens = Tokens(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF09090B),
    primary = Color(0xFF18181B),
    primaryForeground = Color(0xFFFAFAFA),
    muted = Color(0xFF71717A),
    border = Color(0xFFE4E4E7),
    surface = Color(0xFFFAFAFA),
    menuBackground = Color(0xFFF4F4F5),
    pageBackground = Color(0xFFF2F2F7),
    danger = Color(0xFFDC2626),
    success = Color(0xFF1A7F37),
    warning = Color(0xFFD97706),
    disabled = Color(0xFFA1A1AA),
    accent = Color(0xFF0969DA),
    accentSubtle = Color(0xFFDDF4FF),
    dangerSubtle = Color(0xFFFFEBE9),
    radius = 12.dp,
    title = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    header = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    subtitle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    buttonLabel = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    display = TextStyle(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light),
    keyLabel = TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
    mono = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    ),
)

public val LocalTokens: ProvidableCompositionLocal<Tokens> = staticCompositionLocalOf { LightTokens }

/**
 * Read as `AppTheme.foreground` inside any composable — short enough to use at every call site,
 * which is what keeps a screen from inventing a colour of its own.
 */
public val AppTheme: Tokens
    @Composable
    @ReadOnlyComposable
    get() = LocalTokens.current

/** Wraps a tree in a set of tokens. A host calls it once, at the root. */
@Composable
public fun SuperizerTheme(tokens: Tokens = LightTokens, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalTokens provides tokens, content = content)
}
