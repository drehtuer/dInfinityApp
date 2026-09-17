package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Scoring a throw that is not finished, which is what an explosion makes every
 * throw (`docs/dice-notation.md`, "Evaluation", step 5).
 *
 * The dice an explosion or a reroll adds are thrown into the tray on screen,
 * one at a time, and each takes a second and a half of real time to land. So
 * the scoring cannot ask for a face and wait: it is run again from the
 * beginning each time a die lands, and says either what the roll came to or
 * which die has to be thrown next.
 *
 * What these hold is that the answer does not depend on how many times it was
 * asked. A die that has landed keeps the face it landed on however often the
 * scoring is re-run over it — which is the only way "the physics result is the
 * roll" survives being asked twice (`.claude/CLAUDE.md`).
 */
class RunningScoreTest {
  private val catalog = DiceCatalog.of(listOf(StandardDice.set()))

  @Test
  fun `a throw with nothing to add is scored where it stands`() {
    val scored = assertIs<Scoring.Scored>(score("3d6 + 4", values = listOf(2, 3, 5)))

    assertEquals(14L, scored.result.total)
  }

  @Test
  fun `a six on an exploding d6 asks for one more die before it is a total`() {
    val asked = assertIs<Scoring.OneMoreDie>(score("1d6!", values = listOf(6)))

    assertEquals("d6", asked.die.id)
    assertEquals(0, asked.ordinal, "the first die a roll adds is the first it asks for")
  }

  @Test
  fun `the roll is scored once the die it asked for has landed`() {
    val scored = assertIs<Scoring.Scored>(score("1d6!", values = listOf(6), added = listOf(1)))

    assertEquals(7L, scored.result.total, "a six and then a one")
    assertTrue(
      DieNote.FromExplosion in
        scored.result.dice
          .last()
          .notes,
    )
  }

  @Test
  fun `a chain asks for one die at a time, counting up`() {
    // `8d6!` might add none or a dozen, and which it is cannot be known until
    // the first ones land — so it is a conversation and not a list.
    assertEquals(1, assertIs<Scoring.OneMoreDie>(score("1d6!", listOf(6), added = listOf(6))).ordinal)
    assertEquals(2, assertIs<Scoring.OneMoreDie>(score("1d6!", listOf(6), added = listOf(6, 6))).ordinal)

    val scored = assertIs<Scoring.Scored>(score("1d6!", listOf(6), added = listOf(6, 6, 2)))
    assertEquals(20L, scored.result.total)
  }

  @Test
  fun `re-running the scoring over the same dice gives the same answer`() {
    // It is run once per die that lands, so it is run a dozen times for one
    // roll of `8d6!`. A scoring that drifted would be a total that depended on
    // how long the player watched.
    val once = assertIs<Scoring.Scored>(score("2d6!", listOf(6, 3), added = listOf(2)))
    val again = assertIs<Scoring.Scored>(score("2d6!", listOf(6, 3), added = listOf(2)))

    assertEquals(once.result, again.result)
  }

  @Test
  fun `each chain gets its own dice, in the order they were asked for`() {
    // `2d6!kh1` keeps the better of two chains, which is what a player means by
    // it — so the answers have to go to the dice that asked for them.
    val scored = assertIs<Scoring.Scored>(score("2d6!", listOf(6, 6), added = listOf(1, 5)))

    assertEquals(listOf(6, 1, 6, 5), scored.result.dice.map(RolledDie::value))
  }

  @Test
  fun `a chain stops when the tray has nowhere left to put a die`() {
    // The other end of a chain, and the honest one: an added die is dropped
    // into clear floor, and a tray with none left cannot take one. The
    // alternative is a die dropped onto a settled pile
    // (`docs/tables.md`, "Capacity rule").
    val scored = assertIs<Scoring.Scored>(score("1d6!", values = listOf(6), room = { false }))

    assertEquals(6L, scored.result.total, "a six that could not explode is still a six")
    assertTrue(
      DieNote.TrayFull in
        scored.result.dice
          .single()
          .notes,
    )
  }

  @Test
  fun `a reroll the tray has no room for leaves the die as it fell`() {
    val scored = assertIs<Scoring.Scored>(score("1d6r2", values = listOf(1), room = { false }))

    assertEquals(1L, scored.result.total)
    assertTrue(
      DieNote.TrayFull in
        scored.result.dice
          .single()
          .notes,
    )
    assertTrue(
      scored.result.dice
        .single()
        .kept,
      "a die nothing replaced was struck through",
    )
  }

  @Test
  fun `a die that has already landed is never taken back off the table`() {
    // The tray fills up as a chain grows, so the live question "is there room
    // for another" eventually answers no. Asking it again about a die that was
    // thrown ten seconds ago would drop it out of the breakdown — a die the
    // player watched land, gone.
    val scored = assertIs<Scoring.Scored>(score("1d6!", values = listOf(6), added = listOf(6), room = { false }))

    assertEquals(12L, scored.result.total, "the die that was already thrown was taken back")
    assertEquals(2, scored.result.dice.size)
    assertTrue(
      DieNote.FromExplosion in
        scored.result.dice
          .last()
          .notes,
    )
    assertTrue(
      DieNote.TrayFull in
        scored.result.dice
          .last()
          .notes,
    )
  }

  @Test
  fun `every chain that earned a die is owed one at the same moment`() {
    // Three sixes in one throw earn three dice, and they are all owed the
    // instant the dice stop. Asking for them one at a time asked the player for
    // three shakes where a table asks for one
    // (`docs/dice-notation.md`, "Evaluation").
    val owed = pending("4d6!", values = listOf(6, 2, 6, 6))

    assertEquals(3, owed.size, "three sixes earn three dice")
    assertTrue(owed.all { it.id == "d6" })
  }

  @Test
  fun `a throw with nothing to add is owed nothing`() {
    assertEquals(emptyList(), pending("4d6!", values = listOf(1, 2, 3, 4)))
  }

  @Test
  fun `a chain is owed one die at a time however deep it goes`() {
    // Depth is the one thing that *is* sequential: a chain cannot know it needs
    // a third die until the second has landed.
    assertEquals(1, pending("1d6!", values = listOf(6)).size)
    assertEquals(1, pending("1d6!", values = listOf(6), added = listOf(6)).size)
    assertEquals(0, pending("1d6!", values = listOf(6), added = listOf(6, 2)).size)
  }

  @Test
  fun `what is owed is what the scoring would have asked for first`() {
    // The two answers cannot disagree about the first die, or a roll would
    // throw one die and score another.
    val asked = assertIs<Scoring.OneMoreDie>(score("4d6!", values = listOf(6, 2, 6, 6)))

    assertEquals(asked.die.id, pending("4d6!", values = listOf(6, 2, 6, 6)).first().id)
  }

  @Test
  fun `a chain with no room left on the table is owed nothing`() {
    assertEquals(emptyList(), pending("1d6!", values = listOf(6), room = { false }))
  }

  private fun pending(
    text: String,
    values: List<Int>,
    added: List<Int> = emptyList(),
    room: (Die) -> Boolean = { true },
  ): List<Die> {
    val plan = plan(text)
    val faces =
      plan.dice.mapIndexed { position, instance -> position to faceShowing(instance.die, values[position]) }
    return RunningScore.pending(
      formula = parsed(text),
      plan = plan,
      outcome = ThrowOutcome(faces = faces.toMap()),
      added =
        AddedDice(
          faces = added.map { value -> faceShowing(plan.dice.first().die, value) },
          room = room,
        ),
    )
  }

  private fun score(
    text: String,
    values: List<Int>,
    added: List<Int> = emptyList(),
    room: (Die) -> Boolean = { true },
  ): Scoring {
    val plan = plan(text)
    val faces =
      plan.dice.mapIndexed { position, instance -> position to faceShowing(instance.die, values[position]) }
    return RunningScore.of(
      formula = parsed(text),
      plan = plan,
      outcome = ThrowOutcome(faces = faces.toMap()),
      added =
        AddedDice(
          faces = added.map { value -> faceShowing(plan.dice.first().die, value) },
          room = room,
        ),
    )
  }

  private fun parsed(text: String): Formula = (FormulaParser.parse(text) as ParseResult.Parsed).formula

  private fun plan(text: String): RollPlan = (RollPlanner.plan(text, catalog) as PlanResult.Planned).plan

  private fun faceShowing(
    die: Die,
    value: Int,
  ): Int =
    die.faces.indexOfFirst { it.value == value }.also {
      require(it >= 0) { "${die.id} has no face showing $value" }
    }
}
