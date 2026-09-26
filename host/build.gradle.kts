plugins {
    id("superizer.kmp-library")
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.lifecycle.runtime)
            // The lock's sheet (06 §5.2). `api` for the fragment library: BiometricPrompt can only be
            // shown from a FragmentActivity, so a host's activity has to be one, and the class it
            // extends is part of what this module asks of it.
            implementation(libs.androidx.biometric)
            api(libs.androidx.fragment)
            // Firebase Cloud Messaging: the Android half of the push seam (13 §8). Compiled in
            // unconditionally, so the messaging service is always part of any host built on this
            // library. Whether it can do anything is a runtime question — it needs a
            // `google-services.json` in the *app*, which is a per-deployment secret and cannot
            // live here. Without one, `PushTransport.available` is false and every call is inert.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            // A test-only edge, which the layering check ignores on purpose: the rule is that an
            // app must not *ship* host code, not that the host's own tests may not use a fake.
            implementation(project(":testing"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android.namespace = "cx.m42.superizer.host"
