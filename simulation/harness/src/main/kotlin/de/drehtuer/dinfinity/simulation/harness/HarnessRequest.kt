package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.CapacityVerdict
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * What a run was asked for, read off the instrumentation arguments.
 *
 * The arguments come from a device and the parsing of them does not: a
 * misspelt shape, a dice count past the engine's cap and a run of zero rolls
 * are all mistakes somebody makes at a terminal, and all three should be
 * answered by a sentence rather than by a phone. So the whole of it is here,
 * behind a lambda that stands in for `InstrumentationRegistry.getArguments()`,
 * and is tested on the JVM.
 *
 * The pattern of taking the numbers from instrumentation arguments is
 * `FairnessTest`'s (`docs/physics-and-rendering.md`, "Are the dice fair") — the
 * honest number of rolls and the affordable one are not the same, so the run
 * says which it wants.
 *
 * @param label a name for the run, and for the files it writes.
 * @param shape the catalogue solid every die in the throw is.
 * @param diceCount how many dice are in each throw.
 * @param rolls how many throws to make.
 * @param seed the run's base seed. Every roll's own seed comes from it, so one
 *   number replays a whole run.
 */
data class HarnessRequest(
  val label: String,
  val shape: DieShape,
  val diceCount: Int,
  val rolls: Int,
  val seed: Long,
) {
  init {
    require(rolls > 0) { "a run of $rolls rolls has nothing to measure" }
    require(diceCount in 1..TableCapacity.MAX_DICE) {
      "$diceCount dice is outside the 1..${TableCapacity.MAX_DICE} the engine takes"
    }
  }

  /**
   * The throw this asks for, on [geometry], worked out down to the scale the
   * capacity rule gives it.
   *
   * Here rather than in the instrumented test because every line of it is a
   * decision — which dice, how far shrunk, refused or not — and a decision
   * belongs where a JVM test can reach it (`docs/architecture.md`, decision
   * 40).
   */
  fun plan(
    geometry: TableGeometry = TableGeometry.referenceDevice(),
    table: TableLook = PLAIN,
  ): HarnessPlan {
    val die = Die.standard(shape.id, shape)
    val dice = List(diceCount) { die }
    val scale =
      when (val verdict = TableCapacity.check(dice, geometry)) {
        is CapacityVerdict.Fits -> verdict.scale
        // Refused before a body is created, and said in the words the roll
        // screen would use (`docs/tables.md`). A harness asked for a throw the
        // app would not make is measuring nothing.
        is CapacityVerdict.Refused ->
          throw IllegalArgumentException("the harness asked for a throw the table refuses: ${verdict.reason}")
      }
    return HarnessPlan(
      dice =
        dice.mapIndexed { index, each ->
          DieInstance(index = index, groupId = 0, setId = BUILT_IN, requestedSetId = BUILT_IN, die = each)
        },
      dieScale = scale,
      geometry = geometry,
      table = table,
      seed = seed,
    )
  }

  companion object {
    /** How many rolls to make. Absent means no run was asked for at all. */
    const val ROLLS: String = "harness.rolls"

    /** How many dice in each throw. */
    const val DICE: String = "harness.dice"

    /** Which solid they are: a catalogue id, or `d20`, or `20`. */
    const val SHAPE: String = "harness.shape"

    /** The run's base seed. */
    const val SEED: String = "harness.seed"

    /** What to call the run, and its files. */
    const val LABEL: String = "harness.label"

    /** Twenty dice, because that is the count Step 5.5 states its settle targets at. */
    const val DEFAULT_DICE: Int = 20

    /** And the d20, because it is the die most rolls are made of. */
    val DEFAULT_SHAPE: DieShape = DieShape.Icosahedron

    /** A run with no seed given still replays: it is seeded, just not by hand. */
    const val DEFAULT_SEED: Long = 1L

    /**
     * The run [arguments] asks for, or null when it asks for none.
     *
     * Null rather than a default run, so that the harness can sit in the
     * ordinary device suite without adding minutes to it: no
     * `-e harness.rolls`, no run (`docs/build-setup.md`).
     */
    fun from(arguments: (String) -> String?): HarnessRequest? {
      val rolls = arguments(ROLLS)?.trim()?.toIntOrNull() ?: return null
      if (rolls <= 0) return null
      val shape = arguments(SHAPE)?.let(::shapeOf) ?: DEFAULT_SHAPE
      val diceCount = arguments(DICE)?.trim()?.toIntOrNull() ?: DEFAULT_DICE
      return HarnessRequest(
        label = arguments(LABEL)?.trim()?.takeIf(String::isNotEmpty) ?: "${diceCount}d${shape.faceCount}",
        shape = shape,
        diceCount = diceCount,
        rolls = rolls,
        seed = arguments(SEED)?.trim()?.toLongOrNull() ?: DEFAULT_SEED,
      )
    }

    /**
     * The catalogue solid [name] means.
     *
     * Three spellings, because all three are what somebody types: the
     * catalogue id (`icosahedron`), the die (`d20`) and the bare face count
     * (`20`). A name that is none of them is refused with the list, which is
     * shorter than the guess it would otherwise take.
     */
    fun shapeOf(name: String): DieShape {
      val wanted = name.trim().lowercase()
      val faces = wanted.removePrefix("d").toIntOrNull()
      return DieShape.entries.firstOrNull { it.id == wanted || it.faceCount == faces }
        ?: throw IllegalArgumentException(
          "there is no die called \"$name\"; the catalogue has " +
            DieShape.entries.joinToString { "${it.id} (d${it.faceCount})" },
        )
    }

    private const val BUILT_IN = "builtin"

    /** The tray's physics with none of its looks, which is all a headless run needs. */
    val PLAIN: TableLook = TableLook(id = "plain", name = "Plain")
  }
}

/**
 * The throw a request came to, ready to be run roll after roll.
 *
 * @param dice the dice, in throw order.
 * @param dieScale how far down the capacity rule shrank them.
 * @param geometry the tray they are thrown in.
 * @param table its friction and restitution.
 * @param seed the run's base seed.
 */
data class HarnessPlan(
  val dice: List<DieInstance>,
  val dieScale: Double,
  val geometry: TableGeometry,
  val table: TableLook,
  val seed: Long,
) {
  /**
   * The throw for roll [index].
   *
   * Its seed goes through [Seeds.derived] rather than being counted up,
   * because two seeds that differ in their low bits are not two independent
   * streams — a harness resting on rolls that are not independent cannot say
   * anything about the physics (`simulation/api`'s `Seeds`). The roll number
   * still decides it, so a run is as repeatable as the number it started from.
   */
  fun specFor(index: Int): ThrowSpec =
    ThrowSpec(
      dice = dice,
      geometry = geometry,
      table = table,
      seed = Seeds.derived(seed, index),
      dieScale = dieScale,
    )
}
