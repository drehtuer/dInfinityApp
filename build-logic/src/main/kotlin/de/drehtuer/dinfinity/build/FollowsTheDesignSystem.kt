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
 * It checks the rules that are unambiguous, because a check that argues about
 * a defensible value is a check somebody turns off:
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
 * - **A Material component this system has its own version of.** `Button`,
 *   `OutlinedButton` and `TextButton` all read the `CornerFull` shape token,
 *   which resolves straight to `CircleShape` and never consults
 *   `MaterialTheme.shapes` — so no theme can stop them being pills, and the
 *   app went a long time with every filled button a pill because of it.
 *   `FilterChip` is a rounded, spaced, tick-prefixed control in a system that
 *   butts its options together. `AlertDialog` is a centred card with its
 *   actions at the right, where every sheet in the prototype is on the bottom
 *   edge with its actions at the left. And `HorizontalDivider` only ever draws
 *   the 1 dp line, so the 2 dp rule that does the grouping had no way of being
 *   drawn. Each has a replacement in `ui/common`, and each of those carries
 *   the reasoning in its KDoc.
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

  /**
   * Material components this design system has its own version of, and what to
   * reach for instead.
   *
   * Matched on the **import**, not on the call, so a file either brings one in
   * or it does not — and so a local composable that happens to share a name is
   * not caught by accident.
   */
  private val INSTEAD =
    mapOf(
      "Button" to "ModernistButton(kind = Primary)",
      "OutlinedButton" to "ModernistButton(kind = Secondary)",
      "TextButton" to "ModernistButton(kind = Ghost)",
      "FilterChip" to "OptionBox, or SegmentedControl for a fixed few",
      "AssistChip" to "ModernistButton — an action is a button, not an option",
      "AlertDialog" to "Sheet",
      "HorizontalDivider" to "Rule(), or Rule(weight = RuleWeight.Hairline)",
    )

  private val MATERIAL_IMPORT = Regex("""^\s*import\s+androidx\.compose\.material3\.([A-Za-z]+)\s*$""")

  /** A line that says why it is an exception is taken at its word. */
  private const val EXCUSE = "design-system-exception"

  /** `// …` or `/* … */`: what an excuse is allowed to be written in. */
  private val COMMENT = Regex("""^\s*(//|\*|/\*)""")

  /**
   * Every offence in [source], in line order.
   *
   * A line carrying the word `design-system-exception` is skipped, so a screen
   * with a real reason states it next to the value instead of in a list
   * somewhere else — the same bargain the coverage exclusions make.
   *
   * **The reason may be in the comment block immediately above the line**, and
   * usually is. A real reason for reaching past the design system takes a
   * sentence or two — why this control is not a button, what it is instead,
   * what would go wrong if somebody "fixed" it — and none of that fits after a
   * `//` on an import. An excuse that has to be short is an excuse that ends
   * up saying nothing.
   */
  fun offences(source: String): List<Offence> {
    val lines = source.lines()
    return lines.flatMapIndexed { index, line ->
      if (isExcused(lines, index)) {
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
          MATERIAL_IMPORT.find(line)?.let { found ->
            val component = found.groupValues[1]
            INSTEAD[component]?.takeUnless { excuses(lines, component) }?.let { replacement ->
              add(
                Offence(
                  line = index + 1,
                  text = found.value.trim(),
                  why = "this system draws its own; use ui/common's $replacement",
                ),
              )
            }
          }
        }
      }
    }
  }

  /**
   * Whether the file excuses [component] by name, anywhere in it.
   *
   * **An import cannot carry its own reason**: ktlint refuses a comment inside
   * the import list, and one line after a `//` could not hold a reason worth
   * reading anyway. So the excuse lives where the control that needs it is
   * — in the KDoc of the composable that legitimately reaches for Material's
   * version — and it **names the component**, so an exception written for one
   * control cannot quietly cover a second one somebody adds later.
   *
   * `// design-system-exception: TextButton — `Tool` is a two-state control …`
   */
  private fun excuses(
    lines: List<String>,
    component: String,
  ): Boolean =
    lines.any { line ->
      line.contains(EXCUSE) && line.substringAfter(EXCUSE).contains(component)
    }

  /**
   * Whether the line at [index] has said why it is an exception — on itself,
   * or anywhere in the run of comment lines directly above it.
   *
   * The walk stops at the first line that is not a comment, so an excuse
   * cannot reach past blank space or past another declaration into a line it
   * was never written for.
   */
  private fun isExcused(
    lines: List<String>,
    index: Int,
  ): Boolean {
    if (lines[index].contains(EXCUSE)) return true
    var above = index - 1
    while (above >= 0 && COMMENT.containsMatchIn(lines[above])) {
      if (lines[above].contains(EXCUSE)) return true
      above--
    }
    return false
  }
}
