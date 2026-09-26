package cx.m42.apps.testapp

/**
 * The bench app's own words, in its own tables (D11).
 *
 * An app owns its strings for the same reason it owns its config: an app that had to add a line to
 * the host's table to name a button could not ship anywhere else. The host only supplies the tag,
 * through `runtime.locale`.
 */
internal interface TestStrings {
    val identity: String
    val config: String
    val lifecycle: String
    val closeMe: String
    val state: String
    val stateHint: String
    val storage: String
    val clear: String
    val network: String
    val ping: String
    val analytics: String
    val analyticsHint: String
    val sendEvent: String
    val navigation: String
    val openCalculator: String
    val openUnknown: String
    val openSettings: String
    val services: String
    val locale: String
    val hello: String
    val haptics: String
    val tick: String
    val push: String
    val subscribe: String
    val unsubscribe: String
    val permission: String
    val auth: String
    val veto: String
    val blockClose: String
    val vetoHint: String
    val secrets: String
    val storeSecret: String
    val keyring: String
    val signWithKey: String
}

internal object TestStringsEn : TestStrings {
    override val secrets = "Secrets"
    override val storeSecret = "Store a secret"
    override val keyring = "SSH keyring"
    override val signWithKey = "Sign with a key"
    override val identity = "Identity"
    override val config = "Config"
    override val lifecycle = "Lifecycle"
    override val closeMe = "Close me"
    override val state = "State"
    override val stateHint = "This text survives backgrounding and a process death."
    override val storage = "Storage"
    override val clear = "Clear"
    override val network = "Network"
    override val ping = "Ping"
    override val analytics = "Analytics"
    override val analyticsHint = "The host prefixes the app id before this leaves the process."
    override val sendEvent = "Send event"
    override val navigation = "Navigation"
    override val openCalculator = "Open Calculator (scientific)"
    override val openUnknown = "Open unknown"
    override val openSettings = "Open Settings"
    override val services = "Services"
    override val locale = "Locale"
    override val hello = "Hello from the app's own table"
    override val haptics = "Haptics"
    override val tick = "Tick"
    override val push = "Push"
    override val subscribe = "Subscribe"
    override val unsubscribe = "Unsubscribe"
    override val permission = "Request permission"
    override val auth = "Auth"
    override val veto = "Veto"
    override val blockClose = "Block close"
    override val vetoHint = "With this on, back asks the host's dialog instead of closing."
}

internal object TestStringsRu : TestStrings {
    override val secrets = "Секреты"
    override val storeSecret = "Сохранить секрет"
    override val keyring = "SSH-ключи"
    override val signWithKey = "Подписать ключом"
    override val identity = "Идентичность"
    override val config = "Конфигурация"
    override val lifecycle = "Жизненный цикл"
    override val closeMe = "Закрыть меня"
    override val state = "Состояние"
    override val stateHint = "Этот текст переживает сворачивание и смерть процесса."
    override val storage = "Хранилище"
    override val clear = "Очистить"
    override val network = "Сеть"
    override val ping = "Пинг"
    override val analytics = "Аналитика"
    override val analyticsHint = "Хост добавит id приложения перед отправкой."
    override val sendEvent = "Отправить событие"
    override val navigation = "Навигация"
    override val openCalculator = "Открыть калькулятор (инженерный)"
    override val openUnknown = "Открыть несуществующее"
    override val openSettings = "Открыть настройки"
    override val services = "Сервисы"
    override val locale = "Язык"
    override val hello = "Привет из собственной таблицы приложения"
    override val haptics = "Вибрация"
    override val tick = "Тик"
    override val push = "Пуши"
    override val subscribe = "Подписаться"
    override val unsubscribe = "Отписаться"
    override val permission = "Запросить разрешение"
    override val auth = "Авторизация"
    override val veto = "Вето"
    override val blockClose = "Блокировать закрытие"
    override val vetoHint = "С этим переключателем «назад» показывает диалог хоста, а не закрывает."
}

/**
 * Both tables keyed by primary subtag, for `runtime.locale.pick`. The interface above is what makes
 * "every language has every key" a compile error rather than a test.
 */
internal val TestStringTables: Map<String, TestStrings> = mapOf(
    "en" to TestStringsEn,
    "ru" to TestStringsRu,
)
