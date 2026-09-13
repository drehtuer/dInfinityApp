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
    listOf("Brass", "brass set", "../etc", "-brass", "ab").forEach { id ->
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
