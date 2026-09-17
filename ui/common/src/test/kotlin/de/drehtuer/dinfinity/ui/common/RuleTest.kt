package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two weights the Modernist system draws a line at.
 *
 * A rule is one number, and the number is the whole point: the system groups by
 * putting a heavier line between blocks than between the rows inside one, so a
 * rule that came out at the wrong weight would erase the grouping without
 * erasing anything a test could otherwise see.
 */
@RunWith(RobolectricTestRunner::class)
class RuleTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a block rule is 2 dp and a hairline is 1`() {
    compose.setContent {
      Column {
        Rule(modifier = Modifier.testTag("block"))
        Rule(modifier = Modifier.testTag("hairline"), weight = RuleWeight.Hairline)
      }
    }

    compose.onNodeWithTag("block").assertHeightIsEqualTo(2.dp)
    compose.onNodeWithTag("hairline").assertHeightIsEqualTo(1.dp)
  }

  @Test
  fun `the heavier weight is the default, because it is the one that groups`() {
    assertEquals(RuleWeight.Block, RuleWeight.entries.maxBy { it.thickness })
  }
}
