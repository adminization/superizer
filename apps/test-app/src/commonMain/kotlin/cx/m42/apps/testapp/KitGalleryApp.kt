package cx.m42.apps.testapp

import androidx.compose.runtime.Composable
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppLayout
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.AppWidth
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.runtime.InstanceRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Every `ui-kit` component on one page, in its states (01-superizer: the prototype's kit screen,
 * replaced). It is what the theme and the accents are checked by on every platform: open it in
 * light and dark, at a phone's, a tablet's and a desktop's width, after Settings changed the accent.
 *
 * Wide by design ([AppWidth.Adaptive]), no config, not hidden: All apps → Kit.
 */
public class KitGalleryApp : SuperizerApp<Unit>() {

    override val manifest: AppManifest = AppManifest(
        id = AppId("kit"),
        version = "1.0.0",
        metadata = AppMetadata(
            title = localized("en" to "Kit", "ru" to "Компоненты"),
            description = localized(
                "en" to "Every ui-kit component in its states",
                "ru" to "Все компоненты ui-kit во всех состояниях",
            ),
            // Four squares: lucide's layout-grid, as a path the host draws (D29).
            icon = AppIcon.Path("M3 3h8v8H3V3zM13 3h8v8h-8V3zM3 13h8v8H3v-8zM13 13h8v8h-8v-8z"),
            category = "Service",
        ),
    )

    override val configSpec: AppConfigSpec<Unit> = AppConfigSpec.None

    override fun launch(runtime: InstanceRuntime, config: Unit): AppInstance = KitGalleryInstance(runtime)
}

internal class KitGalleryInstance(val runtime: InstanceRuntime) : AppInstance() {

    override val layout: StateFlow<AppLayout> = MutableStateFlow(AppLayout(width = AppWidth.Adaptive))

    @Composable
    override fun Content() {
        KitGalleryScreen(runtime.locale.pick(KitStringTables, KitStringsEn))
    }
}
