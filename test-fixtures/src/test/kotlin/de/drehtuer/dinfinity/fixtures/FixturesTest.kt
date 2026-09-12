package de.drehtuer.dinfinity.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixturesTest {
  @Test
  fun `every declared dice-set fixture exists and is not empty`() {
    (Fixtures.validDiceSets + Fixtures.invalidDiceSets).forEach { name ->
      assertTrue(Fixtures.diceSet(name).isNotBlank(), "$name is empty")
    }
  }

  @Test
  fun `every declared collection fixture exists and is not empty`() {
    Fixtures.collections.forEach { name ->
      assertTrue(Fixtures.collection(name).isNotBlank(), "$name is empty")
    }
  }

  @Test
  fun `each invalid dice set is invalid for its own reason`() {
    // The file names are the contract: one rejection rule each, no overlap.
    assertEquals(Fixtures.invalidDiceSets.size, Fixtures.invalidDiceSets.toSet().size)
    Fixtures.invalidDiceSets.forEach { name ->
      assertTrue(name.startsWith("invalid-"), "$name should be named for its rejection")
    }
  }

  @Test
  fun `golden cases parse and are unique by seed`() {
    val cases = Fixtures.goldenCases()
    assertTrue(cases.size >= 10, "expected the full golden set, got ${cases.size}")
    assertEquals(cases.size, cases.map(GoldenCase::seed).toSet().size)
    cases.forEach { assertTrue(it.formula.isNotBlank(), "case ${it.seed} has no formula") }
  }
}
