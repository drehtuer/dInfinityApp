package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.DieRole
import de.drehtuer.dinfinity.core.model.PlannedGroup
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup

/**
 * Scores one `dice` node from the faces its dice landed on.
 *
 * Modifiers are applied in a fixed order, whatever order they were written in,
 * so `4d6r1dl1` and `4d6dl1r1` mean the same thing:
 *
 * 1. **`r n`** — a die at or below the threshold is thrown once more. Once:
 *    the replacement stands however low it is.
 * 2. **`!`** — a die showing its highest face throws another of the same die,
 *    which joins *that die's* chain rather than the group at large. So
 *    `2d6!kh1` keeps the better of two chains, which is what a player means.
 * 3. **`min n`** — a die below `n` counts as `n`. Per die, as the grammar says.
 *    The face it actually landed on is still what the breakdown shows.
 * 4. **`kh`/`kl`/`dh`/`dl`** — whole chains are kept or dropped, by what they
 *    came to together.
 *
 * Every die that was ever thrown stays in the breakdown, dropped ones struck
 * through (`docs/dice-notation.md`, "Evaluation"). A player wants to see the 1
 * that `4d6dl1` threw away.
 */
internal class GroupRoller(
  private val node: DiceNode,
  private val group: PlannedGroup,
  private val extra: ExtraThrow,
  private val indices: ThrowIndices,
) {
  /** One throw of one die, or of the two halves of a percentile pair. */
  private class Throw(
    val instances: List<DieInstance>,
    val faces: List<Int>,
    var value: Int,
    val notes: MutableSet<DieNote> = mutableSetOf(),
  ) {
    val live: Boolean get() = DieNote.Dropped !in notes

    /** This throw as the one or two dice the breakdown lists. */
    fun breakdown(): List<RolledDie> =
      instances.mapIndexed { position, instance ->
        val face = instance.die.faces[faces[position]]
        RolledDie(
          instanceIndex = instance.index,
          dieId = instance.die.id,
          value = face.value,
          label = face.label,
          naturalMax = face.value == instance.die.maxValue,
          naturalMin = face.value == instance.die.minValue,
          notes = notes + instance.role.note(),
        )
      }
  }

  private val percentile = group.dice.firstOrNull()?.role != DieRole.Normal
  private val unitMaximum =
    if (percentile) {
      PERCENTILE_MAX
    } else {
      group.dice
        .firstOrNull()
        ?.die
        ?.maxValue ?: 0
    }

  /** Scores the group from [faces], the face index each die in the throw landed on. */
  fun roll(faces: Map<Int, Int>): RolledGroup {
    val chains = initialChains(faces)
    node.modifiers.filterIsInstance<DiceModifier.Reroll>().forEach { reroll(chains, it) }
    if (node.explodes && group.dice.isNotEmpty()) chains.forEach(::explode)
    node.modifiers.filterIsInstance<DiceModifier.Minimum>().forEach { raise(chains, it) }
    select(chains)
    return RolledGroup(
      id = group.id,
      notation = group.notation,
      setId =
        group.dice
          .firstOrNull()
          ?.setId
          .orEmpty(),
      requestedSetId =
        group.dice
          .firstOrNull()
          ?.requestedSetId
          .orEmpty(),
      dice = chains.flatten().flatMap(Throw::breakdown),
      subtotal = chains.sumOf { chain -> chain.filter(Throw::live).sumOf { it.value.toLong() } },
    )
  }

  /** The dice of the initial throw, one chain per unit, in throw order. */
  private fun initialChains(faces: Map<Int, Int>): List<MutableList<Throw>> =
    group.dice
      .chunked(if (percentile) 2 else 1)
      .map { instances ->
        mutableListOf(record(instances, instances.map { requireFace(faces, it) }))
      }

  private fun requireFace(
    faces: Map<Int, Int>,
    instance: DieInstance,
  ): Int =
    requireNotNull(faces[instance.index]) {
      "the simulation reported no face for die ${instance.index} of ${group.notation}"
    }

  private fun record(
    instances: List<DieInstance>,
    faces: List<Int>,
  ): Throw = Throw(instances, faces, score(instances, faces))

  /**
   * What a unit scored. A percentile pair is tens plus units, with `00` and `0`
   * reading as 100 (`docs/dice-notation.md`, "d100 and d%").
   */
  private fun score(
    instances: List<DieInstance>,
    faces: List<Int>,
  ): Int {
    val values = instances.mapIndexed { position, instance -> instance.die.valueAt(faces[position]) }
    if (!percentile) return values.single()
    return values.sum().let { if (it == 0) PERCENTILE_MAX else it }
  }

  /**
   * Throws the same dice again, for a reroll or an explosion.
   *
   * The replacements are new dice with new indices, not the old ones with new
   * numbers: they are physically thrown into the tray a moment later, and a
   * tap on one has to find its own line in the breakdown.
   */
  private fun again(source: Throw): Throw {
    val instances = source.instances.map { it.copy(index = indices.next()) }
    return record(instances, instances.map { extra.roll(it.die) })
  }

  private fun reroll(
    chains: List<MutableList<Throw>>,
    modifier: DiceModifier.Reroll,
  ) {
    chains.forEach { chain ->
      val first = chain.first()
      if (first.value > modifier.threshold) return@forEach
      // A reroll that the tray has no room for does not happen, and the die
      // that would have been replaced stands. The alternative is a die dropped
      // onto a settled pile, and there is no version of this app where that is
      // the better answer.
      if (!extra.roomForAnother(first.instances.first().die)) {
        first.notes += DieNote.TrayFull
        return@forEach
      }
      first.notes += setOf(DieNote.Dropped, DieNote.Rerolled)
      chain.add(1, again(first).also { it.notes += DieNote.Rerolled })
    }
  }

  private fun explode(chain: MutableList<Throw>) {
    var depth = 0
    while (chain.last().value == unitMaximum) {
      if (depth == NotationLimits.MAX_EXPLOSION_DEPTH) {
        chain.last().notes += DieNote.ExplosionLimitReached
        return
      }
      // The other end of the chain: the tray has run out of clear floor, so
      // there is nowhere to drop the die this one called for.
      if (!extra.roomForAnother(
          chain
            .last()
            .instances
            .first()
            .die,
        )
      ) {
        chain.last().notes += DieNote.TrayFull
        return
      }
      chain += again(chain.last()).also { it.notes += DieNote.FromExplosion }
      depth++
    }
  }

  private fun raise(
    chains: List<MutableList<Throw>>,
    modifier: DiceModifier.Minimum,
  ) {
    chains.flatten().filter(Throw::live).forEach { thrown ->
      if (thrown.value >= modifier.value) return@forEach
      thrown.value = modifier.value
      thrown.notes += DieNote.ClampedToMin
    }
  }

  /** Marks the chains `kh`/`kl`/`dh`/`dl` leaves out, by what each chain came to. */
  private fun select(chains: List<MutableList<Throw>>) {
    val selection = node.modifiers.firstOrNull { it.selects } ?: return
    val totals = chains.map { chain -> chain.filter(Throw::live).sumOf { it.value.toLong() } }
    val ranked = totals.indices.sortedBy { totals[it] }
    val dropped =
      when (selection) {
        is DiceModifier.KeepHighest -> ranked.dropLast(selection.n)
        is DiceModifier.KeepLowest -> ranked.drop(selection.n)
        is DiceModifier.DropHighest -> ranked.takeLast(selection.n)
        is DiceModifier.DropLowest -> ranked.take(selection.n)
        else -> emptyList()
      }
    dropped.forEach { index -> chains[index].forEach { it.notes += DieNote.Dropped } }
  }

  private companion object {
    /** A percentile pair reads 1 to 100; `00` and `0` together are the 100. */
    const val PERCENTILE_MAX = 100
  }
}

/** True for the four modifiers that choose which dice count. */
private val DiceModifier.selects: Boolean
  get() =
    this is DiceModifier.KeepHighest ||
      this is DiceModifier.KeepLowest ||
      this is DiceModifier.DropHighest ||
      this is DiceModifier.DropLowest

/** What the breakdown says about a die because of the part it plays. */
private fun DieRole.note(): Set<DieNote> =
  when (this) {
    DieRole.Normal -> emptySet()
    DieRole.PercentileTens -> setOf(DieNote.PercentileTens)
    DieRole.PercentileUnits -> setOf(DieNote.PercentileUnits)
  }

/**
 * Throws one more die into the same tray, for a reroll or an explosion, and
 * reports which face came up.
 *
 * It is a callback rather than a list of extra values because how many extra
 * dice a roll needs is not known until the first ones land: `8d6!` might add
 * none or a dozen. In normal mode the dice visibly drop into the tray; in
 * power-saving mode they simply do not get drawn
 * (`docs/dice-notation.md`, "Evaluation", step 5).
 */
fun interface ExtraThrow {
  /** The index of the face [die] landed on. */
  fun roll(die: Die): Int

  /**
   * Whether the tray could take one more [die] at all.
   *
   * Asked before [roll], because an added die is dropped into the floor the
   * dice already down leave clear, and a tray with no clear floor left has
   * nowhere to drop one. A chain that runs out of table stops there and says so
   * in the breakdown, which is the same shape the depth limit has
   * (`docs/dice-notation.md`, "Limits"; `docs/tables.md`, "Capacity rule").
   *
   * It defaults to yes for the callers that are not throwing anything into a
   * real tray — the brute-force check behind the outcome graph, and the
   * property tests — where the question has no meaning.
   */
  fun roomForAnother(die: Die): Boolean = true
}

/**
 * Hands out the throw positions of dice that were not in the first throw.
 *
 * It counts on from the end of the initial throw and is shared across every
 * group, so no two dice in one roll ever carry the same index however many
 * explosions and rerolls a formula sets off.
 */
internal class ThrowIndices(
  private var next: Int,
) {
  fun next(): Int = next++
}
