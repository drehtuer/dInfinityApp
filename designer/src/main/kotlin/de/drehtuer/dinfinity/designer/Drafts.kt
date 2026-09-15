package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import java.io.File

/**
 * Where a drawing is between one sitting and the next
 * (`docs/face-designer.md`, "Drawing tools").
 *
 * An interface so the designer screen can be tested without a disk, and so
 * *which thread the disk is touched on* stays a wiring decision rather than
 * something the presenter has to remember — the same seam `SetLibrary` draws
 * for the same reason (`docs/architecture.md`, "Threading").
 */
interface Drafts {
  /** The drawing on [die], or a blank one when there is none. */
  fun load(die: Die): Draft

  /** Writes [draft] down. */
  fun save(draft: Draft)

  companion object {
    /** Drafts that do not outlive the sitting. What a test uses, and the fallback. */
    val NONE: Drafts =
      object : Drafts {
        override fun load(die: Die): Draft = Draft(die = die)

        override fun save(draft: Draft) = Unit
      }
  }
}

/**
 * Drafts as files in a folder, one per die.
 *
 * One file per die rather than one file for everything, because that is what
 * the screen asks for: it opens on a die and wants that die's drawing, and a
 * single file would be every drawing read and rewritten for every stroke.
 *
 * **Fifty drafts, and the one nobody has touched for longest makes room.**
 * `docs/face-designer.md` caps them to bound storage and export time. Refusing
 * the fifty-first would be a dead end — there is no screen yet on which to
 * delete one ("My dice", `docs/TODO.md` 4.6) — and a cap that cannot be
 * reached is not a cap, so the oldest goes, which is the same answer the
 * history's fifty thousand rows already take.
 *
 * Writing is atomic: the text goes to a neighbouring file and is renamed over
 * the real one. A draft half-written by a process that died is a draft that
 * will not read back, and the drawing it replaced would be gone.
 */
class DraftStore(
  private val directory: File,
  private val limit: Int = MAX_DRAFTS,
) : Drafts {
  override fun load(die: Die): Draft {
    val file = fileFor(die.id)
    if (!file.isFile) return Draft(die = die)
    val text = runCatching { file.readText() }.getOrNull() ?: return Draft(die = die)
    return DraftFile.read(text, die) ?: Draft(die = die)
  }

  override fun save(draft: Draft) {
    // A drawing with nothing on it is not a draft. Saving one would let
    // opening the designer and leaving it push somebody's oldest real drawing
    // over the limit.
    if (draft.blank) {
      forget(draft.die.id)
      return
    }
    directory.mkdirs()
    val file = fileFor(draft.die.id)
    val partial = File(directory, "${file.name}$PARTIAL")
    runCatching {
      partial.writeText(DraftFile.write(draft))
      // `renameTo` rather than a copy: on one filesystem it is the one step
      // that either happened or did not.
      if (!partial.renameTo(file)) partial.delete()
    }.onFailure { partial.delete() }
    makeRoom(keeping = file)
  }

  /** Takes one die's drawing away. */
  fun forget(dieId: String) {
    fileFor(dieId).delete()
  }

  /** The ids of the dice that have a drawing, newest first. */
  fun known(): List<String> =
    files().mapNotNull { file ->
      DraftFile.dieOf(runCatching { file.readText() }.getOrDefault(""))
    }

  /**
   * Drops drafts until there are [limit] of them, oldest first.
   *
   * [keeping] is never dropped even if the clock says it is the oldest: a
   * filesystem whose timestamps are a whole second apart can call the file
   * just written the oldest of fifty, and losing the drawing somebody is
   * looking at is the one outcome this must not have.
   */
  private fun makeRoom(keeping: File) {
    val all = files()
    if (all.size <= limit) return
    all
      .sortedByDescending(File::lastModified)
      .drop(limit)
      .filterNot { it.name == keeping.name }
      .forEach { it.delete() }
  }

  private fun files(): List<File> = directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(SUFFIX) }

  /**
   * The file one die's drawing lives in.
   *
   * The id is a slug — `Die.IdPattern` allows lower-case letters, digits and
   * hyphens and nothing else — so it is already a file name. It is filtered
   * here all the same, because a name that reaches the filesystem is the last
   * place to find out that an id was something else.
   */
  private fun fileFor(dieId: String): File = File(directory, dieId.filter { it in ALLOWED } + SUFFIX)

  companion object {
    /**
     * How many drawings a device keeps (`docs/face-designer.md`,
     * "Constraints").
     */
    const val MAX_DRAFTS: Int = 50

    /** The folder these live in, under the app's own files. */
    const val DIRECTORY: String = "drafts"

    private const val SUFFIX = ".json"
    private const val PARTIAL = ".part"
    private val ALLOWED = ('a'..'z') + ('0'..'9') + '-'
  }
}
