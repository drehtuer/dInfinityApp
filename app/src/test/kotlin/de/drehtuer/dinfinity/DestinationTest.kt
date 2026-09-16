package de.drehtuer.dinfinity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.navigation.MenuGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric because a destination's name and line are now resources rather
 * than literals: what has to be true is that each one *resolves*, and only a
 * real `Resources` can say so (`docs/architecture.md`, "Text a person reads").
 */
@RunWith(RobolectricTestRunner::class)
class DestinationTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `every screen in the plan has a destination`() {
    // Step 4 of docs/TODO.md lists ten screens and the menu lists all ten.
    // Saved-roll statistics is the eleventh row: it belongs to Step 4.7 rather
    // than being a step of its own, and the prototype's menu has it. Notation
    // is the twelfth and the last of the prototype's rows the app had no
    // screen for (`docs/TODO.md`, Step 4.10).
    assertEquals(12, Destination.inTheMenu.size)
    assertTrue(Destination.SavedRollStats in Destination.inTheMenu)
    assertTrue(Destination.Notation in Destination.inTheMenu)
  }

  @Test
  fun `the developer screen is listed only when the toggle is on`() {
    // Off on every install, so it is not one of the twelve above — and the
    // route exists either way, because a route that came and went would be a
    // back stack that could not be restored
    // (`docs/physics-and-rendering.md`, "Debug tooling").
    assertTrue(Destination.Developer.developerOnly)
    assertFalse(Destination.Developer in Destination.inTheMenu)
    assertTrue(Destination.Developer in Destination.inTheMenu(developerTools = true))
    assertEquals(Destination.inTheMenu.size + 1, Destination.inTheMenu(developerTools = true).size)
    // And it is the only screen behind the toggle: the rest of the app is
    // exactly what it was.
    assertEquals(listOf(Destination.Developer), Destination.entries.filter { it.developerOnly })
  }

  @Test
  fun `a screen that is not in the menu is one you arrive at from somewhere`() {
    // The menu, which is the list; the saved-roll editor, which is about one
    // roll and is reached from that roll; and importing a collection, which is
    // about saved rolls and would be a menu row that means nothing until
    // somebody has a file.
    assertEquals(
      listOf(Destination.Menu, Destination.SavedRollEditor, Destination.CollectionImport, Destination.SetDetail),
      Destination.entries.filter { it.group == null },
    )
  }

  @Test
  fun `every screen the menu lists says what it is for`() {
    // A menu of ten names is a quiz. "Sessions" means nothing until it does.
    Destination.inTheMenu.forEach { destination ->
      val description = destination.description
      assertNotNull("${destination.route} has no description", description)
      assertTrue(
        "${destination.route}'s description resolves to nothing",
        context.getString(description!!).isNotBlank(),
      )
    }
  }

  @Test
  fun `a screen the menu does not list has no line under its name`() {
    // Null rather than a blank resource: a translator asked to translate an
    // empty string is a translator asked a question with no answer.
    Destination.entries.filter { it.group == null }.forEach { destination ->
      assertNull("${destination.route} has a description nothing draws", destination.description)
    }
  }

  @Test
  fun `every screen is named by a resource that resolves`() {
    // The point of the whole extraction: a name that is a resource id nothing
    // answers to is a screen that draws a crash instead of a heading.
    Destination.entries.forEach { destination ->
      assertTrue(
        "${destination.route} has no title",
        context.getString(destination.title).isNotBlank(),
      )
    }
    MenuGroup.entries.forEach { group ->
      assertTrue("$group has no heading", context.getString(group.title).isNotBlank())
    }
  }

  @Test
  fun `routes are unique`() {
    val routes = Destination.entries.map { it.route }
    assertEquals(routes.size, routes.toSet().size)
  }

  @Test
  fun `routes are lookup keys, not free text`() {
    Destination.entries.forEach { destination ->
      assertEquals(destination, Destination.ofRoute(destination.route))
      assertTrue(
        "route '${destination.route}' should be a plain slug",
        destination.route.matches(Regex("[a-z][a-z0-9-]*")),
      )
    }
    assertNull(Destination.ofRoute("no-such-screen"))
  }

  @Test
  fun `the roll screen is home`() {
    assertEquals(Destination.Roll, Destination.home)
    assertEquals(MenuGroup.Play, Destination.home.group)
  }

  @Test
  fun `every menu group is used`() {
    val used = Destination.inTheMenu.map { it.group }.toSet()
    assertEquals(MenuGroup.entries.toSet(), used)
  }
}
