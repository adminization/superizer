package cx.m42.apps.testapp

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.runtime.InstanceRuntime
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * What the bench app is configured with.
 *
 * `fallbackToDefault = false` on the spec below is the point of this type existing: every other app
 * in the MVP prefers to open on defaults when a payload is broken, and this one refuses, so the
 * *other* half of D18 — `ConfigRejected` followed by the host's error screen — is a path somebody
 * can walk rather than a branch nobody has run.
 */
@Serializable
public data class TestConfig(val mode: String = "default", val label: String? = null)

/**
 * The hidden app that shows every service a runtime has, one card each (07).
 *
 * It is how §22 is answered: the lifecycle is observable because this renders the event flow, and
 * the config is source-blind because this shows the config with no field saying where it came from
 * — opened by promo code, by QR, by deep link or from the Service Menu, the screen is identical.
 *
 * Hidden, and unlocked by the promo code the host ships (D8). It is a diagnostic instrument, and a
 * tile for it on a user's home screen would be a bug report waiting to happen.
 */
public class TestApp : SuperizerApp<TestConfig>() {

    override val manifest: AppManifest = AppManifest(
        id = AppId("test-app"),
        version = "1.0.0",
        metadata = AppMetadata(
            title = localized("en" to "Test App", "ru" to "Тестовое приложение"),
            description = localized(
                "en" to "Every runtime service, one card each",
                "ru" to "Каждый сервис рантайма — своей карточкой",
            ),
            icon = AppIcon.Letter('A'),
            hidden = true,
            category = "Service",
        ),
        deepLinks = setOf("echo"),
        pushTopics = setOf("demo"),
        networkHosts = setOf("api.frankfurter.app"),
    )

    override val configSpec: AppConfigSpec<TestConfig> = AppConfigSpec(
        serializer = TestConfig.serializer(),
        default = TestConfig(),
        fallbackToDefault = false,
    )

    override fun setup(ctx: AppSetupContext) {
        // The target for `?link=` in the web smoke test, `adb shell am start`, and a push tap
        // (13 §7). One path, three ways in, and the app cannot tell which one it was.
        ctx.deepLink("echo") { params -> configSpec.encode(TestConfig(mode = "deeplink", label = params["x"])) }

        // Declared in the manifest, so the topic store accepts it. Subscribing to anything else
        // would be refused and logged — which is the test that D47 is enforced and not decorative.
        ctx.runtime.scope.launch { ctx.runtime.push.subscribe("demo") }
    }

    override fun launch(runtime: InstanceRuntime, config: TestConfig): AppInstance =
        TestAppInstance(runtime, config)

    public companion object {
        /** What a host would put in its promo table to let anyone in (06). */
        public val PROMO_CODE: String = "TEST-2026"

        public fun promoConfig(): AppConfig =
            AppConfigSpec(TestConfig.serializer(), TestConfig()).encode(TestConfig(mode = "promo"))
    }
}
