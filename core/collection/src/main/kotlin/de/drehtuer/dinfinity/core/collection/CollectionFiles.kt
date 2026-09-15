package de.drehtuer.dinfinity.core.collection

/**
 * What a collection file is called
 * (`docs/dice-notation.md`, "Export and import").
 *
 * One rule, in one place, because both ends of the same journey need it. The
 * app names what it exports `<slug>.dinfinity.json`; importing from a git
 * repository has to recognise that same name at the repository's root, since a
 * repository is a folder of files and a collection is one file. Two spellings
 * of "this is a collection" would be two rules to keep in step, and the one
 * that drifted would be the one nobody noticed until a repository that plainly
 * holds a collection was refused.
 *
 * It is the *name* that is recognised and nothing else. Whether the file is a
 * collection is `CollectionReader`'s word, and a repository that names a file
 * this way and fills it with something else is refused by the reader exactly
 * as a pasted link to the same bytes would be.
 */
object CollectionFiles {
  /**
   * What a collection file's name ends in, so one can be recognised at a
   * glance in a folder of other JSON.
   *
   * Two extensions rather than a bare `.json` on purpose: a repository of stat
   * blocks is full of JSON, and `.dinfinity.json` says which one of them is
   * meant for this app while staying a perfectly ordinary JSON file to
   * everything else.
   */
  const val EXTENSION: String = ".dinfinity.json"

  /**
   * True when [name] is what a collection is called.
   *
   * Ignoring case, because a file that travelled through a Windows share or a
   * mail client may arrive shouting, and `THORIN.DINFINITY.JSON` is nobody
   * trying anything — it is the same file.
   *
   * A name that is *only* the extension is not one: `.dinfinity.json` with
   * nothing in front of it is a dotfile, and treating it as a collection would
   * let a repository hide one from the person who cloned it.
   */
  fun isCollection(name: String): Boolean =
    name.length > EXTENSION.length && name.endsWith(EXTENSION, ignoreCase = true)
}
