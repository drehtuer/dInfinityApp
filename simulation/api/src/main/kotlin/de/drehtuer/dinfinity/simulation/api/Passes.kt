package de.drehtuer.dinfinity.simulation.api

/**
 * One throw, and the throws of the dice it could not read that follow it
 * (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice").
 *
 * A throw is counted when its dice stop: every die showing a face is read, and
 * a die that came to rest cocked or standing on another one is **not thrown
 * again by the roll**. The throw stops there, the dice that could not be read
 * stay exactly where they lie, and the player's next shake throws those and
 * only those — a throw of its own, into a world of its own, through the same
 * [ThrowSpec.among] an explosion's die is thrown with. Then they are counted,
 * and whatever still cannot be read waits for the shake after that, until
 * every die has a face. A roll that threw itself again was the app's hand on
 * the dice; this is the player's (`docs/architecture.md`, decision 70).
 *
 * This is the bookkeeping of that, and nothing else: which die of the first
 * throw each die of a later pass is, the faces read so far, the throw the next
 * shake makes, and the one outcome all of it adds up to. Plain Kotlin, so the
 * roll screen and the headless harness keep one account of it rather than two
 * that could disagree about which die is which.
 *
 * Every die is identified by its index in [spec], whichever pass it was read
 * in. A pass's own outcome is keyed by its position in *that* pass, so the
 * mapping between the two is the whole of the arithmetic here.
 *
 * Not thread-safe: it belongs to whoever owns the roll, exactly as the roll
 * does.
 */
class Passes(
  /** The throw the first pass was, which every later pass is a part of. */
  val spec: ThrowSpec,
) {
  /** The faces read so far, keyed by index in [spec]. */
  private val faces = sortedMapOf<Int, Int>()

  /** Which dice of [spec] the pass in the air is throwing, in its own order. */
  private var inTheAir: List<Int> = spec.dice.indices.toList()

  /** The dice the last pass could not read, waiting for a hand. */
  private var unread: List<Int> = emptyList()

  /** Where the dice of the last pass stopped, once it was the last. */
  private var resting: Map<Int, RestingPlace> = emptyMap()

  /**
   * True from a throw until it lands or gives up: the pass is still the
   * roll's, and nothing it will come to can be asked for yet.
   */
  var airborne: Boolean = true
    private set

  private var landedPasses = 0
  private var thrownAgain = 0
  private var steps = 0
  private var rethrows = 0
  private var corrections = 0
  private var forcedSettles = 0
  private var postRestCorrections = 0
  private var stackedAtRest = 0
  private var deepestDiePenetrationMm = 0.0
  private var medianTurnsAfterLanding = 0.0

  /**
   * The dice of [spec] waiting for the player's next shake, by index, or empty
   * when nothing is.
   *
   * Empty while a pass is in the air, too: those dice are not waiting for
   * anything but the floor.
   */
  val waiting: List<Int> get() = unread

  /** The faces read so far, by index in [spec]. */
  val read: Map<Int, Int> get() = faces.toMap()

  /** True once every die of [spec] has been read. */
  val complete: Boolean get() = !airborne && unread.isEmpty()

  /** How many passes have landed so far. A pass that gave up did not land. */
  val landed: Int get() = landedPasses

  /**
   * Which pass is in the air, or was the last to come down, counting the first
   * throw as one — whether or not it landed or gave up.
   */
  val pass: Int get() = thrownAgain + 1

  /**
   * [read], with what the pass in the air has counted so far added to it.
   *
   * What the screen's running total is made of: the pass in the air reports
   * the dice *it* has read by its own positions, and those are not the
   * positions the formula knows them by.
   */
  fun readSoFar(counted: Map<Int, Int>): Map<Int, Int> =
    faces + counted.mapNotNull { (at, face) -> inTheAir.getOrNull(at)?.let { it to face } }

  /**
   * The pass in the air has stopped, and [outcome] is what it came to.
   *
   * Its faces are filed under the dice of [spec] they belong to, and the dice
   * it could not read become [waiting]. Nothing is thrown: that is the next
   * shake's to do ([next]). A die the outcome names and the pass never threw
   * is ignored rather than filed somewhere it does not belong.
   */
  fun landed(outcome: SimulationOutcome) {
    check(airborne) { "a pass landed with nothing in the air" }
    // A die the pass did not throw is not one of its dice, whatever position
    // it was reported at, and nothing is filed under it.
    val ours = inTheAir.indices
    file(outcome.faces.filterKeys { it in ours }, outcome.unread.filter { it in ours })
    // Only the last pass leaves dice on the table. Every earlier one is
    // followed by a throw, and that throw lifts what it read
    // ([SimulationOutcome.restingAt]).
    resting =
      if (unread.isEmpty()) {
        outcome.restingAt.filterKeys { it in ours }.mapKeys { (at, _) -> inTheAir[at] }
      } else {
        emptyMap()
      }
    landedPasses++
    steps += outcome.steps
    rethrows += outcome.rethrows
    corrections += outcome.corrections
    forcedSettles += outcome.forcedSettles
    postRestCorrections += outcome.postRestCorrections
    // The last pass's, because a die standing on another in an earlier one
    // was not left standing: it was thrown again.
    stackedAtRest = outcome.stackedAtRest
    deepestDiePenetrationMm = maxOf(deepestDiePenetrationMm, outcome.deepestDiePenetrationMm)
    // The first pass's. It threw every die and read nearly all of them, and a
    // median over a pass of one die is a figure about that die alone.
    if (landedPasses == 1) medianTurnsAfterLanding = outcome.medianTurnsAfterLanding
  }

  /**
   * The pass in the air gave up: [unsettled] never stopped, and [read] did.
   *
   * There is no outcome for a pass that gave up, so what it did manage is
   * passed in by hand, by the pass's own positions as the roll reported them
   * (`WatchedRoll.countedSoFar`, `WatchedRoll.unsettled`). The dice that never
   * settled wait for a shake exactly as the ones that could not be read do;
   * only what the screen says about them differs.
   */
  fun gaveUp(
    read: Map<Int, Int>,
    unsettled: List<Int>,
  ) {
    check(airborne) { "a pass gave up with nothing in the air" }
    file(read.filterKeys { it in inTheAir.indices }, unsettled.filter { it in inTheAir.indices })
    resting = emptyMap()
  }

  /**
   * The throw the player's shake makes: the [waiting] dice, and nothing else.
   *
   * Into a world of their own. The dice the last pass read are lifted off the
   * table — they are read, and their floor is the room the throw needs — and
   * the dice that were already down before this throw began are carried as
   * they were, in [ThrowSpec.among], with no body anywhere. The seed is
   * [Seeds.again] of [spec]'s and the pass, so a roll replays its re-throws
   * the same way it replays its first throw.
   *
   * @param shake the samples the hand that threw them has made so far, which
   *   for a shake caught as it starts is none: the rest reach the roll as it
   *   runs.
   */
  fun next(shake: List<ShakeSample> = emptyList()): ThrowSpec {
    check(!airborne && unread.isNotEmpty()) { "nothing is waiting to be thrown again" }
    inTheAir = unread
    unread = emptyList()
    airborne = true
    thrownAgain++
    rethrows += inTheAir.size
    return ThrowSpec(
      dice = inTheAir.mapIndexed { at, index -> spec.dice[index].copy(index = at) },
      geometry = spec.geometry,
      table = spec.table,
      seed = Seeds.again(spec.seed, thrownAgain),
      dieScale = spec.dieScale,
      shake = shake,
      among = spec.among,
    )
  }

  /**
   * Everything the passes came to, keyed by index in [spec] — one outcome for
   * one throw, however many shakes it took.
   */
  fun outcome(): SimulationOutcome {
    check(complete) { "the throw has dice that have not been read yet" }
    return SimulationOutcome(
      faces = faces.toMap(),
      steps = steps,
      corrections = corrections,
      rethrows = rethrows,
      forcedSettles = forcedSettles,
      postRestCorrections = postRestCorrections,
      stackedAtRest = stackedAtRest,
      deepestDiePenetrationMm = deepestDiePenetrationMm,
      restingAt = resting,
      medianTurnsAfterLanding = medianTurnsAfterLanding,
      passes = landedPasses,
    )
  }

  private fun file(
    read: Map<Int, Int>,
    notRead: List<Int>,
  ) {
    read.forEach { (at, face) -> faces[inTheAir[at]] = face }
    unread = notRead.map { inTheAir[it] }
    airborne = false
  }

  companion object {
    /**
     * How many passes a throw nobody is watching is given before it is given
     * up on.
     *
     * A player throws a die that will not come good as often as they like, and
     * that is theirs to stop. A scripted hand has nobody to stop it, and a die
     * that lands cocked every time would hold a headless run for ever. At the
     * share of dice a pass leaves unread on the Pixel 10a — under 3 % — the
     * chance that one die needs sixteen is below one in 10²⁴.
     */
    const val MOST_UNWATCHED: Int = 16

    /**
     * Throws [spec] the way a hand that never tires would: every pass that
     * leaves dice unread is followed at once by the throw of those dice, with
     * no shake in it, until every die is read.
     *
     * What the harness and power-saving tests use where nobody is there to
     * shake — the same passes, the same seeds and the same worlds the screen
     * makes, only without the wait between them. Null when a pass gave up
     * ([throwOnce] answered null) or after [MOST_UNWATCHED] passes.
     *
     * @param throwOnce runs one pass to the end and reports it, or null when
     *   it gave up.
     */
    fun scripted(
      spec: ThrowSpec,
      throwOnce: (ThrowSpec) -> SimulationOutcome?,
    ): SimulationOutcome? {
      val passes = Passes(spec)
      var pass: ThrowSpec? = spec
      var left = MOST_UNWATCHED
      while (pass != null && left > 0) {
        left--
        // A pass that gave up leaves the throw in the air, which is what
        // "not complete" is below.
        val landed = throwOnce(pass) ?: break
        passes.landed(landed)
        pass = if (passes.complete) null else passes.next()
      }
      return if (passes.complete) passes.outcome() else null
    }
  }
}
