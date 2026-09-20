/**
 * A [superizer.kmp-library] that also draws: the Compose Multiplatform plugin and its compiler.
 *
 * Kept separate so `core` can stay off `compose.foundation` entirely — the contract only needs the
 * `@Composable` annotation and snapshot state, which is `compose.runtime` (02).
 */
plugins {
    id("superizer.kmp-library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
