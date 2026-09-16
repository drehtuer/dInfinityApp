package de.drehtuer.dinfinity.render.filament

/**
 * The table pictures already drawn, and which one goes when there are too many
 * (`docs/tables.md`, "Thumbnails").
 *
 * A thumbnail costs a swap chain, a scene and a wait on the GPU, and the
 * answer never changes: a look's colours, textures and lighting are fixed by
 * the package that shipped it. So it is drawn once and kept — across a visit
 * to the picker and across the next one, because the engine it was drawn with
 * outlives both ([RollThread], `docs/architecture.md`, decision 50).
 *
 * Kept is not the same as kept for ever. Every installed package may ship
 * tables and a picture is a quarter of a megabyte, so the room is bounded here
 * rather than by how many packages somebody installed, and the least recently
 * asked-for goes first — which on this screen means the look furthest up a
 * list nobody has scrolled back to.
 *
 * **A look that could not be drawn is remembered too**, as nothing. A phone
 * whose driver will not hand a frame back would otherwise be asked again for
 * every row, on every visit, for a picture that is never going to arrive; what
 * it gets instead is one attempt and the swatch (`TablesScreen`).
 *
 * Plain Kotlin and single-threaded on purpose: it is reached only from the
 * roll thread, which is where the engine is and therefore where every
 * thumbnail is drawn. Everything it decides — what is kept, what is asked
 * again, what goes when — is testable on a JVM, which is the line decisions 40
 * and 47 draw.
 */
class ThumbnailCache(
  private val capacity: Int = DEFAULT_CAPACITY,
) {
  init {
    require(capacity > 0) { "a cache with room for $capacity pictures is not a cache" }
  }

  /**
   * Least recently asked for first, which is the order they are dropped in.
   *
   * A `LinkedHashMap` in insertion order with the touch done by hand, rather
   * than one in access order: `get` on an access-ordered map is a mutation,
   * and a map that reorders itself when it is read is a thing to be surprised
   * by later.
   */
  private val kept = LinkedHashMap<String, Snapshot?>()

  /** How many looks have been tried, drawn or not. */
  val size: Int get() = kept.size

  /** What is held, least recently asked for first. */
  val keys: List<String> get() = kept.keys.toList()

  /** Whether [key] has been tried at all — which is not whether it produced a picture. */
  fun asked(key: String): Boolean = kept.containsKey(key)

  /**
   * The picture of [key], or null for one that has not been tried and one that
   * could not be drawn alike.
   *
   * Asking counts as using: a look the player is looking at now is the last
   * one that should be dropped to make room for a look they scroll to next.
   */
  fun of(key: String): Snapshot? {
    if (!kept.containsKey(key)) return null
    val picture = kept.remove(key)
    kept[key] = picture
    return picture
  }

  /**
   * Remembers what came of drawing [key] — a picture, or null for an attempt
   * that produced none.
   */
  fun put(
    key: String,
    picture: Snapshot?,
  ) {
    kept.remove(key)
    kept[key] = picture
    while (kept.size > capacity) {
      kept.remove(kept.keys.first())
    }
  }

  /** Forgets everything, so the next visit draws again. */
  fun clear() {
    kept.clear()
  }

  companion object {
    /**
     * How many looks are kept at once.
     *
     * Sixteen is more than any set of installed packages has yet offered — the
     * bundled package ships five and a player may keep six photographs — and
     * at [ThumbnailPlan.MAX_SIDE] it is four megabytes at the very worst,
     * which is a picture of every table somebody owns.
     */
    const val DEFAULT_CAPACITY: Int = 16
  }
}
