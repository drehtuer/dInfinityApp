package de.drehtuer.dinfinity.core.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reading a collection file (`docs/dice-notation.md`, "Export and import").
 *
 * Every test here is a file somebody could send. The reader's contract is that
 * it either hands back a collection that is known to be sound or a list of
 * reasons it is not — never something in between — so each of these asserts
 * one of those two and never a partial state.
 */
class CollectionReaderTest {
  @Test
  fun `the example in the specification reads`() {
    // Copied from docs/dice-notation.md. If this stops passing, one of the two
    // is wrong and the document is the one that says what shipped.
    val loaded =
      loaded(
        """
        {
          "format": 1,
          "name": "Thorin, level 5 fighter",
          "groups": [
            { "id": "thorin", "name": "Thorin", "icon": "⚔️", "parent": null }
          ],
          "rolls": [
            { "group": "thorin", "name": "Longsword", "icon": "🗡️", "formula": "1d20 + 7 [Attack]" },
            { "group": "thorin", "name": "Longsword damage", "icon": "💥", "formula": "1d8 + 4 [Slashing]" }
          ]
        }
        """.trimIndent(),
      )

    assertEquals("Thorin, level 5 fighter", loaded.collection.name)
    assertEquals(listOf("thorin"), loaded.collection.groups.map { it.id })
    assertEquals(listOf("Longsword", "Longsword damage"), loaded.collection.rolls.map { it.name })
    assertEquals(
      "⚔️",
      loaded.collection.groups
        .single()
        .icon,
    )
  }

  @Test
  fun `a file bigger than a megabyte is refused without being parsed`() {
    // The limit is on the file, so it is checked before anything else looks at
    // it: a megabyte of JSON is a megabyte of parsing nobody asked for.
    val huge = "{\"name\":\"" + "x".repeat(CollectionLimits.MAX_BYTES) + "\"}"

    assertEquals(CollectionCode.TooLarge, only(huge).code)
  }

  @Test
  fun `the limit is on bytes, not characters`() {
    // A name in Japanese is three bytes a character, and a limit that counted
    // characters would let a file three times the size through.
    val text = "{\"format\":1,\"name\":\"" + "あ".repeat(CollectionLimits.MAX_BYTES / 2) + "\"}"

    assertEquals(CollectionCode.TooLarge, only(text).code)
  }

  @Test
  fun `something that is not JSON says so`() {
    assertEquals(CollectionCode.NotJson, only("this is not a collection").code)
  }

  @Test
  fun `JSON that is not an object is not a collection`() {
    assertEquals(CollectionCode.NotACollection, only("[1, 2, 3]").code)
  }

  @Test
  fun `a format this version does not know is refused rather than guessed at`() {
    // Reading a newer file as best it can is how a format silently drops what
    // it does not understand.
    val rejected = rejected("""{"format": 2, "name": "Later", "groups": [], "rolls": []}""")

    assertTrue(rejected.errors.any { it.code == CollectionCode.UnknownFormat })
  }

  @Test
  fun `a missing format is a missing field, not a default`() {
    val rejected = rejected("""{"name": "No version", "groups": [], "rolls": []}""")

    assertEquals(CollectionCode.MissingField, rejected.errors.single().code)
  }

  @Test
  fun `a collection with nothing in it is refused`() {
    val rejected = rejected("""{"format": 1, "name": "Empty", "groups": [], "rolls": []}""")

    assertEquals(CollectionCode.Empty, rejected.errors.single().code)
  }

  @Test
  fun `every problem is reported, not just the first`() {
    // Somebody fixing a file by hand wants the whole list. The import is
    // refused entirely either way, so there is nothing to gain by stopping.
    val rejected =
      rejected(
        """
        {
          "format": 1, "name": "Broken",
          "groups": [{ "id": "a", "name": "A" }],
          "rolls": [
            { "group": "a", "name": "One", "formula": "3d" },
            { "group": "nowhere", "name": "Two", "formula": "1d20" }
          ]
        }
        """.trimIndent(),
      )

    assertEquals(
      listOf(CollectionCode.BadFormula, CollectionCode.UnknownGroup),
      rejected.errors.map { it.code },
    )
  }

  @Test
  fun `a problem says where in the file it is`() {
    val rejected =
      rejected(
        """
        {
          "format": 1, "name": "Broken",
          "groups": [{ "id": "a", "name": "A" }],
          "rolls": [{ "group": "a", "name": "One", "formula": "3d" }]
        }
        """.trimIndent(),
      )

    assertEquals("rolls[0].formula", rejected.errors.single().at)
  }

  @Test
  fun `a formula the parser refuses is refused here`() {
    // A collection that could carry a formula the app cannot read would be a
    // file that imports and then fails when it is pressed, weeks later.
    val rejected = rejected(collection(rolls = """{ "group": "a", "name": "One", "formula": "4d6kh1dl1" }"""))

    assertEquals(CollectionCode.BadFormula, rejected.errors.single().code)
  }

  @Test
  fun `a roll filed in a group the file does not contain is refused`() {
    val rejected = rejected(collection(rolls = """{ "group": "elsewhere", "name": "One", "formula": "1d20" }"""))

    assertEquals(CollectionCode.UnknownGroup, rejected.errors.single().code)
  }

  @Test
  fun `two groups claiming the same id are refused`() {
    val rejected =
      rejected(
        collection(
          groups = """{ "id": "a", "name": "A" }, { "id": "a", "name": "B" }""",
        ),
      )

    assertTrue(rejected.errors.any { it.code == CollectionCode.DuplicateId })
  }

  @Test
  fun `two groups with the same name are refused, because the app allows no such pair either`() {
    val rejected =
      rejected(
        collection(
          groups = """{ "id": "a", "name": "Thorin" }, { "id": "b", "name": "thorin" }""",
        ),
      )

    assertTrue(rejected.errors.any { it.code == CollectionCode.DuplicateName })
  }

  @Test
  fun `a group two levels deep is refused`() {
    // One level, the same rule the app keeps. A deeper tree is one the
    // switcher has no control for.
    val rejected =
      rejected(
        collection(
          groups =
            """{ "id": "a", "name": "A" }, { "id": "b", "name": "B", "parent": "a" }, """ +
              """{ "id": "c", "name": "C", "parent": "b" }""",
        ),
      )

    assertEquals(CollectionCode.NestedTooDeep, rejected.errors.single().code)
  }

  @Test
  fun `one level of nesting reads`() {
    val loaded =
      loaded(
        collection(groups = """{ "id": "a", "name": "A" }, { "id": "b", "name": "B", "parent": "a" }"""),
      )

    assertEquals(
      "a",
      loaded.collection.groups
        .first { it.id == "b" }
        .parent,
    )
  }

  @Test
  fun `a group inside one the file does not contain is refused`() {
    val rejected =
      rejected(collection(groups = """{ "id": "a", "name": "A", "parent": "nowhere" }"""))

    assertEquals(CollectionCode.UnknownGroup, rejected.errors.single().code)
  }

  @Test
  fun `an id that is not a slug is refused, because nothing could refer to it`() {
    val rejected = rejected(collection(groups = """{ "id": "Not A Slug", "name": "A" }"""))

    assertTrue(rejected.errors.any { it.code == CollectionCode.BadId })
  }

  @Test
  fun `a field of the wrong kind says which kind it should be`() {
    val rejected = rejected(collection(groups = """{ "id": "a", "name": 7 }"""))

    assertEquals(CollectionCode.WrongType, rejected.errors.first().code)
  }

  @Test
  fun `more groups than a collection may carry is refused`() {
    val groups = (1..CollectionLimits.MAX_GROUPS + 1).joinToString(",") { """{ "id": "g$it", "name": "G$it" }""" }

    val rejected = rejected(collection(groups = groups))

    assertTrue(rejected.errors.any { it.code == CollectionCode.TooMany })
  }

  @Test
  fun `more rolls than a collection may carry is refused`() {
    val rolls =
      (1..CollectionLimits.MAX_ROLLS + 1).joinToString(",") {
        """{ "group": "a", "name": "R$it", "formula": "1d20" }"""
      }

    val rejected = rejected(collection(rolls = rolls))

    assertTrue(rejected.errors.any { it.code == CollectionCode.TooMany })
  }

  @Test
  fun `a name longer than a name is refused`() {
    val long = "x".repeat(CollectionLimits.MAX_NAME + 1)

    val rejected = rejected(collection(groups = """{ "id": "a", "name": "$long" }"""))

    assertTrue(rejected.errors.any { it.code == CollectionCode.TooLong })
  }

  @Test
  fun `an icon longer than an emoji is refused, because no image travels in a collection`() {
    val long = "x".repeat(CollectionLimits.MAX_ICON + 1)

    val rejected = rejected(collection(groups = """{ "id": "a", "name": "A", "icon": "$long" }"""))

    assertTrue(rejected.errors.any { it.code == CollectionCode.TooLong })
  }

  @Test
  fun `a dice set that is not installed is a warning, not a refusal`() {
    // The set may be installed tomorrow, and rewriting what somebody wrote
    // would be worse than carrying it as written.
    val loaded =
      loaded(
        collection(rolls = """{ "group": "a", "name": "One", "formula": "brass:1d20" }"""),
        installed = setOf("builtin"),
      )

    assertEquals(listOf(CollectionCode.UnknownDiceSet), loaded.warnings.map { it.code })
    assertEquals(
      "brass:1d20",
      loaded.collection.rolls
        .single()
        .formula,
    )
  }

  @Test
  fun `a dice set that is installed raises nothing`() {
    val loaded =
      loaded(
        collection(rolls = """{ "group": "a", "name": "One", "formula": "brass:1d20" }"""),
        installed = setOf("brass"),
      )

    assertEquals(emptyList(), loaded.warnings)
  }

  @Test
  fun `nothing is said about dice sets when it is not known which are installed`() {
    val loaded = loaded(collection(rolls = """{ "group": "a", "name": "One", "formula": "brass:1d20" }"""))

    assertEquals(emptyList(), loaded.warnings)
  }

  @Test
  fun `a set named inside braces is checked like any other`() {
    // `3{brass:skull-d6}` asks for brass as surely as `brass:1d20` does
    // (`docs/dice-notation.md`, "A set's own dice").
    val loaded =
      loaded(
        collection(rolls = """{ "group": "a", "name": "Bones", "formula": "3{brass:skull-d6}kh1" }"""),
        installed = setOf("builtin"),
      )

    assertEquals(listOf(CollectionCode.UnknownDiceSet), loaded.warnings.map { it.code })
    assertEquals(
      "3{brass:skull-d6}kh1",
      loaded.collection.rolls
        .single()
        .formula,
    )
  }

  @Test
  fun `a braced die the parser refuses is refused here`() {
    val rejected = rejected(collection(rolls = """{ "group": "a", "name": "Bones", "formula": "3{}" }"""))

    assertEquals(CollectionCode.BadFormula, rejected.errors.single().code)
  }

  @Test
  fun `a set named twice in one formula is only mentioned once`() {
    val loaded =
      loaded(
        collection(rolls = """{ "group": "a", "name": "One", "formula": "brass:1d20 + brass:1d6" }"""),
        installed = emptySet(),
      )

    assertEquals(1, loaded.warnings.size)
  }

  @Test
  fun `an icon left out is simply absent`() {
    val loaded = loaded(collection(groups = """{ "id": "a", "name": "A" }"""))

    assertEquals(
      "",
      loaded.collection.groups
        .single()
        .icon,
    )
  }

  @Test
  fun `a collection written before pinning went still imports`() {
    // The design took the favourite flag out; a file somebody exported from a
    // version that still had it must not become unreadable for carrying it.
    // It is read as any other file, and the flag is simply not there any more.
    // This is forward compatibility in the one direction it is safe: a field
    // the format does not know is ignored rather than refused.
    val loaded =
      loaded(
        collection(
          rolls =
            """{ "group": "a", "name": "One", "formula": "1d20", "favourite": true },""" +
              """{ "group": "a", "name": "Two", "formula": "1d6" }""",
        ),
      )

    assertEquals(listOf("One", "Two"), loaded.collection.rolls.map { it.name })
  }

  @Test
  fun `the rolls arrive in the order the file has them in`() {
    // The file's own order is the whole of what a collection says about order
    // (`docs/dice-notation.md`, "Export and import").
    val loaded =
      loaded(
        collection(
          rolls =
            """{ "group": "a", "name": "Third", "formula": "1d4" },""" +
              """{ "group": "a", "name": "First", "formula": "1d6" },""" +
              """{ "group": "a", "name": "Second", "formula": "1d8" }""",
        ),
      )

    assertEquals(listOf("Third", "First", "Second"), loaded.collection.rolls.map { it.name })
  }

  @Test
  fun `a format that is not a whole number is the wrong kind of field`() {
    val rejected = rejected("""{"format": "one", "name": "Words", "groups": [], "rolls": []}""")

    assertEquals(CollectionCode.WrongType, rejected.errors.single().code)
    assertEquals("format", rejected.errors.single().at)
  }

  @Test
  fun `a list that is not a list is refused by name`() {
    val rejected = rejected("""{"format": 1, "name": "Odd", "groups": {"id": "a"}, "rolls": "none"}""")

    assertEquals(listOf("groups", "rolls"), rejected.errors.map { it.at })
    assertTrue(rejected.errors.all { it.code == CollectionCode.WrongType })
  }

  @Test
  fun `an entry that is not an object is refused at its place in the list`() {
    // Dropped from the collection, but the file is still refused because of
    // it: an entry nobody can read must not quietly go missing.
    val rejected = rejected(collection(groups = """{ "id": "a", "name": "A" }, 7""", rolls = "\"1d20\""))

    assertEquals(listOf("groups[1]", "rolls[0]"), rejected.errors.map { it.at })
    assertTrue(rejected.errors.all { it.code == CollectionCode.WrongType })
  }

  @Test
  fun `a roll that is missing its fields names each of them`() {
    val rejected = rejected(collection(rolls = """{ "group": "a" }, { "group": "a", "name": "Two" }"""))

    assertEquals(listOf("rolls[0].name", "rolls[0].formula", "rolls[1].formula"), rejected.errors.map { it.at })
    assertTrue(rejected.errors.all { it.code == CollectionCode.MissingField })
  }

  @Test
  fun `text that arrives as an object is the wrong kind, not missing`() {
    val rejected = rejected(collection(groups = """{ "id": "a", "name": { "en": "A" } }"""))

    assertEquals(CollectionCode.WrongType, rejected.errors.first().code)
    assertEquals("groups[0].name", rejected.errors.first().at)
  }

  @Test
  fun `a problem prints where it is in front of what is wrong, unless it is the whole file`() {
    val inside = CollectionProblem(CollectionCode.BadFormula, "\"3d\" ends after \"d\"", "rolls[3].formula")
    val whole = only("this is not a collection")

    assertEquals("rolls[3].formula: \"3d\" ends after \"d\"", inside.toString())
    assertEquals("", whole.at)
    assertEquals(whole.text, whole.toString())
  }

  private fun collection(
    groups: String = """{ "id": "a", "name": "A" }""",
    rolls: String = """{ "group": "a", "name": "One", "formula": "1d20" }""",
  ) = """{ "format": 1, "name": "Test", "groups": [$groups], "rolls": [$rolls] }"""

  private fun loaded(
    text: String,
    installed: Set<String>? = null,
  ): CollectionResult.Loaded {
    val result = CollectionReader.read(text, installed)
    assertTrue(result is CollectionResult.Loaded, "expected it to read, but: $result")
    return result
  }

  private fun rejected(text: String): CollectionResult.Rejected {
    val result = CollectionReader.read(text)
    assertTrue(result is CollectionResult.Rejected, "expected it to be refused, but it read")
    return result
  }

  private fun only(text: String): CollectionProblem = rejected(text).errors.single()
}
