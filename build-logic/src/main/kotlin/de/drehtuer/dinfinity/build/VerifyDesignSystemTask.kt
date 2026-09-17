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
 * Fails the build on a screen that reached past the theme
 * ([FollowsTheDesignSystem]).
 *
 * The design system ships this rule as an oxlint config for the prototype's
 * JSX, where it cannot see a line of Kotlin. Without it the drift is invisible
 * until somebody opens the prototype and the app side by side — which is how
 * every screen in this app ended up with a corner radius the system says it
 * does not have (`docs/design-handover.md`).
 *
 * Per module, like `verifyTextIsAResource` and for the same reasons: it runs
 * where the sources are and complains next to the module that has to answer it.
 */
@CacheableTask
abstract class VerifyDesignSystemTask : DefaultTask() {
  @get:InputFiles
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ConfigurableFileCollection

  /** What paths are reported relative to, so the message is something to click. */
  @get:Internal
  abstract val relativeTo: DirectoryProperty

  /**
   * File names allowed to write the palette out as numbers.
   *
   * There is one honest reason to: being the palette. Declared by name in the
   * module's own build script beside that reason, so the gap is a line in a
   * diff rather than a folder nothing looks in.
   */
  @get:Input
  abstract val exempt: SetProperty<String>

  @TaskAction
  fun verify() {
    val root = relativeTo.get().asFile
    val excused = exempt.get()
    val complaints =
      sources.files
        .sortedBy { it.path }
        .filterNot { it.name in excused }
        .flatMap { file ->
          FollowsTheDesignSystem.offences(file.readText()).map { offence ->
            "  - ${file.relativeTo(root).path}:${offence.line}: ${offence.text} — ${offence.why}"
          }
        }
    if (complaints.isNotEmpty()) {
      throw GradleException(
        "These reach past the design system rather than using it:\n" +
          complaints.joinToString("\n") +
          "\nUse the theme's tokens and ui/common's components, or say why not: " +
          "write \"design-system-exception\" on the line, or in the comment " +
          "directly above it, with the reason. See docs/design-handover.md.",
      )
    }
    logger.lifecycle("Design: ${sources.files.size} files follow the system (${excused.size} excused).")
  }
}
