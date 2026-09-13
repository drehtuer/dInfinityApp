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
    // Step 4 of docs/TODO.md lists ten screens, and the menu is the eleventh
    // destination: it lists the other ten and is not in the list itself.
    assertEquals(10, Destination.inTheMenu.size)
    assertEquals(11, Destination.entries.size)
  }

  @Test
  fun `every screen the menu lists says what it is for`() {
    // A menu of ten names is a quiz. "Sessions" means nothing until it does.
    Destination.inTheMenu.forEach { destination ->
      assertTrue("${destination.route} has no description", destination.description.isNotBlank())
    }
  }

  @Test
  fun `the menu is not a row in itself`() {
    assertNull(Destination.Menu.group)
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
