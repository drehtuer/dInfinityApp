package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * The sample sets the app carries, installed once as if the player had picked
 * each archive with the file picker (`docs/dice-sets.md`, "Sets that come
 * installed"; `docs/architecture.md`, decision 107).
 *
 * Installed, not bundled: once one is in `dicesets/` it is the player's like
 * any other set — it can be switched off, and it can be removed. So what has
 * been installed is written down in [record], and the rules follow from that:
 *
 * - **never installed before:** installed, unless the player already has a
 *   set with that id at the same version or a newer one;
 * - **installed before and still there:** replaced when the app carries a
 *   newer version than the one on disk, and left alone otherwise — a player's
 *   own newer copy is never downgraded;
 * - **installed before and gone:** the player removed it, and it stays
 *   removed, whatever version the app carries now.
 *
 * Every archive goes through [PackageInstaller], so through the validator,
 * exactly as a download does: the app's own archives get no path around it
 * (`.claude/CLAUDE.md`, "Never trust downloaded content").
 *
 * Only once per version of the app: the archives are read and checked when
 * the app is new or has been updated, and a launch after that costs one small
 * file read ([seed]'s `stamp`).
 *
 * @param record one `id=version` line per set this has ever installed, and
 *   the app version it last ran for.
 * @param workspace where an archive is written for the installer to read; it
 *   is deleted again afterwards.
 */
class PreinstalledSets(
  private val installer: PackageInstaller,
  private val record: File,
  private val workspace: File,
) {
  /** One archive the app carries: its file name, and its bytes when they can be read. */
  data class Archive(
    val name: String,
    val bytes: () -> ByteArray?,
  )

  /** What became of one archive. */
  sealed interface Outcome {
    val name: String

    /** Installed, for the first time or over an older copy. */
    data class Installed(
      override val name: String,
      val id: String,
      val version: String,
    ) : Outcome

    /** Nothing to do: already there at this version or newer, or removed by the player. */
    data class Kept(
      override val name: String,
      val id: String,
    ) : Outcome

    /** The archive could not be read or did not install; nothing was written. */
    data class Failed(
      override val name: String,
      val reason: String,
    ) : Outcome
  }

  /**
   * Installs what [archives] call for, given the [installed] sets' versions
   * by id, and writes down what it installed — unless it has already run for
   * this [stamp], the app's version, in which case it reads nothing at all
   * and returns nothing. [installed] is only asked for when it is needed.
   */
  fun seed(
    stamp: String,
    archives: List<Archive>,
    installed: () -> Map<String, String>,
  ): List<Outcome> {
    val seeded = readRecord().toMutableMap()
    if (seeded[STAMP_KEY] == stamp) return emptyList()
    val onDisk = installed()
    val outcomes = archives.map { archive -> seedOne(archive, onDisk, seeded) }
    seeded[STAMP_KEY] = stamp
    writeRecord(seeded)
    return outcomes
  }

  private fun seedOne(
    archive: Archive,
    installed: Map<String, String>,
    seeded: MutableMap<String, String>,
  ): Outcome {
    val bytes = archive.bytes() ?: return Outcome.Failed(archive.name, "the app does not carry it")
    val set =
      (DiceSetValidator.validate(packageOf(bytes)) as? ValidationResult.Valid)?.set
        ?: return Outcome.Failed(archive.name, "it does not validate")
    val onDisk = installed[set.id]
    return if (wanted(set.id in seeded, set.version, onDisk)) {
      install(archive.name, bytes, seeded)
    } else {
      // A set the player already had is now one this has seen, so removing
      // it later is a removal and not a set that was never put there.
      seeded.putIfAbsent(set.id, onDisk ?: set.version)
      Outcome.Kept(archive.name, set.id)
    }
  }

  /**
   * Whether the app's [version] of a set goes on disk, given whether this
   * ever installed it ([seenBefore]) and the version there now ([onDisk],
   * null for none): the three rules in the class's description.
   */
  private fun wanted(
    seenBefore: Boolean,
    version: String,
    onDisk: String?,
  ): Boolean =
    if (seenBefore) {
      onDisk != null && isNewer(version, onDisk)
    } else {
      onDisk == null || isNewer(version, onDisk)
    }

  private fun install(
    name: String,
    bytes: ByteArray,
    seeded: MutableMap<String, String>,
  ): Outcome {
    workspace.mkdirs()
    val file = File(workspace, name)
    return try {
      file.writeBytes(bytes)
      when (val result = installer.installFrom(file, from = "$SOURCE_PREFIX$name")) {
        is PackageInstaller.Result.Installed -> {
          seeded[result.set.id] = result.set.version
          Outcome.Installed(name, result.set.id, result.set.version)
        }
        is PackageInstaller.Result.Failed -> Outcome.Failed(name, result.reason)
      }
    } finally {
      file.delete()
    }
  }

  private fun readRecord(): Map<String, String> =
    if (!record.isFile) {
      emptyMap()
    } else {
      // A line that is not `id=version` is skipped, so a damaged record costs
      // the lines that are damaged and nothing else.
      record
        .readLines()
        .mapNotNull { line ->
          val parts = line.split('=', limit = 2)
          if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
        }.toMap()
    }

  private fun writeRecord(seeded: Map<String, String>) {
    record.parentFile?.mkdirs()
    record.writeText(seeded.entries.sortedBy { it.key }.joinToString("") { "${it.key}=${it.value}\n" })
  }

  companion object {
    /**
     * The record's line for the app version it last ran for. Never a set id:
     * those are lower-case letters, digits and dashes (`DiceSet.IdPattern`).
     */
    internal const val STAMP_KEY: String = "@app"

    /** What an installed sample set says it was installed from. */
    const val SOURCE_PREFIX: String = "app:"

    /**
     * The archive's files, held in memory and named as inside its one folder
     * — the layout `sample-sets/build-archives.py` writes — so the validator
     * can say what set it is before anything touches the disk.
     */
    internal fun packageOf(bytes: ByteArray): PackageFiles {
      val files = mutableMapOf<String, ByteArray>()
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
          files[entry.name.substringAfter('/')] = zip.readBytes()
        }
      }
      return PackageFiles.of(files)
    }

    /**
     * Whether [candidate] is a later semver than [current]: major, minor and
     * patch compared as numbers, anything after a `-` or `+` ignored. If
     * either is not three numbers the answer is no, so a set whose version
     * cannot be read is left as it is rather than replaced or replacing.
     */
    fun isNewer(
      candidate: String,
      current: String,
    ): Boolean {
      val new = numbersOf(candidate) ?: return false
      val old = numbersOf(current) ?: return false
      return compareValuesBy(new, old, { it[0] }, { it[1] }, { it[2] }) > 0
    }

    private fun numbersOf(version: String): List<Int>? =
      version
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .takeIf { it.size == SEMVER_PARTS }
        ?.map { it.toIntOrNull() ?: return null }

    private const val SEMVER_PARTS = 3
  }
}
