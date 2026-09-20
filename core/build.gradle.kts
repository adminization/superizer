plugins {
    id("superizer.kmp-library")
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // compose.runtime and nothing else from Compose (02): the contract describes an app,
            // it does not draw one. `@Composable` and snapshot state are all it takes from here.
            api(libs.compose.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android.namespace = "cx.m42.superizer.core"
