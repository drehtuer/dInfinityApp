package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.dicesets.install.AtlasDecode
import de.drehtuer.dinfinity.render.filament.AtlasKey

/**
 * The far side of the renderer's artwork seam, wired up in the one place that
 * is allowed to know both halves.
 *
 * The tray asks for a key — a package and a path inside it ([AtlasKey]) — and
 * knows nothing about where a package lives or how a PNG becomes pixels.
 * `dicesets/install` knows both and knows nothing about a tray. This is the
 * join, and it is in `:app` for the same reason `RollWiring` is: naming
 * `dicesets/install` from `render/filament` would be a renderer that can read
 * the disk (`docs/architecture.md`, decision 40).
 *
 * It is asked once per key per engine, because the cache on the other side
 * remembers both a picture and the absence of one ([AtlasKey]'s `AtlasCache`),
 * so this may read a file and decode it without being on a hot path.
 *
 * @param read what answers for one package and one path — [InstalledArtwork]
 *   in the app, and something simpler in a test, which is the only reason it
 *   is a parameter.
 */
class DieArtwork(
  private val read: (String, String) -> AtlasDecode,
) : (String) -> AtlasImage? {
  /**
   * The picture [key] names, or `null` when there is not one.
   *
   * A key that names no package is not an error and not a miss worth
   * reporting: it is a table look's floor or wall texture, which carries a
   * path and no package and so cannot be resolved yet
   * (`docs/TODO.md`, "Open questions").
   *
   * Everything else that goes wrong — a package that is not installed, a path
   * that tries to leave it, a file that will not decode — comes back from
   * [read] as lines of a report, and the die answers them by printing its
   * labels. Nothing here throws, because this runs on the roll thread and a
   * stranger's PNG must not be able to take a roll with it.
   */
  override fun invoke(key: String): AtlasImage? {
    val (setId, path) = AtlasKey.split(key) ?: return null
    return read(setId, path).drawn
  }
}
