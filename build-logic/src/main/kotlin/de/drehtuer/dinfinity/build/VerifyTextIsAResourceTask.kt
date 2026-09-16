package de.drehtuer.dinfinity.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build on a word written straight into Kotlin where a screen reads
 * it out ([TextIsAResource]).
 *
 * Per module rather than once at the root, so it runs where the sources are,
 * stays up to date with them, and puts its complaint next to the module that
 * has to answer it. Applied by `dinfinity.quality`, which every module applies,
 * so no module can forget it — the pure-Kotlin ones simply have nothing that
 * draws and so nothing to report.
 */
@CacheableTask
abstract class VerifyTextIsAResourceTask : DefaultTask() {
  @get:InputFiles
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ConfigurableFileCollection

  /**
   * What paths are reported relative to, so the message is something to click
   * rather than an absolute path out of somebody else's checkout.
   */
  @get:Internal
  abstract val relativeTo: DirectoryProperty

  /**
   * File names this module is allowed to write English into, each one an
   * argument somebody had to make.
   *
   * By name rather than by directory, and declared in the module's own build
   * script beside the reason, so the gap is a line in a diff instead of a
   * folder nothing looks in — the same way the coverage exclusions are kept
   * (`.claude/CLAUDE.md`).
   */
  @get:Input
  abstract val exempt: SetProperty<String>

  @TaskAction
  fun verify() {
    val root = relativeTo.get().asFile
    val excused = exempt.get()
    val complaints =
      sources.files.sortedBy { it.path }.filterNot { it.name in excused }.flatMap { file ->
        TextIsAResource.offences(file.readText()).map { offence ->
          "  - ${file.relativeTo(root).path}:${offence.line}: \"${offence.literal}\""
        }
      }
    if (complaints.isNotEmpty()) {
      throw GradleException(
        "Text a person reads must be a string resource, not a Kotlin literal:\n" +
          complaints.joinToString("\n") +
          "\nMove each one into the module's res/values/strings.xml and read it with " +
          "stringResource(...). See docs/architecture.md, \"Text a person reads\".",
      )
    }
    logger.lifecycle(
      "Text: ${sources.files.size} files, every word a person reads is a resource " +
        "(${excused.size} excused).",
    )
  }
}
