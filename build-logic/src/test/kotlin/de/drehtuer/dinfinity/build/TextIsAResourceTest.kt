package de.drehtuer.dinfinity.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check that keeps the next hard-coded caption out
 * (`docs/architecture.md`, "Text a person reads").
 *
 * A linter nobody tested is a linter that passes everything, and this one is
 * the only thing standing between the app and a screen that cannot be
 * translated — Android Lint's `HardcodedText` reads layout XML and this app has
 * no layouts.
 */
class TextIsAResourceTest {
  @Test
  fun `a caption written into a composable is an offence`() {
    val offences = TextIsAResource.offences("""Text("Roll the dice")""")

    assertEquals(1, offences.size)
    assertEquals(1, offences.first().line)
    assertEquals("Roll the dice", offences.first().literal)
  }

  @Test
  fun `the same caption on its own argument line is an offence`() {
    // The common shape: `text =` is the sink, and it is a line of its own.
    val offences =
      TextIsAResource.offences(
        """
        Text(
          text = "Shake the phone",
          style = MaterialTheme.typography.bodyLarge,
        )
        """.trimIndent(),
      )

    assertEquals(listOf("Shake the phone"), offences.map { it.literal })
    assertEquals(2, offences.first().line)
  }

  @Test
  fun `a caption on the line after the call that opens is an offence`() {
    // The positional form, wrapped by the formatter. The sink is the last
    // thing on its line, so what follows it is what it is handed.
    val offences =
      TextIsAResource.offences(
        """
        Text(
          "Just show me the tray",
        )
        """.trimIndent(),
      )

    assertEquals(listOf("Just show me the tray"), offences.map { it.literal })
    assertEquals(2, offences.first().line)
  }

  @Test
  fun `a literal two lines below a call is not what the call was handed`() {
    // Only the line after, and only when nothing else intervened — otherwise
    // every argument of every wrapped call would count as a caption.
    val source =
      """
      Text(
        style = MaterialTheme.typography.bodyLarge,
        fallback = "nothing here",
      )
      """.trimIndent()

    assertTrue(TextIsAResource.offences(source).isEmpty())
  }

  @Test
  fun `a raw string is not read as code`() {
    // Its contents must not be mistaken for a call, and nothing in this code
    // base captions a screen with one.
    val source = "val pattern = \"\"\"Text(\"Roll\")\"\"\""

    assertTrue(TextIsAResource.offences(source).isEmpty())
  }

  @Test
  fun `a spoken label is an offence, because TalkBack reads it out`() {
    assertEquals(
      listOf("Open the export sheet"),
      TextIsAResource.offences("""Modifier.semantics { contentDescription = "Open the export sheet" }""")
        .map { it.literal },
    )
  }

  @Test
  fun `a resource is not an offence`() {
    assertTrue(TextIsAResource.offences("""Text(stringResource(R.string.roll_hint_empty))""").isEmpty())
  }

  @Test
  fun `a test handle is not text`() {
    // Test tags are English and never read out. The exemption is the line, not
    // the literal: a tag and a caption do not share a line.
    assertTrue(TextIsAResource.offences("""Text(modifier = Modifier.testTag("saved:new"))""").isEmpty())
    assertTrue(TextIsAResource.offences("""const val SCREEN: String = "menu:screen"""").isEmpty())
  }

  @Test
  fun `a programmer's message is not text`() {
    // `require`/`check`/`error` messages go to a crash report, never a screen,
    // and none of them is a sink.
    val source = """require(faces.isNotEmpty()) { "the added die reported no face" }"""

    assertTrue(TextIsAResource.offences(source).isEmpty())
  }

  @Test
  fun `a key, a route and a file name are not text`() {
    assertTrue(TextIsAResource.offences("""val route = "savedstats"""").isEmpty())
    assertTrue(TextIsAResource.offences("""putString("standardDeviation", value)""").isEmpty())
    assertTrue(TextIsAResource.offences("""File(dir, "collections")""").isEmpty())
  }

  @Test
  fun `a name is not a sink, because a name is as often a key`() {
    // `TableLook(id = …, name = …)` is a model, not a caption. Treating every
    // `name =` as a sink is how a check earns being switched off.
    assertTrue(TextIsAResource.offences("""TableLook(id = "default", name = look)""").isEmpty())
  }

  @Test
  fun `a comment is not code`() {
    val source =
      """
      // Text("Roll") would be wrong here.
      /* Text("Roll") too. */
      /*
       * Text("Roll") across lines.
       */
      Text(stringResource(R.string.screen_roll))
      """.trimIndent()

    assertTrue(TextIsAResource.offences(source).isEmpty())
  }

  @Test
  fun `a comment after code does not hide the code`() {
    assertEquals(
      listOf("Roll"),
      TextIsAResource.offences("""Text("Roll") // the tray's own button""").map { it.literal },
    )
  }

  @Test
  fun `a slash inside a string does not start a comment`() {
    // `//` in a URL used to swallow the rest of the line — and with it the
    // caption after it.
    assertEquals(
      listOf("Where this came from"),
      TextIsAResource.offences("""Text(text = "Where this came from", link = "https://example.org")""")
        .map { it.literal },
    )
  }

  @Test
  fun `words a literal borrows are not its own`() {
    assertFalse(TextIsAResource.readsAsWords("""${'$'}groupName ▴"""))
    assertFalse(TextIsAResource.readsAsWords("""${'$'}{roll.name} ★"""))
    assertTrue(TextIsAResource.readsAsWords("""${'$'}{count} dice left"""))
  }

  @Test
  fun `the shape a number is printed in is not a word`() {
    // `String.format` already follows the device's locale for the decimal
    // point, so `%.1f` is not something a translator changes.
    assertFalse(TextIsAResource.readsAsWords("%.2f"))
    assertFalse(TextIsAResource.readsAsWords("%.1f %%"))
    assertFalse(TextIsAResource.readsAsWords("0 %"))
    assertFalse(TextIsAResource.readsAsWords("—"))
    assertFalse(TextIsAResource.readsAsWords(" · "))
    assertTrue(TextIsAResource.readsAsWords("%1${'$'}d dice"))
  }

  @Test
  fun `an import is not a caption`() {
    assertTrue(TextIsAResource.offences("import androidx.compose.material3.Text").isEmpty())
  }

  @Test
  fun `every offence is reported, not just the first`() {
    val source =
      """
      Text("Roll")
      Text(stringResource(R.string.screen_graph))
      Text("Save as roll")
      """.trimIndent()

    assertEquals(listOf(1, 3), TextIsAResource.offences(source).map { it.line })
  }
}
