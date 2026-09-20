plugins {
    id("superizer.compose-library")
    alias(libs.plugins.kotlinxSerialization)
}

/**
 * Apps version themselves; the library's own version says nothing about them (02). The bench app
 * lives in the library because it exercises the *framework* — it is `module-manager` from
 * Adminizer's fixture, not a product.
 */
group = "cx.m42.superizer.apps"
version = "1.0.0"

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(project(":ui-theme"))
            implementation(project(":ui"))
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(project(":testing"))
            implementation(compose.desktop.currentOs)
        }
    }
}

android.namespace = "cx.m42.apps.testapp"
