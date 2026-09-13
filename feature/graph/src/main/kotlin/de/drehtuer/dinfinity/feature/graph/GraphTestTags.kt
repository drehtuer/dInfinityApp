package de.drehtuer.dinfinity.feature.graph

/** What the tests reach the outcome graph by. */
object GraphTestTags {
  const val SCREEN: String = "graph:screen"
  const val FORMULA: String = "graph:formula"
  const val CHART: String = "graph:chart"
  const val INVALID: String = "graph:invalid"
  const val TOO_LARGE: String = "graph:too-large"
  const val EMPTY: String = "graph:empty"

  /** The `P(total = k)` / `P(total ≥ k)` question. */
  const val MODE: String = "graph:mode"

  fun modeOf(mode: GraphMode): String = "graph:mode:${mode.name.lowercase()}"

  /** What the tapped bar says, and what the roll that opened this says. */
  const val PICKED: String = "graph:picked"
  const val ROLLED: String = "graph:rolled"

  /** One of the six numbers beside the chart. */
  fun statOf(name: String): String = "graph:stat:$name"
}
