package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.model.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That a colour tag can always be seen (`docs/dice-notation.md`, "Saved
 * rolls").
 *
 * The measurable half of a design decision: `ink` at night and `bone` on paper
 * are each a mark that is simply not there, and the rule is that **every**
 * colour goes through the clamp — the twelve and a custom one alike.
 *
 * The grounds are the design system's own (`design/_ds`): paper `#F3F2F2` with
 * ink on it, and `#201E1D` at night with paper on it.
 */
class LegibleColourTest {
  private val paper = 0xFFF3F2F2.toInt()
  private val night = 0xFF201E1D.toInt()

  @Test
  fun `every tag is legible on paper`() {
    RollColour.entries.forEach { tag ->
      val drawn = LegibleColour.legibleOn(tag.argb, background = paper, towards = night)
      assertTrue(
        "${tag.name} cannot be seen on paper",
        Contrast.meets(drawn, paper, Contrast.COMPONENT),
      )
    }
  }

  @Test
  fun `every tag is legible at night`() {
    RollColour.entries.forEach { tag ->
      val drawn = LegibleColour.legibleOn(tag.argb, background = night, towards = paper)
      assertTrue(
        "${tag.name} cannot be seen at night",
        Contrast.meets(drawn, night, Contrast.COMPONENT),
      )
    }
  }

  @Test
  fun `a colour that can already be seen is drawn exactly as it was chosen`() {
    // The clamp moves a colour as little as it takes, and nothing at all when
    // it takes nothing: a tag somebody picked should look like the tag they
    // picked wherever it can.
    assertEquals(
      RollColour.Cobalt.argb,
      LegibleColour.legibleOn(RollColour.Cobalt.argb, background = paper, towards = night),
    )
  }

  @Test
  fun `a colour that cannot be seen is moved, and only then`() {
    // `bone` on paper is the case the clamp exists for.
    val drawn = LegibleColour.legibleOn(RollColour.Bone.argb, background = paper, towards = night)
    assertTrue("bone on paper was left as it was", drawn != RollColour.Bone.argb)
    assertTrue(Contrast.meets(drawn, paper, Contrast.COMPONENT))
  }

  @Test
  fun `ink at night is rescued the other way`() {
    val drawn = LegibleColour.legibleOn(RollColour.Ink.argb, background = night, towards = paper)
    assertTrue("ink at night was left as it was", drawn != RollColour.Ink.argb)
    assertTrue(Contrast.meets(drawn, night, Contrast.COMPONENT))
  }

  @Test
  fun `a custom colour goes through exactly the same clamp`() {
    // The pale yellow that made a free picker refusable in the first place
    // (`docs/architecture.md`, decision 22).
    val yellow = 0xFFFFF7B0.toInt()
    val drawn = LegibleColour.legibleOn(yellow, background = paper, towards = night)
    assertTrue("a pale yellow on paper stayed invisible", Contrast.meets(drawn, paper, Contrast.COMPONENT))
  }

  @Test
  fun `a colour the same as its ground still comes back as something readable`() {
    // The worst input there is: a mark the exact colour of the paper. It is
    // moved towards the ink until it can be seen, and the last step is the ink
    // itself — so there is no input for which this hands back a mark nobody
    // can see.
    val drawn = LegibleColour.legibleOn(paper, background = paper, towards = night)
    assertTrue("paper on paper stayed paper", Contrast.meets(drawn, paper, Contrast.COMPONENT))
  }

  @Test
  fun `a colour that cannot be rescued at all is drawn in the ink`() {
    // Nothing between a colour and a ground it is identical to, when the ink
    // it would be moved towards is that same colour: the fallback is what is
    // left, and it is the one colour the ground is guaranteed to show.
    assertEquals(paper, LegibleColour.legibleOn(paper, background = paper, towards = paper))
  }
}
