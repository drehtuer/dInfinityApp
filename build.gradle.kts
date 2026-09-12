// The root project builds nothing itself. It carries the repository-wide
// verification tasks: invariants that are easy to break and cheap to check.

plugins {
  base
}

/**
 * Every directory holding a `build.gradle.kts` must be registered in
 * `settings.gradle.kts`, and every registered module must exist on disk.
 * Forgetting the `include(...)` line produces a module that silently never
 * builds and never runs its tests, which is worse than a broken build.
 */
val verifyModuleGraph by tasks.registering {
  group = "verification"
  description = "Checks that the modules on disk and the modules in settings.gradle.kts agree."

  val rootDir = layout.projectDirectory.asFile
  val declared = subprojects.filter { it.buildFile.exists() }.map { it.path }.sorted()
  val declaredDirs = subprojects.map { it.projectDir.relativeTo(rootDir).path }.sorted()

  inputs.property("declared", declared)
  outputs.upToDateWhen { false }

  doLast {
    val ignored = setOf("build-logic", "build", ".git", ".gradle", "design", "docs")
    val onDisk = rootDir.walkTopDown()
      .onEnter { it == rootDir || it.name !in ignored && !it.name.startsWith(".") }
      .filter { it.isFile && it.name == "build.gradle.kts" && it.parentFile != rootDir }
      .map { it.parentFile.relativeTo(rootDir).path }
      .toSortedSet()

    val missingFromSettings = onDisk - declaredDirs.toSet()
    val missingFromDisk = declaredDirs.filterNot { rootDir.resolve(it).isDirectory }

    val problems = buildList {
      missingFromSettings.forEach {
        add("$it has a build script but no include(\":${it.replace('/', ':')}\") in settings.gradle.kts")
      }
      missingFromDisk.forEach { add("settings.gradle.kts includes $it, which does not exist") }
    }
    if (problems.isNotEmpty()) {
      error("Module graph is inconsistent:\n" + problems.joinToString("\n") { "  - $it" })
    }
    logger.lifecycle("Module graph: ${declared.size} modules, settings and disk agree.")
  }
}

/**
 * `.claude/CLAUDE.md` requires every document in `docs/` to be linked from the
 * table in `README.md`. That rule is easier to enforce than to remember.
 */
val verifyDocsIndex by tasks.registering {
  group = "verification"
  description = "Checks that README.md links every document in docs/, plus SECURITY.md and LICENSE."

  val readme = layout.projectDirectory.file("README.md").asFile
  val docsDir = layout.projectDirectory.dir("docs").asFile

  inputs.file(readme)
  inputs.dir(docsDir)
  outputs.upToDateWhen { false }

  doLast {
    val text = readme.readText()
    val unlinked = docsDir.listFiles()
      .orEmpty()
      .filter { it.isFile && it.extension == "md" }
      .map { "docs/${it.name}" }
      .plus(listOf("SECURITY.md", "LICENSE"))
      .filterNot { text.contains("($it)") }
      .sorted()
    if (unlinked.isNotEmpty()) {
      error(
        "README.md does not link:\n" + unlinked.joinToString("\n") { "  - $it" } +
          "\nAdd them to the Documentation table (see .claude/CLAUDE.md)."
      )
    }
    logger.lifecycle("Documentation index: README.md links every document in docs/, SECURITY.md and LICENSE.")
  }
}

/**
 * Source that git ignores is source that never reaches a clone. That is how
 * `RenameApkTask.kt` went missing: the `build/` line in `.gitignore` matches
 * any directory of that name, and one Kotlin package happens to be called
 * `build`. The repository still built for everyone who already had the file,
 * which is exactly what makes the failure worth a check rather than care.
 */
val verifySourcesTracked by tasks.registering {
  group = "verification"
  description = "Checks that git does not ignore any Kotlin source file."

  val rootDir = layout.projectDirectory.asFile
  outputs.upToDateWhen { false }

  doLast {
    // A directory named `build` is Gradle output when it sits next to a build
    // script, and a Kotlin package otherwise — which is the whole point here.
    fun isGradleOutput(dir: File) = dir.name == "build" && dir.parentFile.resolve("build.gradle.kts").isFile
    val sources = rootDir.walkTopDown()
      .onEnter { it == rootDir || (!it.name.startsWith(".") && !isGradleOutput(it)) }
      .filter { it.isFile && it.extension in setOf("kt", "kts") }
      .map { it.relativeTo(rootDir).path }
      .toList()
    if (sources.isEmpty()) return@doLast

    val process = ProcessBuilder("git", "check-ignore", "--stdin")
      .directory(rootDir)
      .redirectErrorStream(true)
      .start()
    process.outputStream.bufferedWriter().use { writer ->
      sources.forEach { writer.appendLine(it) }
    }
    val ignored = process.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
    val status = process.waitFor()
    // 0: some path is ignored. 1: none is. Anything else means git could not
    // answer (no repository, no git), which is not this check's business.
    if (status > 1) {
      logger.lifecycle("Tracked sources: skipped, git could not be asked.")
      return@doLast
    }
    if (ignored.isNotEmpty()) {
      error(
        "git ignores these source files, so they will not reach a clone:\n" +
          ignored.sorted().joinToString("\n") { "  - $it" } +
          "\nFix .gitignore rather than committing them with --force."
      )
    }
    logger.lifecycle("Tracked sources: git ignores none of the ${sources.size} Kotlin files.")
  }
}

tasks.named("check") {
  dependsOn(verifyModuleGraph, verifyDocsIndex, verifySourcesTracked)
}
