package de.drehtuer.dinfinity.simulation.api

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dice waiting on the table, before anybody throws them.
 *
 * What matters is that they are *on* the table and not *in* each other: this
 * is a picture of what is about to be thrown, and two dice in one place would
 * say the table holds fewer than it does.
 */
class RestingPlacesTest {
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `every die gets a place of its own`() {
    val places = RestingPlaces.of(geometry, List(TEN) { RADIUS_MM })

    assertEquals(TEN, places.size)
    assertEquals(TEN, places.distinct().size, "two dice were put in the same place")
  }

  @Test
  fun `no two dice are laid out inside each other`() {
    val places = RestingPlaces.of(geometry, List(TWENTY) { RADIUS_MM })

    places.indices.forEach { a ->
      (a + 1 until places.size).forEach { b ->
        val apart = places[a].flatDistanceTo(places[b])
        assertTrue(apart >= RADIUS_MM * 2, "dice $a and $b overlap, $apart mm apart")
      }
    }
  }

  @Test
  fun `a die sits on the floor rather than half through it`() {
    val places = RestingPlaces.of(geometry, listOf(RADIUS_MM))

    assertEquals(RADIUS_MM, places.single().z, 0.0)
  }

  @Test
  fun `every die is inside the walls`() {
    val places = RestingPlaces.of(geometry, List(TWENTY) { RADIUS_MM })

    places.forEach { place ->
      assertTrue(abs(place.x) + RADIUS_MM <= geometry.longSideMm / 2, "a die was laid out past the long wall")
      assertTrue(abs(place.y) + RADIUS_MM <= geometry.shortSideMm / 2, "a die was laid out past the short wall")
    }
  }

  @Test
  fun `the same dice are always laid out the same way`() {
    // A player who taps the same saved roll twice sees the same table.
    val once = RestingPlaces.of(geometry, List(TEN) { RADIUS_MM })
    val again = RestingPlaces.of(geometry, List(TEN) { RADIUS_MM })

    assertEquals(once, again)
  }

  @Test
  fun `more dice than there is floor for are laid out as far as they go`() {
    // Rather than throwing: a formula the table cannot hold is refused long
    // before this, and one die fewer on the picture is not worth a crash.
    val tooMany = RestingPlaces.of(geometry, List(FAR_TOO_MANY) { RADIUS_MM })

    assertTrue(tooMany.size < FAR_TOO_MANY, "the whole tray was filled with more dice than it holds")
    assertTrue(tooMany.isNotEmpty(), "nothing was laid out at all")
  }

  @Test
  fun `dice of different sizes each get room for their own size`() {
    val places = RestingPlaces.of(geometry, listOf(RADIUS_MM, BIG_RADIUS_MM))

    assertEquals(BIG_RADIUS_MM, places.last().z, 0.0)
    assertTrue(places.first().flatDistanceTo(places.last()) >= RADIUS_MM + BIG_RADIUS_MM)
  }

  private fun Vector3.flatDistanceTo(other: Vector3): Double {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
  }

  private companion object {
    const val RADIUS_MM = 8.0
    const val BIG_RADIUS_MM = 12.0
    const val TEN = 10
    const val TWENTY = 20
    const val FAR_TOO_MANY = 400
  }
}
