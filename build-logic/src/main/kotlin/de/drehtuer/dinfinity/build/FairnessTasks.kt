package de.drehtuer.dinfinity.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the fingerprint of the physics as it is now ([PhysicsFingerprint]),
 * which is what `tools/fairness.sh` puts in the record it makes.
 */
@DisableCachingByDefault(because = "one hash over a few hundred files; nothing to gain from a cache")
abstract class PhysicsFingerprintTask : DefaultTask() {
  /** Every file that decides how a die lands (`dinfinity.fairness`). */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val physics: ConfigurableFileCollection

  /** What the paths in the fingerprint are relative to: the repository. */
  @get:Internal
  abstract val root: DirectoryProperty

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction
  fun write() {
    val fingerprint = PhysicsFingerprint.of(root.get().asFile, physics.files)
    output.get().asFile.writeText(fingerprint + "\n")
    logger.lifecycle("physics fingerprint: $fingerprint")
  }
}

/**
 * Refuses a release whose physics has no passing fairness run on the reference
 * device ([FairnessRecord.problemsFor]): the one check of fairness that can
 * reach CI, which has no device to throw dice on (`.claude/CLAUDE.md`).
 */
@DisableCachingByDefault(because = "a release gate is asked every time")
abstract class VerifyFairnessRecordTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val physics: ConfigurableFileCollection

  @get:Internal
  abstract val root: DirectoryProperty

  /** `fairness/record.txt`, which may not exist yet. */
  @get:Internal
  abstract val record: RegularFileProperty

  @TaskAction
  fun verify() {
    val fingerprint = PhysicsFingerprint.of(root.get().asFile, physics.files)
    val file = record.get().asFile
    val problems =
      if (file.isFile) {
        FairnessRecord.parse(file.readText()).problemsFor(fingerprint)
      } else {
        listOf("there is no fairness record at ${file.relativeTo(root.get().asFile).path}")
      }
    if (problems.isNotEmpty()) {
      throw GradleException(
        "This physics has not been shown fair on the reference device:\n" +
          problems.joinToString("\n") { "  - $it" } +
          "\nRun tools/fairness.sh on the Pixel 10a and commit fairness/record.txt (docs/build-setup.md).",
      )
    }
    logger.lifecycle("fairness record matches physics $fingerprint")
  }
}
