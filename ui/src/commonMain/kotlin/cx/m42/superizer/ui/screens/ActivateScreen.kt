package cx.m42.superizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.Superizer
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppId
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppTextField
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.i18n.HostStrings
import cx.m42.superizer.ui.i18n.hostStrings
import kotlinx.coroutines.launch

/**
 * Two fields and a button: a promo code, and the text a camera would have read out of a QR code.
 *
 * No camera (D7). The contract of activation does not depend on one — a QR payload is a string,
 * and where the string came from is the platform's problem, not the model's. Saying so on the
 * screen is better than an empty viewfinder that never gets built.
 */
@Composable
public fun ActivateScreen(
    superizer: Superizer,
    onActivated: (AppId) -> Unit,
    onHostCommand: (ActivationResult.Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    val strings = hostStrings
    val scope = rememberCoroutineScope()

    var promo by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun handle(result: ActivationResult) {
        when (result) {
            is ActivationResult.Success -> {
                error = null
                scope.launch {
                    superizer.activation.apply(result.activation, source = if (promo.isNotBlank()) "promo" else "qr")
                        .onSuccess { onActivated(result.activation.appId) }
                        .onFailure { error = it.message }
                }
            }

            is ActivationResult.HostCommand -> {
                error = null
                onHostCommand(result.command)
            }

            is ActivationResult.Rejected -> error = strings.reasonText(result.reason)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(text = strings.activatePromoLabel, style = tokens.label, color = tokens.muted)
        Spacer(Modifier.height(6.dp))
        AppTextField(
            value = promo,
            onValueChange = { promo = it; error = null },
            placeholder = strings.activatePromoLabel,
            label = strings.activatePromoLabel,
            modifier = Modifier.testTag("activate:promo"),
        )

        Spacer(Modifier.height(20.dp))

        Text(text = strings.activateQrLabel, style = tokens.label, color = tokens.muted)
        Spacer(Modifier.height(6.dp))
        AppTextField(
            value = payload,
            onValueChange = { payload = it; error = null },
            placeholder = strings.activateQrLabel,
            label = strings.activateQrLabel,
            singleLine = false,
            minHeight = 120.dp,
            modifier = Modifier.testTag("activate:qr"),
        )

        Spacer(Modifier.height(8.dp))
        Text(text = strings.activateCameraNote, style = tokens.footnote, color = tokens.muted)

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error.orEmpty(),
                style = tokens.label,
                color = tokens.danger,
                modifier = Modifier.testTag("activate:error"),
            )
        }

        Spacer(Modifier.height(20.dp))
        AppButton(
            text = strings.activateApply,
            size = ButtonSize.Default,
            modifier = Modifier.fillMaxWidth().testTag("activate:apply"),
            onClick = {
                scope.launch {
                    // The code wins when both are filled: it is the shorter thing to type, so it is
                    // the thing someone deliberately typed.
                    val result = when {
                        promo.isNotBlank() -> superizer.activation.fromPromo(promo)
                        payload.isNotBlank() -> superizer.activation.fromQr(payload)
                        else -> ActivationResult.Rejected(ActivationResult.Reason.Malformed)
                    }
                    handle(result)
                }
            },
        )
    }
}

/** Every rejection has a sentence, so the screen never has to show an enum name. */
private fun HostStrings.reasonText(reason: ActivationResult.Reason): String = when (reason) {
    ActivationResult.Reason.Malformed -> activateErrorMalformed
    ActivationResult.Reason.UnsupportedSchema -> activateErrorUnsupported
    ActivationResult.Reason.UnknownApp -> activateErrorUnknownApp
    ActivationResult.Reason.UnknownCode -> activateErrorUnknownCode
    ActivationResult.Reason.Expired -> activateErrorExpired
}
