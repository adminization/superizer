package cx.m42.superizer.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.lock.AppLockPort
import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.i18n.hostStrings
import kotlinx.coroutines.launch

/**
 * What a protected app's container shows instead of the app (06 §5.4, D128).
 *
 * A curtain rather than a gate: the app is launched as ever, and only its `Content()` is not drawn.
 * That is what lets one `when` branch cover every road onto the screen — a tile, the menu, a link,
 * an activation, a restored snapshot, the Service Menu, another app, a notification tap — and the
 * relock of a live session with them.
 *
 * The sheet comes up by itself once each time the curtain does. Cancelled, the curtain stays, and
 * the host's menu and back still work: the way out is always there.
 */
@Composable
internal fun LockCurtain(
    lock: AppLockPort,
    title: String,
    icon: AppIcon,
) {
    val tokens = AppTheme
    val strings = hostStrings
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<String?>(null) }

    suspend fun ask() {
        problem = when (val outcome = lock.unlock(strings.unlockPrompt(title), strings.unlockPromptSubtitle)) {
            is AuthOutcome.LockedOut -> outcome.message
            is AuthOutcome.Error -> outcome.message
            AuthOutcome.Cancelled, AuthOutcome.Success -> null
        }
    }

    LaunchedEffect(Unit) { ask() }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).testTag("shell:lock"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconGlyph(icon = icon, tint = tokens.foreground, size = 48.dp, fallbackLetter = title.firstOrNull() ?: '?')
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = tokens.header, color = tokens.foreground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text = strings.locked, style = tokens.body, color = tokens.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text = strings.lockedHint, style = tokens.footnote, color = tokens.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = strings.unlock,
            onClick = { scope.launch { ask() } },
            size = ButtonSize.Default,
            modifier = Modifier.testTag("shell:unlock"),
        )
        problem?.let {
            Spacer(Modifier.height(12.dp))
            // The system's own words — "Too many attempts. Try again later." — already localized.
            Text(
                text = it,
                style = tokens.footnote,
                color = tokens.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("shell:lock-problem"),
            )
        }
    }
}

/**
 * The host's banner over an app that wants a lock this device cannot give (§5.8, D140). It cannot be
 * dismissed; it goes away by itself once a screen lock is on, which the host checks every time the
 * app comes back to the front.
 */
@Composable
internal fun UnprotectedBanner(lock: AppLockPort, availability: AuthAvailability) {
    val tokens = AppTheme
    val strings = hostStrings
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("shell:unprotected"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (availability == AuthAvailability.NoScreenLock) {
                strings.unprotectedNoScreenLock
            } else {
                strings.unprotectedPlatform
            },
            style = tokens.footnote,
            color = tokens.danger,
            modifier = Modifier.weight(1f),
        )
        if (availability == AuthAvailability.NoScreenLock) {
            Spacer(Modifier.width(12.dp))
            AppButton(
                text = strings.setUpScreenLock,
                onClick = { lock.openDeviceSettings() },
                variant = ButtonVariant.Outline,
                size = ButtonSize.Sm,
                modifier = Modifier.testTag("shell:set-up-lock"),
            )
        }
    }
}
