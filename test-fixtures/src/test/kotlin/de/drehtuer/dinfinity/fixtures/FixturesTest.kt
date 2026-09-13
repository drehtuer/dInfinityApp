package de.drehtuer.dinfinity.fixtures

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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

  @Test
  fun `every golden case carries a recorded outcome`() {
    // The suite itself says this far more loudly, with the line to paste in.
    // It is here as well because this module owns the file's shape, and a
    // fixture that has quietly lost its right-hand columns still parses.
    Fixtures.goldenCases().forEach { case ->
      assertNotNull(case.expected, "case ${case.seed} '${case.formula}' has no recorded outcome")
    }
  }

  @Test
  fun `a recorded case comes back out as the line the fixture carries`() {
    // What a recording run logs has to be pasteable into the file unedited,
    // or re-recording is a transcription exercise (`docs/build-setup.md`).
    val original = Fixtures.read(Fixtures.GOLDEN_CASES).lineSequence().filterNot { it.startsWith("#") }
    val rewritten = Fixtures.goldenCases().map { Fixtures.goldenLine(it, assertNotNull(it.expected)) }

    assertEquals(rewritten, original.filter(String::isNotBlank).toList())
  }

  @Test
  fun `an input the file does not define is refused by name`() {
    val failure = assertFailsWith<IllegalStateException> { GoldenInput.of("waggle") }
    assertTrue(failure.message.orEmpty().contains("waggle"), failure.message)
  }

  @Test
  fun `the golden shake is one full set of swings and never exceeds its peak`() {
    val samples = GoldenShake.samples()

    assertEquals(GoldenShake.STEPS, samples.size)
    assertEquals(samples.indices.toList(), samples.map(GoldenShakeSample::stepIndex))
    samples.forEach { sample ->
      assertTrue(
        abs(sample.accelerationMmPerSecond2.x) <= GoldenShake.PEAK_MM_PER_SECOND2,
        "step ${sample.stepIndex} pulls harder than a hand can",
      )
      assertTrue(abs(sample.accelerationMmPerSecond2.y) <= GoldenShake.CROSS_PEAK_MM_PER_SECOND2)
      assertEquals(GoldenVector(0.0, 0.0, -1.0), sample.gravity)
    }
  }

  @Test
  fun `the golden shake reverses, so the dice are shaken rather than pushed`() {
    val along = GoldenShake.samples().map { it.accelerationMmPerSecond2.x }

    assertTrue(along.any { it > 0.0 } && along.any { it < 0.0 }, "a shake that never reverses is a shove")
    assertEquals(0.0, along.sum(), 1e-9, "every swing of a triangle cancels the one before it")
  }

  @Test
  fun `the golden shake is the same shake every time it is asked for`() {
    assertEquals(GoldenShake.samples(), GoldenShake.samples())
  }

  @Test
  fun `a digest notices one bit`() {
    val one = GoldenDigest().add("die").add(1.0).hex
    val other = GoldenDigest().add("die").add(Double.fromBits(1.0.toRawBits() + 1)).hex

    assertEquals(one, GoldenDigest().add("die").add(1.0).hex)
    assertNotEquals(one, other, "two doubles an ulp apart digested the same")
  }

  @Test
  fun `a digest is sixteen hex digits, whatever went into it`() {
    listOf(
      GoldenDigest(),
      GoldenDigest().add(0),
      GoldenDigest().add("").add(Long.MIN_VALUE).add(Double.NaN),
    ).forEach { digest ->
      assertEquals(16, digest.hex.length, digest.hex)
      assertTrue(digest.hex.all { it in "0123456789abcdef" }, digest.hex)
    }
  }

  @Test
  fun `a digest depends on the order things went into it`() {
    assertNotEquals(
      GoldenDigest().add("a").add("b").hex,
      GoldenDigest().add("b").add("a").hex,
    )
    // And on where one field ends and the next begins, which is what the
    // separator between texts is for.
    assertNotEquals(
      GoldenDigest().add("ab").add("c").hex,
      GoldenDigest().add("a").add("bc").hex,
    )
  }
}
