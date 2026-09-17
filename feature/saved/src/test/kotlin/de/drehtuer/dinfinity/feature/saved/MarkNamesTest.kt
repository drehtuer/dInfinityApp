package de.drehtuer.dinfinity.feature.saved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every mark the app offers has a name a screen reader can say.
 *
 * The pickers draw the emoji itself, so a mark without a name is a button that
 * announces nothing — and nothing about adding one to a list would tell you
 * that. This is the check that would have caught it, and it is what keeps the
 * two lists and the one map from drifting apart.
 */
class MarkNamesTest {
  @Test
  fun `every mark a roll can wear is named`() {
    ICONS.forEach { mark -> assertNotNull("no name for $mark", markName(mark)) }
  }

  @Test
  fun `every mark a group can wear is named`() {
    GROUP_ICONS.forEach { mark -> assertNotNull("no name for $mark", markName(mark)) }
  }

  @Test
  fun `a mark that is in both lists has one name, not two`() {
    // `⚔️` and `🎲` are offered in both pickers. A mark is a picture, and the
    // picture is the same one whichever list it is in — naming it twice would
    // be two places for the same word to drift.
    val shared = ICONS.toSet() intersect GROUP_ICONS.toSet()
    assertEquals("the lists no longer overlap; this test is about what happens when they do", 2, shared.size)
    shared.forEach { mark -> assertNotNull(markName(mark)) }
  }

  @Test
  fun `a mark nobody has named falls back to no name, not to the emoji`() {
    // A screen reader given the raw string says "crossed swords emoji
    // variation selector" or nothing at all, depending on the platform. None
    // is the honest answer, and it is the one that shows up as a bug.
    assertNull(markName("🦆"))
  }
}
