package cx.m42.superizer.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.m42.superizer.kit.ItemGroup

/**
 * A kit [ItemGroup] with the gaps the host's list screens are laid out with: inset from the screen's
 * edges and spaced from the next group. `ItemGroup` itself leaves the gaps to the screen; every host
 * screen wants the same ones, so they live here.
 */
@Composable
internal fun HostGroup(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ItemGroup(
        modifier = modifier.padding(horizontal = 12.dp).padding(bottom = 20.dp),
        title = title,
        description = hint,
        content = content,
    )
}
