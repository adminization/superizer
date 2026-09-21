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

/**
 * Its own floor, on its own counter.
 *
 * "Apps version themselves" has to survive the branch-driven publishing the library uses, or the
 * bench app would republish a fixed `1.0.0` on every push and the registry would answer 409. CI
 * resolves this one against `cx.m42.superizer.apps:test-app` separately and passes it back, which
 * is what independent versioning means when the release is not a manual act.
 */
version = providers.gradleProperty("superizer.appsVersion").getOrElse("1.0.0")

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
