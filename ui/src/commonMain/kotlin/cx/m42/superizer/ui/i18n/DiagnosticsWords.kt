package cx.m42.superizer.ui.i18n

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cx.m42.superizer.diagnostics.DiagnosticsStrings
import cx.m42.superizer.diagnostics.RawDiagnosticsStrings

/**
 * The host's words for diagnostics codes (06 §6), for every screen and app under it.
 *
 * The host provides its table around the shell; an app's badge reads the same one, so an app never
 * has words of its own about how its data is kept and cannot disagree with the host. With no table
 * provided — a library screenshot, an app's own test — the codes themselves are shown, never nothing.
 */
public val LocalDiagnosticsStrings: ProvidableCompositionLocal<DiagnosticsStrings> =
    staticCompositionLocalOf { RawDiagnosticsStrings }
