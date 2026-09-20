plugins {
    id("superizer.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            // Foundation, not the whole of `ui`: tokens need Dp, Color, TextStyle and a
            // CompositionLocal, and a third-party component set should be able to match the host's
            // look without taking on the host's components (D23).
            api(libs.compose.foundation)
        }
    }
}

android.namespace = "cx.m42.superizer.theme"
