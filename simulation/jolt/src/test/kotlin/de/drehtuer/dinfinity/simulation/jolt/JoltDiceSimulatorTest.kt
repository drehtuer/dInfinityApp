package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The wiring between a [ThrowSpec] and the loop that runs it.
 *
 * The engine itself is on the other side of the JNI boundary and only a device
 * can say whether it settles dice. What can be said here is that the right
 * dice are spawned, at the right scale, with the hull the shape catalogue
 * computed — and that a hull from anywhere else would be a die whose printed
 * face and scored face disagree (`docs/architecture.md`, decision 35).
 */
class JoltDiceSimulatorTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun `every die in the throw is spawned, in throw order`() {
    val world = FakeWorld(3) { _, _, _ -> FakeWorld.settled() }
    val outcome = JoltDiceSimulator { world }.run(spec(listOf(StandardDice.d20, StandardDice.d6, StandardDice.d4)))

    assertEquals(3, world.spawned.size)
    assertEquals(3, outcome.faces.size)
    assertTrue("the world outlives no roll", world.closed)
  }

  @Test
  fun `a die is spawned with the hull the shape catalogue computed, at the throw's scale`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }
    JoltDiceSimulator { world }.run(spec(listOf(StandardDice.d20), scale = 0.6))

    assertEquals(
      "the solver's hull, the renderer's mesh and the face reading come from one place",
      ShapeGeometry.hullOf(StandardDice.d20, 0.6),
      world.hulls.single(),
    )
  }

  @Test
  fun `the capacity rule's scale reaches the dice`() {
    val spawnedAtFullSize = radiusSpawnedFor(scale = 1.0)
    val spawnedShrunk = radiusSpawnedFor(scale = 0.5)

    assertTrue(
      "a shrunk throw has to be laid out for shrunk dice",
      spawnedShrunk < spawnedAtFullSize,
    )
  }

  @Test
  fun `a mixed throw is laid out for its largest die`() {
    // A d4 and a d20 of the same nominal size are very different sizes: the
    // tetrahedron's corners are much closer in than the icosahedron's. Cells
    // sized for the d4 would have the d20 starting inside its neighbour.
    val small = FakeWorld(2) { _, _, _ -> FakeWorld.settled() }
    JoltDiceSimulator { small }.run(spec(listOf(StandardDice.d4, StandardDice.d20)))

    val large = FakeWorld(2) { _, _, _ -> FakeWorld.settled() }
    JoltDiceSimulator { large }.run(spec(listOf(StandardDice.d20, StandardDice.d20)))

    assertEquals(
      "the layout must not change when the smaller die is swapped for a second big one",
      large.spawned.map { it.position.z },
      small.spawned.map { it.position.z },
    )
  }

  @Test
  fun `a throw with no dice never opens a world`() {
    var opened = false
    val outcome =
      JoltDiceSimulator {
        opened = true
        null
      }.run(spec(emptyList()))

    assertEquals(0, outcome.diceCount)
    assertFalse("an empty throw is answered without an engine", opened)
  }

  @Test
  fun `a missing engine is a broken build, and says so`() {
    val failure =
      runCatching { JoltDiceSimulator { null }.run(spec(listOf(StandardDice.d6))) }.exceptionOrNull()

    assertTrue(failure is IllegalStateException)
    assertTrue(failure!!.message!!.contains("libdinfinity_jolt"))
  }

  private fun radiusSpawnedFor(scale: Double): Double {
    val world = FakeWorld(4) { _, _, _ -> FakeWorld.settled() }
    JoltDiceSimulator { world }.run(spec(List(4) { StandardDice.d20 }, scale))
    return world.spawned.maxOf { abs(it.position.z) }
  }

  private fun spec(
    dice: List<Die>,
    scale: Double = 1.0,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        dice.mapIndexed { index, die ->
          DieInstance(
            index = index,
            groupId = 0,
            setId = "builtin",
            requestedSetId = "builtin",
            die = die,
          )
        },
      geometry = geometry,
      table = table,
      seed = 17L,
      dieScale = scale,
    )
}
