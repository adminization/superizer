package cx.m42.apps.testapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppTextField
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.Section
import cx.m42.superizer.ui.components.ToggleRow
import kotlinx.serialization.json.Json

/**
 * One scrollable page, one card per runtime service (07).
 *
 * The Config card is the important one and the dullest to look at: it shows the config and nothing
 * else. There is no "opened by" line, because there is nothing for it to read — that is §12–13
 * made visible by an absence.
 */
@Composable
internal fun TestAppScreen(instance: TestAppInstance) {
    val tokens = AppTheme
    val strings = instance.runtime.locale.pick(TestStringTables, TestStringsEn)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Card("identity", strings.identity) {
            val host = instance.runtime.host
            Mono("appId = ${instance.runtime.appId}")
            Mono("host = ${host.name} ${host.version} (${host.build})")
            Mono("platform = ${host.platform} · contract ${host.contractVersion} · debug=${host.debug}")
        }

        Card("config", strings.config) {
            Mono(PrettyJson.encodeToString(TestConfig.serializer(), instance.config))
        }

        Card("lifecycle", strings.lifecycle) {
            val lifecycle by instance.runtime.lifecycle.collectAsState()
            val foregrounds by instance.foregrounds.collectAsState()
            val backgrounds by instance.backgrounds.collectAsState()
            val events by instance.events.collectAsState()
            Mono("state = $lifecycle · foreground ×$foregrounds · background ×$backgrounds")
            Spacer(Modifier.height(8.dp))
            events.asReversed().forEach { Mono(it) }
            Spacer(Modifier.height(8.dp))
            AppButton(strings.closeMe, onClick = instance::close, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
        }

        Card("state", strings.state) {
            val note by instance.note.collectAsState()
            Text(text = strings.stateHint, style = tokens.footnote, color = tokens.muted)
            Spacer(Modifier.height(6.dp))
            AppTextField(
                value = note,
                onValueChange = { instance.note.value = it },
                placeholder = strings.state,
                label = "test-app:note",
                modifier = Modifier.testTag("test-app:note"),
            )
        }

        Card("storage", strings.storage) {
            val launches by instance.launches.collectAsState()
            Mono("launches = $launches")
            Spacer(Modifier.height(8.dp))
            AppButton(strings.clear, onClick = instance::clearStorage, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
        }

        Card("network", strings.network) {
            val online by instance.runtime.network.online.collectAsState()
            val ping by instance.ping.collectAsState()
            Mono("online = $online")
            if (ping != null) Mono(ping.orEmpty())
            Spacer(Modifier.height(8.dp))
            AppButton(strings.ping, onClick = instance::ping, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
        }

        Card("analytics", strings.analytics) {
            Text(text = strings.analyticsHint, style = tokens.footnote, color = tokens.muted)
            Spacer(Modifier.height(8.dp))
            AppButton(strings.sendEvent, onClick = instance::sendEvent, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
        }

        Card("navigation", strings.navigation) {
            val error by instance.navigationError.collectAsState()
            if (error != null) Text(text = error.orEmpty(), style = tokens.mono, color = tokens.danger)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(strings.openCalculator, onClick = instance::openCalculatorScientific, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
                AppButton(strings.openUnknown, onClick = instance::openUnknownApp, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
            }
            Spacer(Modifier.height(8.dp))
            AppButton(strings.openSettings, onClick = { instance.runtime.navigation.openSettings() }, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
        }

        Card("services", strings.services) {
            // D19: what the host offers, and a null for a key nobody registered. The null is the
            // interesting half — it is what an app that did not declare `requires` would get.
            Mono("host.services = ${instance.runtime.host.services.map { it.name }}")
            Mono("service(ServiceKey(\"camera\")) = ${instance.runtime.service(CameraKey)}")
        }

        Card("secrets", strings.secrets) {
            val keys by instance.secretKeys.collectAsState()
            val findings by instance.runtime.diagnostics.findings.collectAsState()
            Mono("secrets.keys = $keys")
            Mono("diagnostics = ${findings.map { "${it.code}:${it.status}" }}")
            Spacer(Modifier.height(8.dp))
            AppButton(strings.storeSecret, onClick = instance::storeSecret, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
        }

        Card("keyring", strings.keyring) {
            val keyring = instance.runtime.service(cx.m42.superizer.ssh.SshKeyring.Key)
            val signed by instance.signed.collectAsState()
            Mono("service = ${if (keyring == null) "none" else "present"}")
            if (keyring != null) {
                val keys by keyring.keys.collectAsState()
                Mono("granted keys = ${keys.map { "${it.label} ${it.id}" }}")
            }
            signed?.let { Mono(it) }
            Spacer(Modifier.height(8.dp))
            AppButton(strings.signWithKey, onClick = instance::signWithKey, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
        }

        Card("locale", strings.locale) {
            val tag by instance.runtime.locale.langTag.collectAsState()
            Mono("langTag = $tag")
            Mono(strings.hello)
        }

        Card("haptics", strings.haptics) {
            AppButton(strings.tick, onClick = { instance.runtime.haptics.tick() }, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
        }

        Card("push", strings.push) {
            val enabled by instance.runtime.push.enabled.collectAsState()
            val subscriptions by instance.runtime.push.subscriptions.collectAsState()
            val last by instance.lastPush.collectAsState()
            var topic by remember { mutableStateOf("demo") }

            Mono("enabled = $enabled")
            Mono("subscriptions = $subscriptions")
            Mono("last = ${last?.let { "topic=${it.topic} data=${it.data} link=${it.link}" } ?: "—"}")
            Spacer(Modifier.height(8.dp))
            AppTextField(
                value = topic,
                onValueChange = { topic = it },
                placeholder = "topic",
                label = "test-app:topic",
                modifier = Modifier.testTag("test-app:topic"),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(strings.subscribe, onClick = { instance.subscribe(topic) }, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
                AppButton(strings.unsubscribe, onClick = { instance.unsubscribe(topic) }, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
                AppButton(strings.permission, onClick = instance::requestPushPermission, size = ButtonSize.Sm, variant = ButtonVariant.Secondary)
            }
        }

        Card("auth", strings.auth) {
            val session by instance.runtime.auth.session.collectAsState()
            Mono("session = ${session ?: "null (anonymous)"}")
        }

        Card("veto", strings.veto) {
            val blocked by instance.blockClose.collectAsState()
            ToggleRow(
                label = strings.blockClose,
                checked = blocked,
                onCheckedChange = { instance.blockClose.value = it },
            )
            Text(text = strings.vetoHint, style = tokens.footnote, color = tokens.muted, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun Card(tag: String, title: String, content: @Composable () -> Unit) {
    Section(title = title, modifier = Modifier.testTag("test-app:section-$tag")) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text = text, style = AppTheme.mono, color = AppTheme.foreground)
}

/** A key no host in the MVP registers — which is exactly what the Services card is showing. */
private val CameraKey = cx.m42.superizer.runtime.ServiceKey<Any>("camera")

private val PrettyJson = Json { prettyPrint = true; encodeDefaults = true }
