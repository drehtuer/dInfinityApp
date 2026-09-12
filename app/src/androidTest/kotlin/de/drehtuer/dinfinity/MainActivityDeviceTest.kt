package de.drehtuer.dinfinity

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.navigation.Destination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The device tier's smoke test: the real [MainActivity] on real hardware.
 *
 * Robolectric already covers the same composition on the JVM
 * ([DInfinityAppTest]); this exists to prove the *path* — build, install, run,
 * report — works from inside the devcontainer over WiFi debugging, so the
 * physics and rendering tests of Step 5 have somewhere to land. It cannot run
 * on CI (`.claude/CLAUDE.md`).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityDeviceTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun launchesOnTheRollScreen() {
    compose.onNodeWithTag("screen:${Destination.Roll.route}").assertIsDisplayed()
    compose.onNodeWithText(Destination.Roll.title).assertIsDisplayed()
  }

  @Test
  fun survivesActivityRecreation() {
    compose.activityRule.scenario.recreate()
    compose.waitForIdle()
    compose.onNodeWithTag("screen:${Destination.Roll.route}").assertIsDisplayed()
  }

  /**
   * Guards the tier itself. A device suite that quietly fell back to a
   * simulated runtime would report green while proving nothing, which is the
   * one failure mode this tier exists to rule out.
   */
  @Test
  fun runsOnRealHardware() {
    assertFalse(
      "expected a device, got ${Build.FINGERPRINT}",
      Build.FINGERPRINT.contains("robolectric", ignoreCase = true),
    )
    assertTrue(
      "device runs API ${Build.VERSION.SDK_INT}, below the app's minSdk",
      Build.VERSION.SDK_INT >= MIN_SDK,
    )
  }

  private companion object {
    /** Mirrors `dinfinity.minSdk` in `gradle.properties`. */
    const val MIN_SDK = 36
  }
}
