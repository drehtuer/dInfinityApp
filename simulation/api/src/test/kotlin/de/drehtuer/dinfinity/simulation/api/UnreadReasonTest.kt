package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Why a die was left unread, from what touched it (`UnreadReason`). */
class UnreadReasonTest {
  @Test
  fun `a die on another die is that, whatever else touches it and whether it is still`() {
    listOf(true, false).forEach { atRest ->
      assertEquals(
        UnreadReason.OnAnotherDie,
        reason(onAnotherDie = true, atRest = atRest, touchingDie = true, touchingWall = true),
      )
    }
  }

  @Test
  fun `a die still moving shows nothing yet, whatever it leans on`() {
    assertEquals(UnreadReason.StillMoving, reason(atRest = false, touchingDie = true, touchingWall = true))
  }

  @Test
  fun `a die cocked against a die and a wall at once is the pile-up's, not the wall's`() {
    assertEquals(UnreadReason.CockedOnADie, reason(touchingDie = true, touchingWall = true))
    assertEquals(UnreadReason.CockedOnADie, reason(touchingDie = true))
  }

  @Test
  fun `a die cocked against a wall alone is the wall's`() {
    assertEquals(UnreadReason.CockedOnAWall, reason(touchingWall = true))
  }

  @Test
  fun `a die cocked with nothing to lean on is on the floor alone`() {
    assertEquals(UnreadReason.CockedOnTheFloor, reason())
  }

  @Test
  fun `every reason is found again by its id, and nothing else is`() {
    UnreadReason.entries.forEach { assertEquals(it, UnreadReason.ofId(it.id)) }
    assertEquals(UnreadReason.entries.size, UnreadReason.entries.distinctBy(UnreadReason::id).size)
    assertNull(UnreadReason.ofId("cocked"))
  }

  private fun reason(
    onAnotherDie: Boolean = false,
    atRest: Boolean = true,
    touchingDie: Boolean = false,
    touchingWall: Boolean = false,
  ): UnreadReason = UnreadReason.of(onAnotherDie, atRest, touchingDie, touchingWall)
}
