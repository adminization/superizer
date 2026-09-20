package cx.m42.superizer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.i18n.hostStrings

/**
 * What the container shows when a launch failed or an app's coroutine threw (D31).
 *
 * Drawn by the host, never by the app: the app may not have been constructed, and if it crashed it
 * is the last thing that should be asked to render an apology. The reason is not shown to the user
 * — it is in the event log and the Service Menu, where someone can act on it.
 */
@Composable
public fun HostErrorScreen(
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp).testTag("host:error"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = hostStrings.errorTitle,
            style = tokens.header,
            color = tokens.foreground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row {
            AppButton(
                text = hostStrings.errorBack,
                onClick = onBack,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Default,
            )
            Spacer(Modifier.width(12.dp))
            AppButton(text = hostStrings.errorRetry, onClick = onRetry, size = ButtonSize.Default)
        }
    }
}
