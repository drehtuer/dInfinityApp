package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How large a photograph is allowed to end up (`docs/tables.md`, "Your own
 * photo").
 *
 * The whole of the security-relevant decision is here, and none of it needs a
 * device: a target derived from the texture limit, a ladder derived from the
 * byte limit, and the power of two a decoder subsamples by to reach the first
 * without ever holding the picture at full size.
 */
class PhotoScalingTest {
  @Test
  fun `the target comes from the texture limit rather than a number of its own`() {
    assertEquals(DiceSetLimits.MAX_TEXTURE_PIXELS, PhotoScaling.LONGEST_SIDE)
    assertEquals(DiceSetLimits.MAX_TEXTURE_BYTES, PhotoScaling.MAX_BYTES)
  }

  @Test
  fun `a camera photo is cut to the long side with its shape kept`() {
    // 4080 x 3072 is an ordinary phone camera, and twelve megapixels is
    // forty-eight megabytes decoded.
    val target = PhotoScaling.target(PhotoSize(4080, 3072))

    assertEquals(2048, target.width)
    assertEquals(1542, target.height)
    assertEquals(
      "the photo was reshaped rather than scaled",
      4080.0 / 3072.0,
      target.width.toDouble() / target.height,
      0.01,
    )
  }

  @Test
  fun `a tall photo is cut by its height, because the limit is about the longest side`() {
    val target = PhotoScaling.target(PhotoSize(3072, 4080))

    assertEquals(2048, target.height)
    assertEquals(1542, target.width)
  }

  @Test
  fun `a picture already inside the limit is left alone`() {
    // Upscaling makes a larger file out of no more detail.
    val small = PhotoSize(800, 600)

    assertEquals(small, PhotoScaling.target(small))
  }

  @Test
  fun `a preposterous claim is arithmetic rather than an allocation`() {
    // Thirty thousand square is three and a half gigabytes decoded. Nothing
    // here decodes it; it comes out as a 2048-pixel target like anything else.
    val target = PhotoScaling.target(PhotoSize(30_000, 30_000))

    assertEquals(PhotoSize(2048, 2048), target)
  }

  @Test
  fun `a side never rounds away to nothing`() {
    // A panorama 20000 x 3 would scale its short side to 0.3 of a pixel.
    val target = PhotoScaling.target(PhotoSize(20_000, 3))

    assertEquals(2048, target.width)
    assertEquals(1, target.height)
  }

  @Test
  fun `a size no decoder could have produced is not a size`() {
    assertFalse(PhotoSize(0, 0).real)
    assertFalse(PhotoSize(-1, -1).real)
    assertTrue(PhotoSize(1, 1).real)
    assertEquals(PhotoSize(-1, -1), PhotoScaling.target(PhotoSize(-1, -1)))
    assertTrue(PhotoScaling.steps(PhotoSize(-1, -1)).isEmpty())
  }

  @Test
  fun `the ladder halves, largest first, and stops at the smallest worth having`() {
    val steps = PhotoScaling.steps(PhotoSize(4080, 3072))

    assertEquals(PhotoSize(2048, 1542), steps.first())
    assertEquals(listOf(2048, 1024, 512, 256), steps.map { it.width })
    assertTrue("the ladder went below the floor", steps.all { it.longestSide >= PhotoScaling.SMALLEST_SIDE })
  }

  @Test
  fun `a picture below the floor is not refused for being below it`() {
    // The floor is where halving stops, not a size a photo has to reach: a
    // 64-pixel tile somebody chose is still a table.
    val steps = PhotoScaling.steps(PhotoSize(64, 64))

    assertEquals(listOf(PhotoSize(64, 64)), steps)
  }

  @Test
  fun `the subsample is the largest power of two that still leaves enough pixels`() {
    val source = PhotoSize(4080, 3072)

    // 4080 / 2 = 2040, which is already under 2048, so the full-size target
    // cannot be subsampled at all.
    assertEquals(1, PhotoScaling.sampleSize(source, PhotoSize(2048, 1542)))
    assertEquals(2, PhotoScaling.sampleSize(source, PhotoSize(1024, 771)))
    assertEquals(4, PhotoScaling.sampleSize(source, PhotoSize(512, 385)))
    assertEquals(8, PhotoScaling.sampleSize(source, PhotoSize(256, 192)))
  }

  @Test
  fun `a subsample is never zero, whatever it is asked about`() {
    assertEquals(1, PhotoScaling.sampleSize(PhotoSize(0, 0), PhotoSize(256, 256)))
    assertEquals(1, PhotoScaling.sampleSize(PhotoSize(256, 256), PhotoSize(0, 0)))
    assertEquals(1, PhotoScaling.sampleSize(PhotoSize(100, 100), PhotoSize(2048, 2048)))
  }

  @Test
  fun `what fits is what a texture may weigh, and nothing weighs nothing`() {
    assertTrue(PhotoScaling.fits(1))
    assertTrue(PhotoScaling.fits(PhotoScaling.MAX_BYTES.toInt()))
    assertFalse("a texture one byte over the cap was allowed", PhotoScaling.fits(PhotoScaling.MAX_BYTES.toInt() + 1))
    assertFalse("an empty encode counted as a photo", PhotoScaling.fits(0))
  }
}
