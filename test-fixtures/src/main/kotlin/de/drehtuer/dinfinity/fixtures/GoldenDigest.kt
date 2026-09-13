package de.drehtuer.dinfinity.fixtures

/**
 * The digest the golden suite records everything-before-the-engine as.
 *
 * A throw is handed to the physics as a few hundred doubles — hulls,
 * placements, a gravity per step — and writing them all into the fixture would
 * make a file nobody reads and every change unreviewable. One number per case
 * is readable, and a diff in it says exactly what it should: something that
 * decides a roll has moved.
 *
 * Doubles go in **by their raw bits**, not formatted. A digest over `%.6f`
 * would agree about two numbers that differ in the twelfth place, and those
 * two numbers are a different roll a hundred steps later
 * (`docs/architecture.md`, decision 43). It also removes the locale from the
 * question entirely.
 *
 * FNV-1a, because this is a fingerprint and not a security boundary: 64 bits
 * is far more than enough to notice a change nobody meant to make, and it is
 * ten lines rather than a dependency.
 */
class GoldenDigest {
  private var hash: ULong = OFFSET_BASIS

  /** Mixes in a label, a die id or any other text. */
  fun add(text: String): GoldenDigest {
    text.encodeToByteArray().forEach { byte -> mix(byte.toUByte().toULong()) }
    mix(SEPARATOR.toULong())
    return this
  }

  /** Mixes in a number exactly as the machine holds it. */
  fun add(value: Double): GoldenDigest = add(value.toRawBits())

  /** Mixes in a whole number. */
  fun add(value: Int): GoldenDigest = add(value.toLong())

  /** Mixes in a whole number. */
  fun add(value: Long): GoldenDigest {
    repeat(Long.SIZE_BYTES) { byte ->
      mix(((value ushr (byte * Byte.SIZE_BITS)) and BYTE_MASK).toULong())
    }
    return this
  }

  /** The digest so far, as the sixteen hex digits the fixture carries. */
  val hex: String get() = hash.toString(HEX).padStart(HEX_DIGITS, '0')

  private fun mix(byte: ULong) {
    hash = (hash xor byte) * PRIME
  }

  private companion object {
    const val OFFSET_BASIS: ULong = 0xCBF2_9CE4_8422_2325uL
    const val PRIME: ULong = 0x0000_0100_0000_01B3uL
    const val BYTE_MASK = 0xFFL
    const val SEPARATOR = 0x1F
    const val HEX = 16
    const val HEX_DIGITS = 16
  }
}
