package de.drehtuer.dinfinity.dicesets.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One test per error in the list in `docs/dice-sets.md`, "Validation".
 *
 * Each of these is a package a stranger could have written, and each has to be
 * refused with a line a person can act on rather than with a stack trace.
 */
class ValidationErrorTest {
  @Test
  fun `a package with no set file is refused`() {
    val result = DiceSetValidator.validate(PackageFiles.of(emptyMap()))
    assertTrue(result is ValidationResult.Rejected)
    assertEquals(listOf(ValidationCode.MissingFile), result.codes())
  }

  @Test
  fun `a set file that is not TOML is refused, pointing at the line`() {
    val rejected = rejecting("format = 1\nthis is not toml at all\n")
    assertTrue(ValidationCode.SyntaxError in rejected.codes(), "${rejected.messages}")
    assertEquals(2, rejected.errors.first().line)
  }

  @Test
  fun `a set file with no format is refused`() {
    val rejected = rejecting("[set]\nid = \"x-set\"\nname = \"X\"\nversion = \"1.0.0\"\n")
    assertTrue(ValidationCode.MissingField in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a format newer than this app knows is refused`() {
    val rejected = rejecting(minimalToml().replace("format = 1", "format = 2"))
    assertTrue(ValidationCode.UnsupportedFormat in rejected.codes(), "${rejected.messages}")
    assertTrue(rejected.errors.any { "format 2" in it.text }, "${rejected.errors}")
  }

  @Test
  fun `a format older than the first is refused too`() {
    val rejected = rejecting(minimalToml().replace("format = 1", "format = 0"))
    assertTrue(ValidationCode.UnsupportedFormat in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a format that is not a number is refused`() {
    val rejected = rejecting(minimalToml().replace("format = 1", "format = \"one\""))
    assertTrue(ValidationCode.WrongType in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a package with no set table is refused`() {
    val rejected = rejecting("format = 1\n")
    assertTrue(ValidationCode.MissingField in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a set missing its name or version is refused`() {
    val rejected = rejecting("format = 1\n\n[set]\nid = \"x-set\"\n")
    assertEquals(2, rejected.errors.count { it.code == ValidationCode.MissingField })
  }

  @Test
  fun `a set id that is not a slug is refused`() {
    listOf("Brass", "brass set", "../etc", "-brass", "ab", "b".repeat(41)).forEach { id ->
      val rejected = rejecting(minimalToml().replace("id = \"fixture\"", "id = \"$id\"", ignoreCase = false))
      assertTrue(ValidationCode.BadSlug in rejected.codes(), "'$id' should not be a set id")
    }
  }

  @Test
  fun `a die id that is not a slug is refused`() {
    val rejected = rejecting(minimalToml().replace("id = \"d6\"", "id = \"D6\""))
    assertTrue(ValidationCode.BadSlug in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a die with no shape is refused`() {
    val rejected = rejecting(minimalToml().replace("shape = \"cube\"\n", ""))
    assertTrue(ValidationCode.MissingField in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a face value outside the documented range is refused`() {
    val rejected = rejecting(minimalToml().replace("[1, 2, 3, 4, 5, 6]", "[1, 2, 3, 4, 5, 10000]"))
    assertTrue(ValidationCode.FaceValueOutOfRange in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `faces that are not numbers are refused`() {
    val rejected = rejecting(minimalToml().replace("[1, 2, 3, 4, 5, 6]", "[\"a\", \"b\", \"c\", \"d\", \"e\", \"f\"]"))
    assertTrue(ValidationCode.WrongType in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `faces that are not a list are refused`() {
    val rejected = rejecting(minimalToml().replace("[1, 2, 3, 4, 5, 6]", "6"))
    assertTrue(ValidationCode.WrongType in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `text written as a number is refused rather than printed`() {
    val misnamed = rejecting(minimalToml().replace("name = \"Fixture\"", "name = 7"))
    assertTrue(ValidationCode.WrongType in misnamed.codes(), "${misnamed.messages}")

    val labelled = rejecting(minimalToml("labels = [1, 2, 3, 4, 5, 6]"))
    assertTrue(ValidationCode.WrongType in labelled.codes(), "${labelled.messages}")
  }

  @Test
  fun `the wrong number of labels for the faces is refused`() {
    val rejected = rejecting(minimalToml("labels = [\"1\", \"2\"]"))
    assertTrue(ValidationCode.FaceCountMismatch in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a read mode the app does not have is refused`() {
    val rejected = rejecting(minimalToml("read = \"edge-up\""))
    assertTrue(ValidationCode.UnknownPreset in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a texture that is not in the package is refused`() {
    val rejected = rejecting(minimalToml("texture = \"textures/d6.png\""))
    assertTrue(ValidationCode.ReferencedFileMissing in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `an absolute texture path is refused`() {
    val rejected = rejecting(minimalToml("texture = \"/etc/passwd.png\""))
    assertTrue(ValidationCode.BadFileReference in rejected.codes(), "${rejected.messages}")
    assertTrue(rejected.errors.any { "absolute" in it.text }, "${rejected.errors}")
  }

  @Test
  fun `a texture with an extension not on the allowlist is refused`() {
    val rejected = rejecting(minimalToml("texture = \"textures/d6.svg\""))
    assertTrue(ValidationCode.BadFileReference in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a texture larger than the dimension limit is refused without decoding it`() {
    val huge = DiceSetLimits.MAX_TEXTURE_PIXELS + 1
    val rejected =
      rejecting(minimalToml("texture = \"d6.png\""), "d6.png" to png(huge, huge))
    assertTrue(ValidationCode.TextureTooLarge in rejected.codes(), "${rejected.messages}")
    assertTrue(rejected.errors.any { "$huge×$huge" in it.text }, "${rejected.errors}")

    // Either side over the limit is enough: a strip one pixel wide is still
    // a strip too tall to upload.
    val tall = rejecting(minimalToml("texture = \"d6.png\""), "d6.png" to png(1, huge))
    assertTrue(ValidationCode.TextureTooLarge in tall.codes(), "${tall.messages}")
  }

  /**
   * Each texture under its own limit, and the package over its total: the
   * per-file limit alone would let a set carry as many four-megabyte pictures
   * as it has dice. The package only *claims* the sizes, which is all the
   * check reads, so the test does not have to allocate them.
   */
  @Test
  fun `textures that are each small enough but too much together are refused`() {
    val count = (DiceSetLimits.MAX_PACKAGE_TEXTURE_BYTES / DiceSetLimits.MAX_TEXTURE_BYTES).toInt() + 1
    val dice =
      (1..count).joinToString("\n") { i ->
        "[[die]]\nid = \"d6-$i\"\nshape = \"cube\"\nfaces = [1, 2, 3, 4, 5, 6]\ntexture = \"t$i.png\"\n"
      }
    val toml = minimalToml().substringBefore("[[die]]") + dice
    val small = png(30, 20)
    val inner = packageOf(toml, *(1..count).map { "t$it.png" to small }.toTypedArray())
    val heavy =
      object : PackageFiles {
        override fun read(path: String): ByteArray? = inner.read(path)

        override fun size(path: String): Long? =
          if (path.endsWith(".png")) DiceSetLimits.MAX_TEXTURE_BYTES else inner.size(path)
      }

    val result = DiceSetValidator.validate(heavy)

    assertTrue(result is ValidationResult.Rejected, "$result")
    assertEquals(listOf(ValidationCode.FileTooLarge), result.errors.map(ValidationMessage::code))
    assertTrue(
      result.errors
        .single()
        .text
        .contains("together"),
      "${result.errors}",
    )
  }

  @Test
  fun `a texture over the byte limit is refused`() {
    val rejected =
      rejecting(
        minimalToml("texture = \"d6.png\""),
        "d6.png" to ByteArray((DiceSetLimits.MAX_TEXTURE_BYTES + 1).toInt()),
      )
    assertTrue(ValidationCode.FileTooLarge in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a texture whose bytes are not a picture is refused`() {
    val rejected = rejecting(minimalToml("texture = \"d6.png\""), "d6.png" to "not a png".encodeToByteArray())
    assertTrue(ValidationCode.TextureUnreadable in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a physics value that is not a number is refused`() {
    val rejected = rejecting(minimalToml("friction = nan"))
    assertTrue(ValidationCode.NotFinite in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `an infinite physics value is refused`() {
    val rejected = rejecting(minimalToml("density = inf"))
    assertTrue(ValidationCode.NotFinite in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a physics value written as text is refused`() {
    val rejected = rejecting(minimalToml("roughness = \"glossy\""))
    assertTrue(ValidationCode.WrongType in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a colour that is not one is refused`() {
    val rejected = rejecting(minimalToml("color = \"bone\""))
    assertTrue(ValidationCode.WrongType in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a homepage that is not https is refused`() {
    val withHomepage = minimalToml().replace("version = \"1.0.0\"", "version = \"1.0.0\"\nhomepage = \"http://x\"")
    val rejected = rejecting(withHomepage)
    assertTrue(ValidationCode.BadFileReference in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a set file bigger than a megabyte is refused before it is parsed`() {
    val huge = ByteArray((DiceSetLimits.MAX_TOML_BYTES + 1).toInt())
    val result = DiceSetValidator.validate(PackageFiles.of(mapOf(DiceSetValidator.DICE_SET_FILE to huge)))
    assertTrue(result is ValidationResult.Rejected)
    assertEquals(listOf(ValidationCode.FileTooLarge), result.codes())
  }

  @Test
  fun `every error is reported, not only the first`() {
    val rejected =
      rejecting(
        """
        format = 1

        [set]
        id = "Bad Id"
        name = "Broken"
        version = "1.0.0"

        [[die]]
        id = "d6"
        shape = "sphere"
        faces = [1, 2, 3]

        [[die]]
        id = "d8"
        shape = "octahedron"
        faces = [1, 2, 3]
        """.trimIndent(),
      )
    assertTrue(rejected.errors.size >= 3, "only got ${rejected.errors}")
    assertTrue(ValidationCode.BadSlug in rejected.codes())
    assertTrue(ValidationCode.UnknownShape in rejected.codes())
    assertTrue(ValidationCode.FaceCountMismatch in rejected.codes())
  }

  @Test
  fun `a table with a preset the app does not have is refused`() {
    val rejected =
      rejecting(
        minimalToml() + "\n\n[[table]]\nid = \"oak\"\nname = \"Oak\"\nsound = \"thunder\"\n",
      )
    assertTrue(ValidationCode.UnknownPreset in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a table with no id, a bad id or no name is refused`() {
    listOf(
      "[[table]]\nname = \"Oak\"\n" to ValidationCode.MissingField,
      "[[table]]\nid = \"Oak Table\"\nname = \"Oak\"\n" to ValidationCode.BadSlug,
      "[[table]]\nid = \"oak\"\n" to ValidationCode.MissingField,
    ).forEach { (table, code) ->
      val rejected = rejecting(minimalToml() + "\n\n" + table)
      assertTrue(code in rejected.codes(), "$table: ${rejected.messages}")
    }
  }

  @Test
  fun `two tables with the same id are refused`() {
    val rejected =
      rejecting(
        minimalToml() +
          "\n\n[[table]]\nid = \"oak\"\nname = \"Oak\"\n\n[[table]]\nid = \"oak\"\nname = \"Also oak\"\n",
      )
    assertTrue(ValidationCode.DuplicateId in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a tiling that is not two numbers is refused`() {
    val rejected =
      rejecting(minimalToml() + "\n\n[[table]]\nid = \"oak\"\nname = \"Oak\"\nfloor_tiling = [3]\n")
    assertTrue(ValidationCode.WrongType in rejected.codes(), "${rejected.messages}")
  }
}
