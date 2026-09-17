package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a `FilterChip` is filled and lettered with while it stands in for a
 * `.seg-opt` (`design/_ds/modernist-…/styles.css`).
 *
 * Two screens — the outcome graph and the saved-roll editor — still draw their
 * one-of-a-few choices as Material chips rather than as [SegmentedControl].
 * The chip's own defaults reach for `secondaryContainer` and
 * `onSecondaryContainer`, and until the theme filled those in they came out in
 * Material's baseline lavender, which the Modernist palette has no step of.
 *
 * **What these can and cannot say.** `SelectableChipColors` keeps every colour
 * private, so no test can read a fill back out of one; what is checkable is
 * that each helper is *not* the chip's own default — which is the bug they
 * exist to fix — and that the two variants are not each other, which is the
 * distinction between them. The colours themselves are the theme's, and
 * `InkTest` holds the theme to the palette.
 */
@RunWith(RobolectricTestRunner::class)
class ChipColoursTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `neither variant is the chip's own default`() {
    // The default is the whole problem: `FilterChipDefaults.filterChipColors()`
    // fills with `secondaryContainer`, which is a role that had nothing in it.
    var fallback: SelectableChipColors? = null
    var seg: SelectableChipColors? = null
    var ink: SelectableChipColors? = null
    compose.setContent {
      fallback = FilterChipDefaults.filterChipColors()
      seg = segColours()
      ink = inkColours()
    }

    compose.runOnIdle {
      assertNotEquals("a segmented option is still Material's own chip", fallback, seg)
      assertNotEquals("an inverting option is still Material's own chip", fallback, ink)
    }
  }

  @Test
  fun `an option whose content is already the accent does not also fill with it`() {
    // A red square behind a red mark would be the two saying the same thing
    // over each other (`design/dInfinityPhone.dc.html`, `iconOptions`), so the
    // inverting variant has to differ from the accent one.
    var seg: SelectableChipColors? = null
    var ink: SelectableChipColors? = null
    compose.setContent {
      seg = segColours()
      ink = inkColours()
    }

    compose.runOnIdle {
      assertNotEquals("the two variants are the same control", seg, ink)
    }
  }

  @Test
  fun `the control's own edge is a hairline of the divider`() {
    var divider: Color? = null
    var border: BorderStroke? = null
    compose.setContent {
      divider = MaterialTheme.colorScheme.outline
      border = segBorder()
    }

    compose.runOnIdle {
      val edge = requireNotNull(border)
      assertEquals("`.seg` is edged at 1 px, not at the 2 dp rule weight", Modernist.hairline, edge.width)
      assertEquals(SolidColor(requireNotNull(divider)), edge.brush)
    }
  }
}
