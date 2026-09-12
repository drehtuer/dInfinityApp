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
import javax.xml.parsers.DocumentBuilderFactory

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
      logger.lifecycle("No instrumented tests in ${project.path}.")
      return
    }

    val totals = reports.map(::readCounts)
    val tests = totals.sumOf(Counts::tests)
    val failures = totals.sumOf(Counts::failures)
    val errors = totals.sumOf(Counts::errors)
    val skipped = totals.sumOf(Counts::skipped)

    if (failures > 0 || errors > 0) {
      throw GradleException(
        "$failures failing and $errors erroring instrumented tests out of $tests. " +
          "See the report at ${directory?.absolutePath}.",
      )
    }
    if (tests == 0 && expectResults.get()) {
      throw GradleException("The instrumented test run reported no tests at all in ${project.path}.")
    }
    logger.lifecycle("$tests instrumented tests passed on device ($skipped skipped).")
  }

  private fun isJUnitReport(file: File): Boolean =
    file.isFile && file.name.startsWith("TEST-") && file.extension == "xml"

  private fun readCounts(report: File): Counts {
    val root =
      DocumentBuilderFactory
        .newInstance()
        .apply { isNamespaceAware = false }
        .newDocumentBuilder()
        .parse(report)
        .documentElement

    fun attribute(name: String): Int = root.getAttribute(name).toIntOrNull() ?: 0
    return Counts(
      tests = attribute("tests"),
      failures = attribute("failures"),
      errors = attribute("errors"),
      skipped = attribute("skipped"),
    )
  }

  private data class Counts(
    val tests: Int,
    val failures: Int,
    val errors: Int,
    val skipped: Int,
  )
}
