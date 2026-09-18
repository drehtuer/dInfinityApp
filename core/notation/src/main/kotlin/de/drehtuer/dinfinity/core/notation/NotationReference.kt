package de.drehtuer.dinfinity.core.notation

/**
 * The notation, written out for somebody to read
 * (`docs/dice-notation.md`; `docs/TODO.md`, 4.10).
 *
 * The same grammar `FormulaGrammar` implements, in sentences rather than in
 * recursive descent. It is **here, beside the parser**, rather than in the
 * screen that draws it, for one reason: a notation the app accepts and a
 * notation the app explains that have drifted apart are worse than no
 * explanation at all, and the only way to keep them together is to keep them
 * in the same module and test one against the other.
 *
 * Two things hold them there:
 *
 * - every [NotationEntry.example] is parsed by `NotationReferenceTest`, so an
 *   example that stopped being valid fails the build rather than a player's
 *   first attempt;
 * - the modifiers are described through an exhaustive `when` over
 *   [DiceModifier], so adding one to the grammar stops this file compiling
 *   until it is explained.
 *
 * It carries no Android types and no strings from resources. That is what lets
 * it be tested on the JVM against the parser, which is the whole point of it
 * being here; the screen adds the layout and nothing else
 * (`docs/architecture.md`).
 */
object NotationReference {
  /** Everything there is to say, in the order it is worth reading. */
  val sections: List<NotationSection> by lazy {
    listOf(
      NotationSection(
        title = "The basics",
        blurb = "A number of dice, the letter d, and how many sides they have.",
        entries =
          listOf(
            NotationEntry("d20", "One twenty-sided die.", "d20"),
            NotationEntry("3d6", "Three six-sided dice, added together.", "3d6"),
            NotationEntry("3d6 + 2", "Add or subtract a flat number.", "3d6 + 2"),
            NotationEntry("2 * (1d8 + 3)", "Brackets and × ÷ work the way they look.", "2 * (1d8 + 3)"),
            NotationEntry("d%", "Percentile: two d10 read as tens and units. Same as d100.", "d%"),
            NotationEntry("4dF", "Fudge dice, each −1, 0 or +1.", "4dF"),
          ),
      ),
      NotationSection(
        title = "Keeping and dropping",
        blurb = "Roll several, then count only some of them.",
        entries = MODIFIERS.filter { it.syntax.first() in "kd" },
      ),
      NotationSection(
        title = "Changing what a die scores",
        blurb = "The die is still thrown; what it is worth afterwards is what moves.",
        entries = MODIFIERS.filterNot { it.syntax.first() in "kd" },
      ),
      NotationSection(
        title = "Naming and borrowing",
        blurb = "What a roll is for, and whose dice it uses.",
        entries =
          listOf(
            NotationEntry(
              syntax = "[…]",
              meaning = "A label at the end. It changes nothing and shows up in the breakdown.",
              example = "1d6 + 1d4 [Fire]",
            ),
            NotationEntry(
              syntax = "set:",
              meaning = "Take the dice from an installed set instead of the default one.",
              example = "builtin:1d20",
            ),
          ),
      ),
    )
  }

  /** Every entry of every section, which is what a test walks. */
  val entries: List<NotationEntry> get() = sections.flatMap(NotationSection::entries)

  /**
   * What the notation will not do, and what happens when you ask
   * (`docs/dice-notation.md`, "Limits").
   *
   * The numbers come from [NotationLimits] rather than from this prose, so a
   * limit that moves moves here too.
   */
  val limits: List<NotationLimit> by lazy {
    listOf(
      NotationLimit(
        what = "Dice in one formula",
        value = "${NotationLimits.MAX_DICE_PER_FORMULA}",
        then = "Refused as you type. The odds graph is what this keeps cheap.",
      ),
      NotationLimit(
        what = "Dice in one throw",
        value = "what the table holds",
        then = "The formula is fine and the graph works; the tray says how many would fit.",
      ),
      NotationLimit(
        what = "Explosions in a row",
        value = "${NotationLimits.MAX_EXPLOSION_DEPTH}",
        then = "It stops there, and the breakdown says so.",
      ),
      NotationLimit(
        what = "Brackets inside brackets",
        value = "${NotationLimits.MAX_PARENTHESIS_DEPTH}",
        then = "Refused as you type.",
      ),
      NotationLimit(
        what = "Characters in a label",
        value = "${NotationLimits.MAX_LABEL_LENGTH}",
        then = "Refused as you type.",
      ),
    )
  }

  /**
   * Every modifier the grammar has, explained.
   *
   * Built by describing one of each rather than by writing a list of prose,
   * because [describe] is an exhaustive `when`: a modifier added to
   * [DiceModifier] stops this compiling until somebody says what it does.
   */
  private val MODIFIERS: List<NotationEntry> by lazy { EVERY_MODIFIER.map(::describe) }

  /**
   * One of each modifier, to be described.
   *
   * The numbers are the ones the examples use, so the description and its
   * example cannot disagree about what `n` was.
   */
  private val EVERY_MODIFIER: List<DiceModifier> =
    listOf(
      DiceModifier.KeepHighest(1, NOWHERE),
      DiceModifier.KeepLowest(1, NOWHERE),
      DiceModifier.DropHighest(1, NOWHERE),
      DiceModifier.DropLowest(1, NOWHERE),
      DiceModifier.Explode(NOWHERE),
      DiceModifier.Reroll(1, NOWHERE),
      DiceModifier.Minimum(2, NOWHERE),
    )

  /** What [modifier] does, and a formula that does it. */
  private fun describe(modifier: DiceModifier): NotationEntry =
    when (modifier) {
      is DiceModifier.KeepHighest ->
        NotationEntry(
          syntax = "kh${modifier.n}",
          meaning = "Roll them all, keep the ${modifier.n} highest. This is advantage.",
          example = "2d20kh${modifier.n}",
        )

      is DiceModifier.KeepLowest ->
        NotationEntry(
          syntax = "kl${modifier.n}",
          meaning = "Keep the ${modifier.n} lowest. This is disadvantage.",
          example = "2d20kl${modifier.n}",
        )

      is DiceModifier.DropHighest ->
        NotationEntry(
          syntax = "dh${modifier.n}",
          meaning = "Drop the ${modifier.n} highest and count the rest.",
          example = "4d6dh${modifier.n}",
        )

      is DiceModifier.DropLowest ->
        NotationEntry(
          syntax = "dl${modifier.n}",
          meaning = "Drop the ${modifier.n} lowest. This is how a stat line is rolled.",
          example = "4d6dl${modifier.n}",
        )

      is DiceModifier.Explode ->
        NotationEntry(
          syntax = "!",
          meaning = "A die on its highest face throws another of the same die, and that one can explode too.",
          example = "8d6!",
        )

      is DiceModifier.Reroll ->
        NotationEntry(
          syntax = "r${modifier.threshold}",
          meaning = "A die of ${modifier.threshold} or less is thrown once more. The second throw stands.",
          example = "4d6r${modifier.threshold}",
        )

      is DiceModifier.Minimum ->
        NotationEntry(
          syntax = "min${modifier.value}",
          meaning = "A die under ${modifier.value} counts as ${modifier.value}. It still shows the face it landed on.",
          example = "4d6min${modifier.value}",
        )
    }
}

/**
 * Where a modifier in the reference came from in a formula: nowhere.
 *
 * [DiceModifier.range] exists so an error message can underline the part of
 * the text that was wrong. These modifiers were never typed, so there is no
 * text to point at.
 */
private val NOWHERE = IntRange.EMPTY

/** A run of entries under one heading. */
data class NotationSection(
  val title: String,
  /** One line under the heading, saying what the run is about. */
  val blurb: String,
  val entries: List<NotationEntry>,
)

/**
 * One thing you can write, what it means, and a formula that uses it.
 *
 * [example] is a **rollable formula** rather than an illustration: the screen
 * puts it in the field, so it has to be something the parser accepts against
 * the bundled set, and a test parses every one of them.
 */
data class NotationEntry(
  val syntax: String,
  val meaning: String,
  val example: String,
)

/** A bound the notation keeps, and what it does when you reach it. */
data class NotationLimit(
  val what: String,
  val value: String,
  val then: String,
)
