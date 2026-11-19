package cx.m42.superizer.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OverlaysTest {

    @Test
    fun dialogShowsItsTitleAndAnswers() = runComposeUiTest {
        var confirmed = 0
        var open by mutableStateOf(true)
        setContent {
            KitTheme {
                if (open) {
                    Dialog(
                        onDismiss = { open = false },
                        title = "Выключить сервис?",
                        description = "Сайт перестанет работать",
                        footer = { DialogActions("Выключить", { confirmed++; open = false }, "Отмена", { open = false }) },
                    )
                }
            }
        }
        onNodeWithText("Выключить сервис?").assertIsDisplayed()
        onNodeWithText("Отмена").assertIsDisplayed()
        onNodeWithText("Выключить").performClick()
        assertEquals(1, confirmed)
        onAllNodes(hasText("Выключить сервис?")).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun typeToConfirmKeepsTheAnswerLockedUntilTheNameIsTyped() = runComposeUiTest {
        var confirmed = 0
        setContent {
            KitTheme {
                ConfirmDialog(
                    title = "Удалить «Пицца Север»?",
                    text = "Сервер и база данных будут удалены навсегда.",
                    confirm = "Удалить",
                    cancel = "Отмена",
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                    destructive = true,
                    typeToConfirm = "Пицца Север",
                    typeToConfirmLabel = "Введите название",
                )
            }
        }
        onNodeWithText("Удалить").assertIsNotEnabled().performClick()
        assertEquals(0, confirmed)
        onNode(hasSetTextAction()).performTextInput("Пицца Север")
        onNodeWithText("Удалить").assertIsEnabled().performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun selectOpensAndPicks() = runComposeUiTest {
        var value by mutableStateOf("start")
        val options = listOf(SelectOption("start", "Старт"), SelectOption("pro", "Про", hint = "4 vCPU"))
        setContent {
            KitTheme {
                // A wide window: the dropdown under the field.
                CompositionLocalProvider(LocalWindowClass provides WindowClass.Expanded) {
                    Select(value, options, { value = it }, title = "Тариф")
                }
            }
        }
        onNodeWithContentDescription("Тариф").performClick()
        onNodeWithText("Про").performClick()
        assertEquals("pro", value)
        onNodeWithText("Про").assertIsDisplayed() // now the field's own text; the menu is gone
        assertEquals(1, onAllNodes(hasText("Про")).fetchSemanticsNodes().size)
    }

    @Test
    fun selectOnAPhoneIsASheet() = runComposeUiTest {
        var value by mutableStateOf("start")
        setContent {
            KitTheme {
                Select(value, listOf(SelectOption("start", "Старт"), SelectOption("pro", "Про")), { value = it }, title = "Тариф")
            }
        }
        onNodeWithContentDescription("Тариф").performClick()
        onNodeWithText("Тариф").assertIsDisplayed() // the sheet's title
        onNodeWithText("Про").performClick()
        assertEquals("pro", value)
    }

    @Test
    fun menuItemFiresAndTheMenuCloses() = runComposeUiTest {
        var open by mutableStateOf(true)
        var clicked = 0
        setContent {
            KitTheme {
                Box {
                    Menu(open, { open = false }) {
                        MenuLabel("Сервис")
                        MenuItem("Перезапустить", { clicked++; open = false })
                        MenuSeparator()
                        MenuItem("Удалить", {}, destructive = true)
                    }
                }
            }
        }
        onNodeWithText("Сервис").assertIsDisplayed()
        onNodeWithText("Перезапустить").performClick()
        assertEquals(1, clicked)
        assertEquals(0, onAllNodes(hasText("Удалить")).fetchSemanticsNodes().size)
    }

    @Test
    fun toastComesAndGoesByItself() = runComposeUiTest {
        val state = ToastState(durationMillis = 4_000)
        setContent { KitTheme { Box(Modifier.fillMaxSize()) { ToastHost(state) } } }
        mainClock.autoAdvance = false
        state.show("Настройки сохранены", "Сервис перезапускается")
        mainClock.advanceTimeByFrame()
        onNodeWithText("Настройки сохранены").assertIsDisplayed()
        onNodeWithText("Сервис перезапускается").assertIsDisplayed()
        mainClock.advanceTimeBy(4_500)
        assertEquals(0, onAllNodes(hasText("Настройки сохранены")).fetchSemanticsNodes().size)
        assertEquals(0, state.toasts.size)
    }
}
