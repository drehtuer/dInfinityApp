package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.data.FinishedRoll
import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.RollRecording
import de.drehtuer.dinfinity.data.RollReplay
import de.drehtuer.dinfinity.data.db.RollHistoryRow
import de.drehtuer.dinfinity.feature.roll.FinishedThrow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.ParameterizedType

/**
 * Where the record of a throw stops (`docs/architecture.md`, decision 13, and
 * `docs/statistics.md`).
 *
 * A finished roll carries the throw that would replay it: the spec it started
 * as, with the shake that actually arrived written back into it. That is for a
 * bug report, on a developer's machine, about a roll still on screen — and it
 * is deliberately the *last* place it exists. **A past roll is a record, not
 * something to re-run.** `HistoryEntry` has no seed on it to show, the exports
 * have no column for one, and neither has anywhere to put a shake.
 *
 * The rule is kept by the shapes of the types rather than by anybody
 * remembering it, so that is what is asserted here. This is the one module that
 * can see both ends of the seam, which is why the test lives here.
 */
class ShakeStopsAtTheRollScreenTest {
  @Test
  fun `a throw the roll screen hands out is replayable`() {
    // The other half of the rule, and worth pinning twice over: what follows is
    // only interesting because there is something for it to stop, and a walk
    // that found nothing anywhere would pass every test below without looking.
    assertEquals(
      "a finished throw no longer carries the spec and the shake that replay it",
      REPLAYABLE,
      reachableFrom(FinishedThrow::class.java).intersect(REPLAYABLE),
    )
  }

  @Test
  fun `the seam to the database has nowhere to put a throw`() {
    // `ThrowRecorder` is handed a `FinishedThrow` and takes the result, the
    // plan, the seed and where the throw came from off it. This is what makes
    // "and nothing else" a fact rather than a habit: there is no parameter a
    // shake or a spec could be passed as.
    val record =
      RollRecording::class.java.declaredMethods
        .single { it.name == "record" }

    assertEquals(
      "the recorder can be handed a throw to re-run",
      emptyList<String>(),
      record.parameterTypes.map { it.simpleName }.filter { it in REPLAYABLE },
    )
  }

  @Test
  fun `nothing a roll is written down as can carry a shake`() {
    // Walked rather than listed field by field, because a shake would arrive
    // nested inside something else long before anybody added it to one of these
    // directly.
    listOf(FinishedRoll::class.java, RollReplay::class.java, RollHistoryRow::class.java).forEach { written ->
      assertEquals(
        "${written.simpleName} can be given a throw to re-run",
        emptySet<String>(),
        reachableFrom(written).intersect(REPLAYABLE),
      )
    }
  }

  @Test
  fun `nothing the history screen reads can carry a shake`() {
    // And so nothing the exports draw from can either: they are written out of
    // `HistoryEntry` and out of nothing else (`docs/statistics.md`).
    assertEquals(
      "a past roll arrived at the history screen with a shake on it",
      emptySet<String>(),
      reachableFrom(HistoryEntry::class.java).intersect(REPLAYABLE),
    )
  }

  /**
   * Every type reachable from [root] by following its fields, by simple name.
   *
   * Stops at the standard library, which has no dInfinity type below it, and
   * remembers where it has been, which is what keeps a type that refers to
   * itself from running forever.
   */
  private fun reachableFrom(root: Class<*>): Set<String> {
    val seen = mutableSetOf<Class<*>>()
    val toVisit = ArrayDeque(listOf(root))
    while (toVisit.isNotEmpty()) {
      val here = toVisit.removeFirst()
      if (!seen.add(here)) continue
      here.declaredFields.forEach { field ->
        (listOf(field.type) + argumentsOf(field.genericType))
          .filterNot { it.name.startsWith("java.") || it.name.startsWith("kotlin.") }
          .forEach(toVisit::addLast)
      }
    }
    return seen.map(Class<*>::getSimpleName).toSet()
  }

  /** The types inside a `List<Something>`, and nothing for a plain field. */
  private fun argumentsOf(type: java.lang.reflect.Type): List<Class<*>> =
    (type as? ParameterizedType)
      ?.actualTypeArguments
      ?.filterIsInstance<Class<*>>()
      .orEmpty()

  private companion object {
    /** The two types that could turn a record back into a roll. */
    val REPLAYABLE = setOf("ThrowSpec", "ShakeSample")
  }
}
