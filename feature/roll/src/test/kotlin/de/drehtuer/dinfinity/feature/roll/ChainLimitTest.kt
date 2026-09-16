package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a group has to say about the dice that are *not* on the sheet
 * (`docs/dice-notation.md`, "Limits").
 *
 * A plain test with no Compose in it, because this is arithmetic over a
 * group's notes rather than a drawing — the lever the coverage section of
 * `docs/TODO.md` records.
 */
class ChainLimitTest {
  @Test
  fun `a group that hit nothing has nothing to say`() {
    assertEquals(emptyList<ChainLimit>(), ChainLimit.of(group(die(0, 3))))
  }

  @Test
  fun `a chain that ran out of depth says so`() {
    val stopped = group(die(0, 6, DieNote.ExplosionLimitReached))

    assertEquals(listOf(ChainLimit.ExplosionDepth), ChainLimit.of(stopped))
  }

  @Test
  fun `a chain that ran out of table says so`() {
    assertEquals(listOf(ChainLimit.TrayFull), ChainLimit.of(group(die(0, 6, DieNote.TrayFull))))
  }

  @Test
  fun `a reroll the tray had no room for is the same fact about the tray`() {
    // `4d6r1` reaches TrayFull without any explosion in the formula at all,
    // and the sheet says the same thing about it: no die was added.
    val refused = group(die(0, 1, DieNote.TrayFull))

    assertEquals(listOf(ChainLimit.TrayFull), ChainLimit.of(refused))
  }

  @Test
  fun `a group that hit both limits says both, in a fixed order`() {
    // Two chains in one group can stop for different reasons, and which of
    // them happened to be first is not something the sheet should reorder
    // itself around.
    val depthFirst =
      group(
        die(0, 6, DieNote.ExplosionLimitReached),
        die(1, 6, DieNote.TrayFull),
      )
    val trayFirst =
      group(
        die(0, 6, DieNote.TrayFull),
        die(1, 6, DieNote.ExplosionLimitReached),
      )

    assertEquals(listOf(ChainLimit.ExplosionDepth, ChainLimit.TrayFull), ChainLimit.of(depthFirst))
    assertEquals(ChainLimit.of(depthFirst), ChainLimit.of(trayFirst))
  }

  @Test
  fun `twelve dice that each ran out of table are one line, not twelve`() {
    val many = group(*Array(12) { die(it, 6, DieNote.TrayFull) })

    assertEquals(listOf(ChainLimit.TrayFull), ChainLimit.of(many))
  }

  @Test
  fun `the notes a die carries for other reasons are not limits`() {
    // Every one of these is already legible on the sheet: a dropped die is
    // struck through, a rerolled one stands beside its replacement, an
    // exploded one is another chip in the row.
    val ordinary =
      group(
        die(0, 1, DieNote.Dropped),
        die(1, 4, DieNote.Rerolled),
        die(2, 6, DieNote.FromExplosion),
        die(3, 2, DieNote.ClampedToMin),
        die(4, 30, DieNote.PercentileTens),
        die(5, 7, DieNote.PercentileUnits),
      )

    assertEquals(emptyList<ChainLimit>(), ChainLimit.of(ordinary))
  }

  @Test
  fun `a group with no dice at all has nothing to say`() {
    assertEquals(emptyList<ChainLimit>(), ChainLimit.of(group()))
  }

  @Test
  fun `each limit reads the note it is about`() {
    assertEquals(DieNote.ExplosionLimitReached, ChainLimit.ExplosionDepth.note)
    assertEquals(DieNote.TrayFull, ChainLimit.TrayFull.note)
  }

  private fun group(vararg dice: RolledDie): RolledGroup =
    RolledGroup(
      id = 0,
      notation = "8d6!",
      setId = "builtin",
      requestedSetId = "builtin",
      subtotal = dice.sumOf { it.value }.toLong(),
      dice = dice.toList(),
    )

  private fun die(
    index: Int,
    value: Int,
    vararg notes: DieNote,
  ): RolledDie =
    RolledDie(
      instanceIndex = index,
      dieId = "d6",
      value = value,
      notes = notes.toSet(),
    )
}
