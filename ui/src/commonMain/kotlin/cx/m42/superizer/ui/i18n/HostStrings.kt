package cx.m42.superizer.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Every word the *host* writes for itself, in one table per language.
 *
 * Only the host's own vocabulary lives here — the drawer, the frame, Settings, Activate, the
 * Service Menu. An app owns its own strings and its own tables (D11), because an app that had to
 * add a line to this file to name a button would be an app that cannot ship on its own.
 *
 * An interface rather than a resource file, for the reason it was one in Unitool: text is
 * assembled outside composition too, and an interface hands the completeness check to the
 * compiler — a language whose table is missing an entry does not build.
 *
 * Rules for adding: whole phrases, never fragments; named after the meaning, not the words;
 * anything interpolated is a parameter, so no language is forced into another's word order.
 */
public interface HostStrings {

    /** This table's BCP-47 primary subtag. */
    public val langTag: String

    // ------------------------------------------------------------------ frame
    public val menu: String
    public val openMenu: String
    public val backAction: String
    public fun version(value: String): String

    // ------------------------------------------------------------------ destinations
    public val home: String
    public val allApps: String
    public val settings: String
    public val activate: String
    public val serviceMenu: String

    // ------------------------------------------------------------------ home (D48)
    public val noApps: String
    public val ungrouped: String
    public val homeEmpty: String
    /** The tile that leads to the catalog. */
    public val addApp: String
    /** The long-press label on a tile, so the gesture is announced rather than folklore. */
    public val removeFromHome: String

    // ------------------------------------------------------------------ catalog (D48)
    public val catalogHint: String
    public val onHome: String

    // ------------------------------------------------------------------ settings
    public val settingsLanguage: String
    public val settingsLanguageHint: String
    public val settingsHaptics: String
    public val settingsHapticsHint: String
    public val settingsAbout: String
    public fun settingsBuild(value: String): String

    // ------------------------------------------------------------------ activation
    public val activatePromoLabel: String
    public val activateQrLabel: String
    public val activateApply: String
    public val activateCameraNote: String
    public val activateErrorMalformed: String
    public val activateErrorUnsupported: String
    public val activateErrorUnknownApp: String
    public val activateErrorUnknownCode: String
    public val activateErrorExpired: String

    // ------------------------------------------------------------------ closing an app
    public val closeDialogTitle: String
    public val closeDialogBody: String
    public val closeDialogConfirm: String
    public val closeDialogStay: String

    // ------------------------------------------------------------------ lock (06)
    /** The curtain's line under the app's name. */
    public val locked: String
    public val lockedHint: String
    public val unlock: String
    /** The system sheet's title when the curtain asks. */
    public fun unlockPrompt(app: String): String
    public val unlockPromptSubtitle: String
    /** The banner over an app that wants a lock the device cannot give (§5.8). */
    public val unprotectedNoScreenLock: String
    public val unprotectedPlatform: String
    public val setUpScreenLock: String
    public val settingsProtection: String
    public val settingsProtectionHint: String
    public val settingsLockAfter: String
    public fun lockGrace(millis: Long): String
    /** What a `Required` app shows where an optional one has its switch. */
    public val settingsLockAlways: String
    public val settingsLockNow: String
    public val settingsNoScreenLock: String
    /** The sheet's title when a change in Settings would weaken the lock. */
    public val settingsLockChange: String

    // ------------------------------------------------------------------ failure
    public val errorTitle: String
    public val errorRetry: String
    public val errorBack: String

    // ------------------------------------------------------------------ service menu
    public val serviceApps: String
    public val serviceRun: String
    public val serviceRunWithConfig: String
    public val serviceUnlock: String
    public val serviceLock: String
    public val serviceEnable: String
    public val serviceDisable: String
    public val serviceReset: String
    public val serviceResetConfirm: String
    public val serviceEvents: String
    public val serviceLog: String
    public val serviceSimulatePush: String
    public val serviceManifest: String
    public val serviceRejected: String
}

internal object HostStringsEn : HostStrings {
    override val langTag = "en"

    override val menu = "Menu"
    override val openMenu = "Open menu"
    override val backAction = "Back"
    override fun version(value: String) = "Version $value"

    override val home = "Home"
    override val allApps = "All apps"
    override val settings = "Settings"
    override val activate = "Activate"
    override val serviceMenu = "Service menu"

    override val noApps = "No apps installed"
    override val ungrouped = "Other"
    override val homeEmpty = "Nothing on Home yet. Add an app from the list."
    override val addApp = "Add"
    override val removeFromHome = "Remove from Home"

    override val catalogHint = "Tap to put an app on Home or take it off. Hidden apps appear here once activated."
    override val onHome = "On Home"

    override val settingsLanguage = "Language"
    override val settingsLanguageHint = "Interface language on this device"
    override val settingsHaptics = "Haptics"
    override val settingsHapticsHint = "A short tap response on every key"
    override val settingsAbout = "About"
    override fun settingsBuild(value: String) = "Build $value"

    override val activatePromoLabel = "Promo code"
    override val activateQrLabel = "QR contents"
    override val activateApply = "Apply"
    override val activateCameraNote = "Scanning will come later; paste the text."
    override val activateErrorMalformed = "This is not an activation payload"
    override val activateErrorUnsupported = "This payload is from a newer version"
    override val activateErrorUnknownApp = "No such app in this build"
    override val activateErrorUnknownCode = "Code not found"
    override val activateErrorExpired = "The code has expired"

    override val closeDialogTitle = "Close the app?"
    override val closeDialogBody = "It has unsaved changes."
    override val closeDialogConfirm = "Close"
    override val closeDialogStay = "Stay"

    override val locked = "Locked"
    override val lockedHint = "Confirm it is you: fingerprint, face or the screen lock."
    override val unlock = "Unlock"
    override fun unlockPrompt(app: String) = "Unlock $app"
    override val unlockPromptSubtitle = "Fingerprint, face or screen lock"
    override val unprotectedNoScreenLock = "No screen lock is set up on this device, so this app is not protected."
    override val unprotectedPlatform = "This platform has no lock, so this app is not protected here."
    override val setUpScreenLock = "Set up"
    override val settingsProtection = "Protection"
    override val settingsProtectionHint = "Apps holding what is yours alone open with your fingerprint, face or screen lock"
    override val settingsLockAfter = "Lock after leaving the app"
    override fun lockGrace(millis: Long) = when {
        millis <= 0L -> "Immediately"
        millis < 60_000L -> "${millis / 1000} seconds"
        millis == 60_000L -> "1 minute"
        else -> "${millis / 60_000L} minutes"
    }
    override val settingsLockAlways = "Always"
    override val settingsLockNow = "Lock now"
    override val settingsNoScreenLock = "Turn on the device's screen lock first."
    override val settingsLockChange = "Change protection"

    override val errorTitle = "The app could not start"
    override val errorRetry = "Try again"
    override val errorBack = "Back to Home"

    override val serviceApps = "Apps"
    override val serviceRun = "Run"
    override val serviceRunWithConfig = "Run with config…"
    override val serviceUnlock = "Unlock"
    override val serviceLock = "Hide"
    override val serviceEnable = "Enable"
    override val serviceDisable = "Disable"
    override val serviceReset = "Reset"
    override val serviceResetConfirm = "Erase all data of this app?"
    override val serviceEvents = "Events"
    override val serviceLog = "Log"
    override val serviceSimulatePush = "Simulate push"
    override val serviceManifest = "Manifest"
    override val serviceRejected = "Rejected at registration"
}

/** The language the host is written in; the other table is translated from it. */
internal object HostStringsRu : HostStrings {
    override val langTag = "ru"

    override val menu = "Меню"
    override val openMenu = "Открыть меню"
    override val backAction = "Назад"
    override fun version(value: String) = "Версия $value"

    override val home = "Главная"
    override val allApps = "Все приложения"
    override val settings = "Настройки"
    override val activate = "Активировать"
    override val serviceMenu = "Служебное меню"

    override val noApps = "Нет приложений"
    override val ungrouped = "Прочее"
    override val homeEmpty = "На главной пока пусто. Добавьте приложение из списка."
    override val addApp = "Добавить"
    override val removeFromHome = "Убрать с главной"

    override val catalogHint = "Нажмите, чтобы добавить приложение на главную или убрать с неё. Скрытые появляются здесь после активации."
    override val onHome = "На главной"

    override val settingsLanguage = "Язык"
    override val settingsLanguageHint = "Язык интерфейса на этом устройстве"
    override val settingsHaptics = "Вибрация"
    override val settingsHapticsHint = "Короткий отклик при нажатии клавиши"
    override val settingsAbout = "О приложении"
    override fun settingsBuild(value: String) = "Сборка $value"

    override val activatePromoLabel = "Промокод"
    override val activateQrLabel = "Содержимое QR"
    override val activateApply = "Применить"
    override val activateCameraNote = "Сканирование появится позже; вставьте текст."
    override val activateErrorMalformed = "Это не QR активации"
    override val activateErrorUnsupported = "Этот код из более новой версии"
    override val activateErrorUnknownApp = "Такого приложения нет в этой сборке"
    override val activateErrorUnknownCode = "Код не найден"
    override val activateErrorExpired = "Срок действия кода истёк"

    override val closeDialogTitle = "Закрыть приложение?"
    override val closeDialogBody = "В нём есть несохранённые изменения."
    override val closeDialogConfirm = "Закрыть"
    override val closeDialogStay = "Остаться"

    override val locked = "Заблокировано"
    override val lockedHint = "Подтвердите, что это вы: отпечаток, лицо или блокировка экрана."
    override val unlock = "Разблокировать"
    override fun unlockPrompt(app: String) = "Разблокировать «$app»"
    override val unlockPromptSubtitle = "Отпечаток, лицо или блокировка экрана"
    override val unprotectedNoScreenLock = "На устройстве не настроена блокировка экрана, поэтому приложение не защищено."
    override val unprotectedPlatform = "На этой платформе блокировки нет, поэтому здесь приложение не защищено."
    override val setUpScreenLock = "Настроить"
    override val settingsProtection = "Защита"
    override val settingsProtectionHint = "Приложения с тем, что принадлежит только вам, открываются по отпечатку, лицу или блокировке экрана"
    override val settingsLockAfter = "Блокировать после ухода из приложения"
    override fun lockGrace(millis: Long) = when {
        millis <= 0L -> "Сразу"
        millis < 60_000L -> "${millis / 1000} секунд"
        millis == 60_000L -> "1 минута"
        else -> "${millis / 60_000L} минут"
    }
    override val settingsLockAlways = "Всегда"
    override val settingsLockNow = "Заблокировать сейчас"
    override val settingsNoScreenLock = "Сначала включите блокировку экрана устройства."
    override val settingsLockChange = "Изменить защиту"

    override val errorTitle = "Приложение не запустилось"
    override val errorRetry = "Повторить"
    override val errorBack = "На главную"

    override val serviceApps = "Приложения"
    override val serviceRun = "Запустить"
    override val serviceRunWithConfig = "С конфигом…"
    override val serviceUnlock = "Разблокировать"
    override val serviceLock = "Скрыть"
    override val serviceEnable = "Включить"
    override val serviceDisable = "Выключить"
    override val serviceReset = "Сбросить"
    override val serviceResetConfirm = "Стереть все данные этого приложения?"
    override val serviceEvents = "События"
    override val serviceLog = "Лог"
    override val serviceSimulatePush = "Отправить пуш"
    override val serviceManifest = "Манифест"
    override val serviceRejected = "Отклонено при регистрации"
}

/** Every table the host ships, by primary subtag. A host that adds a language adds a table. */
public val HostStringTables: Map<String, HostStrings> = mapOf(
    HostStringsRu.langTag to HostStringsRu,
    HostStringsEn.langTag to HostStringsEn,
)

/**
 * What a device speaking neither of the host's languages gets. English rather than Russian: the
 * strings were written in Russian, but a host is listed worldwide and English is the language a
 * reader who has neither is likeliest to have some of.
 */
public val HostStringsFallback: HostStrings = HostStringsEn

/**
 * Provided by the shell from `runtime.locale`, so every host screen repaints when the language
 * changes without observing anything itself.
 */
public val LocalHostStrings: ProvidableCompositionLocal<HostStrings> =
    staticCompositionLocalOf { HostStringsFallback }

/** Read at call sites as `hostStrings.menu`. */
public val hostStrings: HostStrings
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalHostStrings.current

/** Picks a table by primary subtag — the same rule [cx.m42.superizer.runtime.LocaleService.pick] uses. */
public fun hostStringsFor(langTag: String): HostStrings =
    HostStringTables[langTag.substringBefore('-').substringBefore('_').lowercase()] ?: HostStringsFallback
