package cx.m42.superizer.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Roles of a dense screen that [KitTypography]'s ten steps do not name, kept apart so that type never
 * changes. In the family of the theme's typography; read as `Kit.textStyles`.
 */
@Immutable
public data class KitTextStyles(
    /** A time under a row, a chart's axis, a hint in a badge: 12 sp. */
    val caption: TextStyle,
    /** The title over a group of navigation items: 11 sp, semibold, tracked. Upper-case the text yourself. */
    val overline: TextStyle,
    /** A row's title, a menu item, a tab: 14 sp medium — between `small` and `p`. */
    val label: TextStyle,
    /** A number in a column or a total: tabular figures, so digits line up. */
    val number: TextStyle,
    /** The number of a stat tile. */
    val metric: TextStyle,
    /** The one big number of a page: a balance. */
    val display: TextStyle,
    /** Logs and long identifiers. */
    val monoSmall: TextStyle,
)

/** The roles in [typography]'s families. */
public fun kitTextStyles(typography: KitTypography): KitTextStyles {
    val family = typography.p.fontFamily
    fun s(size: Double, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0, tnum: Boolean = false) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
        fontFeatureSettings = if (tnum) "tnum" else null,
    )
    return KitTextStyles(
        caption = s(12.0, 16),
        overline = s(11.0, 16, FontWeight.SemiBold, 0.06),
        label = s(14.0, 20, FontWeight.Medium),
        number = s(14.0, 20, tnum = true),
        metric = s(26.0, 32, FontWeight.SemiBold, -0.02, tnum = true),
        display = s(40.0, 44, FontWeight.SemiBold, -0.03, tnum = true),
        monoSmall = typography.mono.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
    )
}

public val LocalKitTextStyles: ProvidableCompositionLocal<KitTextStyles> = staticCompositionLocalOf { kitTextStyles(kitTypography()) }

/** `Kit.textStyles.caption`, beside `Kit.typography`. */
public val Kit.textStyles: KitTextStyles
    @Composable @ReadOnlyComposable get() = LocalKitTextStyles.current
