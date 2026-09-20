plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

/**
 * D21: the dependency graph of 02 is a build rule, not a review convention. A forbidden edge — an
 * app reaching into `host`, `core` growing a dependency on `ui` — fails here rather than being
 * noticed a year later when it can no longer be undone.
 *
 * It reads the build scripts as text rather than the resolved graph on purpose: the rule is about
 * what somebody *wrote*, the check has to survive the configuration cache, and a declared
 * `project(":superizer:host")` is exactly the mistake being looked for.
 */
val allowedProjectDependencies = mapOf(
    ":core" to emptySet<String>(),
    ":ui-theme" to setOf(":core"),
    ":ui" to setOf(":core", ":ui-theme"),
    ":host" to setOf(":core"),
    ":testing" to setOf(":core", ":ui"),
    ":apps:test-app" to setOf(":core", ":ui-theme", ":ui", ":testing"),
    ":fixture" to setOf(":core", ":ui-theme", ":ui", ":host", ":testing", ":apps:test-app"),
)

/**
 * Test source sets are out of scope.
 *
 * The rule is "an app must not *ship* host code", not "a test may not use a fake": `host`'s own
 * tests legitimately reach for `testing`, and a check that forbade it would be a check people
 * work around rather than one they keep.
 */
fun mainDependenciesOf(script: String): String {
    val out = StringBuilder()
    var depth = 0
    var skipping = false
    script.lineSequence().forEach { line ->
        if (!skipping && Regex("""[Tt]est\.dependencies\s*\{""").containsMatchIn(line)) {
            skipping = true
            depth = 0
        }
        if (skipping) {
            depth += line.count { it == '{' } - line.count { it == '}' }
            if (depth <= 0) skipping = false
            return@forEach
        }
        out.appendLine(line)
    }
    return out.toString()
}

val checkDependencyRules by tasks.registering {
    group = "verification"
    description = "Fails on a module dependency the layering of 02 forbids."

    val scripts = subprojects.associate { it.path to it.buildFile }
    scripts.values.forEach { inputs.file(it).withPathSensitivity(PathSensitivity.RELATIVE) }
    // A task with no output is never up to date; a stamp file keeps repeat runs free.
    val stamp = layout.buildDirectory.file("dependency-rules.ok")
    outputs.file(stamp)

    doLast {
        val declaration = Regex("""project\("(:[A-Za-z0-9:_-]+)"\)""")
        val violations = buildList {
            scripts.forEach { (path, file) ->
                if (!file.exists()) return@forEach
                val allowed = allowedProjectDependencies[path]
                val used = declaration.findAll(mainDependenciesOf(file.readText()))
                    .map { it.groupValues[1] }
                    .toSet()
                if (allowed == null) {
                    if (used.isNotEmpty()) add("$path is not in the layering table but depends on $used")
                    return@forEach
                }
                (used - allowed).forEach { add("$path must not depend on $it (allowed: $allowed)") }
            }
        }
        check(violations.isEmpty()) {
            "Dependency rules (02) violated:\n" + violations.joinToString("\n") { "  - $it" }
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}

/**
 * What CI and the IDE run; `scripts/verify.sh` adds the web smoke test, the Android branch and the
 * report on top of it.
 */
tasks.register("verify") {
    group = "verification"
    description = "Static checks plus every test that needs no browser or device."
    dependsOn(checkDependencyRules)
    // Kotlin's own ABI validation (D14): the published surface is reviewed as a diff of `api/*.api`,
    // which only means anything if a change that forgot to regenerate the dump fails the build.
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:checkLegacyAbi" })
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:desktopTest" })
}
