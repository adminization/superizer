package cx.m42.apps.testapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
public data class SecretTestConfig(val mode: String = "default")

/**
 * A secret app (Unitool idea/09), to see what one looks like from inside and to show from outside
 * that nothing gives it away: not the catalog, Home, the drawer, Settings, a release Service Menu
 * or the backup's list. Its code opens it, for one visit; whatever it stores outlives the visit,
 * and the visit counter on its screen is how a person checks that.
 *
 * Nothing but the code: no deep link, no push topic — the registry would refuse either.
 */
public class SecretTestApp : SuperizerApp<SecretTestConfig>() {

    override val manifest: AppManifest = AppManifest(
        id = AppId("secret-test"),
        version = "1.0.0",
        minHostContract = 4,
        metadata = AppMetadata(
            title = localized("en" to "Secret app", "ru" to "Секретное приложение"),
            description = localized(
                "en" to "Opened by its code only, one visit at a time",
                "ru" to "Открывается только своим кодом, на один визит",
            ),
            icon = AppIcon.Letter('S'),
            hidden = true,
            secret = true,
            category = "Service",
        ),
    )

    override val configSpec: AppConfigSpec<SecretTestConfig> =
        AppConfigSpec(SecretTestConfig.serializer(), SecretTestConfig())

    override fun launch(runtime: InstanceRuntime, config: SecretTestConfig): AppInstance =
        SecretTestInstance(runtime, config)

    public companion object {
        /** What a host would put in its promo table — and tell the store's reviewers (idea/09 §6). */
        public val PROMO_CODE: String = "SECRET-2026"

        public fun promoConfig(): AppConfig =
            AppConfigSpec(SecretTestConfig.serializer(), SecretTestConfig()).encode(SecretTestConfig(mode = "promo"))
    }
}

internal class SecretTestInstance(
    val runtime: InstanceRuntime,
    val config: SecretTestConfig,
) : AppInstance() {

    private val _visits = MutableStateFlow(0)
    val visits: StateFlow<Int> get() = _visits.asStateFlow()

    /** When the visit before this one began; null on the first. */
    private val _previous = MutableStateFlow<Long?>(null)
    val previous: StateFlow<Long?> get() = _previous.asStateFlow()

    override suspend fun onLaunch() {
        val visits = (runtime.storage.get(KEY_VISITS)?.toIntOrNull() ?: 0) + 1
        _previous.value = runtime.storage.get(KEY_LAST)?.toLongOrNull()
        runtime.storage.set(KEY_VISITS, visits.toString())
        runtime.storage.set(KEY_LAST, runtime.clock.now().toString())
        _visits.value = visits
    }

    @Composable
    override fun Content() {
        SecretTestScreen(this)
    }

    internal companion object {
        const val KEY_VISITS = "visits"
        const val KEY_LAST = "last-visit"
    }
}

@Composable
private fun SecretTestScreen(instance: SecretTestInstance) {
    val strings = instance.runtime.locale.pick(SecretStringTables, SecretStringsEn)
    val visits by instance.visits.collectAsState()
    val previous by instance.previous.collectAsState()
    val tokens = AppTheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Section(title = strings.visitTitle) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = strings.visit(visits),
                    style = tokens.title,
                    color = tokens.foreground,
                    modifier = Modifier.testTag("secret-test:visits"),
                )
                Spacer(Modifier.height(4.dp))
                val ago = previous?.let { ((instance.runtime.clock.now() - it) / 60_000).coerceAtLeast(0) }
                Text(
                    text = if (ago == null) strings.firstVisit else strings.previousVisit(ago),
                    style = tokens.label,
                    color = tokens.muted,
                )
            }
        }
        Section(title = strings.hiddenTitle) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                strings.hiddenLines.forEach { line ->
                    Text(text = "• $line", style = tokens.body, color = tokens.foreground)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Section(title = strings.keptTitle) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(text = strings.kept, style = tokens.body, color = tokens.foreground)
                Spacer(Modifier.height(8.dp))
                Text(text = "mode = ${instance.config.mode}", style = tokens.mono, color = tokens.muted)
            }
        }
    }
}

/** The secret app's own words (D11), next to the bench app's. */
internal interface SecretStrings {
    val visitTitle: String
    fun visit(n: Int): String
    val firstVisit: String
    fun previousVisit(minutesAgo: Long): String
    val hiddenTitle: String
    val hiddenLines: List<String>
    val keptTitle: String
    val kept: String
}

internal object SecretStringsEn : SecretStrings {
    override val visitTitle = "This visit"
    override fun visit(n: Int) = "Visit $n"
    override val firstVisit = "The first one on this device"
    override fun previousVisit(minutesAgo: Long) =
        if (minutesAgo < 1) "The previous one began under a minute ago" else "The previous one began $minutesAgo min ago"
    override val hiddenTitle = "Where you will not find it"
    override val hiddenLines = listOf(
        "Not in All Apps, on Home or in the menu",
        "Not in Settings, not in Storage & security",
        "No QR code or link opens it — only its code",
        "Leave it and it is gone; come back by the code",
        "Away in the background for over 5 minutes, and it closes",
    )
    override val keptTitle = "What it keeps"
    override val kept = "Its data stays between visits and goes into the backup, " +
        "but the backup's list does not show it."
}

internal object SecretStringsRu : SecretStrings {
    override val visitTitle = "Этот визит"
    override fun visit(n: Int) = "Визит $n"
    override val firstVisit = "Первый на этом устройстве"
    override fun previousVisit(minutesAgo: Long) =
        if (minutesAgo < 1) "Предыдущий начался меньше минуты назад" else "Предыдущий начался $minutesAgo мин назад"
    override val hiddenTitle = "Где его не найти"
    override val hiddenLines = listOf(
        "Ни во «Всех приложениях», ни на главной, ни в меню",
        "Ни в настройках, ни в «Хранении и безопасности»",
        "Ни QR-код, ни ссылка его не открывают — только свой код",
        "Вышли — и его нет; вернуться можно снова по коду",
        "Пробыло в фоне дольше 5 минут — закрывается",
    )
    override val keptTitle = "Что оно хранит"
    override val kept = "Его данные остаются между визитами и попадают в резервную копию, " +
        "но в её списке оно не показывается."
}

internal val SecretStringTables: Map<String, SecretStrings> = mapOf(
    "en" to SecretStringsEn,
    "ru" to SecretStringsRu,
)
