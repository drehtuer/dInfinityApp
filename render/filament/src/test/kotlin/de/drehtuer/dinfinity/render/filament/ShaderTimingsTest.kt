package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * How long this phone takes to compile each material, and the file that
 * keeps it across an update — which must never be the reason anything fails.
 */
class ShaderTimingsTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `a phone that has measured nothing uses the Pixel 10a's figures`() {
    val timings = ShaderTimings()

    assertEquals(2_400L, timings.estimateOf(DiceMaterial.Variant.OPAQUE))
    assertEquals(2_000L, timings.estimateOf(DiceMaterial.Variant.RESIN))
    DiceMaterial.Variant.entries.forEach { variant ->
      assertEquals(ShaderTimings.DEFAULTS.getValue(variant), timings.estimateOf(variant))
    }
  }

  @Test
  fun `a measurement replaces the figure for its own material only`() {
    val timings = ShaderTimings().with(DiceMaterial.Variant.GLASS, 3_300L)

    assertEquals(3_300L, timings.estimateOf(DiceMaterial.Variant.GLASS))
    assertEquals(2_400L, timings.estimateOf(DiceMaterial.Variant.OPAQUE))
  }

  @Test
  fun `a figure no compiler could have taken is not learnt`() {
    val timings = ShaderTimings()

    assertEquals(timings, timings.with(DiceMaterial.Variant.OPAQUE, 3L))
    assertEquals(timings, timings.with(DiceMaterial.Variant.OPAQUE, 10 * 60 * 1000L))
  }

  @Test
  fun `what is written is what is read`() {
    val timings =
      ShaderTimings()
        .with(DiceMaterial.Variant.TABLE, 1_800L)
        .with(DiceMaterial.Variant.OPAQUE, 2_900L)

    assertEquals("opaque=2900\ntable=1800\n", timings.encode())
    assertEquals(timings, ShaderTimings.decode(timings.encode()))
  }

  @Test
  fun `a garbled file costs only its garbled lines`() {
    val text =
      listOf(
        "opaque=2100",
        "resin",
        "glass=fast",
        "table=-5",
        "marble=900",
        " table = 1700 ",
        "",
      ).joinToString("\n")

    val read = ShaderTimings.decode(text)

    assertEquals(2_100L, read.estimateOf(DiceMaterial.Variant.OPAQUE))
    assertEquals(2_000L, read.estimateOf(DiceMaterial.Variant.RESIN))
    assertEquals(2_000L, read.estimateOf(DiceMaterial.Variant.GLASS))
    assertEquals(1_700L, read.estimateOf(DiceMaterial.Variant.TABLE))
  }

  @Test
  fun `the store keeps the figures across a new store on the same file`() {
    val file = File(folder.root, "deep/er/timings.txt")
    val timings = ShaderTimings().with(DiceMaterial.Variant.RESIN, 1_234L)

    ShaderTimingStore(file).save(timings)

    assertEquals(timings, ShaderTimingStore(file).load())
    assertFalse("the temporary file was left behind", File(file.parentFile, "timings.txt.partial").exists())
  }

  @Test
  fun `a missing file reads as the defaults`() {
    assertEquals(ShaderTimings(), ShaderTimingStore(File(folder.root, "nothing.txt")).load())
  }

  @Test
  fun `a directory where the file should be reads as the defaults and is not overwritten`() {
    val file = File(folder.root, "timings.txt").apply { mkdirs() }
    val store = ShaderTimingStore(file)

    store.save(ShaderTimings().with(DiceMaterial.Variant.OPAQUE, 2_000L))

    assertEquals(ShaderTimings(), store.load())
    assertTrue(file.isDirectory)
    assertFalse(File(folder.root, "timings.txt.partial").exists())
  }

  @Test
  fun `a file that cannot be written is simply not written`() {
    // Its parent is a file, so nothing can be made under it.
    val blocker = folder.newFile("blocker")
    val store = ShaderTimingStore(File(blocker, "timings.txt"))

    store.save(ShaderTimings().with(DiceMaterial.Variant.OPAQUE, 2_000L))

    assertEquals(ShaderTimings(), store.load())
  }

  @Test
  fun `no file keeps nothing`() {
    ShaderTimingStore.NONE.save(ShaderTimings().with(DiceMaterial.Variant.OPAQUE, 2_000L))

    assertEquals(ShaderTimings(), ShaderTimingStore.NONE.load())
  }
}
