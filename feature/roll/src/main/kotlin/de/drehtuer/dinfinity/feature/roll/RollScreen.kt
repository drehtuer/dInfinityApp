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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.SavedRollSource
import de.drehtuer.dinfinity.ui.common.FormulaTestTags
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistToast
import de.drehtuer.dinfinity.ui.common.Plate
import de.drehtuer.dinfinity.ui.common.ToastTestTags

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

  val edges = rememberEdges(landed = presenter.state is RollState.Settled)

  WhileTheScreenIsUp(presenter, shakeToRoll)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag(RollTestTags.SCREEN),
  ) {
    TheTableOrANoticeThatThereIsNone(presenter)

    WhatTheRollIsDoing(
      presenter = presenter,
      modifier = Modifier.align(Alignment.BottomCenter),
      lifted = edges.lifted,
    )

    TheSavedRolls(
      presenter = presenter,
      strip = strip,
      rest = edges.edge.saved,
      onRest = { edges.edge = edges.edge.savedTo(it) },
      onParked = { edges.savedParked = it },
      lifted = edges.resultParked,
      modifier = Modifier.align(Alignment.BottomCenter),
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

    TheDebugOverlay(presenter = presenter, modifier = Modifier.align(Alignment.TopStart))

    TheResult(
      presenter = presenter,
      onSeeTheOdds = onSeeTheOdds,
      onSaveAsRoll = onSaveAsRoll,
      onDoodle = onDoodle,
      rest = edges.result,
      onRest = { edges.edge = edges.edge.resultTo(it) },
      onParked = { edges.resultParked = it },
      modifier = Modifier.align(Alignment.BottomCenter),
    )

    // And, when the roll is waiting on a hand, a line of words that says so
    // and goes away again. The plate behind it says the same thing and stays,
    // but a plate is somewhere a player has to look — the toast is a polite
    // live region, so a shake that is being waited for is *announced*.
    WaitingForAShake(presenter.state, modifier = Modifier.align(Alignment.BottomCenter))

    if (firstLaunch) FirstLaunch(presenter, whatIsThere, onWelcomeSeen, onImportCollection, onAddSets)
  }
}

/**
 * The three things that are true for as long as the screen is, and the one
 * that has to be undone when it is not.
 *
 * Together because they are one answer to one question — what a *visit* to
 * this screen costs — rather than four unrelated effects at the top of a
 * composable.
 *
 * Leaving gives up the physics world and the scene. The roll does not survive
 * it and is not meant to: a throw the player walked away from never landed,
 * so there is nothing to score. The thread and the Filament engine underneath
 * are not given up with them — rebuilding those is a black tray on the way
 * back (`docs/architecture.md`, decision 50).
 *
 * The tray is closed here rather than in [DiceTray], which is where it used
 * to be. That composable is on the screen only when there is something to
 * draw, and a power-saving tray draws nothing — so in that mode nothing
 * closed the tray at all, and a roll the player walked out on ran to the end
 * and was written into the history for a screen nobody was on.
 */
@Composable
private fun WhileTheScreenIsUp(
  presenter: RollPresenter,
  shakeToRoll: Boolean,
) {
  ShakeToRoll(presenter, enabled = shakeToRoll)
  KeepTheScreenAwake()
  LockTheOrientation()
  DisposableEffect(presenter.tray) {
    onDispose { presenter.tray.close() }
  }
}

/**
 * What the roll is doing underneath, for whoever turned the toggle on
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * Over the tray and under the welcome. It draws what the roll is doing and
 * cannot change it, which is the same promise the renderer makes
 * (`docs/architecture.md`, decision 38).
 */
@Composable
private fun TheDebugOverlay(
  presenter: RollPresenter,
  modifier: Modifier = Modifier,
) {
  if (!presenter.showsDebug) return
  DebugOverlay(
    diagnostics = presenter.diagnostics,
    geometry = presenter.geometry,
    modifier = modifier.safeDrawingPadding().padding(8.dp),
  )
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
 *   grip with a number in it. Everything above the sheet is lifted by it, so
 *   the saved rolls and the outcome plate are never under a sheet that has
 *   been parked.
 * @param rest where the sheet is, held by the screen because the saved rolls
 *   are a pull-up on the same edge and the two may not both be up
 *   ([BottomEdge]).
 */
@Composable
private fun TheResult(
  presenter: RollPresenter,
  onSeeTheOdds: (formula: String, total: Long?) -> Unit,
  onSaveAsRoll: (formula: String) -> Unit,
  onDoodle: (String) -> Unit,
  rest: SheetRest,
  onRest: (SheetRest) -> Unit,
  onParked: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val settled = presenter.state as? RollState.Settled ?: return
  PullUpResult(
    result = settled.result,
    divides = settled.divides,
    expected = presenter.expected,
    rest = rest,
    onRest = onRest,
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
 * The notice that the roll is waiting to be shaken
 * (`ui/common`'s `ModernistToast`).
 *
 * Two states reach it and they mean different things: dice that never settled
 * are dice to *throw again*, and an exploding chain's are dice the roll has
 * *earned*. Both wait for the same hand, so both say how many — a player who
 * shakes and sees two dice go up wants to have been told it would be two.
 *
 * The toast takes itself away after 2.6 s while the state it announced is
 * still there; that is the point. The plate under it is the thing that stays,
 * and a notice that never left would sit over the tray for as long as nobody
 * shook.
 */
@Composable
private fun WaitingForAShake(
  state: RollState,
  modifier: Modifier = Modifier,
) {
  val awaiting = state.awaiting()
  // What was announced, or null once the words have had their time. A count
  // of its own rather than a flag, because the state stays and the toast does
  // not: reading it back off [state] would raise the words again on the next
  // recomposition.
  var said by remember { mutableStateOf<Awaiting?>(null) }
  // Which wait this is. A roll that stalls, is shaken, and stalls again on
  // the same number of dice is a new thing to say, and without this it would
  // be the tail of the last toast (`TwoStageBack`).
  var round by remember { mutableIntStateOf(0) }
  LaunchedEffect(awaiting) {
    said = awaiting
    if (awaiting != null) round++
  }
  said?.let { waiting ->
    key(round) {
      ModernistToast(
        text =
          pluralStringResource(
            if (waiting.stalled) R.plurals.roll_toast_stalled else R.plurals.roll_toast_earned,
            waiting.count,
            waiting.count,
          ),
        onDismissed = { said = null },
        // The prototype's `bottom: 18px`, clear of the system's own bar.
        modifier = modifier.safeDrawingPadding().padding(bottom = ABOVE_THE_EDGE),
      )
    }
  }
}

/**
 * The two pull-ups along the bottom edge, and how much of the edge they have
 * between them.
 *
 * A holder rather than four `remember`s in the screen, because they are one
 * thing: which sheet is up is a question about the pair ([BottomEdge]), and
 * the heights are what everything stacked above them is lifted by. What is
 * left in the screen is where the two of them are drawn.
 */
private class Edges {
  var edge: BottomEdge by mutableStateOf(BottomEdge())

  /** Where the result rests, which is also where it arrives. */
  val result: SheetRest get() = edge.result

  /** How much of each grip stays on the bottom edge, in pixels. */
  var resultParked: Float by mutableFloatStateOf(0f)
  var savedParked: Float by mutableFloatStateOf(0f)

  /** The two of them together, which is what rides above both. */
  val lifted: Float get() = resultParked + savedParked
}

/**
 * [Edges], with the one thing that happens to them on its own: a total
 * arriving takes the edge, and the saved rolls get out of its way.
 *
 * A roll put away leaves them where the player left them — a strip that
 * sprang open every time a total went away would be a strip that opens
 * itself once per throw.
 */
@Composable
private fun rememberEdges(landed: Boolean): Edges {
  val edges = remember { Edges() }
  LaunchedEffect(landed) {
    edges.edge = if (landed) edges.edge.resultArrives() else edges.edge.resultGone()
    if (!landed) edges.resultParked = 0f
  }
  return edges
}

/**
 * What the next shake would throw, and whether those dice are being thrown
 * again or for the first time.
 *
 * A value rather than two nullable fields on the screen, and out here rather
 * than inside the composable, so the mapping from state to sentence is
 * arithmetic a plain JVM test can read (`TrayReading` is the same idea for
 * what the tray says).
 */
internal data class Awaiting(
  val count: Int,
  /** True for dice a roll gave up on, false for dice a chain earned. */
  val stalled: Boolean,
)

/** Null for every state that is not waiting on a hand. */
internal fun RollState.awaiting(): Awaiting? =
  when (this) {
    is RollState.Stalled -> Awaiting(count = unsettled, stalled = true)
    is RollState.ShakeAgain -> Awaiting(count = waiting, stalled = false)
    else -> null
  }

/** The prototype's `bottom: 18px`, which is where a toast sits. */
private val ABOVE_THE_EDGE = 18.dp

/**
 * The first-launch screen, over the tray, until it is pressed past
 * (`design/dInfinity.dc.html`, option 9a).
 *
 * Dismissed here as well as remembered on disk, so the screen changes the
 * moment a button is pressed rather than when a write comes back — and it
 * survives a rotation, because a welcome that reappeared when the phone turned
 * would be a welcome that looked broken.
 *
 * Its d20 is **put on the table, not thrown**: `1d20` is typed into the field
 * and the welcome gets out of the way, and the throw is the shake the player
 * makes. A welcome that rolled for them would be teaching the one thing this
 * app does not do (`docs/physics-and-rendering.md`, "Starting a roll"). There
 * is no demonstration path and no canned number (`docs/architecture.md`,
 * goal 1).
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

/** What the first-launch screen puts on the table. One die, and the famous one. */
private const val FIRST_ROLL = "1d20"

/**
 * What the roll has to say, on a plate above the bottom edge.
 *
 * **One plate, and nothing else down here any more.** It was four, then two:
 * the dice are a pull-down at the top ([DiceMenu]), "See the odds" is on the
 * result sheet where a result's actions belong, the Roll button is gone
 * altogether, and now the saved rolls are a pull-up of their own
 * ([PullUpSavedRolls]). What is left is the one plate that says what the roll
 * is doing, and in half the states it says nothing at all — which is the
 * point: with the straight-down table view every band over the felt is a
 * place a die can land and not be seen.
 *
 * **Nothing left in it throws.** A shake is the throw
 * (`docs/physics-and-rendering.md`, "Starting a roll").
 *
 * @param lifted how much of the bottom edge the two pull-ups have between
 *   them, in pixels. The plate rides above both of them.
 */
@Composable
private fun WhatTheRollIsDoing(
  presenter: RollPresenter,
  modifier: Modifier = Modifier,
  lifted: Float = 0f,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .safeDrawingPadding()
        .padding(EDGE)
        .padding(bottom = with(LocalDensity.current) { lifted.toDp() }),
    contentAlignment = Alignment.Center,
  ) {
    Outcome(
      state = presenter.state,
      progress = presenter.progress,
      expected = presenter.expected,
      onGiveUp = presenter::clear,
    )
  }
}

/**
 * The active group's saved rolls, behind a pull-up on the bottom edge
 * (`design/dInfinity.dc.html`, option 1c).
 *
 * Handed in as a slot, so this module does not have to know what a saved roll
 * is (`docs/architecture.md`, "Modules").
 *
 * @param lifted the result sheet's parked height. The saved rolls' grip sits
 *   directly above it rather than under it, so the two grips stack on the
 *   edge and neither is ever out of reach.
 */
@Composable
private fun TheSavedRolls(
  presenter: RollPresenter,
  strip: @Composable ((String, SavedRollSource?) -> Unit) -> Unit,
  rest: SheetRest,
  onRest: (SheetRest) -> Unit,
  onParked: (Float) -> Unit,
  modifier: Modifier = Modifier,
  lifted: Float = 0f,
) {
  PullUpSavedRolls(
    rest = rest,
    onRest = onRest,
    onParked = onParked,
    modifier = modifier.padding(bottom = with(LocalDensity.current) { lifted.toDp() }),
  ) {
    strip { formula, from ->
      // A tap on the strip is a formula *and* which roll put it there, so the
      // throw can be recorded as that roll's. Typed formulas come with none.
      //
      // It **fills and stops**. It used to throw as well, which made the strip
      // the one control in the app that rolled without a hand — tap a saved
      // roll and the dice were already down. Every other way in fills the
      // field and waits for a shake, and now so does this
      // (`docs/physics-and-rendering.md`, "Starting a roll").
      if (from == null) presenter.type(formula) else presenter.typeSaved(formula, from)
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
 * on the right, where the device session asked for it — as a **tab** now,
 * with the formula itself behind it ([FormulaDrawer]).
 *
 * **Everything below the open dice menu moves down.** A pull-down that
 * floated would cover the control under it, and the thing under it is the
 * formula's tab. The formula goes the other way, in from the side, so the
 * two never argue about the same space.
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

    // The formula, under the burger and on the right. Shut it is a tab at the
    // edge and the formula is not on the table at all; open it slides in from
    // the side and is the field, the squiggle and the keyboard. What is wrong
    // with a formula is said inside, under the squiggle, because that is
    // where it can be acted on ([FormulaDrawer]).
    val state = presenter.state
    FormulaDrawer(
      text = presenter.text,
      onChange = presenter::type,
      open = editing,
      onOpen = onEditing,
      error = (state as? RollState.Invalid)?.error,
      // A throw the table cannot hold is a formula that reads perfectly well,
      // so the tab marks that too — the words for it are the outcome plate's.
      wrong = state is RollState.Invalid || state is RollState.TooMany,
      // It closes the drawer first, so what the dice land on is not behind a
      // keyboard.
      onSubmit = {
        onEditing(false)
        presenter.roll()
      },
    )
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
  // Throwing is a shake, and a shake is not something every hand can make.
  // So the table carries a custom accessibility action that throws — not a
  // click, because a tap on the tray deliberately does not roll and a
  // semantic click is a tap to anything that walks the tree
  // (`docs/architecture.md`, "Accessibility";
  // `docs/physics-and-rendering.md`, "Starting a roll").
  //
  // On the power-saving panel as well as on the tray, because the panel
  // stands *instead of* the table: a mode with no surface is still a mode
  // somebody has to be able to roll in.
  if (!presenter.draws) {
    PowerSavingPanel(modifier = Modifier.throwing(presenter))
    return
  }
  DiceTray(
    driver = presenter.tray,
    geometry = presenter.geometry,
    modifier = Modifier.fillMaxSize().throwing(presenter),
    // A surface has nothing under it for a screen reader to find, so what is
    // on the table is said here or nowhere at all (`docs/architecture.md`,
    // "Accessibility").
    describing = TrayReading.of(presenter.state).spoken(),
    // Where the camera is pointed belongs to the presenter, because a new
    // throw puts it back at the whole table and the gesture is not what knows
    // a throw has started ([RollPresenter.looking]).
    view = presenter.looking,
    onLook = presenter::look,
  )
}

/**
 * The one way to throw that is not a hand.
 *
 * It calls exactly what a shake calls — `RollPresenter.roll` with no samples,
 * which is what an added die is thrown with anyway — so there is no second
 * path to a number and nothing here that a shake does not also reach
 * (`docs/architecture.md`, goal 1).
 */
@Composable
private fun Modifier.throwing(presenter: RollPresenter): Modifier {
  val label = stringResource(R.string.roll_throw_action)
  return this.semantics {
    customActions = listOf(CustomAccessibilityAction(label) { presenter.roll() })
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
 * The total, or why there is not one.
 *
 * Every branch of it is on a [Plate]. That is not decoration: this is the one
 * place on the screen where the accent is printed — a refusal, a die that
 * showed its highest face — and accent never touches felt
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * @param expected what the formula in the field is worth, for the state where
 *   nothing has been thrown yet ([ReadyPlate]).
 * @param onGiveUp puts the roll away with no total — what `Cancel the roll`
 *   does, and the only button left on any of these plates.
 */
@Composable
private fun Outcome(
  state: RollState,
  progress: RollProgress?,
  expected: Expectation?,
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
        onCancel = onGiveUp,
        // What the roll can still come out at once those dice have been
        // thrown again, which is what somebody standing over this plate is
        // deciding with (`RollPresenter.progress`).
        range = progress?.range,
      )

    // An exploding die earns a throw rather than taking one, so the screen
    // asks for it. Without this the roll simply appears to stop
    // (`docs/dice-notation.md`, "Evaluation").
    is RollState.ShakeAgain -> EarnedPlate(waiting = state.waiting, range = progress?.range)

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

    // Nothing. `Type a formula, or open Dice at the top.` stood here and the
    // second device session asked for it to go: it pointed at a formula that
    // is no longer on the table and at a menu that says `Dice` on itself, and
    // it was a plate over the felt in the one state where the felt is all
    // there is. An empty tray is now an empty tray, with the two doors along
    // the top and the welcome on a fresh install to say so
    // (`design/dInfinity.dc.html`, option 9a).
    RollState.Empty -> Unit

    // Ready to be shaken, and what shaking would be worth.
    is RollState.Ready -> ReadyPlate(expected = expected)
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

  /** The tab at the right-hand edge, and the drawer behind it (option 2a). */
  const val FORMULA_TAB: String = "roll:formula-tab"
  const val FORMULA_DRAWER: String = "roll:formula-drawer"
  const val TOTAL: String = "roll:total"
  const val ROLLING: String = "roll:rolling"

  /**
   * What the throw is expected to come to, and its average ([Expectation]).
   * On the ready plate before the shake, and in the result sheet's **grip**
   * afterwards — where it stays on the bottom edge beside the total rather
   * than going away with the breakdown.
   */
  const val EXPECTED: String = "roll:expected"
  const val EXPECTED_AVERAGE: String = "roll:expected:average"

  /** The counting plate, while the dice are being read (design option 1j). */
  const val COUNTING: String = "roll:counting"

  /** The `+` on a range a chain can still climb past, and the progress rule. */
  const val COUNTING_MORE: String = "roll:counting:more"
  const val COUNTING_RULE: String = "roll:counting:rule"

  /**
   * Where a roll that is waiting on a hand can still come out, on the earned
   * and stalled plates (`TrayPlates`).
   */
  const val STILL_TO_COME: String = "roll:still"

  /** The chain has earned a throw and is waiting for a hand (design option 1j). */
  const val SHAKE_AGAIN: String = "roll:shake-again"

  /** A roll that gave up, and the one way out of it that is not a shake. */
  const val STALLED: String = "roll:stalled"
  const val STALLED_CANCEL: String = "roll:stalled:cancel"
  const val REFUSED: String = "roll:refused"
  const val INVALID: String = FormulaTestTags.ERROR

  /**
   * The notice that says how many dice are waiting to be thrown again
   * (`ui/common`'s `ModernistToast`).
   */
  const val TOAST: String = ToastTestTags.TOAST

  /** The one-tap fix, shown only when the mistake has an obvious reading. */
  const val SUGGESTION: String = FormulaTestTags.SUGGESTION

  /** That a shake rolls — the one thing nobody would guess at. */
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

  /** The saved rolls as the other pull-up on the same edge (option 1c). */
  const val SAVED_PULL_UP: String = "roll:saved"
  const val SAVED_HANDLE: String = "roll:saved:handle"

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
