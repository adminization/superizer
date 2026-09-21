package cx.m42.superizer.fixture

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.activation.Activation
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.host.build
import cx.m42.superizer.host.platform.currentPlatform
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.shell.SuperizerShell
import cx.m42.apps.testapp.TestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

/**
 * The smallest thing that is still a Super App.
 *
 * Whatever this file has to say about an app is a thing the framework failed to handle: it names
 * two apps and nothing else. If adding a third ever needs a second line here, §24 has been broken.
 */
public fun buildFixture(scope: CoroutineScope, debug: Boolean = true): Superizer = Superizer.build(scope) {
    host(
        HostInfo(
            name = "Fixture",
            version = "0.1.0",
            build = "dev",
            platform = currentPlatform(),
            contractVersion = SuperizerContract.VERSION,
            debug = debug,
        ),
    )
    scheme("superizer")
    register(ProbeApp())
    register(TestApp())
    // A fresh install starts with the probe on Home (D48); the bench app gets there by activation.
    home("probe")
    promoCodes(
        mapOf(
            TestApp.PROMO_CODE to ActivationResult.Success(
                Activation(AppId("test-app"), TestApp.promoConfig()),
            ),
        ),
    )
    serviceCode("SERVICE")
}

@Composable
public fun FixtureApp(superizer: Superizer) {
    SuperizerShell(superizer)
}

/**
 * A second app that exists only to be a second app.
 *
 * It ships its own icon as an SVG path rather than a name from the host's glyph pack, which is the
 * half of D29 that a host cannot fake for it: this square-with-a-dot is drawn by `ui` from a string
 * the app carried, and nobody edited the pack.
 */
@Serializable
public data class ProbeConfig(val mode: String = "default")

public class ProbeApp : SuperizerApp<ProbeConfig>() {
    override val manifest: AppManifest = AppManifest(
        id = AppId("probe"),
        version = "1.0.0",
        metadata = AppMetadata(
            title = localized("en" to "Probe", "ru" to "Проба"),
            // The inner square is wound the other way on purpose: SVG fills by the non-zero rule,
            // so a hole is only a hole when its outline runs opposite to the shape around it.
            icon = AppIcon.Path("M4 4h16v16H4V4zM11 11v2h2v-2z"),
            category = "Tools",
        ),
    )

    override val configSpec: AppConfigSpec<ProbeConfig> =
        AppConfigSpec(ProbeConfig.serializer(), ProbeConfig())

    override fun launch(runtime: InstanceRuntime, config: ProbeConfig): AppInstance =
        ProbeInstance(config)
}

private class ProbeInstance(private val config: ProbeConfig) : AppInstance() {
    @Composable
    override fun Content() {
        Text(
            text = "probe: mode=${config.mode}",
            style = AppTheme.body,
            color = AppTheme.foreground,
            modifier = Modifier.padding(24.dp).testTag("probe:label"),
        )
    }
}
