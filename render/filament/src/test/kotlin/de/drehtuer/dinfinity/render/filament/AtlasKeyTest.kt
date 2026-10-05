package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.AtlasImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AtlasKeyTest {
  @Test
  fun `a key names the package and the path inside it`() {
    assertEquals("mine::textures/d20.png", AtlasKey.of("mine", "textures/d20.png"))
    assertEquals("mine" to "textures/d20.png", AtlasKey.split(AtlasKey.of("mine", "textures/d20.png")))
  }

  @Test
  fun `two sets shipping the same path are two different pictures`() {
    assertEquals("brass" to "textures/d20.png", AtlasKey.split(AtlasKey.of("brass", "textures/d20.png")))
    assertEquals("mine" to "textures/d20.png", AtlasKey.split(AtlasKey.of("mine", "textures/d20.png")))
  }

  @Test
  fun `a path with no package names nothing`() {
    // What a table look's floor and wall textures are today: a path with no
    // package to resolve it against (`docs/TODO.md`, "Open questions").
    assertNull(AtlasKey.split("tables/felt.png"))
    assertNull(AtlasKey.split("felt.png"))
    assertNull(AtlasKey.split(""))
  }

  @Test
  fun `a key with nothing on one side of the separator names nothing`() {
    assertNull(AtlasKey.split("::textures/d20.png"))
    assertNull(AtlasKey.split("mine::"))
  }

  @Test
  fun `a path that contains the separator keeps all of itself`() {
    // A set id cannot contain a colon, so the first one is the one that splits.
    assertEquals("mine" to "odd::name.png", AtlasKey.split("mine::odd::name.png"))
  }

  @Test
  fun `an atlas is decoded and uploaded once, however often it is asked for`() {
    var decodes = 0
    val cache =
      cache(artwork = {
        decodes++
        image()
      })

    val first = cache.of("mine::a.png")
    val again = cache.of("mine::a.png")

    assertSame(first, again)
    assertEquals(1, decodes)
    assertEquals(1, cache.uploaded)
  }

  @Test
  fun `a key with no picture behind it is remembered as having none`() {
    var asks = 0
    val cache =
      cache(artwork = {
        asks++
        null
      })

    assertNull(cache.of("mine::missing.png"))
    assertNull(cache.of("mine::missing.png"))

    assertEquals("the disk was asked twice about the same absent file", 1, asks)
    assertEquals(0, cache.uploaded)
    assertEquals(1, cache.asked)
  }

  @Test
  fun `different keys are different uploads`() {
    val cache = cache(artwork = { image() })

    cache.of("mine::a.png")
    cache.of("brass::a.png")

    assertEquals(2, cache.uploaded)
  }

  @Test
  fun `closing gives every handle back and only the ones that were made`() {
    val destroyed = mutableListOf<String>()
    val cache =
      AtlasCache<String>(
        artwork = { key -> if (key.endsWith("missing.png")) null else image() },
        upload = { "handle" + it.width },
        destroy = { destroyed += it },
      )
    cache.of("mine::a.png")
    cache.of("mine::missing.png")

    cache.close()

    assertEquals(listOf("handle2"), destroyed)
    assertEquals(0, cache.uploaded)
    assertEquals("a closed cache remembers nothing", 0, cache.asked)
  }

  @Test
  fun `a file that changed under its key is decoded again and the old handle given back`() {
    var version = "a"
    var decoded = 0
    val destroyed = mutableListOf<String>()
    val cache =
      AtlasCache<String>(
        artwork = {
          decoded++
          image()
        },
        upload = { "handle-$version" },
        destroy = { destroyed += it },
        stamp = { version },
      )

    assertEquals("handle-a", cache.of("mine::textures/d6.png"))
    assertEquals("handle-a", cache.of("mine::textures/d6.png"))
    version = "b"
    assertEquals("handle-b", cache.of("mine::textures/d6.png"))

    assertEquals(2, decoded)
    assertEquals(listOf("handle-a"), destroyed)
    assertEquals(1, cache.uploaded)
  }

  @Test
  fun `a miss is asked again once the file appears`() {
    var there = false
    val cache =
      AtlasCache<Any>(
        artwork = { if (there) image() else null },
        upload = { Any() },
        destroy = { },
        stamp = { if (there) "1@1" else null },
      )

    assertNull(cache.of("mine::textures/d6.png"))
    there = true

    assertNotNull(cache.of("mine::textures/d6.png"))
  }

  @Test
  fun `an atlas's coverage is read for every count of faces a catalogue die has`() {
    // A 30 x 20 picture with one opaque pixel at (12, 15): face 4 of a d6
    // (a 3 x 2 grid of 10 px cells), and on a d20's 5 x 4 grid of 6 x 5 px
    // cells, column 2 and row 3 — face 17.
    val pixels = ByteArray(30 * 20 * AtlasImage.CHANNELS)
    pixels[(15 * 30 + 12) * AtlasImage.CHANNELS + 3] = 0xFF.toByte()
    val coverage = AtlasCoverage.of(AtlasImage(30, 20, pixels))

    assertEquals(setOf(4), coverage.drawnOn(6))
    assertEquals(setOf(17), coverage.drawnOn(20))
    assertEquals("no die has seven faces", emptySet<Int>(), coverage.drawnOn(7))
  }

  private fun cache(artwork: (String) -> AtlasImage?): AtlasCache<Any> =
    AtlasCache(artwork = artwork, upload = { Any() }, destroy = { })

  private fun image(): AtlasImage = AtlasImage(2, 2, ByteArray(2 * 2 * AtlasImage.CHANNELS))
}
