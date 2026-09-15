package de.drehtuer.dinfinity.feature.designer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.FaceDrawing
import de.drehtuer.dinfinity.designer.GuideMark
import de.drehtuer.dinfinity.designer.Stroke

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
  ;

  val erases: Boolean get() = this == Eraser
}

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
   * The die a change of base is waiting to be confirmed for, or null.
   *
   * Its own field rather than a boolean, because what is being confirmed is
   * *which* die: a dialog that said "throw this away?" and then had to look up
   * what somebody had tapped is a dialog that can answer the wrong question.
   */
  val changingTo: Die? = null,
) {
  /** The die being drawn on. */
  val die: Die get() = draft.die

  /** True when there is more than one die to start from, so a chooser is worth drawing. */
  val baseChoosable: Boolean get() = choosable.size > 1

  /** The drawing on the face in front of the player. */
  val face: FaceDrawing get() = draft.face(cell)

  /** The numbers under it — one, or a d4's three (`docs/dice-sets.md`, "The d4"). */
  val guide: List<GuideMark> get() = if (guideShown) draft.guide(cell) else emptyList()

  val canUndo: Boolean get() = face.canUndo
  val canRedo: Boolean get() = face.canRedo

  /**
   * True when this face is close enough to the limit to say so.
   *
   * The warning comes before the refusal, because a drawing that simply stops
   * taking strokes reads as a broken screen (`docs/face-designer.md`,
   * "Constraints").
   */
  val nearlyFull: Boolean get() = face.strokes.size >= FaceDrawing.MAX_STROKES - ROOM_TO_WARN

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
 */
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
) {
  /** What the screen draws. */
  var state: DesignerState by mutableStateOf(DesignerState(draft = Draft(die = die), choosable = choosable))
    private set

  /**
   * Start again on a different die (`docs/face-designer.md`, "Flow").
   *
   * A different die is a different draft: the faces are a different shape,
   * there are a different number of them, and the values under the guide are
   * that die's. Nothing carries over, so a drawing with anything on it is
   * **asked about first** — losing an evening's work to a mis-tap on a row of
   * dice is not a thing that should be possible.
   */
  fun base(die: Die) {
    if (die.id == state.draft.die.id) return
    state = if (state.draft.blank) state.startingOn(die) else state.copy(changingTo = die)
  }

  /**
   * Answers the question [base] asked: start again on that die, or keep
   * drawing.
   *
   * One function and not two, because it is one question with two answers —
   * and because the die being confirmed is held in the state rather than
   * passed back in, so there is no way for the answer to arrive about a
   * different die than the one that was asked about.
   */
  fun startOver(confirmed: Boolean) {
    val die = state.changingTo
    state = if (confirmed && die != null) state.startingOn(die) else state.copy(changingTo = null)
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

  /** The guide was turned on or off. */
  fun showGuide(shown: Boolean) {
    state = state.copy(guideShown = shown)
  }

  /**
   * A finger finished a stroke.
   *
   * Taken whole rather than point by point: a half-drawn line is the screen's
   * business until the finger lifts, and a model that recorded every sample
   * would have an undo step per pixel.
   *
   * A stroke of fewer than two dots is a tap, and a tap is not a mark.
   */
  fun drew(dots: List<Dot>) {
    if (dots.size < 2) return
    val stroke =
      Stroke(
        dots = dots,
        colorArgb = state.colorArgb,
        width = state.nib.width,
        erases = state.nib.erases,
      )
    state = state.copy(draft = state.draft.onFace(state.cell) { it.draw(stroke) })
  }

  /** Back one step on this face. */
  fun undo() {
    state = state.copy(draft = state.draft.onFace(state.cell) { it.undo() })
  }

  /** Forward one step on this face. */
  fun redo() {
    state = state.copy(draft = state.draft.onFace(state.cell) { it.redo() })
  }

  /** Takes this face back to blank, in one step that can be undone. */
  fun clear() {
    state = state.copy(draft = state.draft.onFace(state.cell) { it.clear() })
  }
}

/**
 * A fresh drawing on [die], keeping the tools where they were.
 *
 * Out here rather than in the presenter because it is a mapping and not a
 * decision — and because the presenter is at detekt's ceiling, which is a fair
 * warning rather than an obstacle.
 *
 * The pen, its colour and whether the guide is showing all stay: they are how
 * somebody is working, not what they are working on.
 */
private fun DesignerState.startingOn(die: Die): DesignerState =
  DesignerState(
    draft = Draft(die = die),
    nib = nib,
    colorArgb = colorArgb,
    guideShown = guideShown,
    choosable = choosable,
  )
