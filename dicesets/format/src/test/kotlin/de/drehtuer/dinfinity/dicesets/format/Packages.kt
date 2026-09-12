package de.drehtuer.dinfinity.dicesets.format

/** A package of a set file and whatever other files a test wants to put in it. */
internal fun packageOf(
  toml: String,
  vararg files: Pair<String, ByteArray>,
): PackageFiles =
  PackageFiles.of(
    mapOf(DiceSetValidator.DICE_SET_FILE to toml.encodeToByteArray()) + files.toMap(),
  )

/** The result of validating [toml], which must be a rejection. */
internal fun rejecting(
  toml: String,
  vararg files: Pair<String, ByteArray>,
): ValidationResult.Rejected =
  when (val result = DiceSetValidator.validate(packageOf(toml, *files))) {
    is ValidationResult.Rejected -> result
    is ValidationResult.Valid -> error("this set should not have installed:\n$toml")
  }

/** The result of validating [toml], which must be a pass. */
internal fun accepting(
  toml: String,
  vararg files: Pair<String, ByteArray>,
): ValidationResult.Valid =
  when (val result = DiceSetValidator.validate(packageOf(toml, *files))) {
    is ValidationResult.Valid -> result
    is ValidationResult.Rejected ->
      error("this set should have installed, but:\n" + result.messages.joinToString("\n") { "  $it" })
  }

/** The codes a result reported, whatever their severity. */
internal fun ValidationResult.codes(): List<ValidationCode> = messages.map(ValidationMessage::code)

/** The smallest set file a test can build on: one d6 and nothing else. */
internal fun minimalToml(body: String = ""): String =
  """
  format = 1

  [set]
  id = "fixture"
  name = "Fixture"
  version = "1.0.0"

  [[die]]
  id = "d6"
  shape = "cube"
  faces = [1, 2, 3, 4, 5, 6]
  $body
  """.trimIndent()

/** A real, smallest-possible PNG of [width] by [height], header and all. */
internal fun png(
  width: Int,
  height: Int,
): ByteArray {
  val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
  val ihdr =
    byteArrayOf(0, 0, 0, 13) + "IHDR".toByteArray() + bigEndian(width) + bigEndian(height) +
      byteArrayOf(8, 6, 0, 0, 0, 0, 0, 0, 0)
  return signature + ihdr + "IEND".toByteArray()
}

private fun bigEndian(value: Int): ByteArray =
  byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte(),
  )
