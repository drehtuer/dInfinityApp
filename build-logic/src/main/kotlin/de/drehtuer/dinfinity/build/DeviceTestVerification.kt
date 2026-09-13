package de.drehtuer.dinfinity.build

import org.gradle.api.Project
import org.gradle.api.tasks.VerificationTask
import org.gradle.kotlin.dsl.register

/**
 * Wires [VerifyDeviceTestResultsTask] behind `connectedDebugAndroidTest` so the
 * verdict on an instrumented run comes from its XML report rather than from
 * AGP's own bookkeeping, which cannot pass on a wireless device. The reason is
 * on [VerifyDeviceTestResultsTask].
 *
 * Only the debug variant is wired: `testBuildType` is the default `debug`, so
 * it is the only variant that has instrumented tests.
 */
fun Project.configureDeviceTestVerification() {
  val resultsDirectory = layout.buildDirectory.dir("outputs/androidTest-results/connected/debug")
  val hasDeviceTests = file("src/androidTest").isDirectory
  // Read here rather than inside the task block, where `path` is the *task's*.
  val modulePath = path

  val verify =
    tasks.register<VerifyDeviceTestResultsTask>("verifyDeviceTestResults") {
      group = "verification"
      description = "Reads the instrumented test report and fails if anything in it failed."
      this.resultsDirectory.set(resultsDirectory)
      expectResults.set(hasDeviceTests)
      this.modulePath.set(modulePath)
    }

  // The task does not exist yet while the convention plugin is applied, so it
  // is matched lazily.
  tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    (this as VerificationTask).ignoreFailures = true
    finalizedBy(verify)
  }
}
