package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollPlanTest {
  private val d6 = Die.standard("d6", DieShape.Cube)
  private val d20 = Die.standard("d20", DieShape.Icosahedron)

  @Test
  fun `an empty plan throws nothing and needs no table`() {
    val plan = RollPlan(formula = "4")
    assertTrue(plan.dice.isEmpty())
    assertEquals(0, plan.capacityDiceCount)
    assertFalse(plan.hasFallbacks)
  }

  @Test
  fun `every group's dice go into one throw, in spawn order`() {
    val plan = plan(group("3d6", d6, 3), group("1d20", d20, 1))
    assertEquals(listOf(0, 1, 2, 3), plan.dice.map(DieInstance::index))
    assertEquals(listOf("d6", "d6", "d6", "d20"), plan.dice.map { it.die.id })
  }

  @Test
  fun `capacity counts the dice thrown when nothing explodes`() {
    assertEquals(4, plan(group("3d6", d6, 3), group("1d20", d20, 1)).capacityDiceCount)
  }

  @Test
  fun `capacity makes room for the dice a first explosion could add`() {
    assertEquals(16, plan(group("8d6!", d6, 8, explodes = true)).capacityDiceCount)
  }

  @Test
  fun `only the exploding group is doubled`() {
    assertEquals(17, plan(group("8d6!", d6, 8, explodes = true), group("1d20", d20, 1)).capacityDiceCount)
  }

  @Test
  fun `a die from the set the formula asked for has not fallen back`() {
    val plan = plan(group("1d20", d20, 1))
    assertFalse(plan.hasFallbacks)
    assertFalse(plan.groups.single().fellBack)
  }

  @Test
  fun `a die the default set lacks falls back per die, and says so`() {
    val fallback =
      DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "brass", die = d20)
    val plan = RollPlan(formula = "1d20", groups = listOf(PlannedGroup(0, "1d20", listOf(fallback))))
    assertTrue(fallback.fellBack)
    assertTrue(plan.hasFallbacks)
  }

  @Test
  fun `a percentile pair is two d10s marked as tens and units`() {
    val tens =
      DieInstance(0, 0, "builtin", "builtin", d6, role = DieRole.PercentileTens)
    val units =
      DieInstance(1, 0, "builtin", "builtin", d6, role = DieRole.PercentileUnits)
    val plan = RollPlan("d100", groups = listOf(PlannedGroup(0, "d100", listOf(tens, units))))
    assertEquals(listOf(DieRole.PercentileTens, DieRole.PercentileUnits), plan.dice.map(DieInstance::role))
  }

  @Test
  fun `a label is carried for the graph to title itself with`() {
    assertEquals("Fire", RollPlan(formula = "8d6 [Fire]", label = "Fire").label)
  }

  /** A group as the parser would hand it over, before the throw numbers its dice. */
  private data class Draft(
    val notation: String,
    val die: Die,
    val count: Int,
    val explodes: Boolean,
  )

  private fun group(
    notation: String,
    die: Die,
    count: Int,
    explodes: Boolean = false,
  ): Draft = Draft(notation, die, count, explodes)

  /** Numbers the drafts into a plan the way `:core:notation` will: groups and dice in source order. */
  private fun plan(vararg drafts: Draft): RollPlan {
    var next = 0
    val groups =
      drafts.mapIndexed { groupId, draft ->
        PlannedGroup(
          id = groupId,
          notation = draft.notation,
          explodes = draft.explodes,
          dice =
            List(draft.count) {
              DieInstance(
                index = next++,
                groupId = groupId,
                setId = "builtin",
                requestedSetId = "builtin",
                die = draft.die,
              )
            },
        )
      }
    return RollPlan(formula = drafts.joinToString(" + ") { it.notation }, groups = groups)
  }
}
