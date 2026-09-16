package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `examples/`, the package a set author is told to copy
 * (`docs/dice-sets.md`, "Authoring tips"), held to the rules a downloaded one
 * is held to.
 *
 * It is worth a suite of its own for two reasons. It is the only *second*
 * hand-written package in the repository, so it tests the validator against
 * something other than its own fixtures — a real folder on a real disk, with
 * real PNGs, read through [PackageFiles.of] rather than out of a map. And an
 * example that has quietly stopped matching the format is worse than no
 * example at all, because it is a working starting point right up until
 * somebody tries to install it.
 *
 * The blank atlases are written by `tools/generate-atlases.py`, which repeats
 * the catalogue and the grid arithmetic in Python. This is what keeps the two
 * honest: the sizes are checked against the real [DieShape] and the real
 * [ShapeAtlas], so a shape added to the catalogue fails here until the
 * generator has been run again.
 */
class ExampleDiceSetTest {
  private val folder = File(repositoryRoot(), EXAMPLES)
  private val files = PackageFiles.of(folder)
  private val set: DiceSet = accepted().set

  @Test
  fun `the example package installs, with nothing to complain about`() {
    val result = DiceSetValidator.validate(files)
    assertTrue(result is ValidationResult.Valid, "examples/ was rejected:\n${report(result)}")
    // Warnings and not only errors: an example that installs with a grumble
    // teaches the grumble. Every one of them has a fix, and the fix is what
    // the file is supposed to be showing.
    assertEquals(emptyList(), result.warnings, "examples/ should not even warn")
  }

  @Test
  fun `it is named after the folder it lives in`() {
    // An install names the folder after the id the validator accepted, and a
    // folder whose id says otherwise is refused when it is read back. Copying
    // examples/ as it stands has to produce something installable.
    assertEquals(folder.name, set.id)
  }

  @Test
  fun `it uses every solid in the catalogue, which is the point of it`() {
    assertEquals(DieShape.entries.toSet(), set.dice.map(Die::shape).toSet())
  }

  @Test
  fun `it defines every die plain notation resolves, so nothing falls back`() {
    DiceSet.StandardDieIds.forEach { id -> assertNotNull(set.die(id), "examples/ has no $id") }
  }

  @Test
  fun `it shows a die that only the dice picker can reach`() {
    val rune = assertNotNull(set.die("rune-d6"), "the picker-only die is gone")
    assertTrue(rune.id !in DiceSet.StandardDieIds, "a picker-only die must not be a standard id")
    assertEquals("💀", rune.faces.first().label)
  }

  @Test
  fun `it ships table looks, because a package may carry those too`() {
    assertTrue(set.tables.isNotEmpty(), "examples/ shows no table look")
    set.tables.forEach { look ->
      assertEquals(look, look.clampedToLimits(), "${look.id} was clamped, so it was outside its range")
    }
  }

  @Test
  fun `there is a blank atlas for every catalogue shape`() {
    val expected = DieShape.entries.map { "${it.id}.png" }.toSet()
    val present =
      File(folder, TEXTURES)
        .listFiles()
        .orEmpty()
        .map(File::getName)
        .toSet()
    assertEquals(expected, present, "textures/ and the catalogue disagree; re-run tools/generate-atlases.py")
  }

  @Test
  fun `every atlas is the grid the app will slice it into`() {
    DieShape.entries.forEach { shape ->
      val grid = ShapeAtlas.gridFor(shape)
      val path = "$TEXTURES/${shape.id}.png"
      val size = assertNotNull(ImageHeader.sizeOf(assertNotNull(files.read(path), "$path is missing")), "$path")
      assertEquals(grid.columns * CELL_PIXELS, size.width, path)
      assertEquals(grid.rows * CELL_PIXELS, size.height, path)
      assertTrue(size.width <= DiceSetLimits.MAX_TEXTURE_PIXELS, "$path is wider than a texture may be")
      assertTrue(size.height <= DiceSetLimits.MAX_TEXTURE_PIXELS, "$path is taller than a texture may be")
    }
  }

  @Test
  fun `the whole package is comfortably inside the size limits`() {
    val textures = File(folder, TEXTURES).listFiles().orEmpty()
    textures.forEach { file ->
      assertTrue(file.length() <= DiceSetLimits.MAX_TEXTURE_BYTES, "${file.name} is over the per-file cap")
    }
    assertTrue(
      textures.sumOf(File::length) <= DiceSetLimits.MAX_PACKAGE_TEXTURE_BYTES,
      "examples/ carries more texture than a package may",
    )
    assertTrue(
      assertNotNull(files.size(DiceSetValidator.DICE_SET_FILE)) <= DiceSetLimits.MAX_TOML_BYTES,
      "the set file is over the cap",
    )
  }

  @Test
  fun `nothing in the folder is left over`() {
    // An atlas for a die somebody deleted is invisible: it installs, it counts
    // against the package's texture budget, and nothing ever draws it.
    val referenced = set.dice.mapNotNull(Die::texturePath).toSet() + setOf(DiceSetValidator.DICE_SET_FILE, "README.md")
    val present =
      folder
        .walkTopDown()
        .filter(File::isFile)
        .map { it.relativeTo(folder).invariantSeparatorsPath }
        .toSet()
    assertEquals(referenced, present, "examples/ holds a file nothing points at")
  }

  private fun accepted(): ValidationResult.Valid =
    when (val result = DiceSetValidator.validate(files)) {
      is ValidationResult.Valid -> result
      is ValidationResult.Rejected -> error("examples/ was rejected:\n${report(result)}")
    }

  private fun report(result: ValidationResult): String = result.messages.joinToString("\n") { "  $it" }

  private companion object {
    const val EXAMPLES = "examples"
    const val TEXTURES = "textures"

    /** What `tools/generate-atlases.py` gives one face, and the face designer too. */
    const val CELL_PIXELS = 256

    /**
     * The repository, found by climbing out of whatever directory the test
     * runner started in.
     *
     * The example is at the root rather than in a module's resources — it is
     * for people, not for the build — so a test that reads it has to say where
     * the root is. Climbing to the settings file is steadier than counting
     * `..`s, which would be wrong the moment this module moved.
     */
    fun repositoryRoot(): File =
      generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, EXAMPLES).isDirectory }
        ?: error("no repository root above ${File("").absolutePath}")
  }
}
