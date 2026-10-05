package de.drehtuer.dinfinity.build

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The verdict on a device run ([VerifyDeviceTestResultsTask]).
 *
 * It stands in for `connectedDebugAndroidTest`'s own, which cannot pass on a
 * phone attached over WiFi, so it is the only thing between a red device run
 * and a green build. What has to hold is that it still *fails*: on a failing
 * test, on a run that never happened, and on a run of nothing.
 */
class VerifyDeviceTestResultsTaskTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `a module with no device tests and no results passes`() {
    task(results = folder.newFolder("results"), expectResults = false).verify()
  }

  @Test
  fun `a module with device tests and no results fails, because the tests never ran`() {
    val failure =
      assertThrows(GradleException::class.java) {
        task(results = folder.newFolder("results"), expectResults = true).verify()
      }

    assertTrue(failure.message, failure.message.orEmpty().contains("never ran"))
  }

  @Test
  fun `a results folder that was never written counts as no results`() {
    val missing = File(folder.root, "not-there")

    assertThrows(GradleException::class.java) { task(results = missing, expectResults = true).verify() }
  }

  @Test
  fun `a clean report passes`() {
    val results = folder.newFolder("results")
    report(results, "TEST-pixel.xml", """<testcase classname="A" name="one"/>""")

    task(results, expectResults = true).verify()
  }

  @Test
  fun `one failing test in any report fails the run`() {
    val results = folder.newFolder("results")
    report(results, "TEST-a.xml", """<testcase classname="A" name="one"/>""")
    report(results, "TEST-b.xml", """<testcase classname="B" name="two"><failure>AssertionError</failure></testcase>""")

    val failure = assertThrows(GradleException::class.java) { task(results, expectResults = true).verify() }

    assertTrue(failure.message, failure.message.orEmpty().startsWith("1 failing and 0 erroring"))
  }

  @Test
  fun `an erroring test fails the run as surely as a failing one`() {
    val results = folder.newFolder("results")
    report(results, "TEST-a.xml", """<testcase classname="A" name="one"><error>NPE</error></testcase>""")

    assertThrows(GradleException::class.java) { task(results, expectResults = true).verify() }
  }

  @Test
  fun `a test that declined to run does not fail the run`() {
    // The case the task reads the test cases for: the runner files an
    // assumption as a failure, and `HarnessTest` declines every ordinary run.
    val results = folder.newFolder("results")
    report(
      results,
      "TEST-a.xml",
      """<testcase classname="A" name="one"><failure>AssumptionViolatedException: not asked</failure></testcase>""",
    )

    task(results, expectResults = true).verify()
  }

  @Test
  fun `a report of no tests at all fails a module that has some`() {
    val results = folder.newFolder("results")
    report(results, "TEST-a.xml", "")

    val failure = assertThrows(GradleException::class.java) { task(results, expectResults = true).verify() }

    assertTrue(failure.message, failure.message.orEmpty().contains(":feature:roll"))
  }

  @Test
  fun `only JUnit reports are read`() {
    // Logcat and the device's own files sit in the same folder; a broken one of
    // those is not a broken test, and a folder holding only them holds no
    // results.
    val results = folder.newFolder("results")
    File(results, "logcat-a.xml").writeText("<failure>not a report</failure>")
    File(results, "TEST-notes.txt").writeText("<failure>not a report</failure>")
    File(results, "TEST-folder.xml").mkdirs()

    assertThrows(GradleException::class.java) { task(results, expectResults = true).verify() }
    task(results, expectResults = false).verify()
  }

  private fun task(
    results: File,
    expectResults: Boolean,
  ): VerifyDeviceTestResultsTask {
    val project = ProjectBuilder.builder().withProjectDir(folder.newFolder()).build()
    return project.tasks.register("verifyDeviceTestResults", VerifyDeviceTestResultsTask::class.java) {
      resultsDirectory.set(results)
      this.expectResults.set(expectResults)
      modulePath.set(":feature:roll")
    }.get()
  }

  private fun report(
    directory: File,
    name: String,
    cases: String,
  ) {
    File(directory, name).writeText("""<testsuite name="Pixel 10a - 17">$cases</testsuite>""")
  }
}
