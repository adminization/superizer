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

val checkDependencyRules by tasks.registering {
    group = "verification"
    description = "Fails on a module dependency the layering of 02 forbids."

    // Only projects that actually have a build file: `:apps` is a container with nothing in it,
    // and declaring a non-existent file as an input fails the task before it runs.
    val scripts = subprojects.filter { it.buildFile.exists() }.associate { it.path to it.buildFile }
    scripts.values.forEach { inputs.file(it).withPathSensitivity(PathSensitivity.RELATIVE) }
    // Captured here rather than read from the script at execution time: with the configuration
    // cache the script object is long gone by then, and a task that reaches for it fails with a
    // null that names nothing.
    val allowed = allowedProjectDependencies
    val stamp = layout.buildDirectory.file("dependency-rules.ok")
    outputs.file(stamp)

    doLast {
        val declaration = Regex("""project\("(:[A-Za-z0-9:_-]+)"\)""")
        val testBlock = Regex("""[Tt]est\.dependencies\s*\{""")

        /**
         * Test source sets are out of scope.
         *
         * The rule is "an app must not *ship* host code", not "a test may not use a fake": `host`'s
         * own tests legitimately reach for `testing`, and a check that forbade that is a check
         * people work around rather than one they keep.
         */
        fun mainDependenciesOf(script: String): String {
            val out = StringBuilder()
            var depth = 0
            var skipping = false
            // Comments go first. The KDoc on a module says in words what this task says in code,
            // and a check that matched its own documentation could only be satisfied by deleting
            // the explanation.
            script.lineSequence().map { it.substringBefore("//") }.filterNot {
                it.trimStart().startsWith("*") || it.trimStart().startsWith("/*")
            }.forEach { line ->
                if (!skipping && testBlock.containsMatchIn(line)) {
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

        val violations = buildList {
            scripts.forEach { (path, file) ->
                val permitted = allowed[path]
                val used = declaration.findAll(mainDependenciesOf(file.readText()))
                    .map { it.groupValues[1] }
                    .toSet()
                if (permitted == null) {
                    if (used.isNotEmpty()) add("$path is not in the layering table but depends on $used")
                    return@forEach
                }
                (used - permitted).forEach { add("$path must not depend on $it (allowed: $permitted)") }
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

    /**
     * Matched by name rather than named by path.
     *
     * Not every module has every task: `:fixture` is not published, so it does not apply
     * `superizer.kmp-library` and has no ABI dump to check. A hard `":fixture:checkLegacyAbi"`
     * fails the task graph before a single check runs — which is what it did, unnoticed, because
     * `verify.sh` names the tasks itself and nothing else asked for `verify`.
     *
     * A lazy `TaskCollection` also has to be used here rather than `findByName`: the root script
     * is evaluated before any subproject, so at this point none of their tasks exist yet.
     */
    // Kotlin's own ABI validation (D14): the published surface is reviewed as a diff of `api/*.api`,
    // which only means anything if a change that forgot to regenerate the dump fails the build.
    val modules = subprojects.filter { it.buildFile.exists() }
    dependsOn(modules.map { module -> module.tasks.matching { it.name == "checkLegacyAbi" } })
    dependsOn(modules.map { module -> module.tasks.matching { it.name == "desktopTest" } })
}
