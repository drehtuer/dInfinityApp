package de.drehtuer.dinfinity.render.headless

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeadlessRendererTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")
  private val renderer = HeadlessRenderer()

  @Test
  fun `a fresh renderer has been shown nothing`() {
    assertFalse(renderer.running)
    assertFalse(renderer.finished)
    assertEquals(0, renderer.framesShown)
    assertNull(renderer.lastFrame)
  }

  @Test
  fun `a throw begins and ends`() {
    renderer.begin(spec(), geometry, look)
    assertTrue(renderer.running)
    renderer.end()
    assertFalse(renderer.running)
  }

  @Test
  fun `power saving draws no frames at all, and still finishes`() {
    renderer.begin(spec(), geometry, look)
    renderer.settled(frame())
    renderer.end()
    assertEquals(0, renderer.framesShown)
    assertTrue(renderer.finished)
    assertEquals(frame(), renderer.lastFrame)
  }

  @Test
  fun `frames are counted when there are any`() {
    renderer.begin(spec(), geometry, look)
    repeat(5) { renderer.show(frame()) }
    assertEquals(5, renderer.framesShown)
  }

  @Test
  fun `beginning again forgets the roll before`() {
    renderer.begin(spec(), geometry, look)
    renderer.show(frame())
    renderer.settled(frame())
    renderer.begin(spec(), geometry, look)
    assertEquals(0, renderer.framesShown)
    assertFalse(renderer.finished)
    assertNull(renderer.lastFrame)
  }

  @Test
  fun `ending without beginning is not an error`() {
    renderer.end()
    assertFalse(renderer.running)
  }

  @Test
  fun `a frame says where every die is and how far between steps it is`() {
    val transform = BodyTransform(index = 2, position = Vector3(1.0, 2.0, 3.0), orientation = Quaternion.Identity)
    val frame = RenderFrame(previous = listOf(transform), current = listOf(transform), interpolation = 0.25)
    assertEquals(2, frame.current.single().index)
    assertEquals(Vector3(1.0, 2.0, 3.0), frame.current.single().position)
    assertEquals(Quaternion.Identity, frame.current.single().orientation)
    assertEquals(0.25, frame.interpolation)
  }

  @Test
  fun `a frame is a whole step by default, which is what a settled roll is`() {
    assertEquals(1.0, RenderFrame.still(emptyList()).interpolation)
  }

  @Test
  fun `a frame blends a die from where it was to where it is`() {
    // A display does not run at 120 Hz, so the moment being drawn is usually
    // between two simulation steps rather than on one.
    val frame =
      RenderFrame(
        previous = listOf(at(Vector3(0.0, 0.0, 0.0), Quaternion.Identity)),
        current = listOf(at(Vector3(4.0, 0.0, 8.0), Quaternion.about(Vector3.Up, PI / 2))),
        interpolation = 0.5,
      )

    val drawn = frame.blended().single()

    assertEquals(Vector3(2.0, 0.0, 4.0), drawn.position)
    assertEquals(1.0, abs(drawn.orientation dot Quaternion.about(Vector3.Up, PI / 4)), 1e-9)
  }

  @Test
  fun `a die that is not moving is drawn where it is, whatever the moment`() {
    val still = RenderFrame.still(listOf(at(Vector3(1.0, 2.0, 3.0), Quaternion.Identity)))

    assertEquals(still.current, still.blended())
  }

  @Test
  fun `a frame cannot describe a step that lost or gained a die`() {
    assertFailsWith<IllegalArgumentException> {
      RenderFrame(previous = emptyList(), current = listOf(at(Vector3.Zero, Quaternion.Identity)))
    }
  }

  @Test
  fun `a frame cannot sit outside the step it is between`() {
    assertFailsWith<IllegalArgumentException> { RenderFrame(emptyList(), emptyList(), interpolation = 1.5) }
    assertFailsWith<IllegalArgumentException> { RenderFrame(emptyList(), emptyList(), interpolation = -0.1) }
  }

  private fun at(
    position: Vector3,
    orientation: Quaternion,
  ): BodyTransform = BodyTransform(index = 0, position = position, orientation = orientation)

  @Test
  fun `a renderer is shown a roll and can do nothing to it`() {
    // The interface returns nothing anywhere, so a renderer has no way to
    // affect the simulation. This test exists to fail loudly if that changes.
    val methods = Renderer::class.java.declaredMethods
    assertTrue(
      methods.all { it.returnType == Void.TYPE },
      "a renderer must not hand anything back to the simulation: ${methods.map { it.name to it.returnType }}",
    )
  }

  private fun spec(): ThrowSpec =
    ThrowSpec(
      dice =
        listOf(
          DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = StandardDice.d20),
        ),
      geometry = geometry,
      table = look,
      seed = 1L,
    )

  private fun frame(): RenderFrame = RenderFrame.still(listOf(at(Vector3.Zero, Quaternion.Identity)))
}
