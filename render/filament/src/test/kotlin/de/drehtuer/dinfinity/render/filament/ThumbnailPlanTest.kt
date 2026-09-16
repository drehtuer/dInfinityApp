package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * What a picture of a table is a picture of — every bit of it decided here and
 * therefore asked about here (`docs/architecture.md`, decisions 40 and 47).
 *
 * A device can only say a frame was drawn. Whether the tray is the shape of
 * the box the picture goes in, whether the camera is on the table at all,
 * whether the die is standing on the floor rather than sunk into it or
 * floating above it, and whether it is showing its best face are all
 * arithmetic — and all of them look like "the thumbnail is a bit odd" on a
 * phone.
 */
class ThumbnailPlanTest {
  private val plan = ThumbnailPlan(widthPx = 156, heightPx = 234)
  private val look = TableLook(id = "plain", name = "Plain")
  private val d20 = ThumbnailDie(StandardDice.d20, setId = "builtin")

  @Test
  fun `a picture with no pixels is not a picture`() {
    assertThrows(IllegalArgumentException::class.java) { ThumbnailPlan(widthPx = 0, heightPx = 10) }
    assertThrows(IllegalArgumentException::class.java) { ThumbnailPlan(widthPx = 10, heightPx = 0) }
    assertThrows(IllegalArgumentException::class.java) { ThumbnailPlan.of(widthPx = -1, heightPx = 10) }
  }

  @Test
  fun `a box that fits is asked for exactly`() {
    assertEquals(ThumbnailPlan(156, 234), ThumbnailPlan.of(156, 234))
  }

  @Test
  fun `a box at the limit is still asked for exactly`() {
    val at = ThumbnailPlan.of(ThumbnailPlan.MAX_SIDE, ThumbnailPlan.MAX_SIDE)

    assertEquals(ThumbnailPlan.MAX_SIDE, at.widthPx)
    assertEquals(ThumbnailPlan.MAX_SIDE, at.heightPx)
  }

  @Test
  fun `a box past the limit is brought under it with its shape kept`() {
    val huge = ThumbnailPlan.of(widthPx = 520, heightPx = 780)

    assertEquals(ThumbnailPlan.MAX_SIDE, huge.heightPx)
    assertTrue("a ${huge.widthPx} by ${huge.heightPx} picture is not the shape asked for", huge.widthPx == 171)
  }

  @Test
  fun `a box past the limit the other way round is brought under it too`() {
    val wide = ThumbnailPlan.of(widthPx = 1000, heightPx = 100)

    assertEquals(ThumbnailPlan.MAX_SIDE, wide.widthPx)
    assertEquals(26, wide.heightPx)
  }

  @Test
  fun `shrinking never leaves a side with no pixels in it`() {
    // A sliver: a straight scale would round the short side to nought, and a
    // plan with no pixels cannot be drawn into.
    val sliver = ThumbnailPlan.of(widthPx = 4000, heightPx = 3)

    assertEquals(1, sliver.heightPx)
  }

  @Test
  fun `the tray is the shape of the picture, because a table is its screen`() {
    // `docs/tables.md`: the long side is always 240 mm and the short one
    // follows the shape of what it is drawn on.
    assertEquals(TableGeometry.LONG_SIDE_MM, plan.geometry.longSideMm, TOLERANCE)
    assertEquals(TableGeometry.LONG_SIDE_MM * plan.aspectRatio, plan.geometry.shortSideMm, TOLERANCE)
  }

  @Test
  fun `a picture shaped like nothing a phone is still gets a tray inside the limits`() {
    val square = ThumbnailPlan(widthPx = 100, heightPx = 100)

    val ratio = square.geometry.shortSideMm / square.geometry.longSideMm
    assertTrue("a tray of $ratio is outside what a table may be", ratio in TableGeometry.ASPECT_RANGE)
  }

  @Test
  fun `the camera is as close as a player may ever get`() {
    assertEquals(TrayView.CLOSEST, plan.view.zoom, TOLERANCE)
  }

  @Test
  fun `and it is in the far corner, as far as the tray allows and no further`() {
    val view = plan.view

    // The rule for how much room a zoom earns is TrayView's; what is asserted
    // here is that the plan asked for the corner and was given the edge of it
    // rather than somewhere off the table.
    assertEquals(view, view.within(plan.geometry))
    assertTrue("the view has not moved along the tray", view.panAlongMm > 0.0)
    assertTrue("the view has not moved across the tray", view.panAcrossMm > 0.0)
  }

  @Test
  fun `the spec is one die on this tray, and nothing that could make it a roll`() {
    val spec = plan.spec(look, d20)

    assertEquals(1, spec.dice.size)
    assertEquals(StandardDice.d20, spec.dice.single().die)
    assertEquals("builtin", spec.dice.single().setId)
    assertEquals(plan.geometry, spec.geometry)
    assertEquals(look, spec.table)
    assertTrue("a picture of a table was given a shake", spec.shake.isEmpty())
    assertTrue("a picture of a table was given dice already down", spec.among.isEmpty())
    assertEquals(1.0, spec.dieScale, TOLERANCE)
  }

  @Test
  fun `the same look is always the same spec, because nothing about it is random`() {
    assertEquals(plan.spec(look, d20), plan.spec(look, d20))
  }

  @Test
  fun `the die stands in the middle of what the camera can see`() {
    val frame = plan.standing(d20)
    val body = frame.current.single()

    assertEquals(plan.view.panAlongMm, body.position.x, TOLERANCE)
    assertEquals(plan.view.panAcrossMm, body.position.y, TOLERANCE)
  }

  @Test
  fun `and it is not moving, which is what a picture of one means`() {
    val frame = plan.standing(d20)

    assertEquals(frame.current, frame.previous)
  }

  @Test
  fun `the die sits on the floor rather than in it or above it`() {
    val body = plan.standing(d20).current.single()
    val corners = ShapeGeometry.hullOf(StandardDice.d20).map { body.orientation.rotate(it) + body.position }

    assertEquals("the lowest corner is not touching the floor", 0.0, corners.minOf { it.z }, TOLERANCE)
    assertTrue("part of the die is under the floor", corners.all { it.z >= -TOLERANCE })
  }

  @Test
  fun `every catalogue shape sits on the floor the same way`() {
    // The resting height is solved from the turned hull rather than tabulated
    // per shape, so a shape added to the catalogue is covered by construction
    // — which is only true if it is true of all of them.
    DieShape.entries.forEach { shape ->
      val die = Die.standard(id = shape.id, shape = shape)
      val turn = ThumbnailPlan.bestFaceUp(die)
      val lowest = ShapeGeometry.hullOf(die).minOf { turn.rotate(it).z }

      assertEquals(shape.id, -lowest, ThumbnailPlan.restingHeightMm(die, turn), TOLERANCE)
      assertTrue("${shape.id} sits below the floor", ThumbnailPlan.restingHeightMm(die, turn) > 0.0)
    }
  }

  @Test
  fun `a bigger die sits higher, in proportion to how big it is`() {
    val big = StandardDice.d20.let { it.copy(material = it.material.copy(sizeMm = it.material.sizeMm * 2)) }
    val turn = ThumbnailPlan.bestFaceUp(StandardDice.d20)

    assertEquals(
      ThumbnailPlan.restingHeightMm(StandardDice.d20, turn) * 2,
      ThumbnailPlan.restingHeightMm(big, turn),
      TOLERANCE,
    )
  }

  @Test
  fun `the face that is up is the best one, not the first one`() {
    val turn = ThumbnailPlan.bestFaceUp(StandardDice.d20)
    val best = StandardDice.d20.faces.indexOfFirst { it.value == StandardDice.d20.maxValue }
    val up = turn.rotate(ShapeGeometry.directionsOf(StandardDice.d20.shape)[best])

    assertEquals("the best face is not the one pointing up", 1.0, up dot Vector3.Up, TOLERANCE)
  }

  @Test
  fun `a die numbered some other way gets its own best face up, not the twentieth`() {
    // A d20 running 0..19: the `19` is face 19, and a rule counting from the
    // face *count* rather than from the values would put a nought up.
    val zeroBased =
      StandardDice.d20.copy(
        faces = List(DieShape.Icosahedron.faceCount) { Face.labelled(index = it, value = it) },
      )
    val turn = ThumbnailPlan.bestFaceUp(zeroBased)
    val up = turn.rotate(ShapeGeometry.directionsOf(zeroBased.shape)[zeroBased.faces.indexOfFirst { it.value == 19 }])

    assertEquals(1.0, up dot Vector3.Up, TOLERANCE)
  }

  @Test
  fun `a die whose best face is already up is not turned at all`() {
    // Face 0 is the face that is up in the reference orientation, so a die
    // whose highest value is on it needs no turn — and the shortest rotation
    // taking a direction to itself has to be the identity rather than a
    // half-turn about something arbitrary.
    val faces = DieShape.Icosahedron.faceCount
    val topFirst =
      StandardDice.d20.copy(faces = List(faces) { Face.labelled(index = it, value = faces - it) })

    val turn = ThumbnailPlan.bestFaceUp(topFirst)

    assertTrue("a die needing no turn was turned: $turn", abs(abs(turn.w) - 1.0) < TOLERANCE)
  }

  @Test
  fun `the bundled package's d20 is the one a thumbnail stands on the table`() {
    val chosen = ThumbnailDie.from(listOf(other, builtin))

    assertNotNull(chosen)
    assertEquals(DiceSet.BUILTIN_ID, chosen!!.setId)
    assertEquals(ThumbnailDie.FACES, chosen.die.shape.faceCount)
  }

  @Test
  fun `another package's d20 will do when the bundled one has gone`() {
    val chosen = ThumbnailDie.from(listOf(other))

    assertEquals("brass", chosen?.setId)
  }

  @Test
  fun `nothing else stands in for a d20`() {
    val noTwenties = builtin.copy(dice = builtin.dice.filter { it.shape.faceCount != ThumbnailDie.FACES })

    assertNull(ThumbnailDie.from(listOf(noTwenties)))
  }

  @Test
  fun `with nothing installed there is no die to draw`() {
    assertNull(ThumbnailDie.from(emptyList()))
  }

  @Test
  fun `a whole picture's worth of dice would still be one die`() {
    // The spec is built by hand rather than by the capacity rule, so this says
    // out loud that it is inside what a throw may be — a plan that produced an
    // illegal ThrowSpec would fail at the first thumbnail on a phone.
    assertTrue(plan.spec(look, d20).dice.size <= TableCapacity.MAX_DICE)
  }

  private val builtin =
    DiceSet(
      id = DiceSet.BUILTIN_ID,
      name = "Built-in",
      version = "1.0",
      dice = listOf(StandardDice.d6, StandardDice.d20),
    )

  private val other =
    DiceSet(
      id = "brass",
      name = "Brass & Bone",
      version = "1.0",
      dice = listOf(StandardDice.d20.copy(id = "bone-d20")),
    )

  private companion object {
    const val TOLERANCE = 1e-9
  }
}
