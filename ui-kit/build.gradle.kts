plugins {
    id("superizer.compose-library")
}

/**
 * The component kit: shadcn/ui-style building blocks with their own light and dark theme.
 *
 * It stands on nothing of Superizer's — no `core`, no `ui-theme` — so an app, a host, or a project
 * that is not a Superizer app at all can take it alone. `ui` exports it, and the widgets there point
 * at it in their deprecations.
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Exported for the same reason `ui` exports them: a component's parameters are Compose
            // types, and an app must not resolve a second, different Compose (02).
            api(libs.compose.foundation)
            api(libs.compose.ui)
            // Lucide as `ImageVector`s: every icon a component takes is a plain `ImageVector`, so an
            // app is free to pass its own, and Lucide is only the set it gets for nothing.
            api(libs.icons.lucide)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
        }
    }
}

android.namespace = "cx.m42.superizer.kit"
