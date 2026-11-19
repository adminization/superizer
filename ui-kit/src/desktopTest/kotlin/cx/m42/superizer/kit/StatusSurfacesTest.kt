package cx.m42.superizer.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StatusSurfacesTest {

    @Test
    fun statusBadgeAndPillShowTheirLabels() = runComposeUiTest {
        setContent {
            KitTheme(dark = false, accent = KitAccent.Violet) {
                Column {
                    StatusBadge("Работает", Tone.Success)
                    Pill("Стандарт")
                    Pill("Выключен", Tone.Muted)
                }
            }
        }
        onNodeWithText("Работает").assertIsDisplayed()
        onNodeWithText("Стандарт").assertIsDisplayed()
        onNodeWithText("Выключен").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsBothTextsAndItsActionFires() = runComposeUiTest {
        var created = 0
        setContent {
            KitTheme {
                EmptyState(Lucide.Inbox, "Сервисов пока нет", "Создайте первый") {
                    Button("Создать", onClick = { created++ })
                }
            }
        }
        onNodeWithText("Сервисов пока нет").assertIsDisplayed()
        onNodeWithText("Создайте первый").assertIsDisplayed()
        onNodeWithText("Создать").performClick()
        assertEquals(1, created)
    }

    @Test
    fun copyableFactAnswersTheTapWithTheCopiedState() = runComposeUiTest {
        var copied: String? = null
        setContent {
            KitTheme {
                Column {
                    FactRow("Идентификатор", "svc_7f3a", mono = true, copyable = true)
                    CopyValue("https://pizza-north.restocloud.app", copyLabel = "Скопировать", copiedLabel = "Скопировано", onCopy = { copied = it })
                }
            }
        }
        onNodeWithText("svc_7f3a").assertIsDisplayed()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Скопировать").performClick()
        mainClock.advanceTimeByFrame()
        assertEquals("https://pizza-north.restocloud.app", copied)
        val state = onNodeWithContentDescription("Скопировать").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertEquals("Скопировано", state)
        mainClock.advanceTimeBy(2000)
        val after = onNodeWithContentDescription("Скопировать").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertEquals("", after)
    }

    @Test
    fun statOpensWhatItCounts() = runComposeUiTest {
        var opened = 0
        setContent { KitTheme { Stat("Баланс", "1 840 кр", hint = "хватит на 42 дня", onClick = { opened++ }) } }
        onNodeWithText("хватит на 42 дня").assertIsDisplayed()
        onNodeWithText("1 840 кр").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun meterAndRingHoldValuesInRange() = runComposeUiTest {
        setContent {
            KitTheme {
                Column {
                    Meter(1.7f, Modifier.width(100.dp).testTag("meter"))
                    Ring(-0.4f, Modifier.testTag("ring"))
                }
            }
        }
        assertEquals(1f, onNodeWithTag("meter").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
        assertEquals(0f, onNodeWithTag("ring").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
        assertEquals(0f, clampFraction(Float.NaN))
        assertEquals(Tone.Danger, meterTone(0.95f))
        assertEquals(Tone.Warn, meterTone(0.8f))
        assertEquals(Tone.Brand, meterTone(0.3f))
    }

    @Test
    fun chartsDrawEmptyAndSingleValueSeries() = runComposeUiTest {
        setContent {
            KitTheme(dark = true, accent = KitAccent.Blue) {
                Column {
                    Sparkline(emptyList(), Modifier.size(100.dp, 30.dp))
                    Sparkline(listOf(3f), Modifier.size(100.dp, 30.dp))
                    BarChart(emptyList(), emptyList(), Modifier.width(300.dp), height = 80.dp)
                    BarChart(listOf(5f), listOf("17 ноя"), Modifier.width(300.dp), height = 80.dp, contentDescription = "Расход")
                    ShareBar(emptyList())
                    ShareBar(listOf(0f, 0f))
                    ShareBar(listOf(3f, 1f, -2f))
                }
            }
        }
        onNodeWithContentDescription("Расход").assertIsDisplayed()
        onNodeWithText("17 ноя").assertIsDisplayed()
    }

    @Test
    fun noticeCarriesItsActionAndTheTerminalItsTitle() = runComposeUiTest {
        var restarted = 0
        setContent {
            KitTheme {
                Column {
                    Notice(Tone.Warn, Lucide.Inbox, "Нужен перезапуск", "Две настройки ждут") {
                        Button("Перезапустить", onClick = { restarted++ })
                    }
                    Terminal("restoapp — pizza-north", live = true, liveLabel = "обновляется", height = 80.dp) {
                        Text("RestoApp 2.14.1 lifted", variant = TextVariant.Mono)
                    }
                    Kbd("⌘K")
                    NameMark("Пицца Север")
                    NameMark("", dimmed = true)
                    IconTile(Lucide.Inbox, Tone.Neutral)
                }
            }
        }
        onNodeWithText("Перезапустить").performClick()
        assertEquals(1, restarted)
        onNodeWithText("restoapp — pizza-north").assertIsDisplayed()
        onNodeWithText("обновляется").assertIsDisplayed()
        onNodeWithText("П").assertIsDisplayed()
        onNodeWithText("?").assertIsDisplayed()
        assertEquals(nameMarkIndex("Пицца Север", 6), nameMarkIndex("Пицца Север", 6))
    }
}
