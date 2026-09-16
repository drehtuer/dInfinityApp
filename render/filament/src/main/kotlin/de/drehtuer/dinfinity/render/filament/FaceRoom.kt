package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.ShapeAtlas

/**
 * The face a label is judged against: the polygon the *mesh* draws, in the
 * coordinates of the cell it is painted from.
 *
 * How much room that polygon has and where in it a number goes is
 * `core/glyphs`' [de.drehtuer.dinfinity.core.glyphs.LabelRoom], because the
 * face designer solves the same question about the same kind of polygon and
 * two solves would eventually disagree (`docs/face-designer.md`, "The stamp").
 * What is left here is the one thing only the renderer can answer: which
 * polygon it is.
 *
 * It is measured off the mesh's own texture coordinates, the same mapping the
 * renderer samples the atlas with, so the box is judged against the face the
 * player will actually see rather than against a second account of the same
 * solid (`docs/architecture.md`, decision 45).
 */
object FaceRoom {
  /** The face's corners in this cell's own coordinates, which run `0..1`. */
  fun cornersOf(
    surface: MeshFace,
    grid: ShapeAtlas.Grid,
    cell: Pair<Int, Int>,
  ): List<Pair<Double, Double>> {
    val (column, row) = cell
    return surface.uvs.map { it.u * grid.columns - column to it.v * grid.rows - row }
  }
}
