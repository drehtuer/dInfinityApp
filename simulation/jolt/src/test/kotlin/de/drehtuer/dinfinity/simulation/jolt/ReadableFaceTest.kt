package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.FaceReader
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Reading
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI

/**
 * The one rule both the count and the overlay's "waiting" read a die by: a
 * face, unless it is cocked or standing on another die.
 */
class ReadableFaceTest {
  private val d6 = StandardDice.d6

  @Test
  fun `a die flat on the felt shows the face the reader says`() {
    val flat = FakeWorld.settled()
    val expected = (FaceReader.read(d6, flat.orientation) as Reading.Face).index

    assertEquals(expected, readableFace(d6, flat))
  }

  @Test
  fun `a cocked die shows nothing`() {
    val cocked = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 4)

    assertNull(readableFace(d6, FakeWorld.settled(cocked)))
  }

  @Test
  fun `a die standing on another shows nothing, however square it sits`() {
    assertNull(readableFace(d6, FakeWorld.settled(supportedByDie = true)))
  }
}
