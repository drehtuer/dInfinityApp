package de.drehtuer.dinfinity.feature.designer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.Drafts
import de.drehtuer.dinfinity.designer.FaceDrawing
import de.drehtuer.dinfinity.designer.FaceEyes
import de.drehtuer.dinfinity.designer.FaceFill
import de.drehtuer.dinfinity.designer.FaceStamp
import de.drehtuer.dinfinity.designer.FaceTransform
import de.drehtuer.dinfinity.designer.GuideMark
import de.drehtuer.dinfinity.designer.Mark
import de.drehtuer.dinfinity.designer.SolidStage
import de.drehtuer.dinfinity.designer.SolidTurn
import de.drehtuer.dinfinity.designer.Stage
import de.drehtuer.dinfinity.designer.StampSize
import de.drehtuer.dinfinity.designer.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * How wide each nib draws, as a fraction of the canvas — so a stroke drawn on
 * a small phone is the same stroke on a large one, and the same stroke again
 * at export resolution.
 */
private const val FINE = 0.008f
private const val MEDIUM = 0.02f
private const val BROAD = 0.05f

/** Wider than the broad pen, because an eraser people have to be accurate with is a bad eraser. */
private const val ERASER = 0.06f

/** The bucket and the stamp draw no line, so they have no width. */
private const val NO_NIB = 0f

/** What the pen is doing (`docs/face-designer.md`, "Drawing tools"). */
enum class Nib(
  /** How wide it draws, as a fraction of the canvas. */
  val width: Float,
) {
  Fine(FINE),
  Medium(MEDIUM),
  Broad(BROAD),

  /**
   * The eraser.
   *
   * A nib rather than a mode, because it is a stroke like any other — which is
   * what lets it be undone one step at a time instead of being a hole somebody
   * has to redraw around.
   */
  Eraser(ERASER),

  /**
   * The fill bucket.
   *
   * In the row with the pens because that is where the design puts it
   * (`design/dInfinity.dc.html`, option `1v`) and because it is the same kind
   * of decision — what the next touch does. It is the one that answers a
   * **tap** rather than a drag: there is no line to draw, only a region to
   * colour (`FaceFill`).
   */
  Bucket(NO_NIB),

  /**
   * The stamp: a glyph of the built-in font, put down where the finger goes
   * (`docs/face-designer.md`, "The stamp").
   *
   * In the row with the pens for the reason the bucket is — it is the same
   * kind of decision, what the next touch does — and it answers a **tap** for
   * the same reason too: there is no line to draw, only a letter to place.
   */
  Stamp(NO_NIB),
  ;

  val erases: Boolean get() = this == Eraser

  /** True for the bucket, which colours a region instead of drawing a line. */
  val fills: Boolean get() = this == Bucket

  /** True for the stamp, which puts a glyph down instead of drawing a line. */
  val stamps: Boolean get() = this == Stamp

  /**
   * True for the tools that answer a tap rather than a drag.
   *
   * The canvas listens for one or the other and never both: a detector
   * listening for both would make a slow tap with a pen into a dot of ink.
   */
  val taps: Boolean get() = fills || stamps
}

/**
 * A step of the taking-back (`docs/face-designer.md`, "Drawing tools").
 *
 * Clear is one of them rather than a thing apart, because it *is* one: a face
 * taken back to blank in a single step, which one press of undo returns.
 */
enum class Step {
  Back,
  Forward,

  /** Everything off the face, in one step. */
  Clear,
}

/**
 * Which of the two halves of the designer is in front of the player
 * (`docs/face-designer.md`, "The solid, not just the face").
 *
 * The flat editor is where a face is drawn and the solid is where the die is
 * turned over. They are two views of one drawing rather than two screens: the
 * face strip, the base die and the way out to the tray are the same underneath
 * both, and moving between them keeps the player's place.
 */
enum class DesignerView {
  /** The canvas, the tools and the palette: one face at a time, flat on. */
  Face,

  /** The real polyhedron, spinning until a drag takes over. */
  Solid,
}

/**
 * The Save-to-set sheet, while it is open (`docs/face-designer.md`, "Save to
 * set").
 *
 * @param into which set the drawings would go to. Null only when there is
 *   nowhere to save, which is a sheet that is never opened.
 * @param busy true while the package is being built. Building it rasterises
 *   every drawn face of every die, so it is not instant and the sheet says so
 *   rather than looking like a button that did nothing.
 * @param done what the last press came to, or null before the first one.
 */
data class Saving(
  val into: String?,
  val busy: Boolean = false,
  val done: SaveResult? = null,
)

/** What the designer is showing. */
data class DesignerState(
  val draft: Draft,
  val cell: Int = 0,
  val nib: Nib = Nib.Medium,
  val colorArgb: Int = INK,
  /** The faint number under the drawing, which can be turned off. */
  val guideShown: Boolean = true,
  /** The dice a drawing can be started from. */
  val choosable: List<Die> = emptyList(),
  /**
   * What was copied off a face, and what a paste puts down
   * (`docs/face-designer.md`, "Copy and paste").
   *
   * It outlives the face it came from and the die: the marks are fractions of
   * the canvas, so a border copied off a d6 lands on a d20's triangle as
   * readily as on another square — masked to the cell like anything else. It
   * is how somebody is working rather than what they are working on, which is
   * the rule the pen and the colour already follow.
   */
  val clipboard: List<Mark> = emptyList(),
  /**
   * What the stamp is loaded with, or null while it follows the face.
   *
   * Null until somebody types something, and then theirs: the commonest thing
   * to stamp is the number that belongs on the face, and the second commonest
   * is a small edit of it that should not be undone by moving to the next face
   * (`docs/face-designer.md`, "The stamp").
   */
  val stampText: String? = null,
  /** How big the next stamp is, against what this face's own number would be. */
  val stampSize: StampSize = StampSize.Medium,
  /** Which of the two tabs is in front of the player. */
  val view: DesignerView = DesignerView.Face,
  /** How the die on the Solid tab is turned. */
  val turn: SolidTurn = SolidTurn(),
  /**
   * True while the die is turning on its own.
   *
   * On until a drag takes over, because a die that has to be dragged before it
   * shows anything but its front face is a die nobody turns over.
   */
  val spinning: Boolean = true,
  /**
   * The Save-to-set sheet while it is open, and null the rest of the time
   * (`docs/face-designer.md`, "Save to set").
   *
   * One nullable field rather than four flags, because the four only ever mean
   * anything together: which set is chosen, whether the write is running and
   * what it came to are all about one sheet, and a `saved` that outlived the
   * sheet would be an answer to a question nobody is looking at.
   */
  val saving: Saving? = null,
) {
  /** The die being drawn on. */
  val die: Die get() = draft.die

  /** True when there is more than one die to start from, so a chooser is worth drawing. */
  val baseChoosable: Boolean get() = choosable.size > 1

  /** The drawing on the face in front of the player. */
  val face: FaceDrawing get() = draft.face(cell)

  /**
   * What that face is *called* — the number the tray would print, or the word
   * a set gave it.
   *
   * The label rather than the index, because "face 3 of 20" is a fact about a
   * list and "face crit of 20" is a fact about this die. A cell no face
   * answers to falls back to its place in the list, which is a state no real
   * die reaches.
   */
  val label: String get() = draft.die.faces.getOrNull(cell)?.label ?: (cell + 1).toString()

  /**
   * The die as the Solid tab sees it, turned by [turn].
   *
   * Worked out here rather than in the draw lambda, which is the line this
   * module draws everywhere: what can be *wrong* — which faces are facing
   * away, what order they go down in, where a corner lands — is decided in
   * plain Kotlin and what is left to draw is paths and colours
   * (`docs/architecture.md`, decision 55).
   */
  val stage: Stage get() = SolidStage.of(draft, turn)

  /** The numbers under it — one, or a d4's three (`docs/dice-sets.md`, "The d4"). */
  val guide: List<GuideMark> get() = if (guideShown) draft.guide(cell) else emptyList()

  /** What a stamp would put down: what somebody typed, or this face's own number. */
  val stamping: String get() = stampText ?: FaceStamp.textOn(draft.die, cell)

  /**
   * True when the font can draw what the stamp is loaded with.
   *
   * The built-in font is deliberately small — digits, two signs, a times, a
   * per cent and a full stop — and a label using anything else is refused
   * whole rather than stamped as the half of it the font happens to have. The
   * row says so before the tap rather than swallowing it
   * (`BuiltinFont.canDraw`).
   */
  val canStamp: Boolean get() = BuiltinFont.canDraw(stamping)

  /**
   * True when this die can be pipped instead of numbered, which is a d6 and
   * only a d6 (`FaceEyes.canBePipped`).
   *
   * What decides whether the two eye buttons are on the screen at all. A pip
   * pattern is a way of writing one to six and there is no pattern for a 7 or
   * for a Fudge die's minus, so the offer is withheld rather than made and
   * refused.
   */
  val canPip: Boolean get() = FaceEyes.canBePipped(draft.die)

  /** True when some face is carrying pips, which is what `Clear eyes` is for. */
  val pipped: Boolean get() = FaceEyes.pipped(draft)

  val canUndo: Boolean get() = face.canUndo
  val canRedo: Boolean get() = face.canRedo

  /** True when there is something on this face to copy. */
  val canCopy: Boolean get() = !face.blank

  /**
   * True when a paste would land.
   *
   * Something copied, and room for all of it: a paste is refused whole rather
   * than in part (`FaceDrawing.paste`), so the control says so before the
   * press rather than after it.
   */
  val canPaste: Boolean get() = clipboard.isNotEmpty() && face.marks.size + clipboard.size <= FaceDrawing.MAX_MARKS

  /**
   * How many turns of its own this die's cells have — 1 when the only thing on
   * offer is the mirror (`FaceTransform`).
   */
  val turnsOffered: Int get() = FaceTransform.stepsOf(draft.outline)

  /**
   * What a finger that left [dots] behind puts on the face, or null when it
   * put nothing there.
   *
   * What a gesture leaves depends on the tool in hand, which is why the
   * decision is here rather than in the screen or in a draw lambda: a pen
   * leaves a line and needs at least two dots to have drawn one, and the
   * bucket leaves a region from the single place it was put down. A tap with a
   * pen is not a mark, and a drag with the bucket is not a line.
   */
  fun markOf(dots: List<Dot>): Mark? =
    when {
      nib.fills -> dots.firstOrNull()?.let { FaceFill.at(point = it, marks = face.marks, colorArgb = colorArgb) }
      nib.stamps ->
        dots.firstOrNull()?.let {
          FaceStamp.at(
            text = stamping,
            point = it,
            outline = draft.outline,
            size = stampSize,
            colorArgb = colorArgb,
          )
        }
      dots.size < 2 -> null
      else -> Stroke(dots = dots, colorArgb = colorArgb, width = nib.width, erases = nib.erases)
    }

  /**
   * True when this face is close enough to the limit to say so.
   *
   * The warning comes before the refusal, because a drawing that simply stops
   * taking strokes reads as a broken screen (`docs/face-designer.md`,
   * "Constraints").
   */
  val nearlyFull: Boolean get() = face.marks.size >= FaceDrawing.MAX_MARKS - ROOM_TO_WARN

  val full: Boolean get() = face.full

  companion object {
    /** Black, which is what a pen is until somebody says otherwise. */
    const val INK: Int = 0xFF000000.toInt()

    /** How many strokes of warning somebody gets before the face stops taking them. */
    const val ROOM_TO_WARN: Int = 20
  }
}

/**
 * Drawing the faces of a die (`design/dInfinity.dc.html`, options `1v`, `4c`
 * and `8d`; `docs/face-designer.md`).
 *
 * The die is **copied**, so the face values are inherited and what is being
 * drawn is only what a face looks like. Nothing here can change what the die
 * scores — which is why the guide can be trusted as a guide.
 *
 * A stroke arrives already in fractions of the canvas. The screen knows how
 * big it is and this does not: a draft outlives the screen it was drawn on and
 * is re-rendered at export resolution, so a stroke in pixels would be a stroke
 * that moved when the phone was turned.
 *
 * It is over detekt's count of what a class may have, like `RollPresenter` and
 * for the same reason: every one of them is a thing a finger does on one
 * screen, and splitting them across two objects would only mean two objects
 * holding one screen's state.
 */
@Suppress("TooManyFunctions")
class DesignerPresenter(
  die: Die,
  /**
   * The dice a drawing can be started from (`docs/face-designer.md`, "Flow":
   * any catalogue shape or any installed die).
   *
   * Every die of every usable set, so a d18 from somebody else's package can
   * be drawn on as readily as the bundled d6. Empty means there is nothing to
   * choose between and no chooser is drawn — which is not a state a real
   * install reaches, since the bundled set is always there.
   */
  choosable: List<Die> = emptyList(),
  /**
   * Where the drawings are kept between sittings.
   *
   * [Drafts.NONE] by default, which is a designer whose work lasts as long as
   * the screen does — what the tests use, and what the screen would do if
   * nothing gave it a folder.
   */
  private val drafts: Drafts = Drafts.NONE,
  /**
   * How the die being drawn is written in a formula, or `null` when notation
   * cannot name it (`docs/dice-notation.md`; `docs/architecture.md`,
   * decision 31).
   *
   * A function rather than a string, because the die changes while the screen
   * is open. It comes from outside for the reason the dice themselves do: it
   * needs the installed sets and which of them a bare `d20` means, and neither
   * is this module's to know.
   *
   * Plain notation names `dN`, `d%` and `dF` and nothing else, so a set's own
   * `skull-d6` has no spelling a formula could carry — and **Roll it** is not
   * offered for one rather than offered and broken.
   */
  private val notationOf: (Die) -> String? = { null },
  /**
   * Where a drawing becomes a dice set ([DesignerSets]).
   *
   * [DesignerSets.NONE] by default, which is a designer with nowhere to save:
   * no Save is offered and **Roll it** falls back to naming the plain die. It
   * is what the tests use, and what the screen would do if nothing gave it a
   * library.
   */
  private val sets: DesignerSets = DesignerSets.NONE,
  /**
   * Where a save runs.
   *
   * Building the personal package rasterises every drawn face, so it cannot
   * happen on the thread the canvas draws on — but *which* thread it does
   * happen on belongs to the wiring, and [DesignerSets] is where that decision
   * is made. What this scope is for is only the waiting.
   *
   * [Dispatchers.Unconfined] by default, so a presenter with
   * [DesignerSets.NONE] behind it does its nothing there and then: an
   * unwired designer behaves exactly as it did before there was anything to
   * save.
   */
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
  /** What the screen draws. */
  var state: DesignerState by mutableStateOf(
    DesignerState(draft = drafts.load(die), choosable = choosable),
  )
    private set

  /**
   * The formula that throws the die being drawn, or null when there is none.
   *
   * What it decides is whether **Roll it** is on the screen at all — absent
   * rather than dead for a die plain notation cannot name, since a set's own
   * `skull-d6` has no spelling a formula could carry (`docs/architecture.md`,
   * decision 31). The formula actually thrown is [roll]'s, which is not
   * necessarily this one: saving the drawing first is what lets the tray be
   * handed `mine:1d20` — the die **with the drawing on it** — rather than the
   * plain `1d20` of whichever set a bare `d20` happens to mean.
   */
  val rollable: String? get() = notationOf(state.draft.die)

  /** The sets a drawing can be saved into, which is what decides whether Save is offered. */
  val writable: List<WritableSet> get() = sets.writable

  /**
   * **Roll it**: make the drawing real, then hand [go] the formula that throws
   * it (`docs/face-designer.md`, "Flow", step 4).
   *
   * The save is not a courtesy, it is the whole of why the tray shows a
   * drawing at all. The drafts are the record and `dicesets/mine/` is a *view*
   * of them, and until that view is written there is no package for a formula
   * to name and no atlas for the renderer to sample — which is exactly how
   * pressing this used to produce a plain die. So the order is: write the
   * drawing down, build the package, and name the die **in the set that now
   * carries it**.
   *
   * A save that comes to nothing is not a dead end. The plain spelling is
   * still a die the tray can throw, so the throw happens either way and what
   * is lost is the artwork rather than the roll.
   */
  fun roll(go: (String) -> Unit) {
    val die = state.draft.die
    val into = sets.writable.firstOrNull()
    if (into == null) {
      notationOf(die)?.let(go)
      return
    }
    scope.launch {
      val outcome = sets.save(into.id, state.draft)
      val formula = (outcome as? SaveResult.Saved)?.rollable ?: notationOf(die)
      formula?.let(go)
    }
  }

  /** The sheet was opened, on the set it was last aimed at or the first writable one. */
  fun offerSave() {
    state = state.copy(saving = Saving(into = state.saving?.into ?: sets.writable.firstOrNull()?.id))
  }

  /** Another set was chosen in the sheet. The last answer goes with it: it was about the other set. */
  fun saveInto(setId: String) {
    state = state.copy(saving = Saving(into = setId))
  }

  /** The sheet was dismissed. */
  fun stopSaving() {
    state = state.copy(saving = null)
  }

  /**
   * **Save to set**: write the drawings into the chosen set
   * (`docs/face-designer.md`, "Save to set").
   *
   * The sheet stays open on the answer rather than closing on the press: a
   * save that was refused has a reason worth reading, and a save that worked
   * has a set worth naming — "it went somewhere" is not what somebody pressing
   * Save is asking.
   */
  fun save() {
    val into = state.saving?.into ?: return
    if (state.saving?.busy == true) return
    state = state.copy(saving = Saving(into = into, busy = true))
    scope.launch {
      val outcome = sets.save(into, state.draft)
      state = state.copy(saving = state.saving?.copy(busy = false, done = outcome))
    }
  }

  /**
   * Draw on a different die (`docs/face-designer.md`, "Flow").
   *
   * A different die is a different draft — the faces are a different shape,
   * there are a different number of them, and the values under the guide are
   * that die's — but it is no longer a drawing *thrown away*: what is on the
   * canvas is written down before the swap and the new die's own drawing is
   * read back, so switching between two dice is switching between two
   * drawings. That is what made the confirmation this used to ask
   * unnecessary, and a dialog that warns about a loss that cannot happen is
   * worse than no dialog at all.
   *
   * The pen, its colour, how big the stamp is, whether the guide is showing and
   * which of the two tabs is open all stay: they are how somebody is working,
   * not what they are working on. So does how the die is turned — a d20 swapped
   * for a d12 is the same hand holding a different die.
   * What the stamp is *loaded with* does not — it goes back to following the
   * face, because a number from the die that was put down is not a number this
   * one has.
   */
  fun base(die: Die) {
    if (die.id == state.draft.die.id) return
    drafts.save(state.draft)
    state =
      DesignerState(
        draft = drafts.load(die),
        nib = state.nib,
        colorArgb = state.colorArgb,
        guideShown = state.guideShown,
        choosable = state.choosable,
        clipboard = state.clipboard,
        stampSize = state.stampSize,
        view = state.view,
        turn = state.turn,
        spinning = state.spinning,
      )
  }

  /** A face was chosen, from the strip or by swiping. */
  fun show(cell: Int) {
    if (cell !in 0 until state.draft.cells) return
    state = state.copy(cell = cell)
  }

  /** The pen was changed, or the eraser picked up. */
  fun use(nib: Nib) {
    state = state.copy(nib = nib)
  }

  /** A colour was chosen. The eraser is put down, because a coloured eraser is not a thing. */
  fun ink(colorArgb: Int) {
    state = state.copy(colorArgb = colorArgb, nib = if (state.nib.erases) Nib.Medium else state.nib)
  }

  /**
   * The stamp was loaded with something else, or made bigger or smaller.
   *
   * One way in for both, because both are the same kind of thing — how the
   * next stamp will come out, the way the nib and the colour are for the next
   * stroke. Whichever is not given is left as it was.
   *
   * The text is kept as somebody typed it, including empty: a field that
   * refuses a character is a field nobody can correct a mistake in. What the
   * font can and cannot draw is said by the row ([DesignerState.canStamp]) and
   * refused at the tap, where nothing has been lost.
   */
  fun stamp(
    text: String? = state.stampText,
    size: StampSize = state.stampSize,
  ) {
    state = state.copy(stampText = text, stampSize = size)
  }

  /**
   * Puts each face's own number on it, in one tap
   * (`docs/face-designer.md`, "The stamp").
   *
   * The starting point somebody who wants a numbered die rather than a drawn
   * one begins from: the number the tray would print, where the tray would
   * print it, in the ink in the pen. Faces already carrying a stamp are left
   * alone, so pressing it twice changes nothing.
   */
  fun fillNumbers() {
    state = state.copy(draft = FaceStamp.fill(state.draft, state.colorArgb))
    drafts.save(state.draft)
  }

  /**
   * Lays the standard pips on all six faces of a d6, in one tap
   * (`docs/face-designer.md`, "Fill all with eyes").
   *
   * The other half of "fill all with numbers", and its opposite: a face
   * carries pips or a numeral and never both, so this takes the numerals off
   * as it goes. A die that cannot be pipped is left alone, and no button
   * offers it one ([DesignerState.canPip]).
   */
  fun fillEyes() {
    state = state.copy(draft = FaceEyes.fill(state.draft, state.colorArgb))
    drafts.save(state.draft)
  }

  /** Takes the pips off again, which is the undo for somebody who pressed it to see. */
  fun clearEyes() {
    state = state.copy(draft = FaceEyes.clear(state.draft))
    drafts.save(state.draft)
  }

  /** The guide was turned on or off. */
  fun showGuide(shown: Boolean) {
    state = state.copy(guideShown = shown)
  }

  /**
   * The other tab was chosen.
   *
   * Nothing else moves: the face in front of the player, the pen, the colour
   * and how the die is turned all stay as they were, so the two tabs are two
   * views of one drawing rather than two screens with their own memories.
   */
  fun look(view: DesignerView) {
    state = state.copy(view = view)
  }

  /**
   * A finger dragged across the stage, by [across] and [down] of its width and
   * height.
   *
   * **The drag unticks Spin.** A die that went on turning under the finger
   * holding it would be a die fighting back, and the tick is how somebody puts
   * it back to turning on its own.
   */
  fun turned(
    across: Float,
    down: Float,
  ) {
    state = state.copy(turn = state.turn.dragged(across, down), spinning = false)
  }

  /** Spin was ticked or unticked. */
  fun spin(spinning: Boolean) {
    state = state.copy(spinning = spinning)
  }

  /**
   * [seconds] of the turn the die makes on its own have passed.
   *
   * Taken as an amount of time rather than as a step, so the die turns at the
   * same rate whatever the panel is running at — which is the same bargain the
   * tray makes with its own frames (`docs/physics-and-rendering.md`). A tick
   * that arrives after somebody has taken hold of the die does nothing.
   */
  fun spun(seconds: Float) {
    if (!state.spinning) return
    state = state.copy(turn = state.turn.spun(seconds))
  }

  /**
   * A finger finished, and left [dots] behind.
   *
   * Taken whole rather than point by point: a half-drawn line is the screen's
   * business until the finger lifts, and a model that recorded every sample
   * would have an undo step per pixel.
   *
   * One way in for every tool, because what a gesture leaves is the tool's
   * business rather than the screen's ([DesignerState.markOf]): a pen leaves a
   * line, the bucket leaves a region from the one dot it was put down on, and
   * a gesture that leaves nothing is not a step to undo.
   */
  fun drew(dots: List<Dot>) {
    val mark = state.markOf(dots) ?: return
    state = state.copy(draft = state.draft.onFace(state.cell) { it.draw(mark) })
    drafts.save(state.draft)
  }

  /**
   * Takes [step] on the face in front of the player.
   *
   * One way in rather than three: undo, redo and clear differ only in which of
   * `FaceDrawing`'s steps they take, and each of them is the same "change the
   * face, write the draft down" either side of that. The screen names the step
   * it means, so nothing is lost at the call.
   */
  fun take(step: Step) {
    state =
      state.copy(
        draft =
          state.draft.onFace(state.cell) { face ->
            when (step) {
              Step.Back -> face.undo()
              Step.Forward -> face.redo()
              Step.Clear -> face.clear()
            }
          },
      )
    drafts.save(state.draft)
  }

  /**
   * Takes a copy of the face in front of the player.
   *
   * The drawing rather than its history: what is copied is what is on the
   * face, and the undo stack belongs to the face it was made on.
   */
  fun copyFace() {
    state = state.copy(clipboard = state.face.marks)
  }

  /**
   * Puts the copy down on this face, turned and mirrored by [transform].
   *
   * One step, which one press of undo takes back — a paste is an action, not
   * however many marks it happened to carry (`FaceDrawing.paste`).
   */
  fun paste(transform: FaceTransform = FaceTransform()) {
    val pasted = transform.applyTo(state.clipboard, state.draft.outline)
    state = state.copy(draft = state.draft.onFace(state.cell) { it.paste(pasted) })
    drafts.save(state.draft)
  }
}
