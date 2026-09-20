/**
 * A build of its own, on purpose.
 *
 * This is the only thing in the repository that consumes Superizer the way a stranger would: from a
 * repository, by coordinates, with no access to the sources. A composite build forgives a missing
 * `api` dependency, an absent sources jar and a klib without metadata; this does not.
 */
rootProject.name = "superizer-consumer"

pluginManagement {
    repositories {
        mavenLocal()
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // mavenLocal first and deliberately: the point is to resolve what `publishToMavenLocal`
        // just wrote, not whatever happens to be on the internet.
        mavenLocal()
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
