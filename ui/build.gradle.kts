plugins {
    id("superizer.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":ui-theme"))
            // Exported: every widget here is deprecated in favour of one in the kit, and an app
            // following the replacement must already have the kit on its classpath.
            api(project(":ui-kit"))
            // Exported, not implementation: an app that draws with these must not end up resolving
            // a second, different Compose (02).
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.unstyled)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
        }
    }
}

android.namespace = "cx.m42.superizer.ui"
