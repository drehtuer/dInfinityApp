package de.drehtuer.dinfinity.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Decides whether the instrumented tests passed, by reading the JUnit XML the
 * run produced.
 *
 * This exists because of a bug in AGP 9.4.0. Its runner keys per-device results
 * by the device id taken out of the JUnit unique id, but JUnit percent-escapes
 * a unique id segment, so a wireless device connected as `192.168.89.49:39337`
 * is recorded as `192.168.89.49%3A39337` and then looked up under its raw
 * serial. The lookup misses, the run is declared failed, and
 * `connectedAndroidTest` fails however green the tests were. Every device
 * attached over WiFi debugging has a colon in its serial, which is how this
 * project attaches its phone (`docs/build-setup.md`), so the task can never
 * pass on its own.
 *
 * The XML report itself is correct, so it is the source of truth here. The
 * verification is real, not a rubber stamp: a failing test, an erroring test or
 * a run that produced no results at all still fails the build. Drop this task
 * and the `ignoreFailures` that goes with it once AGP compares like with like.
 *
 * What it counts is [DeviceTestCounts]', because the report's own root
 * counters are wrong about one thing — a test that opted out with
 * `Assume.assumeTrue` is filed as a failure — and that one thing was enough to
 * stop the whole tier passing.
 */
abstract class VerifyDeviceTestResultsTask : DefaultTask() {
  @get:InputDirectory
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val resultsDirectory: DirectoryProperty

  /**
   * Whether this module is supposed to have produced results. Modules without
   * an `androidTest` source set have nothing to run, and an empty report there
   * is the correct outcome rather than a silent hole.
   */
  @get:Input
  abstract val expectResults: Property<Boolean>

  /**
   * Which module this is, for the messages.
   *
   * Carried as a property rather than read from `project` at execution time,
   * which the configuration cache does not allow. The mistake only ever showed
   * on a module with *no* instrumented tests — the one branch that mentions
   * the module by name — so it stayed hidden until a run that covered every
   * module at once.
   */
  @get:Input
  abstract val modulePath: Property<String>

  @TaskAction
  fun verify() {
    val directory = resultsDirectory.orNull?.asFile
    val reports = directory?.listFiles(::isJUnitReport).orEmpty().sortedBy(File::getName)

    if (reports.isEmpty()) {
      if (expectResults.get()) {
        throw GradleException(
          "No instrumented test results in $directory. The tests never ran — check that a " +
            "device is attached (`adb devices`), see docs/build-setup.md.",
        )
      }
      logger.lifecycle("No instrumented tests in ${modulePath.get()}.")
      return
    }

    val totals =
      reports.fold(DeviceTestCounts.NONE) { running, report ->
        running + DeviceTestCounts.of(report.readText())
      }

    if (totals.failures > 0 || totals.errors > 0) {
      throw GradleException(
        "${totals.failures} failing and ${totals.errors} erroring instrumented tests out of ${totals.tests}. " +
          "See the report at ${directory?.absolutePath}.",
      )
    }
    if (totals.tests == 0 && expectResults.get()) {
      throw GradleException("The instrumented test run reported no tests at all in ${modulePath.get()}.")
    }
    logger.lifecycle("${totals.tests} instrumented tests passed on device (${totals.skipped} skipped).")
  }

  private fun isJUnitReport(file: File): Boolean =
    file.isFile && file.name.startsWith("TEST-") && file.extension == "xml"
}
