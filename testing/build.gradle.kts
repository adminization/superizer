plugins {
    id("superizer.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            // `ui` for the compose helpers a test of a screen needs; never `host` — a test that
            // needed the host would be a test that cannot run without one.
            api(project(":ui"))
            api(libs.compose.ui.test)
            api(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            // The TCK is annotated with `kotlin.test.Test` in a *main* source set, and on the JVM
            // those annotations only exist in the JUnit flavour. Desktop-only is also where app
            // tests live (12 §4.1), so nothing is lost by it.
            api(kotlin("test-junit"))
        }
    }
}

android.namespace = "cx.m42.superizer.testing"
