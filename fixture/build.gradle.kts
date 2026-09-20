import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

/**
 * The library's own minimal host — Adminizer's `fixture/`, in Kotlin.
 *
 * It exists so the library can prove itself without Unitool: UI tests, screenshots and the web
 * smoke test all run against this. It registers the bench app and a probe, and nothing a product
 * would ship.
 *
 * Not a published module, so it skips the convention plugin and its Android target: Android is
 * covered by the real host, and an activity here would be a second manifest to keep in step.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "fixture.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply { port = 8083 }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":ui-theme"))
            implementation(project(":ui"))
            implementation(project(":host"))
            implementation(project(":apps:test-app"))
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(project(":testing"))
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "cx.m42.superizer.fixture.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "SuperizerFixture"
            packageVersion = "1.0.0"
        }
    }
}
