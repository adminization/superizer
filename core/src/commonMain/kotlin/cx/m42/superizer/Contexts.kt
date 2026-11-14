package cx.m42.superizer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * What the whole host is pointed at — a server, an account, a workspace (RestoApp D6).
 *
 * The library draws the switcher and nothing else: a row at the top of the drawer with the current
 * context, and a sheet listing the others, the way ChatGPT lists workspaces and GitHub lists
 * accounts. What switching *means* — another backend, another set of apps, another Home — is the
 * host's business; the library hands it the id and gets out of the way.
 *
 * A host that says nothing has one context, the whole app, and the drawer looks exactly as it did
 * before contexts existed: no row, no sheet. That is what keeps Unitool unchanged.
 */

/** One thing the host can be pointed at, as the switcher shows it. */
public data class HostContext(
    /** Stable: what [ContextPort.switchTo] is called with, and the test tag `context:item-<id>`. */
    val id: String,
    /** Its name, in the host's words: "Demo restaurant". */
    val title: String,
    /** One line under the name — an address, an account. */
    val subtitle: String? = null,
    /**
     * The context this one belongs to — a server of a cloud account: the switcher draws it right under its parent,
     * indented, so the account and its servers read as one group, and either can be picked. It must come after the
     * parent in [ContextPort.contexts]; a parent the list does not have makes it an ordinary row.
     */
    val parent: String? = null,
    /** A short state of it next to the name — «работает», «остановлен» — and its tone; null: nothing. */
    val status: ContextStatus? = null,
)

/** A state of a context the switcher shows as a small badge. [tone]: `success`, `warning`, `danger`, `info`. */
public data class ContextStatus(val text: String, val tone: String? = null)

/** Which contexts the host has, which one is in force, and the doors to add and manage them. */
public interface ContextPort {
    /** In the order the switcher lists them — the person's own order. */
    public val contexts: StateFlow<List<HostContext>>

    /** The one in force; null when the host has none of its own (see [Single]). */
    public val current: StateFlow<HostContext?>

    /** The person picked [id] in the sheet. The drawer is already closing when this is called. */
    public fun switchTo(id: String)

    /**
     * Where a long press on the drawer's row goes: the context used before this one, as a long
     * press on GitHub's profile tab does. Null when there is nowhere to go back to.
     */
    public fun previous(): HostContext? = null

    /** The sheet's heading, in the host's words for [langTag]: "Servers". */
    public fun title(langTag: String): String

    /** A screen that adds one more — the sheet's "+" row reads its title. Null: no such row. */
    public val addScreen: HostScreen? get() = null

    /**
     * Every "+" row of the sheet, in order — "Connect a server", "Connect a cloud account". The first is the one tagged
     * `context:add`, the others `context:add-<screen id>`. By default just [addScreen].
     */
    public val addScreens: List<HostScreen> get() = listOfNotNull(addScreen)

    /** A screen that renames, reorders and removes them — the sheet's last row. Null: no such row. */
    public val manageScreen: HostScreen? get() = null

    public companion object {
        /** The whole app is one context, and the drawer shows no switcher. The default. */
        public val Single: ContextPort = object : ContextPort {
            override val contexts: StateFlow<List<HostContext>> = MutableStateFlow(emptyList<HostContext>()).asStateFlow()
            override val current: StateFlow<HostContext?> = MutableStateFlow<HostContext?>(null).asStateFlow()
            override fun switchTo(id: String) {}
            override fun title(langTag: String): String = ""
        }
    }
}
