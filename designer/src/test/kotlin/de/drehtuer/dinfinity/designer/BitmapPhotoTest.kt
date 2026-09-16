package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * Decoding a photograph, on the device Robolectric provides
 * (`docs/tables.md`, "Your own photo").
 *
 * What this can say is the part [PhotoScalingTest] cannot: that the picture is
 * really opened **twice** — bounds first, pixels second — that a source which
 * fails on either pass costs nothing, and that the two passes join up into
 * bytes. What it cannot say is anything about the pixels, because Robolectric's
 * encoder does not make a real WebP; that is the phone's to answer
 * (`docs/TODO.md`, 4.5).
 */
@RunWith(RobolectricTestRunner::class)
class BitmapPhotoTest {
  private val scaler = BitmapPhoto()

  @Test
  fun `a photograph larger than the limit comes back as bytes`() {
    val photo = png(width = 4200, height = 3150)

    val scaled = scaler.scaled { ByteArrayInputStream(photo) }

    assertNotNull("a photograph did not come back as a texture", scaled)
  }

  @Test
  fun `the picture is read for its header before it is read for its pixels`() {
    // This is the whole of the defence: a file claiming to be thirty thousand
    // pixels square is found out for the cost of a header, where decoding it
    // first would be three and a half gigabytes.
    val opened = mutableListOf<InputStream>()
    val photo = png(width = 4200, height = 3150)

    scaler.scaled {
      ByteArrayInputStream(photo).also(opened::add)
    }

    assertEquals("the photo was not opened exactly twice", 2, opened.size)
  }

  @Test
  fun `a source that will not open costs nothing`() {
    assertNull(scaler.scaled { null })
  }

  @Test
  fun `a source that throws costs nothing`() {
    assertNull(scaler.scaled { throw IOException("gone") })
  }

  @Test
  fun `a source that goes away between the two passes costs nothing`() {
    // The header said something sane and the file was then moved or revoked,
    // which a content URI can do at any moment.
    val photo = png(width = 4200, height = 3150)
    var opens = 0

    val scaled = scaler.scaled { if (opens++ == 0) ByteArrayInputStream(photo) else null }

    assertNull(scaled)
  }

  @Test
  fun `the scaler that scales nothing is what a test without pixels uses`() {
    assertNull(PhotoScaler.NONE.scaled { ByteArrayInputStream(ByteArray(0)) })
  }

  /** A real PNG, written by the JVM's own encoder so the decoder has something true to read. */
  private fun png(
    width: Int,
    height: Int,
  ): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    val bytes = ByteArrayOutputStream()
    ImageIO.write(image, "png", bytes)
    return bytes.toByteArray()
  }
}
