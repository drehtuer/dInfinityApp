package de.drehtuer.dinfinity.fixtures

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

    val collections: List<String> = listOf("thorin.json", "invalid-duplicate-group.json")

    fun diceSet(name: String): String = read("fixtures/dicesets/$name")

    fun collection(name: String): String = read("fixtures/collections/$name")

    /** The (seed, formula) pairs the golden determinism suite runs. */
    fun goldenCases(): List<GoldenCase> =
        read("fixtures/golden/cases.tsv")
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val columns = line.split("\t")
                GoldenCase(seed = columns[0].trim().toLong(), formula = columns[1].trim())
            }.toList()

    private fun read(path: String): String =
        requireNotNull(Fixtures::class.java.classLoader?.getResourceAsStream(path)) {
            "Fixture $path is missing from test-fixtures resources"
        }.use { it.readBytes().decodeToString() }
}

/** One case of the golden determinism suite. */
data class GoldenCase(
    val seed: Long,
    val formula: String,
)
