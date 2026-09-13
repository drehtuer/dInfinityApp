package de.drehtuer.dinfinity.core.notation

/**
 * Scans the `kh`/`kl`/`dh`/`dl`/`!`/`r`/`min` that may follow a group's sides.
 *
 * Written twice is refused rather than quietly taking the last one: `4d6dl1dl1`
 * is a typo every time, and a formula that silently means something other than
 * what it says is worse than one that will not roll.
 */
internal class ModifierScanner(
  private val cursor: Cursor,
) {
  /** Every modifier on this group, in the order they were written. */
  fun scan(): List<DiceModifier> {
    val found = mutableListOf<DiceModifier>()
    while (true) {
      val modifier = next() ?: break
      rejectRepeat(found, modifier)
      found += modifier
    }
    return found
  }

  private fun next(): DiceModifier? {
    val start = cursor.position
    return when {
      cursor.matchWord("min") -> DiceModifier.Minimum(count(start, "min"), cursor.rangeFrom(start))
      cursor.matchWord("kh") -> DiceModifier.KeepHighest(count(start, "kh"), cursor.rangeFrom(start))
      cursor.matchWord("kl") -> DiceModifier.KeepLowest(count(start, "kl"), cursor.rangeFrom(start))
      cursor.matchWord("dh") -> DiceModifier.DropHighest(count(start, "dh"), cursor.rangeFrom(start))
      cursor.matchWord("dl") -> DiceModifier.DropLowest(count(start, "dl"), cursor.rangeFrom(start))
      cursor.match('r') -> DiceModifier.Reroll(count(start, "r"), cursor.rangeFrom(start))
      cursor.match('!') -> DiceModifier.Explode(cursor.rangeFrom(start))
      else -> null
    }
  }

  private fun count(
    start: Int,
    written: String,
  ): Int {
    val digits = cursor.scanDigits()
    if (digits.isEmpty()) {
      throw failure(
        NotationErrorCode.UnexpectedEnd,
        "'$written' has to be followed by a number, e.g. ${written}1",
        cursor.rangeFrom(start),
      )
    }
    return readCount(digits, cursor.rangeFrom(start))
  }

  private fun readCount(
    digits: String,
    range: IntRange,
  ): Int {
    val value = digits.toLongOrNull()
    if (value == null || value > NotationLimits.MAX_LITERAL) {
      throw failure(
        NotationErrorCode.NumberOutOfRange,
        "$digits is larger than the largest number a formula may contain " +
          "(${NotationLimits.MAX_LITERAL})",
        range,
      )
    }
    return value.toInt()
  }

  private fun rejectRepeat(
    found: List<DiceModifier>,
    modifier: DiceModifier,
  ) {
    val clash =
      found.firstOrNull { it::class == modifier::class }
        ?: found.firstOrNull { modifier.selectsDice && it.selectsDice }
    if (clash != null) {
      throw failure(
        NotationErrorCode.DuplicateModifier,
        if (clash::class == modifier::class) {
          "this group already has that modifier"
        } else {
          "a group keeps or drops dice once, not twice"
        },
        modifier.range,
      )
    }
  }
}

/** True for `kh`, `kl`, `dh` and `dl`, which all choose which dice count. */
private val DiceModifier.selectsDice: Boolean
  get() =
    this is DiceModifier.KeepHighest ||
      this is DiceModifier.KeepLowest ||
      this is DiceModifier.DropHighest ||
      this is DiceModifier.DropLowest
