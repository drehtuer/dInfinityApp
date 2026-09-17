package de.drehtuer.dinfinity.build

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What one JUnit XML report from a device run actually says.
 *
 * Read from the test cases rather than from the root element's own counters,
 * because on one point they disagree and the root is wrong: **a test that
 * opted out with `Assume.assumeTrue` is recorded as a failure.** The runner
 * writes it as a `<failure>` carrying an `AssumptionViolatedException`, counts
 * it in `failures`, and leaves `skipped` at nought. Nothing in the suite is
 * broken when that happens — the test said the run was not for it.
 *
 * It matters because of `HarnessTest`, which is in the ordinary device suite
 * and does nothing at all unless it is given `harness.rolls` or `harness.soak`
 * (`docs/build-setup.md`). So the plain `./gradlew connectedDebugAndroidTest`
 * — the whole tier, every module, which is what `.claude/CLAUDE.md` asks a
 * developer to run — could not pass, whatever the code did. It failed on a
 * test that declined to run.
 *
 * Here as plain Kotlin with a test beside it rather than inside the task, for
 * the reason `FollowsTheDesignSystem` is: a rule about what counts as a
 * failure is arithmetic somebody can hold still, and a Gradle task is the one
 * place it cannot be checked without a device, a phone and four minutes.
 */
data class DeviceTestCounts(
  val tests: Int,
  val failures: Int,
  val errors: Int,
  val skipped: Int,
) {
  operator fun plus(other: DeviceTestCounts): DeviceTestCounts =
    DeviceTestCounts(
      tests = tests + other.tests,
      failures = failures + other.failures,
      errors = errors + other.errors,
      skipped = skipped + other.skipped,
    )

  companion object {
    val NONE: DeviceTestCounts = DeviceTestCounts(tests = 0, failures = 0, errors = 0, skipped = 0)

    /** What marks a `<failure>` as a test that opted out rather than one that broke. */
    private const val OPTED_OUT = "AssumptionViolatedException"

    /** The counts in [xml], one JUnit report. */
    fun of(xml: String): DeviceTestCounts {
      val root =
        DocumentBuilderFactory
          .newInstance()
          .apply { isNamespaceAware = false }
          .newDocumentBuilder()
          .parse(InputSource(StringReader(xml)))
          .documentElement

      var tests = 0
      var failures = 0
      var errors = 0
      var skipped = 0
      root.eachTestCase { testCase ->
        tests++
        when {
          testCase.has("skipped") -> skipped++
          testCase.optedOut() -> skipped++
          testCase.has("failure") -> failures++
          testCase.has("error") -> errors++
        }
      }
      return DeviceTestCounts(tests = tests, failures = failures, errors = errors, skipped = skipped)
    }

    private fun Element.eachTestCase(each: (Element) -> Unit) {
      val cases = getElementsByTagName("testcase")
      for (index in 0 until cases.length) {
        val node = cases.item(index)
        if (node.nodeType == Node.ELEMENT_NODE) each(node as Element)
      }
    }

    private fun Element.has(tag: String): Boolean = getElementsByTagName(tag).length > 0

    /**
     * True when every way this test case failed was by declining to run.
     *
     * Every way, not any: a case that both opted out and broke is a case that
     * broke, and there is no honest reading of one that hides the other.
     */
    private fun Element.optedOut(): Boolean {
      val failures = getElementsByTagName("failure")
      if (failures.length == 0) return false
      for (index in 0 until failures.length) {
        if (OPTED_OUT !in (failures.item(index).textContent ?: "")) return false
      }
      return true
    }
  }
}
