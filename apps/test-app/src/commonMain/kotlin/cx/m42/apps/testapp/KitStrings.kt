package cx.m42.apps.testapp

/**
 * The kit gallery's own words (D11), next to the bench app's. Component and enum names
 * (`ButtonVariant.Ghost`, `Tone.Warn`) are shown as they are spelt in code and are not here.
 */
internal interface KitStrings {
    val title: String
    val description: String
    val crumbService: String
    val dark: String
    val light: String
    val metaHint: String
    val newThing: String
    val more: String
    val menuLabel: String
    val edit: String
    val copy: String
    val copied: String
    val selected: String
    val disabled: String
    val disabledHint: String
    val delete: String
    val save: String
    val cancel: String
    val close: String
    val settings: String
    val search: String
    val primary: String
    val accents: String
    val accentsHint: String
    val buttons: String
    val buttonsHint: String
    val variants: String
    val sizes: String
    val loading: String
    val iconButtons: String
    val customContent: String
    val forms: String
    val formsHint: String
    val nameLabel: String
    val nameHint: String
    val namePlaceholder: String
    val emailLabel: String
    val emailError: String
    val readOnly: String
    val password: String
    val comment: String
    val commentHint: String
    val commentPlaceholder: String
    val toggles: String
    val items: String
    val itemsHint: String
    val switchTitle: String
    val switchHint: String
    val choiceHint: String
    val openRow: String
    val selection: String
    val selectionHint: String
    val chips: String
    val chipAll: String
    val plan: String
    val planStart: String
    val planStartHint: String
    val planStandard: String
    val planStandardHint: String
    val planPro: String
    val planProHint: String
    val sort: String
    val sortNew: String
    val sortOld: String
    val steps: String
    val quantity: String
    val status: String
    val statusHint: String
    val statusActive: String
    val statusCooking: String
    val statusLow: String
    val statusOff: String
    val marks: String
    val feedback: String
    val feedbackHint: String
    val alertText: String
    val alertNoIcon: String
    val noticeTitle: String
    val noticeText: String
    val noticeAction: String
    val noticeWarnTitle: String
    val noticeWarnText: String
    val noticeDangerTitle: String
    val noticeDangerText: String
    val noticeOkTitle: String
    val noticeOkText: String
    val surfaces: String
    val surfacesHint: String
    val all: String
    val statBalance: String
    val statBalanceValue: String
    val statBalanceHint: String
    val statRunning: String
    val statRunningValue: String
    val statRunningHint: String
    val statOrders: String
    val statOrdersHint: String
    val statDisk: String
    val statDiskHint: String
    val facts: String
    val factPlan: String
    val factId: String
    val factStatus: String
    val factEmpty: String
    val shortcut: String
    val cardTitle: String
    val cardText: String
    val cardClickable: String
    val panelTitle: String
    val panelText: String
    val type: String
    val typeSample: String
    val charts: String
    val chartsHint: String
    val limits: String
    val limitsHint: String
    val table: String
    val tableHint: String
    val colName: String
    val colStatus: String
    val colOrders: String
    val colRevenue: String
    val emptyTitle: String
    val emptyText: String
    val noMatchTitle: String
    val noMatchText: String
    val reset: String
    val sections: String
    val sectionsHint: String
    val sectionsPageHint: String
    val sectionSummary: String
    val sectionBoard: String
    val sectionReceipts: String
    val sectionMoney: String
    val total: String
    val pay: String
    val overlays: String
    val overlaysHint: String
    val openDialog: String
    val openConfirm: String
    val openDelete: String
    val openSheet: String
    val toastSuccess: String
    val toastWarn: String
    val toastDanger: String
    val toastSaved: String
    val toastSavedDetail: String
    val toastWarnTitle: String
    val toastDangerTitle: String
    val toastDangerDetail: String
    val dialogTitle: String
    val dialogText: String
    val confirmTitle: String
    val confirmText: String
    val confirmAction: String
    val deleteTitle: String
    val deleteText: String
    val typeToConfirm: String
    val sheetTitle: String
    val sheetText: String
    val terminalTitle: String
    val live: String
    val venues: List<String>
    val choices: List<String>
    val chipLabels: List<String>
    val tabLabels: List<String>
    val stepLabels: List<String>
    val days: List<String>
    val logLines: List<String>
}

internal object KitStringsEn : KitStrings {
    override val title = "Kit"
    override val description = "Every ui-kit component in its states: check the theme and the accent here"
    override val crumbService = "Service"
    override val dark = "Dark theme"
    override val light = "Light theme"
    override val metaHint = "Accent and theme come from Settings"
    override val newThing = "New"
    override val more = "More"
    override val menuLabel = "Actions"
    override val edit = "Edit"
    override val copy = "Copy"
    override val copied = "Copied"
    override val selected = "Selected"
    override val disabled = "Disabled"
    override val disabledHint = "Greyed out and ignores taps"
    override val delete = "Delete"
    override val save = "Save"
    override val cancel = "Cancel"
    override val close = "Close"
    override val settings = "Settings"
    override val search = "Search"
    override val primary = "Primary"
    override val accents = "Accents and palette"
    override val accentsHint = "The current palette, then the same sample under each of the six accents"
    override val buttons = "Buttons"
    override val buttonsHint = "Six variants, three sizes; disabled, loading, icon only"
    override val variants = "Variants"
    override val sizes = "Sizes"
    override val loading = "Loading"
    override val iconButtons = "Icon buttons and custom content"
    override val customContent = "Custom"
    override val forms = "Forms"
    override val formsHint = "Field around Input: label, hint, error; Switch, Checkbox, Items"
    override val nameLabel = "Name"
    override val nameHint = "As guests see it"
    override val namePlaceholder = "Pizza North"
    override val emailLabel = "Email"
    override val emailError = "Not an address yet"
    override val readOnly = "Read only"
    override val password = "Password"
    override val comment = "Comment"
    override val commentHint = "Several lines"
    override val commentPlaceholder = "Anything the kitchen should know"
    override val toggles = "Switch and Checkbox: on, off, disabled"
    override val items = "Item group"
    override val itemsHint = "SwitchItem, ChoiceItem, Item"
    override val switchTitle = "Notifications"
    override val switchHint = "New orders and payments"
    override val choiceHint = "The device's own setting"
    override val openRow = "A row that opens something"
    override val selection = "Selection"
    override val selectionHint = "Chip, LineTabs, Tabs, Select, ChoiceTile, StepIndicator, QtyStepper"
    override val chips = "Chips: with a count, with a dot"
    override val chipAll = "All"
    override val plan = "Plan"
    override val planStart = "Start"
    override val planStartHint = "One venue, no delivery"
    override val planStandard = "Standard"
    override val planStandardHint = "Up to three venues"
    override val planPro = "Pro"
    override val planProHint = "Any number, with analytics"
    override val sort = "Sort"
    override val sortNew = "Newest first"
    override val sortOld = "Oldest first"
    override val steps = "Steps: first, middle, done"
    override val quantity = "Quantity: free, at the minimum, at the maximum"
    override val status = "Status"
    override val statusHint = "Every Tone; a solid Badge for counters; marks of people and things"
    override val statusActive = "Active"
    override val statusCooking = "Cooking"
    override val statusLow = "Running low"
    override val statusOff = "Off"
    override val marks = "Avatar, NameMark, IconTile"
    override val feedback = "Feedback"
    override val feedbackHint = "Alert, Notice, Spinner, Progress, Skeleton"
    override val alertText = "Stays until its cause is gone."
    override val alertNoIcon = "Without an icon"
    override val noticeTitle = "Analytics is on"
    override val noticeText = "A tinted message with one thing to do."
    override val noticeAction = "Open"
    override val noticeWarnTitle = "The card expires in 3 days"
    override val noticeWarnText = "Replace it to keep the venue open."
    override val noticeDangerTitle = "Payment failed"
    override val noticeDangerText = "The server will stop tomorrow at 09:00."
    override val noticeOkTitle = "Backup done"
    override val noticeOkText = "Today at 04:00, 1.2 GB."
    override val surfaces = "Surfaces"
    override val surfacesHint = "Stat, FactRow, CopyValue, Kbd, Card, Panel, type"
    override val all = "All"
    override val statBalance = "Balance"
    override val statBalanceValue = "1 840 cr"
    override val statBalanceHint = "Enough for 42 days"
    override val statRunning = "Running"
    override val statRunningValue = "2 of 6"
    override val statRunningHint = "1 with an error"
    override val statOrders = "Orders"
    override val statOrdersHint = "+12% on last week"
    override val statDisk = "Disk"
    override val statDiskHint = "Running out"
    override val facts = "Facts"
    override val factPlan = "Plan"
    override val factId = "Identifier"
    override val factStatus = "Status"
    override val factEmpty = "Not set"
    override val shortcut = "Search"
    override val cardTitle = "Card"
    override val cardText = "A block of content, tappable or not."
    override val cardClickable = "The whole card is one target"
    override val panelTitle = "Panel"
    override val panelText = "Title, icon, description and an action"
    override val type = "Type"
    override val typeSample = "Pizza North, 14 orders"
    override val charts = "Charts"
    override val chartsHint = "BarChart and Sparkline in the accent; a series in a tone"
    override val limits = "Limits and shares"
    override val limitsHint = "Ring, Meter by meterTone, ShareBar in the chart series"
    override val table = "Table"
    override val tableHint = "DataTable in a wide window; on a phone the same records as rows"
    override val colName = "Venue"
    override val colStatus = "Status"
    override val colOrders = "Orders"
    override val colRevenue = "Revenue"
    override val emptyTitle = "Nothing here yet"
    override val emptyText = "What this is, why it is empty and what to do."
    override val noMatchTitle = "No venues match"
    override val noMatchText = "Try another filter or reset it."
    override val reset = "Reset filters"
    override val sections = "App sections and action bar"
    override val sectionsHint = "A bottom bar on a phone, tabs under the header wider; ActionBar under it"
    override val sectionsPageHint = "A section's page has its own header"
    override val sectionSummary = "Summary"
    override val sectionBoard = "Board"
    override val sectionReceipts = "Receipts"
    override val sectionMoney = "Money"
    override val total = "Total"
    override val pay = "Pay"
    override val overlays = "Overlays"
    override val overlaysHint = "Centred in a wide window, a sheet from the bottom on a phone"
    override val openDialog = "Dialog"
    override val openConfirm = "Confirm"
    override val openDelete = "Delete…"
    override val openSheet = "Side sheet"
    override val toastSuccess = "Toast"
    override val toastWarn = "Warning toast"
    override val toastDanger = "Error toast"
    override val toastSaved = "Saved"
    override val toastSavedDetail = "Pizza North, just now"
    override val toastWarnTitle = "Printer is offline"
    override val toastDangerTitle = "Could not save"
    override val toastDangerDetail = "No connection to the server"
    override val dialogTitle = "New venue"
    override val dialogText = "A short form over the page."
    override val confirmTitle = "Publish the menu?"
    override val confirmText = "Guests will see it right away."
    override val confirmAction = "Publish"
    override val deleteTitle = "Delete Pizza North?"
    override val deleteText = "The server and its database are deleted for good."
    override val typeToConfirm = "Type the name to confirm"
    override val sheetTitle = "Venue"
    override val sheetText = "From the right in a wide window, from the bottom on a phone"
    override val terminalTitle = "restoapp — pizza-north"
    override val live = "live"
    override val venues = listOf("Pizza North", "Georgia", "Sushi Bar", "Burger Lab")
    override val choices = listOf("System", "Light", "Dark")
    override val chipLabels = listOf("All", "Running", "Errors", "Off")
    override val tabLabels = listOf("Overview", "Events", "Logs", "Settings")
    override val stepLabels = listOf("Name", "Plan", "Check")
    override val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    override val logLines = listOf("09:41:02 INFO  started on :8080", "09:41:03 INFO  migrations: 42 applied", "09:41:07 WARN  printer kitchen-1 offline", "09:42:15 INFO  order #1043 paid, 1 306 ₽")
}

internal object KitStringsRu : KitStrings {
    override val title = "Компоненты"
    override val description = "Все компоненты ui-kit во всех состояниях: по ним проверяют тему и акцент"
    override val crumbService = "Сервис"
    override val dark = "Тёмная тема"
    override val light = "Светлая тема"
    override val metaHint = "Акцент и тема — из настроек"
    override val newThing = "Создать"
    override val more = "Ещё"
    override val menuLabel = "Действия"
    override val edit = "Изменить"
    override val copy = "Копировать"
    override val copied = "Скопировано"
    override val selected = "Выбрано"
    override val disabled = "Недоступно"
    override val disabledHint = "Приглушено и не реагирует на нажатия"
    override val delete = "Удалить"
    override val save = "Сохранить"
    override val cancel = "Отмена"
    override val close = "Закрыть"
    override val settings = "Настройки"
    override val search = "Поиск"
    override val primary = "Главная"
    override val accents = "Акценты и палитра"
    override val accentsHint = "Текущая палитра, затем один и тот же образец в каждом из шести акцентов"
    override val buttons = "Кнопки"
    override val buttonsHint = "Шесть вариантов, три размера; недоступные, загрузка, только иконка"
    override val variants = "Варианты"
    override val sizes = "Размеры"
    override val loading = "Загрузка"
    override val iconButtons = "Кнопки-иконки и своё содержимое"
    override val customContent = "Своё"
    override val forms = "Формы"
    override val formsHint = "Field вокруг Input: подпись, подсказка, ошибка; Switch, Checkbox, Items"
    override val nameLabel = "Название"
    override val nameHint = "Как его видят гости"
    override val namePlaceholder = "Пицца Север"
    override val emailLabel = "Почта"
    override val emailError = "Это ещё не адрес"
    override val readOnly = "Только чтение"
    override val password = "Пароль"
    override val comment = "Комментарий"
    override val commentHint = "Несколько строк"
    override val commentPlaceholder = "Что нужно знать кухне"
    override val toggles = "Switch и Checkbox: вкл., выкл., недоступно"
    override val items = "Группа строк"
    override val itemsHint = "SwitchItem, ChoiceItem, Item"
    override val switchTitle = "Уведомления"
    override val switchHint = "Новые заказы и оплаты"
    override val choiceHint = "Как настроено на устройстве"
    override val openRow = "Строка, которая что-то открывает"
    override val selection = "Выбор"
    override val selectionHint = "Chip, LineTabs, Tabs, Select, ChoiceTile, StepIndicator, QtyStepper"
    override val chips = "Чипы: со счётчиком, с точкой"
    override val chipAll = "Все"
    override val plan = "Тариф"
    override val planStart = "Старт"
    override val planStartHint = "Одна точка, без доставки"
    override val planStandard = "Стандарт"
    override val planStandardHint = "До трёх точек"
    override val planPro = "Про"
    override val planProHint = "Сколько угодно, с аналитикой"
    override val sort = "Сортировка"
    override val sortNew = "Сначала новые"
    override val sortOld = "Сначала старые"
    override val steps = "Шаги: первый, средний, всё готово"
    override val quantity = "Количество: свободно, на минимуме, на максимуме"
    override val status = "Статусы"
    override val statusHint = "Каждый Tone; сплошной Badge для счётчиков; знаки людей и вещей"
    override val statusActive = "Работает"
    override val statusCooking = "Готовится"
    override val statusLow = "Заканчивается"
    override val statusOff = "Выключено"
    override val marks = "Avatar, NameMark, IconTile"
    override val feedback = "Обратная связь"
    override val feedbackHint = "Alert, Notice, Spinner, Progress, Skeleton"
    override val alertText = "Висит, пока не исчезнет причина."
    override val alertNoIcon = "Без иконки"
    override val noticeTitle = "Аналитика включена"
    override val noticeText = "Тонированное сообщение с одним действием."
    override val noticeAction = "Открыть"
    override val noticeWarnTitle = "Карта истекает через 3 дня"
    override val noticeWarnText = "Замените её, чтобы точка не закрылась."
    override val noticeDangerTitle = "Оплата не прошла"
    override val noticeDangerText = "Сервер остановится завтра в 09:00."
    override val noticeOkTitle = "Резервная копия готова"
    override val noticeOkText = "Сегодня в 04:00, 1,2 ГБ."
    override val surfaces = "Поверхности"
    override val surfacesHint = "Stat, FactRow, CopyValue, Kbd, Card, Panel, шрифты"
    override val all = "Все"
    override val statBalance = "Баланс"
    override val statBalanceValue = "1 840 кр"
    override val statBalanceHint = "Хватит на 42 дня"
    override val statRunning = "Работают"
    override val statRunningValue = "2 из 6"
    override val statRunningHint = "1 с ошибкой"
    override val statOrders = "Заказы"
    override val statOrdersHint = "+12% к прошлой неделе"
    override val statDisk = "Диск"
    override val statDiskHint = "Заканчивается"
    override val facts = "Сведения"
    override val factPlan = "Тариф"
    override val factId = "Идентификатор"
    override val factStatus = "Статус"
    override val factEmpty = "Не задано"
    override val shortcut = "Поиск"
    override val cardTitle = "Карточка"
    override val cardText = "Блок содержимого, нажимаемый или нет."
    override val cardClickable = "Вся карточка — одна цель"
    override val panelTitle = "Панель"
    override val panelText = "Заголовок, иконка, описание и действие"
    override val type = "Шрифты"
    override val typeSample = "Пицца Север, 14 заказов"
    override val charts = "Графики"
    override val chartsHint = "BarChart и Sparkline в акценте; ряд в тоне"
    override val limits = "Лимиты и доли"
    override val limitsHint = "Ring, Meter по meterTone, ShareBar цветами графиков"
    override val table = "Таблица"
    override val tableHint = "DataTable в широком окне; на телефоне те же записи строками"
    override val colName = "Точка"
    override val colStatus = "Статус"
    override val colOrders = "Заказы"
    override val colRevenue = "Выручка"
    override val emptyTitle = "Пока ничего нет"
    override val emptyText = "Что это, почему пусто и что сделать."
    override val noMatchTitle = "Нет подходящих точек"
    override val noMatchText = "Попробуйте другой фильтр или сбросьте его."
    override val reset = "Сбросить фильтры"
    override val sections = "Разделы приложения и панель действий"
    override val sectionsHint = "Нижняя панель на телефоне, вкладки под заголовком шире; ниже ActionBar"
    override val sectionsPageHint = "У страницы раздела свой заголовок"
    override val sectionSummary = "Сводка"
    override val sectionBoard = "Доска"
    override val sectionReceipts = "Чеки"
    override val sectionMoney = "Деньги"
    override val total = "Итого"
    override val pay = "Оплатить"
    override val overlays = "Поверх страницы"
    override val overlaysHint = "По центру в широком окне, лист снизу на телефоне"
    override val openDialog = "Диалог"
    override val openConfirm = "Подтверждение"
    override val openDelete = "Удалить…"
    override val openSheet = "Боковая панель"
    override val toastSuccess = "Тост"
    override val toastWarn = "Тост-предупреждение"
    override val toastDanger = "Тост-ошибка"
    override val toastSaved = "Сохранено"
    override val toastSavedDetail = "Пицца Север, только что"
    override val toastWarnTitle = "Принтер не в сети"
    override val toastDangerTitle = "Не удалось сохранить"
    override val toastDangerDetail = "Нет связи с сервером"
    override val dialogTitle = "Новая точка"
    override val dialogText = "Короткая форма поверх страницы."
    override val confirmTitle = "Опубликовать меню?"
    override val confirmText = "Гости увидят его сразу."
    override val confirmAction = "Опубликовать"
    override val deleteTitle = "Удалить «Пиццу Север»?"
    override val deleteText = "Сервер и его база данных удаляются навсегда."
    override val typeToConfirm = "Введите название для подтверждения"
    override val sheetTitle = "Точка"
    override val sheetText = "Справа в широком окне, снизу на телефоне"
    override val terminalTitle = "restoapp — pizza-north"
    override val live = "в эфире"
    override val venues = listOf("Пицца Север", "Грузия", "Суши-бар", "Бургерная")
    override val choices = listOf("Как в системе", "Светлая", "Тёмная")
    override val chipLabels = listOf("Все", "Работают", "Ошибки", "Выключены")
    override val tabLabels = listOf("Обзор", "События", "Логи", "Настройки")
    override val stepLabels = listOf("Название", "Тариф", "Проверка")
    override val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    override val logLines = listOf("09:41:02 INFO  запущен на :8080", "09:41:03 INFO  миграций применено: 42", "09:41:07 WARN  принтер kitchen-1 не в сети", "09:42:15 INFO  заказ №1043 оплачен, 1 306 ₽")
}

internal val KitStringTables: Map<String, KitStrings> = mapOf(
    "en" to KitStringsEn,
    "ru" to KitStringsRu,
)
