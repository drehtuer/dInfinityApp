package de.drehtuer.dinfinity.build

/**
 * Finds words written straight into Kotlin where a screen will read them out.
 *
 * Android Lint has a check for exactly this rule — `HardcodedText` — and it
 * cannot help here: it reads layout XML, and this app has no layouts. Every
 * screen is Compose, so `Text("Roll")` is an ordinary function call that no
 * resource-aware check ever sees. That is what this stands in for
 * (`docs/architecture.md`, "Text a person reads").
 *
 * It is a heuristic over source text rather than a type-resolved analysis, so
 * it is deliberately narrow: it looks only at the handful of places that put
 * words on a screen, and only complains when the literal *has* words in it. The
 * cost of being wrong in the strict direction is somebody arguing with a build;
 * the cost of being wrong in the lax direction is a string that quietly cannot
 * be translated, which is the thing being prevented.
 */
object TextIsAResource {
  /** One literal that should have been a resource. */
  data class Offence(
    val line: Int,
    val literal: String,
  )

  /**
   * The places a string becomes something a person reads.
   *
   * `Text(` and `text =` are Compose's own; `contentDescription` and
   * `stateDescription` are what TalkBack says; `placeholder`, `label`,
   * `supportingText` and `title` are the furniture around a field.
   *
   * Deliberately **not** here: `name =`, `id =`, `tag =` and `message =`. A
   * name is as often a key as a caption, and a check that cried about every
   * `TableLook(id = "default", …)` would be a check somebody turns off.
   */
  private val SINK =
    Regex(
      """\bText\s*\(|\btext\s*=|\bcontentDescription\s*=|\bstateDescription\s*=""" +
        """|\bplaceholder\s*=|\blabel\s*=|\bsupportingText\s*=|\btitle\s*=""",
    )

  /** `${'$'}{…}` and `${'$'}name`: what a literal borrows rather than says itself. */
  private val TEMPLATE = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")

  /** `%s`, `%1${'$'}d`, `%.1f`, `%%`: a shape for a number, not a word. */
  private val FORMAT = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")

  /**
   * Lines that are never about what a screen says: a test's handle, a
   * suppression, and the two declarations at the top of every file.
   */
  private val EXEMPT = Regex("""\btestTag\s*\(|\bTestTags\b|@Suppress|^\s*(import|package)\s""")

  /**
   * Every literal in [source] that a screen would read out and a translator
   * could not reach.
   *
   * @param source one Kotlin file, as text.
   */
  fun offences(source: String): List<Offence> {
    val found = mutableListOf<Offence>()
    var inBlockComment = false
    var armed = false
    source.lineSequence().forEachIndexed { index, raw ->
      val scanned = scan(raw, inBlockComment)
      inBlockComment = scanned.stillInComment
      val armsNextLine = endsOnASink(scanned.code)
      if (EXEMPT.containsMatchIn(raw)) {
        armed = false
        return@forEachIndexed
      }
      val sinks = SINK.findAll(scanned.code).map { it.range.last + 1 }.toList()
      scanned.literals
        .filter { atASink(scanned.code, it.at, sinks, armed) }
        .map(Literal::value)
        .filter(::readsAsWords)
        .forEach { found += Offence(line = index + 1, literal = it) }
      armed = armsNextLine
    }
    return found
  }

  /**
   * Whether the literal at [at] is what a sink is being handed.
   *
   * Position matters, and the line alone is not enough: in
   * `Text(text = "Roll", link = "https://…")` the URL sits on a line with two
   * sinks on it and belongs to neither. So a literal counts only when nothing
   * but an opening bracket and whitespace stands between it and a sink — or,
   * when [armed], when the sink was the last thing on the line before.
   */
  private fun atASink(
    code: String,
    at: Int,
    sinks: List<Int>,
    armed: Boolean,
  ): Boolean {
    if (armed && isGap(code.substring(0, at))) return true
    return sinks.any { end -> end <= at && isGap(code.substring(end, at)) }
  }

  /** Nothing but whitespace and the bracket a call opens with. */
  private fun isGap(between: String): Boolean = between.all { it.isWhitespace() || it == '(' }

  /**
   * Whether [code] ends on a sink still waiting for its argument, so that the
   * literal on the next line is the one it is handed.
   */
  private fun endsOnASink(code: String): Boolean {
    val last = SINK.findAll(code).lastOrNull() ?: return false
    return isGap(code.substring(last.range.last + 1))
  }

  /** What one line contributed: its code, its string literals, its comment state. */
  private data class Scanned(
    val code: String,
    val literals: List<Literal>,
    val stillInComment: Boolean,
  )

  /** One literal, and where it sat among the line's code. */
  private data class Literal(
    val at: Int,
    val value: String,
  )

  /**
   * Splits one line into the code outside its strings and the strings
   * themselves, with comments removed.
   *
   * Walked character by character rather than matched, because `//` inside a
   * URL is not a comment and a `"` inside a comment does not open a string —
   * and a regex that gets either of those wrong misreads the rest of the file.
   */
  @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
  private fun scan(
    line: String,
    startsInComment: Boolean,
  ): Scanned {
    val code = StringBuilder()
    val literals = mutableListOf<Literal>()
    var comment = startsInComment
    var index = 0
    while (index < line.length) {
      if (comment) {
        if (line.startsWith("*/", index)) {
          comment = false
          index += 2
        } else {
          index++
        }
        continue
      }
      if (line.startsWith("/*", index)) {
        comment = true
        index += 2
        continue
      }
      if (line.startsWith("//", index)) break
      if (line.startsWith("\"\"\"", index)) {
        // A raw string: never a caption in this code base, and its contents
        // must not be mistaken for code.
        val end = line.indexOf("\"\"\"", index + 3)
        index = if (end < 0) line.length else end + 3
        continue
      }
      if (line[index] == '"') {
        val literal = StringBuilder()
        val at = code.length
        index++
        while (index < line.length && line[index] != '"') {
          if (line[index] == '\\' && index + 1 < line.length) {
            literal.append(line[index + 1])
            index += 2
          } else {
            literal.append(line[index])
            index++
          }
        }
        index++
        literals += Literal(at = at, value = literal.toString())
        continue
      }
      code.append(line[index])
      index++
    }
    return Scanned(code = code.toString(), literals = literals, stillInComment = comment)
  }

  /**
   * Whether [literal] says anything in a language.
   *
   * What it borrows from around it is not its own — `"${'$'}name ★"` is a star
   * after somebody's name — and neither is the shape it prints a number in:
   * `"%.1f"` and `"0 %"` carry no words, and `String.format` already follows
   * the device's locale for the decimal point. What is left has to have a
   * letter in it before anybody could translate it.
   */
  internal fun readsAsWords(literal: String): Boolean =
    FORMAT.replace(TEMPLATE.replace(literal, ""), "").any(Char::isLetter)
}
