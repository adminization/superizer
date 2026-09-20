rootProject.name = "superizer"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// The library itself, in dependency order: core knows nothing, host and ui know core, testing
// knows both.
include(":core")
include(":ui-theme")
include(":ui")
include(":host")
include(":testing")

// The bench app that exercises every runtime service. It lives here rather than in a host because
// it tests the framework, not any product built on it.
include(":apps:test-app")

// The minimal host — superizer's own `fixture/`, the way Adminizer has one: enough of a Super App
// to run UI tests, screenshots and the web smoke test without Unitool.
include(":fixture")
