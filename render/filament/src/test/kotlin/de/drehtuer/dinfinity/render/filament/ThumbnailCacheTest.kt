package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which pictures are kept, which are asked for again, and which go when there
 * is no more room (`docs/tables.md`, "Thumbnails").
 *
 * All of it plain arithmetic over a map, so all of it is asked here rather
 * than by installing seventeen packages on a phone and scrolling.
 */
class ThumbnailCacheTest {
  private val cache = ThumbnailCache(capacity = 3)

  @Test
  fun `a cache with no room is not a cache`() {
    assertThrows(IllegalArgumentException::class.java) { ThumbnailCache(capacity = 0) }
  }

  @Test
  fun `a look nobody has asked about has not been asked about`() {
    assertFalse(cache.asked("builtin/oak"))
    assertNull(cache.of("builtin/oak"))
    assertEquals(0, cache.size)
  }

  @Test
  fun `a picture that was drawn is handed back`() {
    val picture = frame()

    cache.put("builtin/oak", picture)

    assertSame(picture, cache.of("builtin/oak"))
    assertTrue(cache.asked("builtin/oak"))
  }

  @Test
  fun `a look that could not be drawn is remembered as having been tried`() {
    // Otherwise a device that cannot draw one is asked again for every row, on
    // every visit, for a picture that is never going to arrive.
    cache.put("builtin/oak", null)

    assertTrue(cache.asked("builtin/oak"))
    assertNull(cache.of("builtin/oak"))
    assertEquals(1, cache.size)
  }

  @Test
  fun `drawing the same look again replaces the picture rather than keeping two`() {
    val second = frame()
    cache.put("builtin/oak", frame())

    cache.put("builtin/oak", second)

    assertEquals(1, cache.size)
    assertSame(second, cache.of("builtin/oak"))
  }

  @Test
  fun `the least recently asked for goes when there is no more room`() {
    listOf("a", "b", "c").forEach { cache.put(it, frame()) }

    cache.put("d", frame())

    assertEquals(listOf("b", "c", "d"), cache.keys)
    assertFalse(cache.asked("a"))
  }

  @Test
  fun `asking for one counts as using it, so it is not the next to go`() {
    listOf("a", "b", "c").forEach { cache.put(it, frame()) }

    cache.of("a")
    cache.put("d", frame())

    assertEquals(listOf("c", "a", "d"), cache.keys)
    assertTrue("the one just asked for was dropped", cache.asked("a"))
    assertFalse("the one nobody has touched was kept", cache.asked("b"))
  }

  @Test
  fun `asking for one that is not there does not disturb the order`() {
    listOf("a", "b").forEach { cache.put(it, frame()) }

    cache.of("nowhere")

    assertEquals(listOf("a", "b"), cache.keys)
  }

  @Test
  fun `a look drawn again is the most recent, wherever it was before`() {
    listOf("a", "b", "c").forEach { cache.put(it, frame()) }

    cache.put("a", frame())
    cache.put("d", frame())

    assertEquals(listOf("c", "a", "d"), cache.keys)
  }

  @Test
  fun `forgetting everything means the next ask draws again`() {
    cache.put("a", frame())

    cache.clear()

    assertFalse(cache.asked("a"))
    assertEquals(0, cache.size)
  }

  @Test
  fun `the default is room for every table anybody is likely to have`() {
    // The bundled package ships five and a player may keep six photographs.
    assertTrue(ThumbnailCache.DEFAULT_CAPACITY >= 11)
  }

  private fun frame(): Snapshot = Snapshot(width = 2, height = 2, pixels = ByteArray(2 * 2 * Snapshot.CHANNELS))
}
