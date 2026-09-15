package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.collection.CollectionFiles
import de.drehtuer.dinfinity.core.collection.CollectionLimits
import de.drehtuer.dinfinity.dicesets.install.ArchiveLimits
import de.drehtuer.dinfinity.dicesets.install.PackageRoot
import de.drehtuer.dinfinity.dicesets.install.RejectionReason
import java.io.File

/**
 * Finding the collection in a repository somebody else keeps
 * (`docs/dice-notation.md`, "Export and import").
 *
 * A dice set is a folder and a collection is a *file*, so the one thing that
 * has to be decided before a repository can be imported from is which file.
 * The rule is as small as it can be made:
 *
 * > **A repository holds one collection, at its root, named the way the app
 * > names one — `<something>.dinfinity.json`.**
 *
 * That is the same shape `docs/dice-sets.md` gives a package — a well-known
 * name marking the thing — and it is a rule somebody can follow without
 * reading anything: export from the app, commit the file the app wrote, push.
 * At the root rather than anywhere, because a repository of stat blocks is
 * full of JSON and "anywhere" would mean guessing which one; one rather than
 * several, because a link names a repository and not a file, and an import
 * that quietly picked one of two would be picking for somebody.
 *
 * Every other answer is a refusal that says what it looked for and what it
 * found instead, which is the whole difference between a rule and a mystery.
 */
object CollectionInRepository : PackageRoot {
  /**
   * What unpacking a repository may cost.
   *
   * The same megabyte a collection itself is capped at
   * ([CollectionLimits.MAX_BYTES]), and only JSON is written at all. Both
   * follow from the same observation: the app is here for one page of JSON,
   * and a repository that expands to more than the file it carries is asking
   * the phone to unpack a library to read a page. Everything else — the paths,
   * the links, the entry count — is the dice set's bound and the dice set's
   * check, unchanged (`SECURITY.md`).
   */
  val Limits: ArchiveLimits =
    ArchiveLimits(
      maxExtractedBytes = CollectionLimits.MAX_BYTES.toLong(),
      allowedExtensions = setOf(JSON),
    )

  /**
   * The folder holding the one collection, or why there is not one.
   *
   * @param subfolder never used: a link to a collection names a repository,
   *   not a folder inside one. It is in the signature because
   *   [PackageRoot] is shared with dice sets, whose URLs can name one.
   */
  override fun of(
    destination: File,
    subfolder: String?,
  ): PackageRoot.Found {
    val root = rootOf(destination)
    val here = collectionsIn(root)
    val deeper = collectionsUnder(root).filterNot { it.parentFile == root }
    return when {
      here.size == 1 -> PackageRoot.Found.Folder(root)
      here.size > 1 -> missing("this repository holds more than one collection at its root: ${names(root, here)}")
      deeper.isNotEmpty() ->
        missing(
          "this repository has no collection at its root; there is one in ${names(root, deeper)}, " +
            "and a collection is imported from the root",
        )
      else ->
        missing(
          "there is no collection in this repository: one is a file called " +
            "'something${CollectionFiles.EXTENSION}', at the root",
        )
    }
  }

  /**
   * The root of the repository inside the folder it was unpacked into.
   *
   * Every forge wraps a repository one folder deep — `repo-<sha>/` — and so
   * does anybody who zips a directory rather than its contents. One folder and
   * nothing in it is therefore that wrapper; anything else is the root itself.
   * Deliberately not a search: "the root" has to mean something definite, or
   * the rule above is not a rule.
   */
  fun rootOf(unpacked: File): File {
    val inside = unpacked.listFiles().orEmpty()
    return inside.singleOrNull()?.takeIf { it.isDirectory } ?: unpacked
  }

  /** The collections directly inside [folder], in a stable order. */
  fun collectionsIn(folder: File): List<File> =
    folder
      .listFiles()
      .orEmpty()
      .filter { it.isFile && CollectionFiles.isCollection(it.name) }
      .sortedBy(File::getName)

  /** Every collection anywhere under [folder], so a refusal can say where they are. */
  private fun collectionsUnder(folder: File): List<File> =
    folder
      .walkTopDown()
      .filter { it.isFile && CollectionFiles.isCollection(it.name) }
      .sortedBy(File::getName)
      .toList()

  private fun missing(detail: String): PackageRoot.Found.Missing =
    PackageRoot.Found.Missing(RejectionReason.NotInTheArchive, detail)

  /** Where the files are, said the way the repository's own tree says it. */
  private fun names(
    root: File,
    files: List<File>,
  ): String =
    files.joinToString(", ") { file ->
      "'${file.relativeToOrSelf(root).path.replace(File.separatorChar, '/')}'"
    }

  /** The only extension written while a repository is unpacked. */
  private const val JSON = "json"
}
