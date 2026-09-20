package cx.m42.superizer

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppConfigSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
private data class Sample(val mode: String = "standard", val count: Int = 3)

private fun config(json: String) = AppConfig(Json.parseToJsonElement(json) as JsonObject)

class AppConfigSpecTest {

    private val spec = AppConfigSpec(Sample.serializer(), Sample())

    @Test
    fun anEmptyConfigIsTheDefault() {
        assertEquals(Sample(), spec.decode(AppConfig.Empty).getOrThrow())
    }

    @Test
    fun aPartialConfigKeepsTheDefaultsItDoesNotMention() {
        // The merge is why `{"mode":"scientific"}` from a QR does not silently reset every other
        // field to whatever the serializer's default happens to be.
        assertEquals(Sample(mode = "scientific", count = 3), spec.decode(config("""{"mode":"scientific"}""")).getOrThrow())
    }

    @Test
    fun aBrokenPayloadIsAFailureAndNotADefault() {
        // D18: the handler turns this into ConfigRejected. Returning the default here would open a
        // plain calculator from a QR that asked for a scientific one, with nothing to show for it.
        assertTrue(spec.decode(config("""{"count":"lots"}""")).isFailure)
    }

    @Test
    fun anUnknownKeyIsANewerBuildAndNotAnError() {
        assertTrue(spec.decode(config("""{"mode":"scientific","future":true}""")).isSuccess)
    }

    @Test
    fun anOlderSchemaIsMigratedBeforeItIsRead() {
        val migrating = AppConfigSpec(
            Sample.serializer(),
            Sample(),
            schemaVersion = 2,
            migrate = { from, raw ->
                if (from >= 2) raw else buildJsonObject {
                    raw.forEach { (k, v) -> if (k != "kind") put(k, v) }
                    raw["kind"]?.let { put("mode", it) }
                }
            },
        )
        val decoded = migrating.decode(config("""{"schemaVersion":1,"kind":"scientific"}""")).getOrThrow()
        assertEquals("scientific", decoded.mode)
    }

    @Test
    fun encodeStampsTheSchemaVersionSoTomorrowCanReadToday() {
        val encoded = spec.encode(Sample(mode = "scientific"))
        assertEquals(JsonPrimitive(1), encoded.json[AppConfigSpec.SCHEMA_VERSION])
        assertEquals(Sample(mode = "scientific"), spec.decode(encoded).getOrThrow())
    }

    @Test
    fun aSpecCanRefuseToRunOnDefaults() {
        val strict = AppConfigSpec(Sample.serializer(), Sample(), fallbackToDefault = false)
        assertFalse(strict.fallbackToDefault)
    }
}
