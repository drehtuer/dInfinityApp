package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.AtlasImage

/**
 * How a surface names the artwork it wants (`docs/dice-sets.md`, "Textures").
 *
 * A die's `texture` is relative to *its own package's folder* — two sets may
 * both ship `textures/d20.png` and they are two different pictures — so the
 * path alone cannot address one. The key is the package and the path together,
 * and it is a plain string because the seam it crosses is
 * `(String) -> Texture?`: what a die wants is decided on this side of [Stage],
 * and finding the folder and decoding the file happens on the other, in `:app`
 * over `dicesets/install` (`docs/architecture.md`, decision 40).
 *
 * The separator is `::` rather than `/`. A set id is a slug and cannot contain
 * one (`DiceSet.IdPattern`), so a key can always be taken apart again — and,
 * more to the point, a path that arrives here *unqualified* has no `::` in it
 * and is refused rather than read as a package called `textures`.
 *
 * A table look's pictures are keyed the same way: their package is the one
 * the validator read the look out of (`TableLook.packageId`), so the bundled
 * felt is `builtin::tables/felt-albedo.webp` and a table can only ever name its
 * own package's files (`docs/tables.md`, "Textures").
 */
object AtlasKey {
  /** What separates the package from the path inside it. */
  const val SEPARATOR: String = "::"

  /** The key for [texturePath] inside the package called [setId]. */
  fun of(
    setId: String,
    texturePath: String,
  ): String = "$setId$SEPARATOR$texturePath"

  /**
   * The package and the path [key] names, or `null` when it names no package.
   *
   * Split at the *first* separator: a set id cannot contain one and a path
   * conceivably could, so everything after the first is the path.
   */
  fun split(key: String): Pair<String, String>? {
    val at = key.indexOf(SEPARATOR)
    if (at <= 0) return null
    val path = key.substring(at + SEPARATOR.length)
    return if (path.isEmpty()) null else key.substring(0, at) to path
  }
}

/**
 * Every atlas this engine has decoded, kept for as long as the engine is.
 *
 * Two reasons it is a cache rather than a load per throw. The cheap one is
 * that decoding a 2048-square PNG is tens of milliseconds and `20d20` is
 * twenty dice wearing one picture. The one that matters is that a `Texture` is
 * a native handle rather than an object a garbage collector knows about: one
 * made per throw and dropped is a leak the JVM cannot see, so each is made
 * once, held against its key, and destroyed in [close] by whoever owns this —
 * which is [FilamentEngine], because artwork belongs to a *package* and
 * outlives any one surface or visit (`docs/architecture.md`, decision 50).
 *
 * **A miss is remembered too.** A die naming an atlas that will not decode is
 * a die that prints its labels, and asking the disk about it again on every
 * throw would be re-reading a file that has already answered.
 *
 * **But not for ever: a key's file can change under it.** The face designer
 * rebuilds "My dice" every time *Roll it* is pressed, under the same package
 * and the same path, so a key alone said "already have it" and the tray went
 * on throwing the drawing as it was the first time — eyes the player had since
 * cleared, strokes they had not drawn yet. Each entry remembers the [stamp] it
 * was made under, and one that no longer matches is made again. A miss is
 * stamped too, so a package installed after its die was first asked about is
 * found.
 *
 * The handle it replaces is given back at once. That is safe because a
 * package only changes while the player is on another screen — the designer,
 * the sets screen — and a visit's scene goes with the visit, so nothing on
 * the GPU still points at it (`RollThread`).
 *
 * The type is left open so that the *caching* — which is the decision here —
 * can be tested on a JVM, where no Filament handle can be made at all.
 *
 * @param artwork where a key's pixels come from, or `null` when there are none.
 * @param upload how those pixels become something the GPU can sample.
 * @param destroy how one of those is given back.
 * @param stamp which version of [key]'s file is there now; asked on every
 *   lookup, so it must be cheap. The default never changes, which keeps
 *   whatever was made first.
 */
class AtlasCache<T : Any>(
  private val artwork: (String) -> AtlasImage?,
  private val upload: (AtlasImage) -> T,
  private val destroy: (T) -> Unit,
  private val stamp: (String) -> Any? = { null },
) : AutoCloseable {
  private class Held<T>(
    val stamp: Any?,
    val made: T?,
  )

  private val held = mutableMapOf<String, Held<T>>()

  /** How many atlases are on the GPU, which is what a test asks to see the cache work. */
  val uploaded: Int get() = held.values.count { it.made != null }

  /** How many keys have been asked about, answered or not. */
  val asked: Int get() = held.size

  /**
   * What [key] names, made once per version of its file, or `null` when there
   * is nothing to draw.
   */
  fun of(key: String): T? {
    val now = stamp(key)
    val before = held[key]
    if (before != null && before.stamp == now) return before.made
    before?.made?.let(destroy)
    val made = artwork(key)?.let(upload)
    held[key] = Held(now, made)
    return made
  }

  /** Gives every handle back. Nothing made from this may be used again. */
  override fun close() {
    held.values.mapNotNull { it.made }.forEach(destroy)
    held.clear()
  }
}
