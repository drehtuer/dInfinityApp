package de.drehtuer.dinfinity.feature.sets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.DicePhysical
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DiePhysical
import de.drehtuer.dinfinity.designer.ExportResult
import de.drehtuer.dinfinity.designer.PackageFile
import de.drehtuer.dinfinity.designer.SetLicense
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the details screen is showing. */
data class SetDetailState(
  val row: SetRow? = null,
  val loaded: Boolean = false,
  val gone: Boolean = false,
  val isDefault: Boolean = false,
  /**
   * The licence the author picked for the personal package, or null while they
   * have not (design `8c`).
   *
   * Null is the whole gate: there is no default, no "unspecified" entry to pick
   * by accident and no way to share until this is something. A licence is the
   * one thing about a package that the app cannot guess and that whoever
   * installs it has to be told.
   */
  val license: SetLicense? = null,
  /** True while the package is being built and zipped. */
  val exporting: Boolean = false,
  /** True once a file has been handed to the share sheet. */
  val exported: Boolean = false,
  /**
   * Why the export did not happen, when the app's own package failed the
   * validator.
   *
   * It should be empty for ever. It is shown rather than swallowed because a
   * drawing that produces an invalid set is a bug, and the lines say which.
   */
  val exportProblem: List<ValidationMessage> = emptyList(),
  /**
   * What the dice of "My dice" are declared to be made of, or null for a set
   * this phone did not write (`docs/dice-sets.md`, "Weight, translucency and
   * size, as a person sets them").
   *
   * **The live value the steppers read and write.** It is kept across a
   * refresh for the reason [license] is: a tap changes a record, the folder is
   * rebuilt from the records at the next reading, and a run of taps must
   * accumulate rather than each one being undone by the frame the last reading
   * finished in.
   */
  val declared: DieMaterial? = null,
) {
  /**
   * Whether this set can be made the default.
   *
   * Not one that is already it, and not one whose dice are not being offered:
   * making a switched-off or broken package the default would name a set that
   * every formula then falls straight past (`docs/dice-notation.md`).
   */
  val canBeDefault: Boolean get() = row?.usable == true && !isDefault

  /**
   * True once the package has been looked for and not found.
   *
   * A separate thing from `row == null`, which is also what "not looked yet"
   * looks like: the screen has to tell "this set was removed" from "still
   * reading the folder", and only one of them is worth a message.
   */
  val missing: Boolean get() = loaded && row == null

  /** True for "My dice", which is the only set with anything to export (`8c`). */
  val personal: Boolean get() = row?.personal == true

  /**
   * The **Physical** block: weight per die, translucency and size, over the
   * dice this set defines.
   *
   * Null for a package with no dice — one that is only tables, or one that
   * stopped validating — because there is then nothing to weigh, and a block
   * of dashes would be three answers to a question nobody asked.
   *
   * For "My dice" it is worked out from [declared] rather than from the
   * `diceset.toml` on disk: that file is a *view* of the records the package
   * is built from, and it catches up a reading later than the finger does.
   */
  val physical: DicePhysical?
    get() {
      val dice = row?.set?.dice.orEmpty()
      val material = declared ?: return DicePhysical.of(dice)
      return DicePhysical.of(
        dice.map { die ->
          die.copy(
            material =
              die.material.copy(
                sizeMm = material.sizeMm,
                density = material.density,
                translucency = material.translucency,
              ),
          )
        },
      )
    }

  /** True where the three figures are this phone's to change (`docs/dice-sets.md`). */
  val editable: Boolean get() = personal && declared != null

  /**
   * Whether the share may happen.
   *
   * A licence chosen, a package that still validates, and nothing in flight.
   * The gate is here rather than in the screen because it is a rule rather
   * than a layout: "do not let somebody hand a stranger a set with no licence
   * on it" is the point of the whole screen (design `8c`).
   */
  val canExport: Boolean get() = personal && license != null && !exporting && row?.broken == false
}

/**
 * One dice set, in detail (`design/dInfinity.dc.html`, options `6a` and `6b`;
 * `docs/dice-sets.md`).
 *
 * Opened with an id rather than handed a row, because the way here is a
 * navigation route and a route carries strings. That costs one folder read,
 * which is the same read the list did — and it buys a screen that survives the
 * back stack being restored, where a row passed in memory would not.
 *
 * **The report and the dice are the same screen.** A package that no longer
 * validates shows what is wrong with it where a valid one shows its dice
 * (`6b`), because they answer the same question — what is in this set — and
 * one of the answers is "nothing yet, and here is why".
 *
 * @param id the folder's name, which is how everything addresses a set.
 * @param onGone called once the set has been removed, so the screen showing it
 *   can be left. Where that goes is the navigation graph's. No default: a
 *   screen that removes a set and then stays is a page about a folder that is
 *   not there, and that is too easy to get by forgetting an argument.
 *
 * Four of the seven are the screen's ways out — where to go when the set is
 * gone, what the default is, how to change it, and where a shared file goes.
 * They are functions rather than one interface because each is answered
 * somewhere different, and bundling them would be a type per screen; hence the
 * suppression rather than a holder.
 */
@Suppress("LongParameterList")
class SetDetailPresenter(
  private val id: String,
  private val library: SetLibrary,
  private val scope: CoroutineScope,
  private val onGone: () -> Unit,
  private val defaultSetId: () -> String,
  private val onDefault: (String) -> Unit,
  /**
   * The exported package, on its way to whatever the author wants to do with
   * it (design `8c`).
   *
   * A function, because opening a share sheet is the application's business
   * and not a feature module's — the same way the source link leaves this
   * screen. Nothing by default: a screen wired without one shows the export
   * and produces a file nobody receives, which is what a test wants.
   */
  private val onShare: (PackageFile) -> Unit = {},
) {
  /** What the screen draws. */
  var state: SetDetailState by mutableStateOf(SetDetailState())
    private set

  init {
    refresh()
  }

  /**
   * Reads this one package again.
   *
   * What the screen has been *told* is kept — the licence in the chooser, and
   * whether a file has just gone out — because neither is a fact about the
   * folder. A reading that reset the chooser would un-answer a question after
   * the export it was asked for.
   */
  fun refresh() {
    scope.launch {
      val row = library.one(id)
      state =
        state.copy(
          row = row,
          loaded = true,
          isDefault = id == defaultSetId(),
          // A licence already written into the package is the choice somebody
          // made last time, so the chooser opens on it rather than on nothing.
          license = state.license ?: SetLicense.ofId(row?.set?.license),
          // Only ever read once. What is in hand is what the steppers have
          // been moving, and the folder is behind it by a reading.
          declared = state.declared ?: if (row?.personal == true) library.personalPhysical() else null,
        )
    }
  }

  /**
   * The weight stepper: one tap is a tenth of a gram
   * (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
   * them").
   *
   * What moves is the density, because that is what a set file keeps and what
   * the solver is given; the grams follow from it and the die's own volume
   * ([DiePhysical.weighted]).
   */
  fun weigh(steps: Int) = change { DiePhysical.weighted(it, steps) }

  /** The translucency stepper: one tap is five per cent. */
  fun seeThrough(steps: Int) = change { DiePhysical.seenThrough(it, steps) }

  /** The size stepper: one tap is five per cent of the average die. */
  fun resize(steps: Int) = change { DiePhysical.sized(it, steps) }

  /**
   * One tap of one stepper.
   *
   * The state moves first and the disk after it. That order is the whole of
   * what "reading the live value" means: a second tap works from what the
   * first one produced rather than from the folder, which has not been
   * rebuilt yet and will not be until something reads it.
   *
   * An imported set has no [SetDetailState.declared] and so cannot get here.
   * That is not a permission check but a fact about what a set is: its numbers
   * came out of somebody else's `diceset.toml` (`docs/dice-sets.md`).
   */
  private fun change(by: (DieMaterial) -> DieMaterial) {
    val now = state.declared?.takeIf { state.personal } ?: return
    val next = by(now)
    if (next == now) return
    state = state.copy(declared = next)
    scope.launch {
      library.setPersonalPhysical(next)
      refresh()
    }
  }

  /**
   * The author picked a licence, or took the choice back (design `8c`).
   *
   * Anything said about a previous export goes with it: a note saying a file
   * was shared, standing under a licence that is not the one it was shared
   * under, would be a lie about what is in somebody's downloads folder.
   */
  fun choose(license: SetLicense?) {
    state = state.copy(license = license, exported = false, exportProblem = emptyList())
  }

  /**
   * Builds the personal package and hands it on (design `8c`).
   *
   * Nothing happens without a licence. That is checked here as well as drawn
   * in the screen, because a disabled button is a courtesy and this is the
   * rule.
   */
  fun export() {
    val license = state.license
    if (license == null || !state.canExport) return
    state = state.copy(exporting = true, exported = false, exportProblem = emptyList())
    scope.launch {
      when (val result = library.exportPersonal(license)) {
        is ExportResult.Ready -> {
          onShare(result.file)
          state = state.copy(exporting = false, exported = true)
          // The folder now carries the chosen licence, so what the screen says
          // about the package catches up with what was just shared.
          refresh()
        }

        is ExportResult.Rejected -> state = state.copy(exporting = false, exportProblem = result.report)
        ExportResult.Empty -> state = state.copy(exporting = false)
      }
    }
  }

  /**
   * Makes this the set plain notation resolves against first (design `6a`).
   *
   * Where that is remembered is the settings' business, not a package's, so it
   * leaves by [onDefault]. The screen says so immediately rather than waiting
   * for the setting to come back round: the answer is not in doubt, and a
   * button that stays unchanged for a frame reads as one that did not work.
   */
  fun makeDefault() {
    val row = state.row?.takeIf { it.usable } ?: return
    state = state.copy(isDefault = true)
    onDefault(row.id)
  }

  /**
   * Switches the set on or off.
   *
   * The folder and every statistic recorded against its dice are untouched,
   * which is what makes this the reversible half of what this screen offers.
   */
  fun setEnabled(enabled: Boolean) {
    val row = state.row ?: return
    scope.launch {
      library.setEnabled(row, enabled)
      refresh()
    }
  }

  /**
   * Takes the set off the phone, then leaves the screen.
   *
   * [onGone] rather than a flag the screen watches: a details screen for a set
   * that is not installed any more has nothing to show, and the honest thing
   * is to not be there.
   */
  fun remove() {
    val row = state.row?.takeUnless { it.bundled } ?: return
    scope.launch {
      library.remove(row)
      state = state.copy(row = null, gone = true)
      onGone()
    }
  }
}
