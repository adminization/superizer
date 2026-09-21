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

/**
 * One released version in `gradle.properties`, and a snapshot of it on demand.
 *
 * `-Psuperizer.snapshot=true` is what CI passes for every push to the default branch, so a
 * downstream build can track the library while it is being written without anybody cutting a tag
 * for a half-finished change. A tag publishes the bare version, and `publish.yml` refuses to run
 * when the tag and this property disagree — the tag is the claim, this is the fact.
 */
val releaseVersion = providers.gradleProperty("superizer.version").getOrElse("0.1.0")
val isSnapshot = providers.gradleProperty("superizer.snapshot").getOrElse("false").toBoolean()
version = if (isSnapshot && !releaseVersion.endsWith("-SNAPSHOT")) "$releaseVersion-SNAPSHOT" else releaseVersion

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

/**
 * Where a release goes, and who it says it is.
 *
 * The slug is a property rather than a constant so a fork publishes to its own registry by
 * setting one line, instead of editing a convention plugin every module inherits.
 */
val githubSlug = providers.gradleProperty("superizer.githubSlug").getOrElse("adminization/superizer")
val projectUrl = "https://github.com/$githubSlug"

publishing {
    repositories {
        /**
         * GitHub Packages, with credentials Gradle resolves at execution time.
         *
         * `credentials(PasswordCredentials::class)` rather than reading the environment here: the
         * values are looked up only when a task actually publishes to this repository, so
         * `publishToMavenLocal` and every test task still run with no token at all, and the token
         * never lands in the configuration cache. The names Gradle looks for come from the
         * repository name — `GitHubPackagesUsername` and `GitHubPackagesPassword`, either as
         * Gradle properties in `~/.gradle/gradle.properties` or as `ORG_GRADLE_PROJECT_*` in the
         * environment, which is what `publish.yml` sets.
         *
         * Reading from here needs a token too: GitHub Packages authenticates downloads even for a
         * public repository. See the README for what a consumer has to do.
         */
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/$githubSlug")
            credentials(PasswordCredentials::class)
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Superizer ${project.name}")
            description.set("Superizer — a Compose Multiplatform host framework for independent apps")
            url.set(projectUrl)
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("adminization")
                    name.set("Adminizer")
                    url.set("https://github.com/adminization")
                }
            }
            scm {
                url.set(projectUrl)
                connection.set("scm:git:$projectUrl.git")
                developerConnection.set("scm:git:ssh://git@github.com/$githubSlug.git")
            }
        }
    }
}
