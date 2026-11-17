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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.HostScreen
import cx.m42.superizer.Superizer
import cx.m42.superizer.ThemeMode
import cx.m42.superizer.app.LockPolicy
import cx.m42.superizer.lock.AppLockPort
import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.registry.listed
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.kit.Button
import cx.m42.superizer.kit.ButtonSize
import cx.m42.superizer.kit.ButtonVariant
import cx.m42.superizer.kit.ChoiceItem
import cx.m42.superizer.kit.Item
import cx.m42.superizer.kit.ItemSeparator
import cx.m42.superizer.kit.SwitchItem
import cx.m42.superizer.kit.TextVariant
import cx.m42.superizer.ui.components.HostGroup
import cx.m42.superizer.ui.i18n.hostStrings
import cx.m42.superizer.app.PushUse
import cx.m42.superizer.push.PushPermission
import kotlinx.coroutines.launch
import cx.m42.superizer.kit.Text as KitText

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
    onOpenHostScreen: (HostScreen) -> Unit = {},
) {
    val tokens = AppTheme
    val langTag by superizer.settings.langTag.collectAsState()
    val haptics by superizer.settings.haptics.collectAsState()
    val theme by superizer.settings.theme.collectAsState()
    val sections by superizer.handler.settingsSections.collectAsState()
    val unlocked by superizer.unlocked.collectAsState()
    val lock by superizer.lock.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        HostGroup(title = hostStrings.settingsLanguage, hint = hostStrings.settingsLanguageHint) {
            superizer.settings.languages.forEachIndexed { index, option ->
                if (index > 0) ItemSeparator()
                ChoiceItem(
                    title = option.nativeName,
                    selected = langTag.substringBefore('-') == option.tag,
                    // Written through the host's settings, so the choice is remembered on this
                    // device and the whole tree repaints in the new language on the next frame.
                    onClick = { superizer.settings.chooseLanguage(option.tag) },
                )
            }
        }

        HostGroup(title = hostStrings.settingsTheme, modifier = Modifier.testTag("settings:theme")) {
            val strings = hostStrings
            listOf(
                ThemeMode.System to strings.settingsThemeSystem,
                ThemeMode.Light to strings.settingsThemeLight,
                ThemeMode.Dark to strings.settingsThemeDark,
            ).forEachIndexed { index, (mode, title) ->
                if (index > 0) ItemSeparator()
                ChoiceItem(
                    title = title,
                    selected = theme == mode,
                    modifier = Modifier.testTag("settings:theme-${mode.name.lowercase()}"),
                    onClick = { superizer.settings.chooseTheme(mode) },
                )
            }
        }

        HostGroup(title = hostStrings.settingsHaptics, hint = hostStrings.settingsHapticsHint) {
            SwitchItem(
                title = hostStrings.settingsHaptics,
                checked = haptics,
                onCheckedChange = { value ->
                    superizer.settings.setHaptics(value)
                    // Fired after the write, so turning it *on* is confirmed by the very thing it
                    // turns on.
                    if (value) superizer.haptics.tick()
                },
            )
        }

        ProtectionSection(superizer)

        NotificationsSection(superizer)

        // The host's own blocks (07 §2.4): its keys, its storage, its backup. After the host's
        // settings and before any app's, because they are the host's and not an app's.
        superizer.hostSections.forEach { section ->
            HostGroup(title = section.title(langTag)) {
                Column(modifier = Modifier.fillMaxWidth().testTag("settings:host:${section.id}")) {
                    section.Content(superizer, onOpenHostScreen)
                }
            }
        }

        // Each app's own block, under its own title. The host does not know what is in one.
        sections.forEach { (appId, section) ->
            val app = superizer.registry.get(appId) ?: return@forEach
            // A hidden app's block waits for its unlock, or Settings would be the reveal (D8); a
            // secret app never registers one (idea/09).
            if (!app.listed(unlocked)) return@forEach
            // A protected app's block is drawn out here, outside its curtain (06 §5.4), so while
            // the curtain would be down the block is not drawn at all.
            if (lock.covers(appId)) return@forEach
            HostGroup(title = app.metadata.title.resolve(langTag)) {
                Column(modifier = Modifier.fillMaxWidth().testTag("settings:section-${appId.value}")) {
                    val runtime = superizer.handler.appRuntime(appId)
                    if (runtime != null) section(runtime)
                }
            }
        }

        HostGroup(title = hostStrings.settingsAbout) {
            BuildRow(superizer = superizer, onOpenServiceMenu = onOpenServiceMenu)
        }
    }
}

/**
 * "Protection" (06 §5.7): how long another app may be in front, a switch per optional app, a line
 * per required one, and "Lock now". Only when some registered app has a lock to speak of, and only
 * where the platform can ask at all — on desktop and the web every row here would be a lie.
 *
 * A change that weakens the lock — a switch off, a longer grace — asks first, unless the lock is
 * open already: otherwise Settings would be the way around the curtain.
 */
@Composable
private fun ProtectionSection(superizer: Superizer) {
    val lock by superizer.lock.state.collectAsState()
    val apps by superizer.registry.apps.collectAsState()
    val langTag by superizer.settings.langTag.collectAsState()
    val strings = hostStrings
    val scope = rememberCoroutineScope()

    val unlocked by superizer.unlocked.collectAsState()
    // Only what the person can see elsewhere: a row for a locked or secret app names it (idea/09).
    // `WhileHolding` has no row either: the app, not the person, decides when it is locked.
    val lockable = apps.filter {
        it.manifest.protection.lock != LockPolicy.Off && it.manifest.protection.lock != LockPolicy.WhileHolding && it.listed(unlocked)
    }
    if (lockable.isEmpty() || lock.availability == AuthAvailability.Unsupported) return
    val canAsk = lock.availability == AuthAvailability.Available

    fun weakening(change: () -> Unit) {
        scope.launch {
            if (canAsk && !lock.open &&
                superizer.lock.unlock(strings.settingsLockChange) != AuthOutcome.Success
            ) {
                return@launch
            }
            change()
        }
    }

    HostGroup(
        title = strings.settingsProtection,
        hint = if (canAsk) strings.settingsProtectionHint else strings.settingsNoScreenLock,
        modifier = Modifier.testTag("settings:protection"),
    ) {
        lockable.forEachIndexed { index, app ->
            if (index > 0) ItemSeparator()
            val title = app.metadata.title.resolve(langTag)
            when (app.manifest.protection.lock) {
                // No switch, because there is nothing to switch (D139): the row says so instead.
                LockPolicy.Required -> Item(
                    title = title,
                    modifier = Modifier.testTag("settings:lock-${app.id.value}"),
                    trailing = { KitText(strings.settingsLockAlways, variant = TextVariant.Muted) },
                )
                else -> {
                    val on = app.id in lock.guarded
                    SwitchItem(
                        title = title,
                        checked = on,
                        enabled = canAsk,
                        modifier = Modifier.testTag("settings:lock-${app.id.value}"),
                        onCheckedChange = { value ->
                            if (value) {
                                superizer.lock.setEnabled(app.id, true)
                            } else {
                                weakening { superizer.lock.setEnabled(app.id, false) }
                            }
                        },
                    )
                }
            }
        }
    }

    if (!canAsk) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Button(
                text = strings.setUpScreenLock,
                onClick = { superizer.lock.openDeviceSettings() },
                variant = ButtonVariant.Outline,
                size = ButtonSize.Lg,
                modifier = Modifier.testTag("settings:set-up-lock"),
            )
        }
        return
    }

    HostGroup(title = strings.settingsLockAfter) {
        AppLockPort.GraceChoices.forEachIndexed { index, millis ->
            if (index > 0) ItemSeparator()
            ChoiceItem(
                title = strings.lockGrace(millis),
                selected = lock.graceMillis == millis,
                onClick = {
                    if (millis <= lock.graceMillis) {
                        superizer.lock.setGrace(millis)
                    } else {
                        weakening { superizer.lock.setGrace(millis) }
                    }
                },
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
        Button(
            text = strings.settingsLockNow,
            onClick = { superizer.lock.lockNow() },
            variant = ButtonVariant.Outline,
            size = ButtonSize.Lg,
            modifier = Modifier.testTag("settings:lock-now"),
        )
    }
}

/**
 * "Notifications" (push-opt-in, D424): a line per push app the person uses, and the one thing they
 * can do about it — turn notifications on. Nothing explains why an app wants them (owner's rule);
 * the line says what is, the button does what it can: the system's question while there is one,
 * the phone's settings once there is not. Absent where there is no push at all.
 */
@Composable
private fun NotificationsSection(superizer: Superizer) {
    val used by superizer.push.used.collectAsState()
    val permission by superizer.push.permission.collectAsState()
    val apps by superizer.registry.apps.collectAsState()
    val unlocked by superizer.unlocked.collectAsState()
    val langTag by superizer.settings.langTag.collectAsState()
    val strings = hostStrings
    val scope = rememberCoroutineScope()

    val shown = apps.filter { it.id in used && it.listed(unlocked) }
    if (shown.isEmpty() || permission == PushPermission.Unavailable) return
    val granted = permission == PushPermission.Granted

    HostGroup(title = strings.settingsNotifications, modifier = Modifier.testTag("settings:notifications")) {
        shown.forEachIndexed { index, app ->
            if (index > 0) ItemSeparator()
            Item(
                title = app.metadata.title.resolve(langTag),
                modifier = Modifier.testTag("settings:push-${app.id.value}"),
                trailing = {
                    KitText(
                        when {
                            app.manifest.push == PushUse.Silent -> strings.notificationsBackground
                            granted -> strings.notificationsOn
                            else -> strings.notificationsOff
                        },
                        variant = TextVariant.Muted,
                    )
                },
            )
        }
    }

    if (!granted && shown.any { it.manifest.push == PushUse.Alerts }) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Button(
                text = if (permission == PushPermission.Blocked) strings.notificationsOpenSettings else strings.notificationsTurnOn,
                onClick = { scope.launch { superizer.push.enable() } },
                variant = ButtonVariant.Outline,
                size = ButtonSize.Lg,
                modifier = Modifier.testTag("settings:push-enable"),
            )
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
