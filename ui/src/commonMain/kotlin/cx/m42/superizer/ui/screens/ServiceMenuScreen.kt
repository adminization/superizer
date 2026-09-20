package cx.m42.superizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.Superizer
import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.registry.RegistrationOutcome
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppTextField
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.Section
import cx.m42.superizer.ui.i18n.hostStrings
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The screen for whoever is building this, not for whoever is using it — Adminizer's
 * `ModuleManagerApp`, as one page.
 *
 * It is a table on purpose. Everything else in the host is a product surface and is designed;
 * this shows every app the registry was offered (including the ones it turned away, with the
 * reason), the manifest each one declares *before* it runs, the events as they happened and the
 * log as it was written. A shopfront here would hide exactly what it exists to show.
 *
 * Getting in: seven taps on the build stamp in a debug build, or the host's promo code in a
 * release (D34).
 */
@Composable
public fun ServiceMenuScreen(
    superizer: Superizer,
    onLaunched: (AppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    val scope = rememberCoroutineScope()
    val manifests by superizer.registry.manifests.collectAsState()
    val states by superizer.handler.states.collectAsState()
    val unlocked by superizer.unlocked.collectAsState()
    val events by superizer.diagnostics.recentEvents.collectAsState()
    val log by superizer.diagnostics.log.collectAsState()

    var expanded by remember { mutableStateOf<AppId?>(null) }
    var configText by remember { mutableStateOf("{}") }
    var pushText by remember { mutableStateOf(SAMPLE_PUSH) }
    var confirmReset by remember { mutableStateOf<AppId?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
            .testTag("service:root"),
    ) {
        Section(title = hostStrings.serviceApps) {
            manifests.forEach { (manifest, outcome) ->
                AppRow(
                    manifest = manifest,
                    outcome = outcome,
                    state = states[manifest.id]?.name ?: "—",
                    unlocked = manifest.id in unlocked,
                    expanded = expanded == manifest.id,
                    onToggle = { expanded = if (expanded == manifest.id) null else manifest.id },
                    onRun = {
                        scope.launch {
                            superizer.handler.launch(manifest.id, force = true)
                                .onSuccess { onLaunched(manifest.id) }
                        }
                    },
                    onRunWithConfig = {
                        scope.launch {
                            // Parsed here and handed on raw: a payload that is not an object is
                            // exactly the input `ConfigRejected` exists for, so it must reach the
                            // handler rather than be swallowed by this screen (D18).
                            val json = runCatching { Json.parseToJsonElement(configText).jsonObject }
                                .getOrElse { JsonObject(mapOf("__malformed" to Json.parseToJsonElement("\"$configText\""))) }
                            superizer.handler.launch(manifest.id, AppConfig(json), force = true)
                                .onSuccess { onLaunched(manifest.id) }
                        }
                    },
                    onToggleLock = {
                        scope.launch {
                            if (manifest.id in unlocked) {
                                superizer.diagnostics.lock(manifest.id)
                            } else {
                                superizer.diagnostics.unlock(manifest.id)
                            }
                        }
                    },
                    onToggleEnabled = {
                        scope.launch {
                            if (superizer.handler.isEnabled(manifest.id)) {
                                superizer.handler.disable(manifest.id)
                            } else {
                                superizer.handler.enable(manifest.id)
                            }
                        }
                    },
                    onReset = { confirmReset = manifest.id },
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(text = hostStrings.serviceRunWithConfig, style = tokens.label, color = tokens.muted)
                Spacer(Modifier.height(6.dp))
                AppTextField(
                    value = configText,
                    onValueChange = { configText = it },
                    placeholder = "{}",
                    label = "service:config",
                    singleLine = false,
                    minHeight = 72.dp,
                    textStyle = tokens.mono,
                    modifier = Modifier.testTag("service:config"),
                )
            }
        }

        Section(title = hostStrings.serviceSimulatePush) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                AppTextField(
                    value = pushText,
                    onValueChange = { pushText = it },
                    placeholder = SAMPLE_PUSH,
                    label = "service:push",
                    singleLine = false,
                    minHeight = 96.dp,
                    textStyle = tokens.mono,
                    modifier = Modifier.testTag("service:push"),
                )
                Spacer(Modifier.height(8.dp))
                AppButton(
                    text = hostStrings.serviceSimulatePush,
                    size = ButtonSize.Sm,
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.testTag("service:push-send"),
                    onClick = {
                        scope.launch {
                            // A push payload is `Map<String, String>` by the time FCM hands it over
                            // (13 §2), so that is what the simulator feeds the router — anything
                            // richer here would be testing a shape that never arrives.
                            val payload = runCatching {
                                Json.parseToJsonElement(pushText).jsonObject
                                    .mapValues { (_, v) -> v.toString().trim('"') }
                            }.getOrDefault(emptyMap())
                            superizer.diagnostics.simulatePush(payload)
                        }
                    },
                )
            }
        }

        Section(title = hostStrings.serviceEvents) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                    .testTag("service:events"),
            ) {
                if (events.isEmpty()) Text(text = "—", style = tokens.mono, color = tokens.muted)
                // Newest first: the thing that just happened is the thing being looked for.
                events.asReversed().forEach { event ->
                    Text(
                        text = event.toString(),
                        style = tokens.mono,
                        color = if (event is cx.m42.superizer.event.SuperizerEvent.ConfigRejected ||
                            event is cx.m42.superizer.event.SuperizerEvent.LaunchFailed ||
                            event is cx.m42.superizer.event.SuperizerEvent.Crashed
                        ) {
                            tokens.danger
                        } else {
                            tokens.foreground
                        },
                    )
                }
            }
        }

        Section(title = hostStrings.serviceLog) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                    .testTag("service:log"),
            ) {
                if (log.isEmpty()) Text(text = "—", style = tokens.mono, color = tokens.muted)
                log.asReversed().forEach { line ->
                    Text(text = line, style = tokens.mono, color = tokens.foreground)
                }
            }
        }
    }

    val resetting = confirmReset
    if (resetting != null) {
        ConfirmReset(
            onConfirm = {
                confirmReset = null
                scope.launch { superizer.diagnostics.reset(resetting) }
            },
            onDismiss = { confirmReset = null },
        )
    }
}

@Composable
private fun AppRow(
    manifest: AppManifest,
    outcome: RegistrationOutcome,
    state: String,
    unlocked: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRun: () -> Unit,
    onRunWithConfig: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleEnabled: () -> Unit,
    onReset: () -> Unit,
) {
    val tokens = AppTheme
    val rejected = outcome as? RegistrationOutcome.Rejected

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .testTag("service:app-${manifest.id.value}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = manifest.id.value,
                style = tokens.body,
                color = if (rejected != null) tokens.muted else tokens.foreground,
                modifier = Modifier.weight(1f),
            )
            Text(text = state, style = tokens.mono, color = tokens.muted)
        }
        Text(
            text = "v${manifest.version} · contract ≥ ${manifest.minHostContract}" +
                (if (manifest.metadata.hidden) " · hidden" else "") +
                (if (unlocked) " · unlocked" else ""),
            style = tokens.mono,
            color = tokens.muted,
        )
        if (rejected != null) {
            Text(
                text = "${hostStrings.serviceRejected}: ${rejected.reason}",
                style = tokens.mono,
                color = tokens.danger,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            // Shown *before* anything of the app has run (D47): what it asks for is knowable from
            // the manifest alone, which is the whole reason the manifest exists as one object.
            Text(text = hostStrings.serviceManifest, style = tokens.label, color = tokens.muted)
            Text(
                text = "requires=${manifest.requires.map { it.name }} " +
                    "deepLinks=${manifest.deepLinks} " +
                    "pushTopics=${manifest.pushTopics} " +
                    "networkHosts=${manifest.networkHosts}",
                style = tokens.mono,
                color = tokens.foreground,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = hostStrings.serviceRun, onClick = onRun, size = ButtonSize.Sm)
                AppButton(
                    text = hostStrings.serviceRunWithConfig,
                    onClick = onRunWithConfig,
                    size = ButtonSize.Sm,
                    variant = ButtonVariant.Secondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    text = if (unlocked) hostStrings.serviceLock else hostStrings.serviceUnlock,
                    onClick = onToggleLock,
                    size = ButtonSize.Sm,
                    variant = ButtonVariant.Secondary,
                )
                AppButton(
                    text = hostStrings.serviceDisable,
                    onClick = onToggleEnabled,
                    size = ButtonSize.Sm,
                    variant = ButtonVariant.Secondary,
                )
                AppButton(
                    text = hostStrings.serviceReset,
                    onClick = onReset,
                    size = ButtonSize.Sm,
                    variant = ButtonVariant.Destructive,
                )
            }
        }
    }
}

@Composable
private fun ConfirmReset(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val tokens = AppTheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(tokens.radius))
            .background(tokens.dangerSubtle)
            .padding(16.dp)
            .testTag("service:confirm-reset"),
    ) {
        Text(text = hostStrings.serviceResetConfirm, style = tokens.body, color = tokens.danger)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                text = hostStrings.closeDialogStay,
                onClick = onDismiss,
                size = ButtonSize.Sm,
                variant = ButtonVariant.Secondary,
            )
            AppButton(
                text = hostStrings.serviceReset,
                onClick = onConfirm,
                size = ButtonSize.Sm,
                variant = ButtonVariant.Destructive,
            )
        }
    }
}

/** What a real payload looks like (13 §2), so nobody has to remember the key names. */
private const val SAMPLE_PUSH =
    """{"schemaVersion":"1","appId":"test-app","topic":"demo","data":"{\"n\":1}"}"""
