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

/**
 * A relative link that points at nothing is a broken document, and the ones
 * that break are exactly the ones nobody clicks: a renamed file, a moved
 * heading, a `docs/` link written from the wrong directory. All three happened
 * while the design links were being repointed away from claude.ai.
 *
 * Only relative links are checked. External URLs need the network and would
 * turn an offline build into a failing one.
 */
val verifyDocsLinks by tasks.registering {
  group = "verification"
  description = "Checks that every relative Markdown link points at something that exists."

  val rootDir = layout.projectDirectory.asFile
  outputs.upToDateWhen { false }

  doLast {
    val ignored = setOf("build", ".git", ".gradle", "node_modules")
    val markdown = rootDir.walkTopDown()
      .onEnter { it == rootDir || (it.name !in ignored && !it.name.startsWith(".")) }
      .filter { it.isFile && it.extension == "md" }
      .toList()

    // [text](target) — skipping images is not wanted; a missing image is a
    // broken document too. Anchors, absolute URLs and mailto: are not ours.
    val link = Regex("""\[[^]]*]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
    val problems = markdown.flatMap { file ->
      link.findAll(file.readText())
        .map { it.groupValues[1] }
        .filterNot { it.startsWith("http://") || it.startsWith("https://") }
        .filterNot { it.startsWith("#") || it.startsWith("mailto:") }
        .map { it.substringBefore('#') }
        .filter { it.isNotEmpty() }
        .filterNot { file.parentFile.resolve(it).exists() }
        .map { "${file.relativeTo(rootDir).path} -> $it" }
        .toList()
    }
    if (problems.isNotEmpty()) {
      error(
        "Broken relative links:\n" + problems.sorted().joinToString("\n") { "  - $it" }
      )
    }
    logger.lifecycle("Documentation links: ${markdown.size} files, every relative link resolves.")
  }
}

/**
 * The coverage floor, enforced here because SonarQube cannot enforce half of it.
 *
 * `.claude/CLAUDE.md` asks for **function and branch** coverage, and for
 * neither to sink in a pull request. SonarQube's coverage model has only line
 * and condition counters — there is no method counter to import — so the
 * function figure reaches JaCoCo's reports and stops there. This task reads
 * those reports and holds both numbers to a floor.
 *
 * A floor rather than a comparison against `main`: the numbers live in
 * `gradle.properties`, so raising them is a visible line in a diff and lowering
 * them is an argument someone has to make in a pull request. A job that
 * recomputes `main`'s coverage to compare against would be slower, would only
 * work on CI, and would still need someone to notice the drop.
 *
 * Device-only modules are left out for the same reason SonarQube leaves them
 * out of the coverage figure: their tests cannot run here, so their number
 * would measure the runner rather than the code.
 */
/**
 * Files that need a device, inside modules that mostly do not.
 *
 * Whole modules are left out below; these are the stragglers — a composable
 * that can only be exercised by a real `Surface`, a sensor listener that needs
 * real sensors, the one file that names the physics engine. Excluding them by
 * name rather than excluding their modules keeps everything around them
 * measured, which is the point: the gap should be visible and small, not hidden
 * behind a directory (`.claude/CLAUDE.md`).
 *
 * Kept in step with sonar.coverage.exclusions, which lists the same files.
 * Static analysis still covers every one of them.
 */
val deviceOnlyFiles: Set<String> =
  setOf(
    "DiceTray.kt",
    "ShakeToRoll.kt",
    "RollWiring.kt",
  )

val coverageReportFiles: List<File> =
  run {
    // Kept in step with sonar.coverage.exclusions: device-only modules cannot
    // run their tests here, so their figure would measure the runner.
    val deviceOnly = setOf("simulation/jolt", "render/filament")
    val rootDirectory = layout.projectDirectory.asFile
    subprojects
      .filterNot { deviceOnly.contains(it.projectDir.relativeTo(rootDirectory).path) }
      .flatMap { project ->
        listOf(
          "reports/jacoco/test/jacocoTestReport.xml",
          "reports/coverage/test/debug/report.xml",
        ).map { project.layout.buildDirectory.file(it).get().asFile }
      }
  }

val verifyCoverage by tasks.registering {
  group = "verification"
  description = "Checks that function and branch coverage stay at or above the floor."

  val minFunction = providers.gradleProperty("dinfinity.coverage.minFunction").get().toDouble()
  val minBranch = providers.gradleProperty("dinfinity.coverage.minBranch").get().toDouble()
  val candidates = coverageReportFiles

  // Captured into the task rather than read from the script inside `doLast`:
  // the configuration cache cannot serialise a reference back to the script.
  val excludedFiles = deviceOnlyFiles

  outputs.upToDateWhen { false }

  doLast {
    val reports = candidates.filter { it.isFile }
    if (reports.isEmpty()) {
      error("No JaCoCo report found. Run `./gradlew coverageReport` first.")
    }

    // A validating parser would fetch the report's DTD over the network, which
    // would make an offline build fail on a document it does not need.
    val factory =
      javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isValidating = false
      }
    val covered = mutableMapOf("METHOD" to 0, "BRANCH" to 0)
    val missed = mutableMapOf("METHOD" to 0, "BRANCH" to 0)
    reports.forEach { report ->
      // Summed class by class rather than read off the report's own totals,
      // because that is the only level at which a single device-only *file*
      // can be left out. JaCoCo gives every class its `sourcefilename`, which
      // is what `deviceOnlyFiles` matches on — the same names, for the same
      // reason, as sonar.coverage.exclusions.
      val classes = factory.newDocumentBuilder().parse(report).getElementsByTagName("class")
      for (index in 0 until classes.length) {
        val element = classes.item(index)
        val sourceFile = element.attributes.getNamedItem("sourcefilename")?.nodeValue
        if (sourceFile != null && excludedFiles.contains(sourceFile)) continue
        val counters = element.childNodes
        for (counter in 0 until counters.length) {
          val node = counters.item(counter)
          if (node.nodeName != "counter") continue
          val type = node.attributes.getNamedItem("type").nodeValue
          if (!covered.containsKey(type)) continue
          covered[type] = covered.getValue(type) + node.attributes.getNamedItem("covered").nodeValue.toInt()
          missed[type] = missed.getValue(type) + node.attributes.getNamedItem("missed").nodeValue.toInt()
        }
      }
    }

    val percentage = { type: String ->
      val total = covered.getValue(type) + missed.getValue(type)
      if (total == 0) 100.0 else 100.0 * covered.getValue(type) / total
    }
    val function = percentage("METHOD")
    val branch = percentage("BRANCH")
    val shortfall =
      buildList {
        if (function < minFunction) add("function %.1f%% is below the %.1f%% floor".format(function, minFunction))
        if (branch < minBranch) add("branch %.1f%% is below the %.1f%% floor".format(branch, minBranch))
      }
    if (shortfall.isNotEmpty()) {
      error(
        "Coverage has sunk:\n" + shortfall.joinToString("\n") { "  - $it" } +
          "\nWrite the tests in this pull request. Lowering the floor in " +
          "gradle.properties is an argument to make in the description, not a fix."
      )
    }
    logger.lifecycle(
      "Coverage: functions %.1f%% (floor %.1f), branches %.1f%% (floor %.1f), over %d reports."
        .format(function, minFunction, branch, minBranch, reports.size)
    )
  }
}

// Coverage can only be judged once every module has written its report.
subprojects {
  plugins.withId("jacoco") {
    verifyCoverage.configure { dependsOn(tasks.named("coverageReport")) }
  }
}

tasks.named("check") {
  dependsOn(verifyModuleGraph, verifyDocsIndex, verifyDocsLinks, verifySourcesTracked, verifyCoverage)
  // build-logic is a separate, included build: nothing here reaches its tasks
  // unless it is asked for by name, so its linter would never run.
  dependsOn(gradle.includedBuild("build-logic").task(":ktlintCheckConventions"))
}
