plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

/**
 * A host in thirty lines, built by somebody who has never seen the library's source.
 *
 * If this compiles and its test passes, the published artifacts are complete: the contract is
 * visible, the widgets are visible, the host services are visible, and the bench app can be
 * registered and driven. If it does not, the publication is broken in a way no amount of green
 * inside the library would have shown.
 */
val superizerVersion = "0.1.0"

dependencies {
    implementation("cx.m42.superizer:core:$superizerVersion")
    implementation("cx.m42.superizer:ui-theme:$superizerVersion")
    implementation("cx.m42.superizer:ui:$superizerVersion")
    implementation("cx.m42.superizer:host:$superizerVersion")
    implementation("cx.m42.superizer.apps:test-app:1.0.0")
    implementation(compose.desktop.currentOs)

    testImplementation(kotlin("test"))
    testImplementation("cx.m42.superizer:testing:$superizerVersion")
    testImplementation("org.jetbrains.compose.ui:ui-test:1.11.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
}
