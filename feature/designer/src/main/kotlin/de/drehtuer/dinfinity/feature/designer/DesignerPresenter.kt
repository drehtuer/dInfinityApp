package de.drehtuer.dinfinity.feature.designer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.Drafts
import de.drehtuer.dinfinity.designer.FaceDrawing
import de.drehtuer.dinfinity.designer.FaceFill
import de.drehtuer.dinfinity.designer.FaceTransform
import de.drehtuer.dinfinity.designer.GuideMark
import de.drehtuer.dinfinity.designer.Mark
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

/** The bucket draws no line, so it has no width. */
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
  ;

  val erases: Boolean get() = this == Eraser

  /** True for the bucket, which colours a region instead of drawing a line. */
  val fills: Boolean get() = this == Bucket
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
) {
  /** What the screen draws. */
  var state: DesignerState by mutableStateOf(
    DesignerState(draft = drafts.load(die), choosable = choosable),
  )
    private set

  /**
   * The formula that throws the die being drawn, or null when there is none.
   *
   * **The die, not the drawing.** The tray throws the base die as its set
   * defines it; the strokes on the canvas are not on it, because nothing puts
   * an atlas on a die yet (`docs/TODO.md`, Step 3). What it answers today is
   * what the prototype asks it to — how the solid looks in motion, which is
   * the preview the designer has instead of a 3D one.
   */
  val rollable: String? get() = notationOf(state.draft.die)

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
   * The pen, its colour and whether the guide is showing all stay: they are
   * how somebody is working, not what they are working on.
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

  /** The guide was turned on or off. */
  fun showGuide(shown: Boolean) {
    state = state.copy(guideShown = shown)
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
