package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which die the designer opens on, from the menu and from "Doodle this die"
 * (`docs/face-designer.md`, "Quick mode").
 *
 * The cases worth writing down are the ones a wiring function would have
 * swallowed: a name nothing answers to, a set with no dice in it, and the
 * coin that must not be what a drawing app opens on.
 */
class OpeningDieTest {
  private val d2 = Die.standard("d2", DieShape.Coin)
  private val d6 = Die.standard("d6", DieShape.Cube)
  private val d20 = Die.standard("d20", DieShape.Icosahedron)
  private val theirs = Die.standard("d18", DieShape.EnneagonalTrapezohedron)

  private val installed = listOf(d2, d6, d20, theirs)

  @Test
  fun `opens on the die quick mode named`() {
    assertEquals(d20, OpeningDie.of(installed, fromDefaultSet = listOf(d2, d6), wanted = "d20"))
  }

  @Test
  fun `reaches a die the default set does not have`() {
    // The point of the shortcut: somebody's own d18 is one long press away
    // rather than a hunt through the chooser.
    assertEquals(theirs, OpeningDie.of(installed, fromDefaultSet = listOf(d2, d6), wanted = "d18"))
  }

  @Test
  fun `opens on the default set's d6 when nothing was named`() {
    assertEquals(d6, OpeningDie.of(installed, fromDefaultSet = listOf(d2, d6, d20)))
  }

  @Test
  fun `falls back to the same answer for a die that is no longer installed`() {
    // A result stays on the roll screen after the package that threw it has
    // been removed, so a long press can name an id the catalogue has lost.
    assertEquals(d6, OpeningDie.of(installed, fromDefaultSet = listOf(d2, d6), wanted = "brass-d12"))
  }

  @Test
  fun `opens on a set's first die when it has no d6`() {
    assertEquals(d20, OpeningDie.of(installed, fromDefaultSet = listOf(d20, theirs)))
  }

  @Test
  fun `uses everything installed when the default set has no dice`() {
    assertEquals(d6, OpeningDie.of(installed, fromDefaultSet = emptyList()))
  }

  @Test
  fun `has no die to open on when nothing is installed`() {
    assertNull(OpeningDie.of(choosable = emptyList()))
  }
}
