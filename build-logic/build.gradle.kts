plugins {
    `kotlin-dsl`
}

/**
 * Seven modules with the same forty lines of KMP configuration is what a convention plugin is for.
 * The plugins are precompiled script plugins, so they read like the build files they replace.
 *
 * The dependencies below are the Gradle plugins those scripts apply: a precompiled script can only
 * `id("…")` a plugin whose implementation is on this build's own classpath.
 */
dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
}
