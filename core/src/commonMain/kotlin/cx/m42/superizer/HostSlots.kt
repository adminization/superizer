package cx.m42.superizer

import androidx.compose.runtime.Composable

/*
 * Where a host puts screens of its own into the shell (ssh-new 07 §2.4, D184).
 *
 * The shell is the library's and a host cannot add a destination to a sealed hierarchy — so the
 * library offers slots instead of screens: a block in Settings, a whole screen behind it, a line on
 * Home. The host writes the content from the library's components and brings its own words; the
 * library draws the frame and nothing else. SSH keys, Storage & security and Backup are the first.
 */

/** A block in Settings, after the host's own sections and before the apps'. */
public interface HostSection {
    /** Stable, for the test tag `settings:host:<id>`. */
    public val id: String

    /** The section's heading, in the host's words for [langTag]. */
    public fun title(langTag: String): String

    /** [open] shows one of the host's screens, with a back button to Settings. */
    @Composable
    public fun Content(superizer: Superizer, open: (HostScreen) -> Unit)
}

/** A whole screen of the host's: the shell draws the bar with [title] and a back button, the host the rest. */
public interface HostScreen {
    /** Stable, for the test tag `host:<id>`. */
    public val id: String

    public fun title(langTag: String): String

    /** Whether the window must be kept out of screenshots while this is shown (an import wizard, T10). */
    public val secure: Boolean get() = false

    @Composable
    public fun Content(superizer: Superizer, back: () -> Unit, open: (HostScreen) -> Unit)
}

/**
 * One line above the tiles on Home. Returns whether it drew anything — the shell keeps the spacing
 * honest with it. D179: a status of Unsafe or worse stays on Home until it is fixed.
 */
public fun interface HomeBanner {
    @Composable
    public fun Content(superizer: Superizer, open: (HostScreen) -> Unit): Boolean
}
