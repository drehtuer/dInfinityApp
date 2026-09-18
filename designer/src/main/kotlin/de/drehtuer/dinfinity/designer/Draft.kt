package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die

/**
 * A point on a face, as a fraction of the canvas.
 *
 * Fractions rather than pixels, because a draft outlives the screen it was
 * drawn on: the canvas is whatever size the phone gave it, and the export is
 * 256 px per cell (`docs/face-designer.md`, "Export details"). A stroke stored
 * in pixels would be a stroke that moved when the phone was turned.
 */
data class Dot(
  val x: Float,
  val y: Float,
)

/**
 * One thing on a face: a line of the pen, a region the bucket coloured in, a
 * glyph the stamp put down, or the pips of a pipped d6.
 *
 * A sealed set rather than one class with flags, because they carry different
 * things — a stroke has a nib width and can be the eraser, a fill has neither,
 * a stamp is closed rings with holes in them — and a combination that cannot
 * be drawn is better made impossible than documented. All of them are dots in
 * fractions of the canvas, which is what lets a paste turn or mirror any of
 * them with the same arithmetic (`FaceTransform`).
 */
sealed interface Mark {
  /** The ink. */
  val colorArgb: Int

  /** The path, or the boundary of the region, in fractions of the canvas. */
  val dots: List<Dot>

  /** The same mark with its dots somewhere else — what a turn and a mirror are made of. */
  fun at(dots: List<Dot>): Mark
}

/**
 * A mark made of closed rings, drawn as one shape under the even-odd rule.
 *
 * Two things are: a [Stamp], whose rings are a glyph's outline and the
 * counters that leave the hole in a `0` open, and [Eyes], whose rings are the
 * pips of a pipped d6. They are drawn, exported, turned and written to the
 * draft file identically, so everything that only cares about the ink asks for
 * this rather than for either of them (`feature/designer`'s `FaceInk`,
 * `BitmapAtlas`, `DraftFile`).
 *
 * What tells them apart is what they *mean*: pips and numerals are mutually
 * exclusive on a face, so filling one has to be able to find the other and
 * take it off (`docs/face-designer.md`, "Fill all with eyes").
 */
sealed interface Rings : Mark {
  /** The outlines, each closing itself, in fractions of the canvas. */
  val rings: List<List<Dot>>
}

/**
 * One stroke of the pen, as a path rather than as pixels.
 *
 * Vectors because the drawing is re-rendered at export resolution and because
 * a draft survives process death (`docs/face-designer.md`, "Drawing tools").
 * A bitmap would be neither.
 *
 * @param colorArgb the ink.
 * @param width how wide the nib is, as a fraction of the canvas — so a stroke
 *   drawn on a small phone is the same stroke on a large one.
 * @param erases true for the eraser, which is a stroke like any other and is
 *   why it can be undone one step at a time.
 */
data class Stroke(
  override val dots: List<Dot>,
  override val colorArgb: Int,
  val width: Float,
  val erases: Boolean = false,
) : Mark {
  override fun at(dots: List<Dot>): Stroke = copy(dots = dots)
}

/**
 * A region of one colour, which is what the bucket leaves behind
 * (`docs/face-designer.md`, "Drawing tools").
 *
 * A vector drawing has no pixels to flood, so a fill is a *shape* added to the
 * drawing rather than a change to the ones already there: the boundary of the
 * closed stroke it was dropped inside, or the canvas itself when it was
 * dropped on bare paper (`FaceFill`).
 *
 * @param dots the boundary, in fractions of the canvas. It closes itself — the
 *   last dot joins the first — so a region is stored once rather than once and
 *   a half.
 */
data class Fill(
  override val dots: List<Dot>,
  override val colorArgb: Int,
) : Mark {
  override fun at(dots: List<Dot>): Fill = copy(dots = dots)
}

/**
 * A glyph of the built-in font, put down whole (`docs/face-designer.md`, "The
 * stamp").
 *
 * **Rings rather than one path, because a `0` has a hole in it.** A glyph is
 * closed outlines — the outside of the ink, and a counter for every hole in it
 * — and a hole drawn as a [Fill] of its own would be a blob where the hole is.
 * They are drawn as one shape with the even-odd rule, which is what leaves the
 * hole open however the contours are wound.
 *
 * **One glyph-string is one mark**, not one per contour: `10` on a d20 is four
 * rings and a stamp of `10` is one press of undo, one mark against the face's
 * two hundred, and one thing a turn or a mirror carries whole.
 *
 * @param rings the outlines, each closing itself, in fractions of the canvas.
 * @param colorArgb the ink. A stamp is ink and sits over the paper like a
 *   stroke does ([FaceDrawing.sunk]).
 */
data class Stamp(
  override val rings: List<List<Dot>>,
  override val colorArgb: Int,
) : Rings {
  init {
    require(rings.isNotEmpty() && rings.all { it.size >= CORNERS_OF_A_RING }) {
      "a stamp is closed rings of at least $CORNERS_OF_A_RING dots, not ${rings.map { it.size }}"
    }
  }

  /**
   * Every ring's dots, end to end.
   *
   * What lets a stamp be turned, mirrored and scaled into an atlas by the same
   * arithmetic as every other mark: none of it cares which ring a dot is in,
   * because none of it moves a dot past its neighbours ([at]).
   */
  override val dots: List<Dot> = rings.flatten()

  /**
   * The same glyph with its dots somewhere else.
   *
   * The rings are cut back out of the flat list by the lengths they had, which
   * is sound because every transform is one dot in and one dot out, in order.
   * A list of another length is not this mark moved, so it is refused rather
   * than cut up wrongly.
   */
  override fun at(dots: List<Dot>): Stamp =
    if (dots.size != this.dots.size) this else copy(rings = cutRings(rings.map { it.size }, dots))

  companion object {
    /**
     * The glyph [dots] make when they are cut into rings of these [lengths],
     * or null when they are not those rings.
     *
     * What reads a stamp back off a draft file: the file keeps the dots of
     * every mark the one way, flat, with a stamp's ring lengths beside them.
     * Lengths that do not add up to the dots they were written with are not a
     * glyph this wrote, and the mark is dropped rather than read as some
     * other shape (`DraftFile`).
     */
    fun of(
      lengths: List<Int>,
      dots: List<Dot>,
      colorArgb: Int,
    ): Stamp? = if (!areRings(lengths, dots)) null else Stamp(rings = cutRings(lengths, dots), colorArgb = colorArgb)
  }
}

/**
 * The pips of one face of a pipped d6 (`docs/face-designer.md`, "Fill all with
 * eyes").
 *
 * One ring per pip and **one mark for the face**, for the same reason a
 * stamped `10` is one mark: what a finger put down in one press comes off in
 * one press of undo, counts once against the two hundred, and is carried whole
 * by a turn or a mirror. The rings are disjoint circles, so the even-odd rule
 * that keeps the hole in a `0` open fills every one of them.
 *
 * @param rings each pip's outline, in fractions of the canvas.
 * @param colorArgb the ink the pen was holding. Pips are ink and sit over the
 *   paper like a stroke does ([FaceDrawing.sunk]).
 */
data class Eyes(
  override val rings: List<List<Dot>>,
  override val colorArgb: Int,
) : Rings {
  init {
    require(rings.isNotEmpty() && rings.all { it.size >= CORNERS_OF_A_RING }) {
      "pips are closed rings of at least $CORNERS_OF_A_RING dots, not ${rings.map { it.size }}"
    }
  }

  override val dots: List<Dot> = rings.flatten()

  override fun at(dots: List<Dot>): Eyes =
    if (dots.size != this.dots.size) this else copy(rings = cutRings(rings.map { it.size }, dots))

  companion object {
    /**
     * The pips [dots] make when they are cut into rings of these [lengths], or
     * null when they are not those rings — what reads them back off a draft
     * file (`DraftFile`).
     */
    fun of(
      lengths: List<Int>,
      dots: List<Dot>,
      colorArgb: Int,
    ): Eyes? = if (!areRings(lengths, dots)) null else Eyes(rings = cutRings(lengths, dots), colorArgb = colorArgb)
  }
}

/** Three corners is the fewest that can enclose anything. */
private const val CORNERS_OF_A_RING = 3

/** Whether [lengths] cut [dots] into rings that each enclose something. */
private fun areRings(
  lengths: List<Int>,
  dots: List<Dot>,
): Boolean = lengths.isNotEmpty() && lengths.all { it >= CORNERS_OF_A_RING } && lengths.sum() == dots.size

/** [dots] in runs of these [lengths], which is what a ring mark's rings are. */
private fun cutRings(
  lengths: List<Int>,
  dots: List<Dot>,
): List<List<Dot>> {
  val starts = lengths.runningFold(0) { at, ring -> at + ring }
  return lengths.mapIndexed { ring, length -> dots.subList(starts[ring], starts[ring] + length).toList() }
}

/**
 * What has been drawn on one cell, and what can be taken back.
 *
 * The two stacks hold **whole states rather than single marks**, so that one
 * action is one step whatever it did. The first version stacked strokes, and
 * then clearing a face — which takes away many at once — could only be undone
 * one stroke at a time, which is not what "clear" means to the finger that
 * pressed it. A step is an action, and an action is a list.
 *
 * Drawing after an undo drops the redo, which is what every drawing program
 * does and what a finger expects: the branch you left is not waiting for you.
 *
 * **Fills sink under the strokes** (`docs/face-designer.md`, "The fill
 * bucket"). The list is kept with every [Fill] at the front, oldest first, and
 * every [Stroke] behind them, each in the order it arrived, so a later fill is
 * over an earlier fill and under all the ink. A bucket colours the paper, not
 * the line: a fill that landed on top would hide the drawing it was aimed at,
 * and the way to cover ink is the eraser.
 */
data class FaceDrawing(
  val marks: List<Mark> = emptyList(),
  /** What the face looked like before each step, newest last. */
  val past: List<List<Mark>> = emptyList(),
  /** What it looked like after each step that was taken back, newest last. */
  val future: List<List<Mark>> = emptyList(),
) {
  /** True when there is nothing on this face yet. */
  val blank: Boolean get() = marks.isEmpty()

  val canUndo: Boolean get() = past.isNotEmpty()
  val canRedo: Boolean get() = future.isNotEmpty()

  /** True when this face is at the limit and will not take another mark. */
  val full: Boolean get() = marks.size >= MAX_MARKS

  /**
   * Adds [mark].
   *
   * At the limit the drawing is returned unchanged rather than throwing: a
   * finger is already on the glass, and the screen's job is to have warned
   * before this (`docs/face-designer.md`, "Constraints").
   */
  fun draw(mark: Mark): FaceDrawing = if (full) this else step(sunk(marks + mark))

  /**
   * Adds all of [pasted], in one step (`docs/face-designer.md`, "Copy and
   * paste").
   *
   * It **merges** rather than replaces: the drawing already on the face stays
   * and the copy lands over it, which is what the border-on-every-face case
   * wants. A face that should be replaced is cleared first — two presses, both
   * of them undoable — whereas a paste that replaced would take work away that
   * nobody asked it to.
   *
   * A paste that would carry the face past the limit is **refused whole**: a
   * paste is one action, and half of what was copied is not what was copied.
   * Nothing is pasted from an empty clipboard either, so an idle press cannot
   * fill the undo stack with steps that changed nothing.
   */
  fun paste(pasted: List<Mark>): FaceDrawing =
    if (pasted.isEmpty() || marks.size + pasted.size > MAX_MARKS) this else step(sunk(marks + pasted))

  /** Takes everything off this face, in one step that can be taken back. */
  fun clear(): FaceDrawing = if (blank) this else step(emptyList())

  /**
   * Every mark [taking] is true of taken off and [added] put on, **in one
   * step**.
   *
   * What pips and numerals need of each other: the two are mutually exclusive
   * on a face, so putting one on takes the other off
   * (`docs/face-designer.md`, "Fill all with eyes"). One step rather than a
   * clear and a paste, because one press of a button is one press of undo —
   * a face that needed undoing twice to get back to where it was would be a
   * button that did two things.
   *
   * A swap that would change nothing is not a step, so pressing a fill twice
   * leaves the undo stack alone, and one that would carry the face past the
   * limit is refused whole, exactly as a paste is.
   */
  fun swap(
    taking: (Mark) -> Boolean,
    added: List<Mark> = emptyList(),
  ): FaceDrawing {
    val kept = marks.filterNot(taking)
    if (kept.size == marks.size && added.isEmpty()) return this
    if (kept.size + added.size > MAX_MARKS) return this
    return step(sunk(kept + added))
  }

  fun undo(): FaceDrawing =
    if (!canUndo) {
      this
    } else {
      FaceDrawing(
        marks = past.last(),
        past = past.dropLast(1),
        future =
          future + listOf(marks),
      )
    }

  fun redo(): FaceDrawing =
    if (!canRedo) {
      this
    } else {
      FaceDrawing(
        marks = future.last(),
        past = past + listOf(marks),
        future = future.dropLast(1),
      )
    }

  /** One action: what the face becomes, with what it was pushed behind it. */
  private fun step(to: List<Mark>) = FaceDrawing(marks = to, past = past + listOf(marks), future = emptyList())

  companion object {
    /**
     * How many marks one face may carry.
     *
     * A bound on storage and on export time rather than on anybody's
     * patience: two hundred strokes is a drawing, and the screen warns
     * before it (`docs/face-designer.md`, "Constraints").
     */
    const val MAX_MARKS: Int = 200

    /**
     * [marks] with the fills at the front, each group in the order it arrived.
     *
     * A stable partition rather than a sort: what it decides is only whether a
     * mark is paper or ink, and two fills keep the order they were made in.
     */
    fun sunk(marks: List<Mark>): List<Mark> = marks.filterIsInstance<Fill>() + marks.filterNot { it is Fill }
  }
}

/**
 * A die being drawn (`docs/face-designer.md`).
 *
 * The die is copied from the catalogue or from an installed set, so the face
 * *values* are inherited and what is being drawn is only what a face looks
 * like. Nothing here can change what the die scores.
 *
 * Held as a map rather than a list so an untouched face costs nothing: a d20
 * somebody has drawn one face of is one entry, not twenty.
 */
data class Draft(
  val die: Die,
  val faces: Map<Int, FaceDrawing> = emptyMap(),
) {
  /** What has been drawn on [cell], which is nothing until something has. */
  fun face(cell: Int): FaceDrawing = faces[cell] ?: FaceDrawing()

  /** The outline every cell of this die is masked into. */
  val outline: FaceOutline get() = FaceOutline.of(die.shape)

  /** How many cells there are to draw on. */
  val cells: Int get() = die.faces.size

  /** True when nothing has been drawn on any face. */
  val blank: Boolean get() = faces.values.all(FaceDrawing::blank)

  /** The guide for [cell] — one number, or a d4's three (`FaceGuide`). */
  fun guide(cell: Int): List<GuideMark> = FaceGuide.of(die, cell)

  /**
   * [change] applied to one cell.
   *
   * A cell outside the die is left alone rather than added: the map is keyed
   * by cell index and a key no face has would be a drawing nothing shows.
   */
  fun onFace(
    cell: Int,
    change: (FaceDrawing) -> FaceDrawing,
  ): Draft {
    if (cell !in die.faces.indices) return this
    return copy(faces = faces + (cell to change(face(cell))))
  }
}
