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
 * @param stamped which version of one package's file is on disk
 *   ([InstalledArtwork.stamp]); the default knows none.
 */
class DieArtwork(
  private val stamped: (String, String) -> String? = { _, _ -> null },
  private val read: (String, String) -> AtlasDecode,
) : (String) -> AtlasImage? {
  /**
   * Which version of [key]'s file is on disk, or `null` when there is none
   * or [key] names no package — what the renderer's cache compares to know a
   * package was rewritten under the same name (`AtlasCache`).
   */
  fun stamp(key: String): String? {
    val (setId, path) = AtlasKey.split(key) ?: return null
    return stamped(setId, path)
  }

  /**
   * The picture [key] names, or `null` when there is not one.
   *
   * A key that names no package is not an error and not a miss worth
   * reporting: nothing the tray asks for is spelt that way, and a path on its
   * own names no file — two packages may both ship it.
   *
   * A table's pictures come through here too, keyed by the package the look
   * was read from (`docs/tables.md`, "Textures"), and the bundled package's
   * are answered by [InstalledArtwork] from its resources.
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
