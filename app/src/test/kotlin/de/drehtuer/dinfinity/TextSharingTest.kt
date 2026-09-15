package de.drehtuer.dinfinity

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Handing the anomaly log to whatever shares text
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * The one export in the app that carries a seed, and the reason it is its own
 * object rather than a third method on [NumbersSharing]: those files are built
 * from types that have no seed on them, and a `share(anything)` that could be
 * handed either would be the place the two got mixed up
 * (`docs/architecture.md`, decision 13).
 */
@RunWith(RobolectricTestRunner::class)
class TextSharingTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `the text goes in the intent rather than into a file on disk`() {
    // A bug report is pasted, not attached, and a copy on disk of a log that
    // lives in memory would outlive the run it describes.
    val chooser = TextSharing.share(context, text = "seed=7 forced=1", title = "anomalies.txt")

    val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    assertEquals(Intent.ACTION_SEND, send?.action)
    assertEquals("seed=7 forced=1", send?.getStringExtra(Intent.EXTRA_TEXT))
    assertEquals(TextSharing.MEDIA_TYPE, send?.type)
  }

  @Test
  fun `it is a chooser, so nothing is sent anywhere without being chosen`() {
    val chooser = TextSharing.share(context, text = "nothing", title = "anomalies.txt")

    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }

  @Test
  fun `nothing is attached, so no file permission is granted to anybody`() {
    val chooser = TextSharing.share(context, text = "seed=7", title = "anomalies.txt")

    val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    assertEquals(null, send?.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
  }
}
