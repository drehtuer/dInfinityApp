package de.drehtuer.dinfinity.core.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a collection file is called (`docs/dice-notation.md`, "Export and import"). */
class CollectionFilesTest {
  @Test
  fun `the extension is the one the app exports with`() {
    assertEquals(".dinfinity.json", CollectionFiles.EXTENSION)
  }

  @Test
  fun `a file named the way the app names one is a collection`() {
    assertTrue(CollectionFiles.isCollection("thorin.dinfinity.json"))
    assertTrue(CollectionFiles.isCollection("monster-manual.dinfinity.json"))
  }

  @Test
  fun `a file that merely ends in json is not`() {
    // A repository of stat blocks is full of JSON. The extension is what says
    // which file is meant for this app.
    assertFalse(CollectionFiles.isCollection("rolls.json"))
    assertFalse(CollectionFiles.isCollection("package.json"))
    assertFalse(CollectionFiles.isCollection("README.md"))
  }

  @Test
  fun `a file that arrives shouting is the same file`() {
    // Through a Windows share, or a mail client that thinks it is helping.
    assertTrue(CollectionFiles.isCollection("THORIN.DINFINITY.JSON"))
    assertTrue(CollectionFiles.isCollection("Thorin.DInfinity.json"))
  }

  @Test
  fun `the extension on its own is a dotfile, not a collection`() {
    // Otherwise a repository could hide the file it offers from the person who
    // cloned it, which is a strange thing to let a stranger do.
    assertFalse(CollectionFiles.isCollection(".dinfinity.json"))
    assertFalse(CollectionFiles.isCollection(""))
  }
}
