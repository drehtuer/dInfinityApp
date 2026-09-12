package de.drehtuer.dinfinity.dicesets.format

import java.io.File

/**
 * The files of one dice-set package, however they happen to be stored.
 *
 * The validator never touches a filesystem directly. The same package has to
 * be checked while it is still in a temporary folder during an install, from
 * the app's own assets, and from a test's memory, and a validator that could
 * only read one of those would be tested on one of those.
 *
 * Every path handed to an implementation has already been through
 * [ReferencedFile.parse], so it is relative, has no `..` in it and stays
 * inside the package. Implementations may still refuse anything they do not
 * like — belt and braces is the point (`docs/dice-sets.md`, "Runtime
 * isolation").
 */
interface PackageFiles {
  /** The bytes at [path], or `null` when the package has no such file. */
  fun read(path: String): ByteArray?

  /** How large the file at [path] is, or `null` when there is none. */
  fun size(path: String): Long?

  companion object {
    /** The files of a folder on disk. */
    fun of(root: File): PackageFiles = FolderPackage(root)

    /** The files of a package held in memory, which is what tests and assets are. */
    fun of(contents: Map<String, ByteArray>): PackageFiles = MemoryPackage(contents)

    /** A package of a single `diceset.toml` and nothing else. */
    fun ofDiceSetToml(toml: String): PackageFiles =
      of(mapOf(DiceSetValidator.DICE_SET_FILE to toml.encodeToByteArray()))
  }
}

private class FolderPackage(
  private val root: File,
) : PackageFiles {
  override fun read(path: String): ByteArray? = resolve(path)?.readBytes()

  override fun size(path: String): Long? = resolve(path)?.length()

  /**
   * The file at [path], or `null` if it is not a plain file inside the
   * package.
   *
   * The canonical path is compared against the package's own, so a symbolic
   * link planted by an archive cannot reach out of the folder even though the
   * path that names it looks perfectly ordinary.
   */
  private fun resolve(path: String): File? {
    val candidate = File(root, path)
    if (!candidate.isFile) return null
    val inside = candidate.canonicalPath.startsWith(root.canonicalPath + File.separator)
    return if (inside) candidate else null
  }
}

private class MemoryPackage(
  private val contents: Map<String, ByteArray>,
) : PackageFiles {
  override fun read(path: String): ByteArray? = contents[path]?.copyOf()

  override fun size(path: String): Long? = contents[path]?.size?.toLong()
}
