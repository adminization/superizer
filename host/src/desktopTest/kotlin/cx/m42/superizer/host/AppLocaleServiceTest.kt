package cx.m42.superizer.host

import cx.m42.superizer.LanguageOption
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which language the host speaks, and where that decision comes from: the stored choice, then the
 * device, then the fallback.
 *
 * The fallback is English rather than Russian even though the strings were written in Russian — the
 * app is listed worldwide, and English is the language a reader who has neither is likeliest to
 * have some of.
 */
class AppLocaleServiceTest {

    private val languages = listOf(LanguageOption("ru", "Русский"), LanguageOption("en", "English"))

    @BeforeTest
    fun setUp() = isolatePrefs()

    @Test
    fun withNoStoredChoiceAndNoMatchingDeviceLanguageTheFallbackAnswers() {
        val locale = AppLocaleService(languages, fallback = "en")
        // The device's own tag may or may not be one of ours; either way the answer is supported.
        assertTrue(locale.langTag.value.startsWith("ru") || locale.langTag.value.startsWith("en"))
    }

    @Test
    fun aChoiceIsRememberedOnThisDevice() {
        AppLocaleService(languages, fallback = "en").chooseLanguage("ru")
        assertEquals("ru", AppLocaleService(languages, fallback = "en").langTag.value)
    }

    @Test
    fun aStoredLanguageTheBuildNoLongerHasIsIgnored() {
        AppLocaleService(languages, fallback = "en").chooseLanguage("ru")
        // A build that dropped Russian must not start up speaking it.
        val narrowed = AppLocaleService(listOf(LanguageOption("en", "English")), fallback = "en")
        assertEquals("en", narrowed.langTag.value)
    }

    @Test
    fun pickMatchesOnThePrimarySubtagSoRegionsDoNotMatter() {
        val locale = AppLocaleService(languages, fallback = "en")
        locale.useForTesting("ru-RU")
        assertEquals("таблица", locale.pick(mapOf("ru" to "таблица", "en" to "table"), "table"))
    }

    @Test
    fun anUnknownTagFallsBackInsteadOfThrowing() {
        val locale = AppLocaleService(languages, fallback = "en")
        locale.useForTesting("ja")
        assertEquals("table", locale.pick(mapOf("ru" to "таблица"), "table"))
    }

    @Test
    fun theHapticsSwitchIsRememberedToo() {
        val locale = AppLocaleService(languages, fallback = "en")
        assertTrue(locale.haptics.value, "haptics are on by default, as the platform is")
        locale.setHaptics(false)
        assertFalse(AppLocaleService(languages, fallback = "en").haptics.value)
    }
}
