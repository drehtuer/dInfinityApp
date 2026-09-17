package de.drehtuer.dinfinity.build

/**
 * Finds a screen overriding the design system instead of using it.
 *
 * The Modernist system in `design/_ds/` ships its own adherence lint, and two
 * of its three rules are about exactly this: **no raw hex colour** and **no raw
 * pixel value** — use a token. That lint is an oxlint config for the prototype's
 * JSX and cannot read Kotlin, so this stands in for it on the app's side
 * (`docs/design-handover.md`).
 *
 * It checks the two rules that are unambiguous, because a check that argues
 * about a defensible value is a check somebody turns off:
 *
 * - **A corner radius that is not zero.** `--radius-sm`, `-md` and `-lg` are
 *   all `0px`, and `ModernistTokens.radius` says so in as many words:
 *   "Nothing in this system has a rounded corner." A screen that writes
 *   `RoundedCornerShape(10.dp)` has not made a judgement, it has reached past
 *   the theme.
 * - **A colour written as a number.** The palette is six tokens and an accent
 *   the player chooses; a screen that writes `Color(0xFF9B9797)` has frozen one
 *   of them, so the accent setting and dark mode both stop reaching it.
 *
 * Deliberately **not** checked: spacing. The scale is 4/8/12/16/24/32, but a
 * 1 dp hairline and the system's own 2 dp rule are neither of those and are
 * both right, and telling them apart needs a person.
 */
object FollowsTheDesignSystem {
  /** One place a screen reached past the theme. */
  data class Offence(
    val line: Int,
    val text: String,
    val why: String,
  )

  /** `RoundedCornerShape(12.dp)` and friends — anything but a zero. */
  private val ROUNDED = Regex("""RoundedCornerShape\s*\(\s*([0-9]+(?:\.[0-9]+)?)\s*\.dp""")

  /** `Color(0xFF9B9797)`: a palette entry written out rather than read. */
  private val HEX_COLOUR = Regex("""\bColor\s*\(\s*0x[0-9A-Fa-f]{6,8}""")

  /** A line that says why it is an exception is taken at its word. */
  private const val EXCUSE = "design-system-exception"

  /**
   * Every offence in [source], in line order.
   *
   * A line carrying the word `design-system-exception` is skipped, so a screen
   * with a real reason states it next to the value instead of in a list
   * somewhere else — the same bargain the coverage exclusions make.
   */
  fun offences(source: String): List<Offence> =
    source.lines().flatMapIndexed { index, line ->
      if (line.contains(EXCUSE)) {
        emptyList()
      } else {
        buildList {
          ROUNDED.findAll(line).forEach { found ->
            if (found.groupValues[1].toDouble() != 0.0) {
              add(
                Offence(
                  line = index + 1,
                  text = found.value.trim(),
                  why = "nothing in this system has a rounded corner; use MaterialTheme.shapes",
                ),
              )
            }
          }
          HEX_COLOUR.find(line)?.let { found ->
            add(
              Offence(
                line = index + 1,
                text = found.value.trim(),
                why = "the palette is the theme's; read it from MaterialTheme or LocalModernistColors",
              ),
            )
          }
        }
      }
    }
}
