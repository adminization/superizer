import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Everything a published Superizer module has in common: three targets, one JVM level, a published
 * artifact and an explicit public API.
 *
 * It exists because seven modules repeating the same forty lines is seven places for them to drift
 * apart — and because `group` and `version` have to be decided in one place for a composite build
 * to substitute this library for its own Maven coordinates (02).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("maven-publish")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun version(name: String): String = libs.findVersion(name).get().requiredVersion

group = providers.gradleProperty("superizer.group").getOrElse("cx.m42.superizer")
version = providers.gradleProperty("superizer.version").getOrElse("0.1.0")

@OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
kotlin {
    // D14: the published surface is reviewed as a diff of `api/*.api`, which only means anything
    // if `public` is a decision someone made rather than Kotlin's default.
    explicitApi()

    /**
     * Kotlin's own ABI validation rather than the standalone binary-compatibility-validator plugin:
     * the latter aborts root-script evaluation on Gradle 9.5 — silently, which cost an afternoon —
     * and the built-in one covers the same ground for a multiplatform library, klibs included.
     *
     * `checkLegacyAbi` fails when the dump in `api/` no longer matches the code; `updateLegacyAbi`
     * regenerates it. The diff of that file in a pull request *is* the review of the contract, which
     * is the whole point of D14.
     */
    // Calling the block is what turns validation on in Kotlin 2.4; the old `enabled` property and
    // the `klib` sub-block were both removed.
    abiValidation {
        // Nothing to configure yet — calling the block is what enables it. The filters hook lives
        // here for the day a generated class needs excluding.
    }

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                // Karma needs a browser binary. CI and this workspace have Playwright's Chromium;
                // where there is none, the wasm tests are skipped explicitly rather than failing
                // the build with a download error (verify.sh reports the skip).
                enabled = System.getenv("CHROME_BIN") != null
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    compileSdk = version("androidCompileSdk").toInt()
    defaultConfig {
        minSdk = version("androidMinSdk").toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Superizer ${project.name}")
            description.set("Superizer — a Compose Multiplatform host framework for independent apps")
            url.set("https://github.com/m42cx/superizer")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            scm {
                url.set("https://github.com/m42cx/superizer")
            }
        }
    }
}
