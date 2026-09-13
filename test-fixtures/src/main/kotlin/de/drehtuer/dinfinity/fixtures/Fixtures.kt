package de.drehtuer.dinfinity.fixtures

import java.util.Locale

/**
 * Test data shared by every module's tests, loaded from this module's
 * resources so a fixture is written once and used everywhere.
 *
 * The dice-set fixtures are named for what they prove: `valid-*` must install,
 * `invalid-*` must be rejected with the error `docs/dice-sets.md` names.
 */
object Fixtures {
  /** A dice set that must pass validation. */
  val validDiceSets: List<String> = listOf("valid-minimal.toml")

  /** Dice sets that must be rejected, each for a different documented reason. */
  val invalidDiceSets: List<String> =
    listOf(
      "invalid-unknown-shape.toml",
      "invalid-mesh-not-in-v1.toml",
      "invalid-face-count.toml",
      "invalid-duplicate-die-id.toml",
      "invalid-escaping-path.toml",
    )

  /** Where the golden cases live, named once so the recorder can quote it. */
  const val GOLDEN_CASES: String = "fixtures/golden/cases.tsv"

  val collections: List<String> = listOf("thorin.json", "invalid-duplicate-group.json")

  fun diceSet(name: String): String = read("fixtures/dicesets/$name")

  fun collection(name: String): String = read("fixtures/collections/$name")

  /** The cases the golden determinism suite runs, in file order. */
  fun goldenCases(): List<GoldenCase> =
    read(GOLDEN_CASES)
      .lineSequence()
      .map(String::trim)
      .filter { it.isNotEmpty() && !it.startsWith("#") }
      .map(::goldenCase)
      .toList()

  /**
   * One line of `cases.tsv`.
   *
   * Three columns is a case whose outcome has not been recorded yet; the suite
   * fails on those rather than skipping them, so an emptied fixture is loud.
   */
  private fun goldenCase(line: String): GoldenCase {
    val columns = line.split("\t")
    require(columns.size == INPUT_COLUMNS || columns.size == ALL_COLUMNS) {
      "a golden case has $INPUT_COLUMNS columns unrecorded and $ALL_COLUMNS recorded, not ${columns.size}: '$line'"
    }
    return GoldenCase(
      seed = columns[0].trim().toLong(),
      formula = columns[1].trim(),
      input = GoldenInput.of(columns[2].trim()),
      expected =
        if (columns.size == INPUT_COLUMNS) {
          null
        } else {
          GoldenOutcome(
            scale = columns[3].trim().toDouble(),
            spawn = columns[4].trim(),
            faces = columns[5].trim().split(",").map(String::toInt),
            steps = columns[6].trim().toInt(),
            corrections = columns[7].trim().toInt(),
            rethrows = columns[8].trim().toInt(),
          )
        },
    )
  }

  /**
   * [case] as the line `cases.tsv` carries, which is how a recording run hands
   * its results back to be pasted in (`docs/build-setup.md`).
   */
  fun goldenLine(
    case: GoldenCase,
    outcome: GoldenOutcome,
  ): String =
    listOf(
      case.seed.toString(),
      case.formula,
      case.input.id,
      String.format(Locale.ROOT, SCALE_FORMAT, outcome.scale),
      outcome.spawn,
      outcome.faces.joinToString(","),
      outcome.steps.toString(),
      outcome.corrections.toString(),
      outcome.rethrows.toString(),
    ).joinToString("\t")

  internal fun read(path: String): String =
    requireNotNull(Fixtures::class.java.classLoader?.getResourceAsStream(path)) {
      "Fixture $path is missing from test-fixtures resources"
    }.use { it.readBytes().decodeToString() }

  /** A case whose outcome has not been recorded carries only its input. */
  private const val INPUT_COLUMNS = 3

  /** And a recorded one carries the six columns of what it came to as well. */
  private const val ALL_COLUMNS = 9

  /** Scale to three places, in a fixed locale: a fixture is not written in German. */
  private const val SCALE_FORMAT = "%.3f"
}
