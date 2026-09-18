package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieRole
import kotlin.math.abs

/**
 * Turns one `dice` node into the physical dice it puts on the table.
 *
 * Two rules from `docs/dice-notation.md` live here.
 *
 * The fallback is **per die**, not per formula: when the default set lacks a
 * d12, `1d20 + 1d12` still rolls, taking the d20 from the default set and the
 * d12 from the bundled one, and the breakdown says so. A `setref:` gets no
 * fallback at all — someone who asked for `brass:d12` wants brass or an error.
 *
 * A d100 is always two d10s. When the set has a real `d10-tens` it is used as
 * it is; otherwise the plain d10 stands in with its values mapped to tens,
 * which is what "multiplied by ten in the breakdown" means in practice. Labels
 * are left alone either way, so the breakdown still shows the face that landed.
 */
internal class DieResolver(
  private val catalog: DiceCatalog,
  private val formulaText: String,
) {
  /** The dice [node] resolves to, numbered from [firstIndex] in throw order. */
  fun resolve(
    node: DiceNode,
    firstIndex: Int,
  ): List<DieInstance> {
    val requestedSetId = node.setRef ?: catalog.defaultSetId
    if (node.setRef != null && catalog.set(node.setRef) == null) {
      throw failure(NotationErrorCode.UnknownSet, "no dice set '${node.setRef}' is installed", node.range)
    }
    var index = firstIndex
    return buildList {
      repeat(node.count) {
        unitOf(node, requestedSetId).forEach { resolved ->
          add(
            DieInstance(
              index = index++,
              groupId = node.id,
              setId = resolved.setId,
              requestedSetId = requestedSetId,
              die = resolved.die,
              role = resolved.role,
            ),
          )
        }
      }
    }
  }

  /** One scoring unit: a single die, or the two halves of a percentile pair. */
  private fun unitOf(
    node: DiceNode,
    requestedSetId: String,
  ): List<Resolved> =
    when (node.sides) {
      is Sides.Numeric -> listOf(find(node, requestedSetId, "d${node.sides.value}"))
      Sides.Fudge -> listOf(find(node, requestedSetId, FUDGE_DIE_ID))
      Sides.Percentile -> percentile(node, requestedSetId)
    }

  private fun percentile(
    node: DiceNode,
    requestedSetId: String,
  ): List<Resolved> {
    val units = find(node, requestedSetId, UNITS_DIE_ID)
    val realTens = catalog.set(units.setId)?.die(TENS_DIE_ID)
    val tens = Resolved(units.setId, realTens ?: units.die.asTensDie(), DieRole.PercentileTens)
    return listOf(tens, Resolved(units.setId, units.die.asUnitsDie(), DieRole.PercentileUnits))
  }

  private fun find(
    node: DiceNode,
    requestedSetId: String,
    dieId: String,
  ): Resolved {
    catalog.set(requestedSetId)?.die(dieId)?.let { return Resolved(requestedSetId, it, DieRole.Normal) }
    if (node.setRef == null) {
      catalog.set(DiceSet.BUILTIN_ID)?.die(dieId)?.let {
        return Resolved(DiceSet.BUILTIN_ID, it, DieRole.Normal)
      }
    }
    throw missingDie(node, requestedSetId, dieId)
  }

  private fun missingDie(
    node: DiceNode,
    setId: String,
    dieId: String,
  ): ParseFailure {
    val searched =
      if (node.setRef == null) {
        listOfNotNull(catalog.set(setId), catalog.set(DiceSet.BUILTIN_ID))
      } else {
        listOfNotNull(catalog.set(setId))
      }
    val nearest = nearestDieId(searched.flatMap(DiceSet::dice).distinctBy(Die::id), dieId)
    return failure(
      code = NotationErrorCode.UnknownDie,
      message = "no $dieId in set \"$setId\"",
      range = node.range,
      suggestion = nearest?.let { swapDie(node, it) },
    )
  }

  /** [formulaText] with this group's die swapped for [dieId] — a formula to offer as it stands. */
  private fun swapDie(
    node: DiceNode,
    dieId: String,
  ): String {
    val prefix = node.setRef?.plus(":").orEmpty()
    val suffix = node.modifiers.joinToString("") { formulaText.substring(it.range) }
    return formulaText.replaceRange(node.range, "$prefix${node.count}$dieId$suffix")
  }

  /** One resolved die and where it came from. */
  private data class Resolved(
    val setId: String,
    val die: Die,
    val role: DieRole,
  )

  companion object {
    /**
     * The Fudge die's id, which more than this resolver needs to know.
     *
     * [FudgeTotal] asks whether a roll is all Fudge dice, and [DicePicker]
     * asks whether a die is the one that spells `dF`. It was written out
     * three times before, which is three chances to disagree about what a
     * Fudge die is.
     */
    const val FUDGE_DIE_ID = "df"

    internal const val UNITS_DIE_ID = "d10"
    internal const val TENS_DIE_ID = "d10-tens"
  }
}

/** A d10 has ten faces, and a percentile pair reads them as units and tens of units. */
private const val D10_FACES = 10

/**
 * The same solid, scoring 0, 10, … 90 — a plain d10 standing in as the tens
 * half, relabelled to match, so the breakdown shows the tens the die is being
 * read as rather than the units printed on it
 * (`docs/dice-notation.md`, "d100 and d%").
 */
private fun Die.asTensDie(): Die =
  copy(
    faces =
      faces.map { face ->
        val tens = (face.value % D10_FACES) * D10_FACES
        face.copy(value = tens, label = "%02d".format(tens))
      },
  )

/** The same solid, scoring 0 … 9 — a d10 whose `10` reads as `0`, as a real one does. */
private fun Die.asUnitsDie(): Die = copy(faces = faces.map { it.copy(value = it.value % D10_FACES) })

/**
 * The `dN` nearest to [wanted] among [dice], to suggest in place of a die the
 * set does not have. A tie goes to the larger die: offering a d8 for a d7 is
 * kinder than offering a d6, because it can at least still roll the number the
 * player asked about.
 */
private fun nearestDieId(
  dice: List<Die>,
  wanted: String,
): String? {
  val sides = wanted.removePrefix("d").toIntOrNull() ?: return null
  return dice
    .mapNotNull { die ->
      die.id
        .removePrefix("d")
        .toIntOrNull()
        ?.let { die.id to it }
    }.minByOrNull { (_, value) -> abs(value - sides) * 2 + if (value < sides) 1 else 0 }
    ?.first
}
