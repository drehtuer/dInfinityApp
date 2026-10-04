package de.drehtuer.dinfinity.feature.settings

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The menu (`design/dInfinity.dc.html`, option 1q).
 *
 * It is the screen that connects the navigation graph, so what it has to get
 * right is small and total: every row is there, every row opens the screen it
 * names, and every row says what that screen is *for*.
 */
@RunWith(RobolectricTestRunner::class)
class MenuScreenTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `every section and every row is listed`() {
    show()

    compose.onNodeWithTag(MenuTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithText("PLAY").assertIsDisplayed()
    compose.onNodeWithTag(MenuTestTags.entryOf("roll")).assertIsDisplayed()
    compose.onNodeWithTag(MenuTestTags.entryOf("settings")).performScrollTo().assertIsDisplayed()
  }

  /**
   * A section name is small, capitalised and in the accent — three ways of
   * saying "heading" that a screen reader gets none of. Marked as one, the
   * menu can be jumped through by section instead of swiped through row by row
   * (`docs/architecture.md`, "Accessibility").
   */
  @Test
  fun `a section name is a heading, not a small red row`() {
    show()

    compose.onNodeWithText("PLAY").assert(isHeading())
  }

  @Test
  fun `a row says what its screen is for, not only its name`() {
    // A menu of ten names is a quiz.
    show()

    compose.onNodeWithTag(MenuTestTags.entryOf("roll")).assertTextContains("The tray.", substring = true)
  }

  @Test
  fun `choosing a row opens that screen and no other`() {
    val opened = mutableListOf<String>()
    show(onOpen = opened::add)

    compose.onNodeWithTag(MenuTestTags.entryOf("settings")).performScrollTo().performClick()

    assertEquals(listOf("settings"), opened)
  }

  @Test
  fun `it says what the app does with the network, which is almost nothing`() {
    show()

    compose.onNodeWithText("Works fully offline.", substring = true).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `the way in is labelled for somebody who cannot see three lines`() {
    val opened = mutableListOf<Unit>()
    compose.setContent { MenuButton(onOpen = { opened += Unit }) }

    compose.onNodeWithTag(MenuTestTags.BUTTON).assertIsDisplayed()
    compose.onNodeWithContentDescription("Menu").performClick()

    assertEquals(1, opened.size)
  }

  /**
   * The three lines are drawn rather than fetched — the app ships no icon set —
   * so the drawing is the one thing that can be wrong without any word on the
   * screen changing. Read down the middle of the glyph: three separate runs
   * of ink, which is what makes it the menu rather than a bar or a box.
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `the way in is drawn as three lines`() {
    compose.setContent { MenuButton(onOpen = {}) }

    val drawn = compose.onNodeWithTag(MenuTestTags.BUTTON).captureToImage().toPixelMap()
    // The button's corner is outside the 22 dp glyph, so it is the ground.
    val ground = drawn[0, 0]
    val middle = drawn.width / 2
    val inked = (0 until drawn.height).map { y -> drawn[middle, y] != ground }
    val runs = inked.indices.count { y -> inked[y] && (y == 0 || !inked[y - 1]) }
    assertEquals("the menu glyph is not three lines", 3, runs)
  }

  private fun show(
    onOpen: (String) -> Unit = {},
    header: MenuHeader? = null,
  ) {
    compose.setContent {
      MenuScreen(
        header = header,
        sections =
          listOf(
            MenuSection(
              name = "Play",
              entries = listOf(entry("roll", "Roll", "The tray. Shake it, or pick dice.", onOpen)),
            ),
            MenuSection(
              name = "App",
              entries = listOf(entry("settings", "Settings", "Appearance, power saving.", onOpen)),
            ),
          ),
      )
    }
  }

  @Test
  fun `with no header the menu is the sections and nothing else`() {
    // A header is something the application supplies; a menu that invented one
    // would be a menu with the app's name hard-coded into a feature module.
    show()

    compose.onNodeWithTag(MenuTestTags.HEADER).assertIsNotDisplayed()
  }

  @Test
  fun `the header names the app`() {
    show(header = MenuHeader(appName = "dInfinity"))

    compose.onNodeWithTag(MenuTestTags.HEADER).assertTextContains("dInfinity", substring = true)
    compose.onNodeWithTag(MenuTestTags.SESSION, useUnmergedTree = true).assertIsNotDisplayed()
  }

  @Test
  fun `the header says which session the rolls are going into`() {
    show(header = MenuHeader(appName = "dInfinity", session = "Tuesday campaign"))

    compose
      .onNodeWithTag(MenuTestTags.SESSION, useUnmergedTree = true)
      .assertTextContains("Tuesday campaign", substring = true)
  }

  private fun entry(
    id: String,
    title: String,
    description: String,
    onOpen: (String) -> Unit,
  ) = MenuEntry(id = id, title = title, description = description, open = { onOpen(id) })
}
