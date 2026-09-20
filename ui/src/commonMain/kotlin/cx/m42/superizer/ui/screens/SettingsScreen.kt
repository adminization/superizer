package cx.m42.superizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.Superizer
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.ChoiceRow
import cx.m42.superizer.ui.components.RowDivider
import cx.m42.superizer.ui.components.Section
import cx.m42.superizer.ui.components.ToggleRow
import cx.m42.superizer.ui.i18n.hostStrings

/**
 * Everything the host lets a person decide — deliberately little — plus whatever the installed apps
 * contributed through `ctx.settingsSection` (03).
 *
 * The app sections are why this screen is in the library rather than in a host: an app that needed
 * the host to add a row for it would not be an app that can ship on its own.
 */
@Composable
public fun SettingsScreen(
    superizer: Superizer,
    onOpenServiceMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    val langTag by superizer.settings.langTag.collectAsState()
    val haptics by superizer.settings.haptics.collectAsState()
    val sections by superizer.handler.settingsSections.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Section(title = hostStrings.settingsLanguage, hint = hostStrings.settingsLanguageHint) {
            superizer.settings.languages.forEachIndexed { index, option ->
                if (index > 0) RowDivider()
                ChoiceRow(
                    label = option.nativeName,
                    selected = langTag.substringBefore('-') == option.tag,
                    // Written through the host's settings, so the choice is remembered on this
                    // device and the whole tree repaints in the new language on the next frame.
                    onClick = { superizer.settings.chooseLanguage(option.tag) },
                )
            }
        }

        Section(title = hostStrings.settingsHaptics, hint = hostStrings.settingsHapticsHint) {
            ToggleRow(
                label = hostStrings.settingsHaptics,
                checked = haptics,
                onCheckedChange = { value ->
                    superizer.settings.setHaptics(value)
                    // Fired after the write, so turning it *on* is confirmed by the very thing it
                    // turns on.
                    if (value) superizer.haptics.tick()
                },
            )
        }

        // Each app's own block, under its own title. The host does not know what is in one.
        sections.forEach { (appId, section) ->
            val app = superizer.registry.get(appId) ?: return@forEach
            Section(title = app.metadata.title.resolve(langTag)) {
                Column(modifier = Modifier.fillMaxWidth().testTag("settings:section-${appId.value}")) {
                    val runtime = superizer.handler.appRuntime(appId)
                    if (runtime != null) section(runtime)
                }
            }
        }

        Section(title = hostStrings.settingsAbout) {
            BuildRow(superizer = superizer, onOpenServiceMenu = onOpenServiceMenu)
        }
    }
}

/**
 * The build stamp, and the seven-tap gesture behind it.
 *
 * The gesture works **only in a debug build** (D34). In a release the Service Menu has exactly one
 * door — the promo code the host was built with — because hidden apps that anyone who knows the
 * folklore can reveal are not hidden from anyone.
 */
@Composable
private fun BuildRow(superizer: Superizer, onOpenServiceMenu: () -> Unit) {
    val tokens = AppTheme
    var taps by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (!superizer.hostInfo.debug) return@clickable
                taps += 1
                if (taps >= TAPS_TO_SERVICE_MENU) {
                    taps = 0
                    onOpenServiceMenu()
                }
            }
            .testTag("settings:build")
            .padding(16.dp),
    ) {
        Text(
            text = hostStrings.settingsBuild("${superizer.hostInfo.version} (${superizer.hostInfo.build})"),
            style = tokens.body,
            color = tokens.foreground,
        )
    }
}

private const val TAPS_TO_SERVICE_MENU = 7
