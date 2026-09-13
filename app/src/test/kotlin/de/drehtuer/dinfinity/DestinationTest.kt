package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.navigation.MenuGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationTest {
  @Test
  fun `every screen in the plan has a destination`() {
    // Step 4 of docs/TODO.md lists ten screens, and the menu lists all ten.
    assertEquals(10, Destination.inTheMenu.size)
  }

  @Test
  fun `a screen that is not in the menu is one you arrive at from somewhere`() {
    // The menu, which is the list; and the saved-roll editor, which is about
    // one roll and is reached from that roll.
    assertEquals(
      listOf(Destination.Menu, Destination.SavedRollEditor),
      Destination.entries.filter { it.group == null },
    )
  }

  @Test
  fun `every screen the menu lists says what it is for`() {
    // A menu of ten names is a quiz. "Sessions" means nothing until it does.
    Destination.inTheMenu.forEach { destination ->
      assertTrue("${destination.route} has no description", destination.description.isNotBlank())
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
