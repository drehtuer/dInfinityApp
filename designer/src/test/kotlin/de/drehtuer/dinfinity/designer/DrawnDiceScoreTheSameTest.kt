package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import de.drehtuer.dinfinity.simulation.api.FaceNumbering
import de.drehtuer.dinfinity.simulation.api.FaceReader
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Reading
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

/**
 * A die somebody drew scores exactly what the die they drew on scores.
 *
 * **This is the fairness question, asked of the one path that could get it
 * wrong**, and it is asked here because a device session asked it: "the rolls
 * from the dice designer don't feel balanced — could the faces be assigned
 * only after the roll?". The honest answer is arithmetic rather than
 * reassurance, so it is written down as tests.
 *
 * What a die scores is decided in two places and nowhere else.
 * `FaceReader.read` picks the *position* whose outward direction is nearest
 * up, which is geometry and knows nothing about any die; the roll then reads
 * `die.faces[index]`. So the whole of "which number did this throw make" is
 * the `faces` array, in the catalogue's face order — and the worry is
 * therefore exactly whether a generated set's array is the base die's array,
 * in the base die's order, whole.
 *
 * Nothing between a drawing and a package is allowed to touch it. A [Draft]
 * copies the die and stores marks against *cells*; [MinePackage] writes
 * `faces = die.faces` across; [DiceSetToml] writes the values in order; and
 * the reader on the far side numbers them by position. Each of those is one
 * line, and each of them is one line that could have been a subtle bias — a
 * sort, a `toSet`, a `filter` over the drawn faces — so the round trip is
 * asserted end to end through the real validator rather than at any one of
 * them.
 *
 * It is the **whole** round trip on purpose: the interesting failure is not a
 * function returning the wrong thing, it is a file that reads back as a
 * different die.
 */
class DrawnDiceScoreTheSameTest {
  @Test
  fun `a drawn die carries the base die's faces, value for value and in order`() {
    BuiltinDiceSet.set.dice.forEach { base ->
      val drawn = exported(Drawings.drawn(base, 0))
      assertEquals("${base.id} lost faces", base.faces.size, drawn.faces.size)
      assertEquals(
        "${base.id} was renumbered on the way through the exporter",
        base.faces.map { it.value },
        drawn.faces.map { it.value },
      )
      // And the *index* is the position in the array, which is what
      // `FaceReader`'s answer is looked up by.
      assertEquals(base.faces.indices.toList(), drawn.faces.map { it.index })
    }
  }

  @Test
  fun `and its labels, so the face somebody reads is the face that scored`() {
    // `labels` is written out only when a face says something its value
    // cannot, so this is also the check that leaving the line out does not
    // quietly change what a fudge die or a tens d10 prints.
    BuiltinDiceSet.set.dice.forEach { base ->
      val drawn = exported(Drawings.drawn(base, 0))
      assertEquals(base.id, base.faces.map { it.label }, drawn.faces.map { it.label })
    }
  }

  @Test
  fun `a die drawn on one face still carries all of them`() {
    // The one shape of bug the worry names: an array built from the faces
    // that were *drawn* rather than from the die. A d20 with one drawn cell
    // would then be a d1, or a d20 whose nineteen blank faces had slid up by
    // one — which is a die that scores wrongly and looks fine.
    val base = BuiltinDiceSet.set.die("d20") ?: error("the bundled set has no d20")
    val drawn = exported(Drawings.drawn(base, 7))

    assertEquals(base.faces.size, drawn.faces.size)
    assertEquals(base.faces.map { it.value }, drawn.faces.map { it.value })
    // The atlas really does have one cell and nineteen holes, so the test is
    // about a partly drawn die rather than about a fully drawn one.
    assertEquals(1, Atlas.plan(Drawings.drawn(base, 7))?.cells?.size)
  }

  @Test
  fun `the opposite-face pairing survives the export`() {
    // The bundled set is numbered the way a moulded die is — opposite faces
    // adding up — and `FaceNumbering` is the app's account of that
    // (`docs/dice-sets.md`, "Numbering"). A generated set inherits the
    // numbering rather than re-deriving it, so the way to say "the same rule
    // was applied" is that the array is still the one `FaceNumbering` would
    // lay out for those values.
    BuiltinDiceSet.set.dice.forEach { base ->
      val drawn = exported(Drawings.drawn(base, 0))
      assertEquals(
        "${base.id} no longer pairs opposite faces",
        FaceNumbering.paired(base.shape, base.faces.map { it.value }),
        drawn.faces.map { it.value },
      )
    }
  }

  @Test
  fun `and a die that is not paired is not quietly paired for it`() {
    // The other half of the same promise. The exporter carries the array
    // across; it does not *improve* it. A set whose author numbered a d6
    // 1..6 down the face order gets that back, opposite faces summing to
    // anything they like — because re-numbering somebody's die on the way to
    // disk is the bug this pair of tests is really about.
    val d6 = BuiltinDiceSet.set.die("d6") ?: error("the bundled set has no d6")
    val inOrder = d6.copy(faces = d6.faces.mapIndexed { index, face -> face.copy(value = index + 1) })

    val drawn = exported(Drawings.drawn(inOrder, 0))

    assertEquals((1..d6.faces.size).toList(), drawn.faces.map { it.value })
    assertNotEquals(FaceNumbering.plain(d6.shape), drawn.faces.map { it.value })
  }

  @Test
  fun `a settled die reads the same number drawn as it does plain`() {
    // The claim at its plainest, made where it is actually made: the same
    // geometry, turned the same way, put through the same reader. If a
    // drawing could change what a throw scores, one of two hundred turns of
    // one of these dice would say so.
    val turns = Random(20_26)
    BuiltinDiceSet.set.dice.forEach { base ->
      val drawn = exported(Drawings.drawn(base, 0))
      repeat(TURNS) {
        val orientation = someTurn(turns)
        assertEquals(
          "${base.id} read differently once it had been drawn on",
          valueOf(base, orientation),
          valueOf(drawn, orientation),
        )
      }
    }
  }

  @Test
  fun `every face of a drawn d20 is reachable, and each of them once`() {
    // A die whose faces array had been shuffled, doubled or truncated would
    // still read *something* for every turn. What it could not do is put all
    // twenty numbers up, one per face — so this is the check that the array
    // is a bijection onto the solid rather than merely the right length.
    val base = BuiltinDiceSet.set.die("d20") ?: error("the bundled set has no d20")
    val drawn = exported(Drawings.drawn(base, 0))
    val directions = ShapeGeometry.directionsOf(drawn.shape)

    val faceUp =
      directions.map { direction ->
        val reading = FaceReader.read(drawn, Quaternion.taking(direction, Vector3.Up))
        drawn.faces[(reading as Reading.Face).index].value
      }

    assertEquals(base.faces.map { it.value }.sorted(), faceUp.sorted())
    assertEquals(base.faces.size, faceUp.toSet().size)
  }

  /** The die the package comes back as, through the real validator and reader. */
  private fun exported(draft: Draft): Die {
    val files = MinePackage.of(listOf(draft), license = "CC0-1.0", author = null, painter = Drawings.headers())
    return when (val checked = DiceSetValidator.validate(PackageFiles.of(files))) {
      is ValidationResult.Rejected -> error("the exporter wrote a package it cannot read: ${checked.messages}")
      is ValidationResult.Valid ->
        checked.set.die(draft.die.id) ?: error("'${draft.die.id}' is not in the package it was drawn into")
    }
  }

  /** What [die] scores when it is turned by [orientation], or null when it is cocked. */
  private fun valueOf(
    die: Die,
    orientation: Quaternion,
  ): Int? = (FaceReader.read(die, orientation) as? Reading.Face)?.let { die.faces[it.index].value }

  /** Some turn or other, which is all a comparison of two dice needs. */
  private fun someTurn(random: Random): Quaternion =
    Quaternion.about(
      Vector3(
        random.nextDouble(-1.0, 1.0),
        random.nextDouble(-1.0, 1.0),
        random.nextDouble(-1.0, 1.0),
      ),
      random.nextDouble(0.0, 2 * PI),
    )

  private companion object {
    /** How many turns each die is read at. Enough that a shuffled array cannot hide. */
    const val TURNS = 200
  }
}
