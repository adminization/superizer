package cx.m42.superizer.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Lucide
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitComponentsTest {

    @Test
    fun buttonClicksWhenEnabledOnly() = runComposeUiTest {
        var clicks = 0
        var enabled by mutableStateOf(true)
        setContent { KitTheme { Button("Сохранить", onClick = { clicks++ }, enabled = enabled) } }

        onNodeWithText("Сохранить").performClick()
        assertEquals(1, clicks)

        enabled = false
        waitForIdle()
        onNodeWithText("Сохранить", useUnmergedTree = true).assertIsDisplayed()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsNotEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun loadingButtonIgnoresTaps() = runComposeUiTest {
        var clicks = 0
        setContent { KitTheme { Button("Войти", onClick = { clicks++ }, loading = true) } }
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun iconButtonIsNamedForScreenReaders() = runComposeUiTest {
        var clicks = 0
        setContent {
            KitTheme {
                IconButton(Lucide.Bell, contentDescription = "Уведомления", onClick = { clicks++ })
            }
        }
        onNodeWithContentDescription("Уведомления").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun inputTakesTextThroughItsTestTag() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent { KitTheme { Input(value, { value = it }, Modifier.testTag("login"), placeholder = "Логин") } }
        onNodeWithTag("login").performTextInput("admin")
        assertEquals("admin", value)
    }

    @Test
    fun passwordInputDoesNotShowTheSecret() = runComposeUiTest {
        setContent { KitTheme { Input("secret", {}, Modifier.testTag("pass"), password = true) } }
        onNodeWithTag("pass").assert(
            SemanticsMatcher("text is masked") { node ->
                node.config.getOrNull(SemanticsProperties.EditableText)?.text?.contains("secret") != true
            },
        )
    }

    @Test
    fun switchItemTogglesFromTheWholeRow() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent { KitTheme { SwitchItem("Звук", checked = on, onCheckedChange = { on = it }) } }
        onNodeWithText("Звук").assertIsOff().performClick()
        onNodeWithText("Звук").assertIsOn()
        assertEquals(true, on)
    }

    @Test
    fun choiceItemReportsSelection() = runComposeUiTest {
        var chosen by mutableIntStateOf(0)
        setContent {
            KitTheme {
                ItemGroup(title = "Язык") {
                    ChoiceItem("Русский", selected = chosen == 0, onClick = { chosen = 0 })
                    ItemSeparator()
                    ChoiceItem("English", selected = chosen == 1, onClick = { chosen = 1 })
                }
            }
        }
        onNodeWithText("English").performClick()
        onNodeWithText("English").assertIsSelected()
        assertEquals(1, chosen)
    }

    @Test
    fun tabsSelectByIndex() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        setContent { KitTheme { Tabs(listOf("Все", "Новые", "Готовые"), selected, { selected = it }) } }
        onNodeWithText("Готовые").performClick()
        onNodeWithText("Готовые").assertIsSelected()
        assertEquals(2, selected)
    }

    @Test
    fun cardIsOneTapTargetWhenClickable() = runComposeUiTest {
        var clicks = 0
        setContent { KitTheme { Card(onClick = { clicks++ }) { Text("№ 1042"); Badge("Новый", dot = true) } } }
        onNodeWithText("Новый").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun fieldShowsTheErrorInsteadOfTheDescription() = runComposeUiTest {
        setContent {
            KitTheme {
                Field("Сервер", description = "Адрес из письма", error = "Сервер не отвечает") { Input("x", {}) }
            }
        }
        onNodeWithText("Сервер не отвечает").assertIsDisplayed()
        onNodeWithText("Адрес из письма").assertDoesNotExist()
    }

    @Test
    fun avatarInitials() {
        assertEquals("ДР", initialsOf("Демо-ресторан"))
        assertEquals("K", initialsOf("Kitchen"))
        assertEquals("?", initialsOf("  "))
    }

    @Test
    fun mutedTextStaysMutedInsideACard() = runComposeUiTest {
        setContent { KitTheme { Card { Text("подсказка", variant = TextVariant.Muted) } } }
        onNodeWithText("подсказка").assertTextEquals("подсказка")
    }
}
