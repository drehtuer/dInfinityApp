package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.SavedRollSource
import de.drehtuer.dinfinity.ui.common.FormulaField
import de.drehtuer.dinfinity.ui.common.FormulaTestTags
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Plate

/**
 * Home: the tray, the formula and the total
 * (`design/dInfinity.dc.html`, options 1a–1j).
 *
 * Stateless over [RollPresenter], which is stateless over [RollMachine] — so
 * what this file contains is layout and nothing that decides anything. What a
 * formula means, whether it fits, what the dice came to and what that adds up
 * to are all settled before a pixel is placed.
 *
 * @param openWith a formula to start from — a saved roll tapped, or a graph
 *   sent to the tray. Empty leaves whatever is in the field alone, which is
 *   what arriving from the menu means.
 * @param menu the way to the menu, drawn in the top corner over the tray. It
 *   is handed in because the navigation graph is `:app`'s and a screen that
 *   knew about another screen would be a feature module depending on one
 *   (`docs/architecture.md`, Modules).
 *
 * Not all of the screen's pieces yet: the squiggle under a bad formula, the
 * power-saving path and the first-launch state are still to come
 * (`docs/TODO.md`, Step 4.1).
 */
@Composable
fun RollScreen(
  presenter: RollPresenter,
  modifier: Modifier = Modifier,
  firstLaunch: Boolean = false,
  /**
   * What a fresh install already has, for the welcome's count line.
   *
   * The dice sets are the screen's own; saved rolls and sessions are handed
   * in, because this module does not know what either of those is
   * (`docs/architecture.md`, "Modules").
   */
  whatIsThere: WhatIsThere = WhatIsThere(),
  onWelcomeSeen: () -> Unit = {},
  /** The welcome's other two ways in (`design/dInfinity.dc.html`, option 9a). */
  onImportCollection: () -> Unit = {},
  onAddSets: () -> Unit = {},
  onSeeTheOdds: (formula: String, total: Long?) -> Unit = { _, _ -> },
  /**
   * Save the throw that has just landed as a named roll
   * (`design/dInfinity.dc.html`, option 3b).
   *
   * The formula and nothing else: `:feature:roll` may not depend on
   * `:feature:saved`, so what a saved roll *is* and where the editor lives are
   * `:app`'s (`docs/architecture.md`, "Modules"). It is the same callback the
   * outcome graph's "Save as roll" is, pointed at the same editor.
   */
  onSaveAsRoll: (formula: String) -> Unit = {},
  /**
   * Quick mode: draw on the die a long press picked out of the breakdown
   * (`docs/face-designer.md`, "Quick mode").
   *
   * The die's id and nothing else. Which die that names, and what the face
   * designer opens on when it names nothing any more, is `:app`'s to decide
   * with the catalogue in hand (`designer`'s `OpeningDie`) — a screen that
   * knew about another screen would be one feature module depending on
   * another (`docs/architecture.md`, "Modules").
   */
  onDoodle: (String) -> Unit = {},
  menu: @Composable () -> Unit = {},
  strip: @Composable ((String, SavedRollSource?) -> Unit) -> Unit = {},
  shakeToRoll: Boolean = true,
  openWith: String = "",
) {
  // Typed in rather than set some other way: a formula arriving from a saved
  // roll or from the graph goes through the same `type` a keystroke does, so
  // it is validated, checked against the table and shown identically
  // (`docs/architecture.md`, "Screens and the states behind them").
  LaunchedEffect(openWith) { if (openWith.isNotBlank()) presenter.type(openWith) }

  // Which of the two menus along the top is open, if either. Remembered
  // across a rotation, because a phone turned mid-formula should come back to
  // the formula being typed rather than to the tray
  // (`design/dInfinity.dc.html`, option 2a).
  //
  // **They are mutually exclusive on purpose.** Both hang off the top edge
  // and both push what is under them down; two open at once is the whole top
  // half of the table covered, which is the thing this layout exists to stop.
  var editing by rememberSaveable { mutableStateOf(false) }
  var picking by rememberSaveable { mutableStateOf(false) }

  // How much of the result sheet stays on the bottom edge ([TheResult]).
  var parked by remember { mutableFloatStateOf(0f) }

  ShakeToRoll(presenter, enabled = shakeToRoll)
  KeepTheScreenAwake()
  LockTheOrientation()

  // Leaving the screen gives up the physics world and the scene. The roll does
  // not survive it and is not meant to: a throw the player walked away from
  // never landed, so there is nothing to score. The thread and the Filament
  // engine underneath are not given up with them — rebuilding those is a black
  // tray on the way back (`docs/architecture.md`, decision 50).
  //
  // Here rather than in [DiceTray], which is where it used to be. That
  // composable is on the screen only when there is something to draw, and a
  // power-saving tray draws nothing — so in that mode nothing closed the tray
  // at all, and a roll the player walked out on ran to the end and was written
  // into the history for a screen nobody was on (`docs/TODO.md`, Step 5.3).
  DisposableEffect(presenter.tray) {
    onDispose { presenter.tray.close() }
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag(RollTestTags.SCREEN),
  ) {
    TheTableOrANoticeThatThereIsNone(presenter)

    Controls(
      presenter = presenter,
      strip = strip,
      modifier = Modifier.align(Alignment.BottomCenter),
      parked = parked,
    )

    AlongTheTop(
      presenter = presenter,
      menu = menu,
      editing = editing,
      // Opening one closes the other, in one place rather than in each
      // control: a menu that had to remember to shut its neighbour is a menu
      // that eventually forgets.
      onEditing = { open -> editing = open.also { if (it) picking = false } },
      picking = picking,
      onPicking = { open -> picking = open.also { if (it) editing = false } },
      modifier = Modifier.align(Alignment.TopStart),
    )

    // Over the tray, under the welcome, and only behind the developer toggle
    // (`docs/physics-and-rendering.md`, "Debug tooling"). It draws what the
    // roll is doing and cannot change it, which is the same promise the
    // renderer makes (`docs/architecture.md`, decision 38).
    if (presenter.showsDebug) {
      DebugOverlay(
        diagnostics = presenter.diagnostics,
        geometry = presenter.geometry,
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .safeDrawingPadding()
            .padding(8.dp),
      )
    }

    TheResult(
      presenter = presenter,
      onSeeTheOdds = onSeeTheOdds,
      onSaveAsRoll = onSaveAsRoll,
      onDoodle = onDoodle,
      onParked = { parked = it },
      modifier = Modifier.align(Alignment.BottomCenter),
    )

    if (firstLaunch) FirstLaunch(presenter, whatIsThere, onWelcomeSeen, onImportCollection, onAddSets)
  }
}

/**
 * What the dice came to, over everything at the bottom of the screen and not in
 * the column with the controls ([PullUpResult]).
 *
 * A result is read and then pushed out of the way. While it was a plate in that
 * column there was no way to see the felt under it, which with the
 * straight-down table view is where a die may well have landed
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * Nothing at all in every other state: a roll that has not landed has no total
 * to carry, and what those states say is the [Outcome] plate's.
 *
 * **What is done with a result is on it**, which is what the device session
 * asked for: "See the odds" and "Save as roll" are in the sheet's body rather
 * than on plates of their own over the felt. They are in the *body* and not
 * in the grip deliberately — `travel = height - parked`, so the grip is what
 * survives a push down, and two buttons that stayed on the bottom edge of the
 * screen for as long as a total did would be two more plates in the way of
 * the table.
 *
 * @param onParked how much of the sheet stays on the bottom edge when it is
 *   pushed all the way down. Measured rather than known: it is the height of a
 *   grip with a number in it. The screen lifts its column of controls by it, so
 *   the Roll button is never under a sheet that has been parked.
 */
@Composable
private fun TheResult(
  presenter: RollPresenter,
  onSeeTheOdds: (formula: String, total: Long?) -> Unit,
  onSaveAsRoll: (formula: String) -> Unit,
  onDoodle: (String) -> Unit,
  onParked: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val settled = presenter.state as? RollState.Settled ?: return
  PullUpResult(
    result = settled.result,
    divides = settled.divides,
    onRound = presenter::round,
    // The formula as it is in the field rather than as the result recorded
    // it: the odds and the editor are both about the roll somebody is about
    // to make again, and typing over it is how they change their mind.
    onSeeTheOdds = { onSeeTheOdds(presenter.text, settled.result.total) },
    onSaveAsRoll = { onSaveAsRoll(presenter.text) },
    onDoodle = onDoodle,
    onParked = onParked,
    modifier = modifier,
  )
}

/**
 * The first-launch screen, over the tray, until it is pressed past
 * (`design/dInfinity.dc.html`, option 9a).
 *
 * Dismissed here as well as remembered on disk, so the screen changes the
 * moment a button is pressed rather than when a write comes back — and it
 * survives a rotation, because a welcome that reappeared when the phone turned
 * would be a welcome that looked broken.
 *
 * Its d20 is thrown for real: `1d20` is typed into the field and the roll is
 * asked for, which is what the player would have done. There is no
 * demonstration path and no canned number (`docs/architecture.md`, goal 1).
 */
@Composable
private fun FirstLaunch(
  presenter: RollPresenter,
  what: WhatIsThere,
  onWelcomeSeen: () -> Unit,
  onImport: () -> Unit,
  onAddSets: () -> Unit,
) {
  var welcomed by rememberSaveable { mutableStateOf(false) }
  if (welcomed) return
  Welcome(
    // The count of sets is the screen's own; the other two are handed in,
    // because this module does not know what a saved roll or a session is.
    what = what.copy(sets = presenter.sets),
    onRollNow = {
      welcomed = true
      onWelcomeSeen()
      presenter.type(FIRST_ROLL)
      presenter.roll()
    },
    onDismiss = {
      welcomed = true
      onWelcomeSeen()
    },
    // Neither of these dismisses it: somebody who goes to fetch something and
    // comes back should find the welcome still there, with a count line that
    // has something new to say.
    onImport = onImport,
    onAddSets = onAddSets,
  )
}

/** What the first-launch screen offers to throw. One die, and the famous one. */
private const val FIRST_ROLL = "1d20"

/**
 * Everything below the tray: what the roll has to say, the saved rolls and the
 * button.
 *
 * One stack at the bottom of the screen, because the tray is the screen and
 * these sit on it rather than beside it — but a **short** one. It was four
 * plates high, which on a phone with the straight-down table view covered a
 * good part of the felt, and the whole of that view's point is being able to
 * see where a die landed. Two of the four have gone somewhere better: the
 * dice are a pull-down at the top ([DiceMenu]) and "See the odds" is on the
 * result sheet, which is where a result's actions belong.
 */
@Composable
private fun Controls(
  presenter: RollPresenter,
  strip: @Composable ((String, SavedRollSource?) -> Unit) -> Unit,
  modifier: Modifier = Modifier,
  /**
   * How much of the result sheet is parked on the bottom edge, in pixels.
   *
   * The column is lifted by it, so the Roll button and the saved rolls are
   * above a sheet that has been pushed down rather than under it. Zero
   * whenever there is no sheet, which is every state but a settled roll.
   */
  parked: Float = 0f,
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .safeDrawingPadding()
        .padding(EDGE)
        .padding(bottom = with(LocalDensity.current) { parked.toDp() }),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Outcome(
      state = state,
      progress = presenter.progress,
      onThrowMore = { presenter.roll() },
      onThrowAgain = { presenter.throwUnsettled() },
      onGiveUp = presenter::clear,
    )
    SavedRollsPlate(presenter, strip)
    // The Roll button is filled in the accent, so it is on a plate: accent
    // never touches felt (`docs/physics-and-rendering.md`, "What is drawn
    // over the table").
    Plate(modifier = Modifier.fillMaxWidth()) {
      ThrowButton(
        enabled = state is RollState.Ready || state is RollState.Settled,
        settled = state is RollState.Settled,
        onRoll = { presenter.roll() },
      )
    }
  }
}

/**
 * The active group's saved rolls, above the loose dice: a roll somebody named
 * comes before a die they have to assemble
 * (`design/dInfinity.dc.html`, option 9a).
 *
 * Handed in as a slot, so this module does not have to know what a saved roll
 * is (`docs/architecture.md`, "Modules").
 */
@Composable
private fun SavedRollsPlate(
  presenter: RollPresenter,
  strip: @Composable ((String, SavedRollSource?) -> Unit) -> Unit,
) {
  // Hugging rather than filling: what is inside is either a row of saved rolls
  // or the one "Save a roll" box, and a plate the width of the screen with a
  // box in the corner of it is a band, which is the thing the tray stopped
  // being (`docs/physics-and-rendering.md`, "What is drawn over the table").
  Plate {
    strip { formula, from ->
      // A tap on the strip is a formula *and* which roll put it there, so the
      // throw can be recorded as that roll's. Typed formulas come with none.
      if (from == null) presenter.type(formula) else presenter.typeSaved(formula, from)
      presenter.roll()
    }
  }
}

/**
 * The top of the screen: the dice, the way to the menu, and the formula
 * (`design/dInfinity.dc.html`, options 1h, 1q and 2a).
 *
 * Three controls in one column rather than three things placed absolutely
 * over the tray, and that is the whole of what this change is. The burger is
 * in the first row and the dice pull-down is beside it, so the room the
 * burger takes is the layout rather than a constant somebody has to remember
 * — which is what `MENU_ROOM` used to be. The formula hangs under the burger,
 * on the right, where the device session asked for it.
 *
 * **Everything below an open menu moves down.** A pull-down that floated
 * would cover the control under it, and the two things under these are the
 * formula and the table.
 *
 * The column fills the width but draws nothing of its own, so the felt
 * between the plates is still felt and still takes a pinch
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 */
@Composable
private fun AlongTheTop(
  presenter: RollPresenter,
  menu: @Composable () -> Unit,
  editing: Boolean,
  onEditing: (Boolean) -> Unit,
  picking: Boolean,
  onPicking: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .safeDrawingPadding()
        .padding(start = CORNER_ACROSS, top = CORNER_DOWN, end = CORNER_ACROSS)
        .testTag(RollTestTags.TOP),
    verticalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
    ) {
      Box(modifier = Modifier.weight(1f)) {
        DiceMenu(
          dice = presenter.pickable,
          counts = presenter.counts,
          sets = presenter.choosableSets,
          pickingFrom = presenter.pickingFrom,
          expanded = picking,
          onExpand = onPicking,
          onAdd = presenter::add,
          onRemove = presenter::remove,
          onChoose = presenter::pickFrom,
        )
      }
      // Over the tray rather than in a bar above it: the tray is the screen,
      // and a bar would be a strip of chrome taken off the table. Handed in
      // rather than built here, so this screen does not have to know what a
      // menu is — which would be one feature module depending on another
      // (`design/dInfinity.dc.html`, option 1q).
      menu()
    }

    // The formula, under the burger and on the right. It is an expanding
    // menu of the same kind as the dice: shut, it is the line somebody has
    // written with a dashed rule under it; open, it is the field and the
    // keyboard. What is wrong with a formula is said inside the open one,
    // under the squiggle, because that is where it can be acted on.
    FormulaMenu(
      presenter = presenter,
      editing = editing,
      onEditing = onEditing,
    )
  }
}

/**
 * The formula, as text with a dashed rule under it until it is tapped
 * (`design/dInfinity.dc.html`, option 2a).
 *
 * A field is a thing to fill in and this is a thing somebody has written, so
 * the keyboard comes up on a tap rather than standing under the dice all the
 * time. A throw the table cannot hold is a formula that reads perfectly well,
 * so both states mark it; *what* is wrong is said in the editor, under the
 * squiggle, and where the total goes.
 */

@Composable
private fun FormulaPlate(
  presenter: RollPresenter,
  state: RollState,
  editing: Boolean,
  onEditing: (Boolean) -> Unit,
) {
  val wrong = state is RollState.Invalid || state is RollState.TooMany
  if (editing) {
    // The editor fills the width, because a field is a thing to type into and
    // a field the width of what was last typed is a field that jumps.
    Plate(modifier = Modifier.fillMaxWidth()) {
      FormulaField(
        text = presenter.text,
        onChange = presenter::type,
        label = stringResource(R.string.roll_formula_label),
        hint = stringResource(R.string.roll_formula_hint),
        error = (state as? RollState.Invalid)?.error,
        wrong = wrong,
        // Enter rolls. It closes the editor first, so what the dice land on is
        // not behind a keyboard.
        onSubmit = {
          onEditing(false)
          presenter.roll()
        },
        takeFocus = true,
      )
    }
  } else {
    // **This plate hugs**, and that is the whole of the fault it fixes. The
    // formula's rule is dashed and used to be drawn the width of the screen
    // with the words centred in it, which reads as a formula struck through
    // rather than one waiting to be edited.
    Plate { FormulaLine(text = presenter.text, onEdit = { onEditing(true) }, wrong = wrong) }
  }
}

/**
 * The table, or — in power-saving mode — the notice that there is not one.
 *
 * No surface at all in that mode, rather than one nothing draws to: a surface
 * is a buffer the compositor keeps, and the claim the mode makes is that none
 * of it exists (`docs/architecture.md`, decision 38). Something has to say so,
 * because an empty screen with a total arriving on it is what a broken
 * renderer looks like ([PowerSavingPanel]).
 */
@Composable
private fun TheTableOrANoticeThatThereIsNone(presenter: RollPresenter) {
  if (!presenter.draws) {
    PowerSavingPanel()
    return
  }
  DiceTray(
    driver = presenter.tray,
    geometry = presenter.geometry,
    modifier = Modifier.fillMaxSize(),
    // A surface has nothing under it for a screen reader to find, so what is
    // on the table is said here or nowhere at all (`docs/architecture.md`,
    // "Accessibility").
    describing = TrayReading.of(presenter.state).spoken(),
  )
}

/**
 * The formula, as an expanding menu on the right under the burger.
 *
 * Its own composable rather than a `Box` in the middle of the screen's own,
 * because where a thing sits and what it is are two questions and the screen
 * only has to answer the first.
 *
 * Shut, it hugs and sits at the end of its row, so it reads as a line hanging
 * off the menu button rather than as a band across the table. Open, it fills
 * the width — a field is a thing to type into, and a field as wide as what
 * was last typed is a field that jumps.
 */
@Composable
private fun FormulaMenu(
  presenter: RollPresenter,
  editing: Boolean,
  onEditing: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End,
  ) {
    FormulaPlate(
      presenter = presenter,
      state = presenter.state,
      editing = editing,
      onEditing = onEditing,
    )
  }
}

/**
 * How far in from the edge of the screen the plates sit.
 *
 * The prototype's blocks over the tray are inset `14px`; the app's stack is
 * one column rather than four absolutely-placed blocks, so it is the column
 * that carries the inset.
 */
private val EDGE = 14.dp

/**
 * Holds the screen on while the tray is up.
 *
 * A dice tray is something a table looks at between turns, and a phone that
 * blanks after fifteen seconds of nobody touching it is a phone that has to be
 * poked every time somebody wants to read the roll. It also took the surface
 * away with it, which is a thing the tray survives now but need not be asked
 * to (`docs/TODO.md`, Step 4.1).
 *
 * On the view rather than on the window's flags, so it is undone by leaving
 * the screen and not by remembering to undo it.
 */
@Composable
private fun KeepTheScreenAwake() {
  val view = LocalView.current
  DisposableEffect(view) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
}

/**
 * The total, or why there is not one.
 *
 * Every branch of it is on a [Plate]. That is not decoration: this is the one
 * place on the screen where the accent is printed — a refusal, a die that
 * showed its highest face — and accent never touches felt
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * @param onThrowMore throws the die a chain earned. The same act the Roll
 *   button and a shake are: the presenter continues the chain rather than
 *   starting a throw.
 * @param onGiveUp puts the roll away with no total — what `Stop the chain` and
 *   `Cancel the roll` both do (`docs/TODO.md`, Step 4.1).
 */
@Composable
private fun Outcome(
  state: RollState,
  progress: RollProgress?,
  onThrowMore: () -> Unit,
  onThrowAgain: () -> Unit,
  onGiveUp: () -> Unit,
) {
  when (state) {
    // Nothing here. A settled roll is a sheet that comes up from the bottom
    // edge and can be pushed back down ([PullUpResult]) — it used to be the
    // first plate of this column, which made it a band across the middle of
    // the tray that nothing could move.
    is RollState.Settled -> Unit

    // A roll that could not finish. It says so and offers the dice back rather
    // than reading them off whatever face they were nearest, which is the one
    // thing this app may not do (`docs/physics-and-rendering.md`).
    is RollState.Stalled ->
      StalledPlate(
        unsettled = state.unsettled,
        read = state.read,
        onThrowAgain = onThrowAgain,
        onCancel = onGiveUp,
      )

    // An exploding die earns a throw rather than taking one, so the screen
    // asks for it. Without this the roll simply appears to stop
    // (`docs/dice-notation.md`, "Evaluation").
    is RollState.ShakeAgain ->
      EarnedPlate(waiting = state.waiting, onThrow = onThrowMore, onStop = onGiveUp)

    // While the dice are in the air the dice are not what to look at: each one
    // is read and taken off the table as it lands, so this is what is left to
    // follow (`docs/TODO.md`, Step 5.5).
    is RollState.Rolling if progress != null -> CountingPlate(requireNotNull(progress))

    is RollState.Rolling ->
      Message(
        text = stringResource(R.string.roll_rolling),
        colour = MaterialTheme.colorScheme.onBackground,
        tag = RollTestTags.ROLLING,
      )

    // A refusal is a sentence, not a silence: it says how many dice were asked
    // for and how many would fit (`docs/tables.md`).
    is RollState.TooMany ->
      Message(
        text = state.reason,
        // The system has one red and the theme maps `error` onto it, so a
        // refusal is printed in the accent (`theme/Theme.kt`).
        colour = MaterialTheme.colorScheme.error,
        tag = RollTestTags.REFUSED,
      )

    // The squiggle under a bad formula is the field's now, not the outcome's
    // (`ui/common`'s `FormulaField`), so an invalid formula says nothing here.
    is RollState.Invalid -> Unit

    // Not a blank: a tray with nothing on it and a button that does nothing is
    // a screen with no way in, and shaking is the part nobody would guess
    // (`design/dInfinity.dc.html`, option 9a).
    RollState.Empty ->
      Message(
        text = stringResource(R.string.roll_hint_empty),
        colour = Ink.muted,
        tag = RollTestTags.HINT,
      )

    is RollState.Ready ->
      Message(
        text = stringResource(R.string.roll_hint_ready),
        colour = Ink.muted,
        tag = RollTestTags.HINT,
      )
  }
}

/**
 * What the tray holds, in the words a screen reader says.
 *
 * Which reading a state is is [TrayReading]'s and is tested on the JVM; all
 * that happens here is looking the words up.
 */
@Composable
private fun TrayReading.spoken(): String =
  when (this) {
    TrayReading.Empty -> stringResource(R.string.roll_tray_empty)
    is TrayReading.Ready -> pluralStringResource(R.plurals.roll_tray_ready, dice, dice)
    is TrayReading.Rolling -> pluralStringResource(R.plurals.roll_tray_rolling, dice, dice)
    is TrayReading.ShakeAgain -> pluralStringResource(R.plurals.roll_tray_shake_again, dice, dice)
    is TrayReading.Stalled -> pluralStringResource(R.plurals.roll_tray_stalled, dice, dice)
    is TrayReading.Settled -> stringResource(R.string.roll_tray_settled, total)
  }

/**
 * One line over the table: the hint, "Rolling…", or a refusal.
 *
 * On a plate like everything else over the felt, and hugging its words rather
 * than filling the width — the prototype's hint block is exactly this, inset
 * from the bottom-left corner with a shadow under it
 * (`design/dInfinityPhone.dc.html`).
 */
@Composable
private fun Message(
  text: String,
  colour: Color,
  tag: String,
) {
  Plate {
    Text(
      text = text,
      // `bodyLarge` is the system's body: 15 sp, the prototype's own default.
      style = MaterialTheme.typography.bodyLarge,
      color = colour,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag(tag),
    )
  }
}

/** The same: values in, one lambda out, so it skips when nothing has moved. */
@Composable
private fun ThrowButton(
  enabled: Boolean,
  settled: Boolean,
  onRoll: () -> Unit,
) {
  // Rolling is blocked while the formula is invalid or the table is too small,
  // and while the dice are still in the air — a second throw would replace the
  // first mid-flight, which is not what a second tap means. A roll that has
  // landed can be thrown again, and that is one press: the presenter puts the
  // total away itself.
  ModernistButton(
    text = stringResource(if (settled) R.string.roll_again else R.string.roll_throw),
    onClick = onRoll,
    kind = ModernistButtonKind.Primary,
    enabled = enabled,
    modifier =
      Modifier
        .fillMaxWidth()
        .testTag(RollTestTags.THROW),
  )
}

/** What the tests reach the screen by. */

object RollTestTags {
  const val SCREEN: String = "roll:screen"
  const val TRAY: String = "roll:tray"

  /** What stands where the tray would be when the pictures are off. */
  const val POWER_SAVING: String = "roll:power-saving"

  /**
   * The formula field and its squiggle, which are `ui/common`'s and shared
   * with the saved-roll editor and the outcome graph. Named here so a test of
   * this screen reads as a test of this screen.
   */
  const val FORMULA: String = FormulaTestTags.FIELD

  /** The formula as it sits on the tray, before anybody taps it (option 2a). */
  const val FORMULA_LINE: String = "roll:formula-line"
  const val THROW: String = "roll:throw"
  const val TOTAL: String = "roll:total"
  const val ROLLING: String = "roll:rolling"

  /** The counting plate, while the dice are being read (design option 1j). */
  const val COUNTING: String = "roll:counting"

  /** The `+` on a range a chain can still climb past, and the progress rule. */
  const val COUNTING_MORE: String = "roll:counting:more"
  const val COUNTING_RULE: String = "roll:counting:rule"

  /** The chain has earned a throw and is waiting for a hand (design option 1j). */
  const val SHAKE_AGAIN: String = "roll:shake-again"
  const val EARNED_THROW: String = "roll:earned:throw"
  const val EARNED_STOP: String = "roll:earned:stop"

  /** A roll that gave up, and the two ways out of it. */
  const val STALLED: String = "roll:stalled"
  const val THROW_AGAIN: String = "roll:throw-again"
  const val STALLED_CANCEL: String = "roll:stalled:cancel"
  const val REFUSED: String = "roll:refused"
  const val INVALID: String = FormulaTestTags.ERROR

  /** The one-tap fix, shown only when the mistake has an obvious reading. */
  const val SUGGESTION: String = FormulaTestTags.SUGGESTION

  /** What to do next, when there is no result and nothing wrong. */
  const val HINT: String = "roll:hint"

  /**
   * The two things to do with a result, on the sheet that carries it: the way
   * to the outcome graph (design option 7a) and the way to the saved-roll
   * editor (option 3b).
   */
  const val ODDS: String = "roll:sheet:odds"
  const val SAVE_AS_ROLL: String = "roll:sheet:save"

  /** The first-launch screen and its two ways out (design option 9a). */
  const val WELCOME: String = "roll:welcome"
  const val WELCOME_SETS: String = "roll:welcome:sets"
  const val WELCOME_ROLL: String = "roll:welcome:roll"
  const val WELCOME_DISMISS: String = "roll:welcome:dismiss"
  const val WELCOME_IMPORT: String = "roll:welcome:import"
  const val WELCOME_SETS_ADD: String = "roll:welcome:sets-add"

  /** The column of controls along the top edge: dice, menu, formula. */
  const val TOP: String = "roll:top"

  /**
   * The dice pull-down's head, and the count of dice printed on it
   * (design option 1h).
   */
  const val DICE_MENU: String = "roll:dice-menu"
  const val DICE_MENU_COUNT: String = "roll:dice-menu:count"

  /** The dice picker row, and one die on it (design option 1h). */
  const val PICKER: String = "roll:picker"

  fun pickerDie(notation: String): String = "roll:picker:$notation"

  fun pickerCount(notation: String): String = "roll:picker:$notation:count"

  /** The set chooser under the picker row, and one set on it (design option `4a`). */
  const val SETS: String = "roll:sets"

  fun setOf(setId: String): String = "roll:sets:$setId"

  /** The Down / Nearest / Up control, shown only for a formula that divides. */
  const val ROUNDING: String = "roll:sheet:rounding"

  fun roundingOf(rounding: Rounding): String = "roll:sheet:rounding:${rounding.id}"

  /**
   * The result as a pull-up: the sheet itself, and the handle that moves it
   * between its two rests (`design/dInfinity.dc.html`, options 1e–1g).
   */
  const val PULL_UP: String = "roll:pull-up"
  const val RESULT_HANDLE: String = "roll:pull-up:handle"

  /** The breakdown under the total (`design/dInfinity.dc.html`, option 1f). */
  const val SHEET: String = "roll:sheet"
  const val SHEET_FORMULA: String = "roll:sheet:formula"

  /** One group's subtotal, and one die as it landed. */
  fun subtotalOf(groupId: Int): String = "roll:sheet:subtotal:$groupId"

  fun adjustmentOf(amount: Long): String = "roll:sheet:adjustment:$amount"

  fun fallbackOf(groupId: Int): String = "roll:sheet:fellback:$groupId"

  /** Why a group stopped throwing dice: the depth limit, or a full tray. */
  fun chainLimitOf(
    groupId: Int,
    limit: String,
  ): String = "roll:sheet:limit:$groupId:$limit"

  fun dieAt(instanceIndex: Int): String = "roll:sheet:die:$instanceIndex"

  /** "Doodle this die", offered by a long press on one (`docs/face-designer.md`, "Quick mode"). */
  fun doodleOf(instanceIndex: Int): String = "roll:sheet:die:$instanceIndex:doodle"
}

/** `left: 14px` — how far in from the side of the table a corner plate sits. */
private val CORNER_ACROSS = 14.dp

/** And `top: 12px`, which is tighter, because a line of type sits high in its box. */
private val CORNER_DOWN = 12.dp
