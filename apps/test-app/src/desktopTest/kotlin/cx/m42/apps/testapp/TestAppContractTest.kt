package cx.m42.apps.testapp

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.testing.AppContractTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The bench app has to pass the same TCK every other app does.
 *
 * It is the app most likely to drift away from the contract, because it is the one written against
 * the runtime rather than against a product idea — so it is the one where inheriting the checks
 * matters most.
 */
class TestAppContractTest : AppContractTest(TestApp()) {

    override val sampleConfig: AppConfig =
        AppConfig(Json.parseToJsonElement("""{"mode":"promo"}""") as JsonObject)

    override val configFixtures: List<String> = listOf(
        """{"schemaVersion":1,"mode":"default","label":null}""",
        """{"mode":"promo"}""",
        """{"mode":"deeplink","label":"1"}""",
    )

    override val stateFixtures: List<String> = listOf(
        """{"note":"kept","blockClose":"false"}""",
        """{"note":"kept"}""",
    )
}
