package de.drehtuer.dinfinity.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check that keeps a screen from quietly arguing with the design system.
 *
 * Both rules are the system's own, and both are things this app actually did:
 * every screen had drifted to a rounded corner the system says it does not
 * have, and several had a palette entry written out as a number.
 */
class FollowsTheDesignSystemTest {
  @Test
  fun `a rounded corner is an offence, because the system has none`() {
    val found = FollowsTheDesignSystem.offences("val shape = RoundedCornerShape(10.dp)")

    assertEquals(1, found.size)
    assertEquals(1, found.single().line)
    assertTrue(found.single().why.contains("rounded corner"))
  }

  @Test
  fun `a zero corner is what the system asks for and is left alone`() {
    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences("RoundedCornerShape(0.dp)"))
  }

  @Test
  fun `the theme's own radius token is not a number and passes`() {
    // It *is* zero, and reading it is the point — a screen that writes the
    // token has done the right thing however the token is spelled.
    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences("RoundedCornerShape(ModernistTokens.radius)"))
  }

  @Test
  fun `a colour written as a number is an offence`() {
    val found = FollowsTheDesignSystem.offences("val grey = Color(0xFF9B9797)")

    assertEquals(1, found.size)
    assertTrue(found.single().why.contains("palette"))
  }

  @Test
  fun `a colour read from the theme is what was wanted`() {
    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences("val c = MaterialTheme.colorScheme.primary"))
  }

  @Test
  fun `a line that says why is taken at its word`() {
    // The bargain: an exception is stated next to the value, so it is a line in
    // a diff rather than an entry in a list nobody reads.
    val excused = "Color(0xFFEC3013) // design-system-exception: this is the palette"

    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences(excused))
  }

  @Test
  fun `every offence is reported, on the line it is on`() {
    val source =
      """
      val a = RoundedCornerShape(6.dp)
      val fine = 0
      val b = Color(0xFF112233)
      """.trimIndent()

    val found = FollowsTheDesignSystem.offences(source)

    assertEquals(listOf(1, 3), found.map { it.line })
  }

  @Test
  fun `spacing is not checked, because a hairline is not the scale and is still right`() {
    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences("Modifier.height(1.dp).padding(2.dp)"))
  }

  @Test
  fun `a Material component this system replaces is caught, and named`() {
    // The reason each one is here is in `FollowsTheDesignSystem`'s KDoc; what
    // matters at this level is that the message says what to use instead. A
    // check that only says "no" is a check somebody works around.
    val source =
      """
      import androidx.compose.material3.Button
      import androidx.compose.material3.AlertDialog
      """.trimIndent()

    val found = FollowsTheDesignSystem.offences(source)

    assertEquals(2, found.size)
    assertTrue(found[0].why, found[0].why.contains("ModernistButton"))
    assertTrue(found[1].why, found[1].why.contains("Sheet"))
  }

  @Test
  fun `a Material component this system has no version of is left alone`() {
    // The check is a list of replacements, not a ban on Material. `Text`,
    // `Icon`, `Scaffold` and the rest are the framework this app is built on.
    val source =
      """
      import androidx.compose.material3.Text
      import androidx.compose.material3.Icon
      import androidx.compose.material3.MaterialTheme
      import androidx.compose.material3.DropdownMenu
      """.trimIndent()

    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences(source))
  }

  @Test
  fun `a local composable that shares a name with one is not caught`() {
    // Matched on the import, not on the call. `ui/common`'s own `Rule` draws
    // what `HorizontalDivider` could not, and a screen calling `Button(...)`
    // that resolves to something else in its own package is not reaching past
    // the theme.
    val source =
      """
      import de.drehtuer.dinfinity.ui.common.Rule
      Button(onClick = {}) { Text("Roll") }
      """.trimIndent()

    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences(source))
  }

  @Test
  fun `a component import can say why it is an exception, like any other line`() {
    val source = "import androidx.compose.material3.OutlinedButton // design-system-exception: it is an .input"

    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences(source))
  }

  @Test
  fun `the reason may be the comment block above the line`() {
    // Which is where it usually is: why this control is not a button, what it
    // is instead, and what would go wrong if somebody "fixed" it does not fit
    // after a `//` on an import.
    val source =
      """
      // design-system-exception: the licence chooser is an `.input`, not a
      // `.btn` — full width, surface-filled, reading from its left edge with a
      // menu behind it. ModernistButton takes a word and centres it.
      import androidx.compose.material3.OutlinedButton
      """.trimIndent()

    assertEquals(emptyList<FollowsTheDesignSystem.Offence>(), FollowsTheDesignSystem.offences(source))
  }

  @Test
  fun `an excuse does not reach past a line that is not a comment`() {
    // Otherwise one exception at the top of a file would quietly cover
    // everything under it, which is the failure mode of every exclusion list
    // kept somewhere else.
    val source =
      """
      // design-system-exception: this one is an `.input`
      import androidx.compose.material3.OutlinedButton
      import androidx.compose.material3.Button
      """.trimIndent()

    val found = FollowsTheDesignSystem.offences(source)

    assertEquals(listOf(3), found.map { it.line })
  }

  @Test
  fun `an excuse does not reach across a blank line`() {
    val source =
      """
      // design-system-exception: about something else entirely

      import androidx.compose.material3.Button
      """.trimIndent()

    assertEquals(listOf(3), FollowsTheDesignSystem.offences(source).map { it.line })
  }
}
