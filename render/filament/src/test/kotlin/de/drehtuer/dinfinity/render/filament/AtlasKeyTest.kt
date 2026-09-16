package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.AtlasImage
import org.junit.Assert.assertEquals
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

  private fun cache(artwork: (String) -> AtlasImage?): AtlasCache<Any> =
    AtlasCache(artwork = artwork, upload = { Any() }, destroy = { })

  private fun image(): AtlasImage = AtlasImage(2, 2, ByteArray(2 * 2 * AtlasImage.CHANNELS))
}
