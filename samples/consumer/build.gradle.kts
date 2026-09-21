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
/**
 * Read from the library's own `gradle.properties`, never written here.
 *
 * It used to be the literal "0.1.0", and it went stale the first time the floor moved: `verify.sh`
 * publishes the library at whatever `superizer.version` says and then builds this, so a hardcoded
 * number asks mavenLocal for a version that was never published. The failure is a resolution error
 * naming a coordinate that looks perfectly reasonable, several steps away from the line that
 * caused it.
 *
 * A stranger consuming this library does pin a literal version, which is the whole point of the
 * sample — but a stranger is not republishing the library between builds, and this one is.
 */
fun libraryProperty(name: String, fallback: String): String {
    val file = rootDir.resolve("../../gradle.properties")
    if (!file.exists()) return fallback
    return file.readLines()
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.trim()
        ?: fallback
}

val superizerVersion = libraryProperty("superizer.version", "0.1.0")
val testAppVersion = libraryProperty("superizer.appsVersion", "1.0.0")

logger.lifecycle("consumer: resolving superizer $superizerVersion, test-app $testAppVersion from mavenLocal")

dependencies {
    implementation("cx.m42.superizer:core:$superizerVersion")
    implementation("cx.m42.superizer:ui-theme:$superizerVersion")
    implementation("cx.m42.superizer:ui:$superizerVersion")
    implementation("cx.m42.superizer:host:$superizerVersion")
    implementation("cx.m42.superizer.apps:test-app:$testAppVersion")
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
