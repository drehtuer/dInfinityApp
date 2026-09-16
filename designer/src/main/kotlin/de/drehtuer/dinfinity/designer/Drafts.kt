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
      if (!partial.renameTo(file)) partial.discard()
    }.onFailure { partial.discard() }
    makeRoom(keeping = file)
  }

  /**
   * Takes one die's drawing away, and says whether it is gone.
   *
   * Hands back false only for a drawing that is still there afterwards. What
   * this must not do is call a file it could not remove forgotten, because the
   * next [load] of that die will hand the drawing straight back.
   */
  fun forget(dieId: String): Boolean = fileFor(dieId).deleted()

  /**
   * A number that changes when the drawings do.
   *
   * Not a time, though a time is most of it. What [MineSets] needs before it
   * rasterises anything is "have any of these moved on", and the personal
   * package is built from all of them at once — so the answer is one number
   * over the whole folder, and it costs a `stat` per file rather than a
   * re-read of each.
   *
   * Each file contributes its name, its size and when it was last written, and
   * the three are **added** so that the order the filesystem lists them in
   * cannot change the answer. The last modification time alone would not do:
   * two drafts written in the same millisecond have the same one, and adding a
   * second drawing would then look like no change at all.
   */
  fun stamp(): Long =
    files().fold(0L) { total, file -> total + file.name.hashCode() + file.lastModified() + file.length() }

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
      .forEach { it.discard() }
  }

  /**
   * Whether this file is off the disk, having just been asked to go.
   *
   * `delete` answers false twice over: for a file it could not remove, and for
   * one that was not there to begin with. Only the first is a failure — the
   * second is already the outcome every caller here wanted — so the answer is
   * whether the file is there now, not whether this call is what removed it.
   */
  private fun File.deleted(): Boolean = delete() || !exists()

  /**
   * Gets rid of a file that has no business being there, and asks again on the
   * way out when it will not go.
   *
   * For the half-written [PARTIAL] files and for drafts dropped to make room —
   * neither of which anybody is waiting on, so a filesystem that refuses is
   * worth a second attempt rather than an error nobody can act on. A leftover
   * partial is invisible to the rest of this class (it does not end in
   * [SUFFIX]) and the next save of that die writes over it; a draft that would
   * not drop simply goes on counting against [limit] until it does.
   */
  private fun File.discard() {
    if (!deleted()) deleteOnExit()
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
