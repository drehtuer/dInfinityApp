package de.drehtuer.dinfinity.feature.graph

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The outcome graph's state, as Compose reads it.
 *
 * Nothing but a window onto [GraphMachine]: the graph has no thread, no
 * engine and nothing to wait for, so unlike the roll screen's presenter there
 * is no hand-off to arrange. Every one of these is a call and a publish.
 *
 * @param formula what the graph is about, as the screen was opened with it.
 * @param rolled the total that was just thrown, or `null` when the graph was
 *   not opened from a roll (`design/dInfinity.dc.html`, option 7a).
 */
class GraphPresenter(
  private val machine: GraphMachine,
  formula: String = "",
  rolled: Int? = null,
) {
  /** What the screen draws. */
  var state: GraphState by mutableStateOf(machine.state)
    private set

  /** The formula this graph is about. */
  var text: String by mutableStateOf(machine.text)
    private set

  init {
    // The mark before the formula: the machine only shows it while the two
    // still agree, and setting it first means the first state published
    // already has it rather than arriving a frame later.
    if (rolled != null) machine.rolled(total = rolled, formula = formula)
    machine.type(formula)
    publish()
  }

  /** The formula changed. */
  fun type(typed: String) {
    machine.type(typed)
    publish()
  }

  /** `P(total = k)` or `P(total ≥ k)`. */
  fun show(mode: GraphMode) {
    machine.show(mode)
    publish()
  }

  /** A bar was tapped. */
  fun pick(value: Int) {
    machine.pick(value)
    publish()
  }

  private fun publish() {
    state = machine.state
    text = machine.text
  }
}
