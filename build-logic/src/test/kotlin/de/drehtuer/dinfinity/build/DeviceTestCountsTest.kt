package de.drehtuer.dinfinity.build

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a device run's JUnit XML is read to mean ([DeviceTestCounts]).
 *
 * The case that matters is the third one, and it is not hypothetical: it is
 * the report the Pixel 10a wrote for `simulation/jolt`, where `HarnessTest`
 * declined to run because nothing passed it `harness.rolls`.
 */
class DeviceTestCountsTest {
  @Test
  fun `a clean run is every test and nothing else`() {
    val counts = DeviceTestCounts.of(report("""<testcase classname="A" name="one"/><testcase classname="A" name="two"/>"""))

    assertEquals(DeviceTestCounts(tests = 2, failures = 0, errors = 0, skipped = 0), counts)
  }

  @Test
  fun `a failing test is a failing test`() {
    val counts =
      DeviceTestCounts.of(
        report("""<testcase classname="A" name="one"><failure>java.lang.AssertionError: nope</failure></testcase>"""),
      )

    assertEquals(DeviceTestCounts(tests = 1, failures = 1, errors = 0, skipped = 0), counts)
  }

  @Test
  fun `a test that declined to run is skipped, however the runner filed it`() {
    // The whole reason this file exists. The runner writes an assumption as a
    // <failure> and counts it in the root's `failures`, so the plain
    // `./gradlew connectedDebugAndroidTest` failed on a test that said the run
    // was not for it (`docs/build-setup.md`, the harness).
    val counts =
      DeviceTestCounts.of(
        report(
          """
          <testcase classname="de.drehtuer.dinfinity.simulation.jolt.HarnessTest" name="aRunOfRollsMeetsTheTargets">
            <failure>org.junit.AssumptionViolatedException: no run was asked for; pass -e harness.rolls &lt;n&gt;</failure>
          </testcase>
          """,
        ),
      )

    assertEquals(DeviceTestCounts(tests = 1, failures = 0, errors = 0, skipped = 1), counts)
  }

  @Test
  fun `the root's own counters are not believed`() {
    // They say one failure and no skips for exactly the report above. Reading
    // them is what this replaced, so a test that stops reading the test cases
    // has to fail.
    val counts =
      DeviceTestCounts.of(
        """
        <testsuite tests="1" failures="1" errors="0" skipped="0">
          <testcase classname="A" name="one">
            <failure>org.junit.AssumptionViolatedException: not for this run</failure>
          </testcase>
        </testsuite>
        """,
      )

    assertEquals(0, counts.failures)
    assertEquals(1, counts.skipped)
  }

  @Test
  fun `a test that both declined and broke is a broken test`() {
    // Every way it failed has to be an opt-out for it to count as one. There
    // is no reading of a real failure that may hide behind an assumption.
    val counts =
      DeviceTestCounts.of(
        report(
          """
          <testcase classname="A" name="one">
            <failure>org.junit.AssumptionViolatedException: not for this run</failure>
            <failure>java.lang.AssertionError: and also this</failure>
          </testcase>
          """,
        ),
      )

    assertEquals(DeviceTestCounts(tests = 1, failures = 1, errors = 0, skipped = 0), counts)
  }

  @Test
  fun `an erroring test is told from a failing one`() {
    val counts =
      DeviceTestCounts.of(report("""<testcase classname="A" name="one"><error>boom</error></testcase>"""))

    assertEquals(DeviceTestCounts(tests = 1, failures = 0, errors = 1, skipped = 0), counts)
  }

  @Test
  fun `a test the runner itself marked skipped is skipped`() {
    val counts =
      DeviceTestCounts.of(report("""<testcase classname="A" name="one"><skipped/></testcase>"""))

    assertEquals(DeviceTestCounts(tests = 1, failures = 0, errors = 0, skipped = 1), counts)
  }

  @Test
  fun `a run of no tests at all counts nothing`() {
    assertEquals(DeviceTestCounts.NONE, DeviceTestCounts.of(report("")))
  }

  @Test
  fun `reports add up across modules`() {
    val one = DeviceTestCounts(tests = 22, failures = 0, errors = 0, skipped = 0)
    val other = DeviceTestCounts(tests = 39, failures = 0, errors = 0, skipped = 1)

    assertEquals(DeviceTestCounts(tests = 61, failures = 0, errors = 0, skipped = 1), one + other)
  }

  private fun report(cases: String): String = """<testsuite name="Pixel 10a - 17">$cases</testsuite>"""
}
