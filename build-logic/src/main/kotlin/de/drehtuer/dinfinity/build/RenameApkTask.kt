package de.drehtuer.dinfinity.build

import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Copies the APKs a variant produced to the names the project ships under:
 * `dInfinityApp-<version>.apk` for a release and `dInfinityApp-<version>-debug.apk`
 * for a debug build (see `.claude/CLAUDE.md`).
 *
 * The build's own output names are left alone — renaming in place fights the
 * Android Gradle plugin — so the named copies land in their own directory and
 * that is what CI publishes.
 */
abstract class RenameApkTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val apkDirectory: DirectoryProperty

  @get:Internal
  abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

  @get:Input
  abstract val appVersion: Property<String>

  /** `-debug` for debug builds, empty for release. */
  @get:Input
  abstract val variantSuffix: Property<String>

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun rename() {
    val loaded =
      builtArtifactsLoader.get().load(apkDirectory.get())
        ?: error("No built APKs found in ${apkDirectory.get().asFile}")
    val target = outputDirectory.get().asFile
    target.mkdirs()
    loaded.elements.forEach { element ->
      val named = File(target, "dInfinityApp-${appVersion.get()}${variantSuffix.get()}.apk")
      File(element.outputFile).copyTo(named, overwrite = true)
      logger.lifecycle("Packaged ${named.absolutePath}")
    }
  }
}
