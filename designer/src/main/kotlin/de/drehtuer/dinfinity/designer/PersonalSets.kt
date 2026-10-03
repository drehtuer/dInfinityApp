package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * What came of naming a new personal set (`docs/face-designer.md`, "Save to
 * set"; `docs/architecture.md`, decision 79).
 *
 * Every refusal is a reason, and every one of them leaves the phone as it was:
 * nothing is written until the name has passed every check, the validator's
 * among them.
 */
sealed interface NewSet {
  /** It exists, and its package is on the disk. */
  data class Made(
    val id: String,
    val name: String,
  ) : NewSet

  /** There is no name to call it by. */
  data object Unnamed : NewSet

  /** The name is longer than [PersonalSetId.NAME_LIMIT]. */
  data object NameTooLong : NewSet

  /** The id the name comes to is not a set id (`docs/dice-sets.md`, "Fields"). */
  data class BadId(
    val id: String,
  ) : NewSet

  /** A set with that id is already on the phone, installed or made here. */
  data class Taken(
    val id: String,
  ) : NewSet

  /** The package the name would make does not validate, and here is why. */
  data class Rejected(
    val report: List<ValidationMessage>,
  ) : NewSet

  /** The disk would not take it, and nothing was left behind. */
  data object NotWritten : NewSet
}

/**
 * How a set's name becomes its id (`docs/architecture.md`, decision 79).
 *
 * Pure, so the sheet can say what the id *will* be while somebody is typing,
 * and so the rule is one function a unit test reads rather than a sequence of
 * string calls somewhere in a click handler.
 */
object PersonalSetId {
  /** The longest name a personal set may have, in characters. */
  const val NAME_LIMIT: Int = 40

  /** The longest id there is (`docs/dice-sets.md`, "Fields"). */
  private const val ID_LIMIT: Int = 40

  private val ACCENTS = Regex("\\p{Mn}+")
  private val NOT_SLUG = Regex("[^a-z0-9]+")

  /**
   * The id [name] comes to: accents dropped, lower case, every run of
   * anything that is not a letter or a digit one hyphen, no hyphen at either
   * end, and no longer than a set id may be.
   *
   * "Brass & Bone" is `brass-bone`; "Café" is `cafe`. What comes out is not
   * promised to be a valid id — "Me" comes to `me`, one letter short — which
   * is [DiceSet.IdPattern]'s to say and the caller's to report.
   */
  fun of(name: String): String {
    val plain = ACCENTS.replace(Normalizer.normalize(name.trim(), Normalizer.Form.NFD), "")
    return NOT_SLUG
      .replace(plain.lowercase(Locale.ROOT), "-")
      .trim('-')
      .take(ID_LIMIT)
      .trimEnd('-')
  }

  /** Why [name] cannot be a set's name, or null when it can. */
  fun problemWith(name: String): NewSet? {
    val called = name.trim()
    return when {
      called.isEmpty() -> NewSet.Unnamed
      called.length > NAME_LIMIT -> NewSet.NameTooLong
      !DiceSet.IdPattern.matches(of(called)) -> NewSet.BadId(of(called))
      else -> null
    }
  }
}

/**
 * Every personal set on the phone: "My dice", and every set somebody named in
 * the face designer (`docs/face-designer.md`, "Save to set";
 * `docs/architecture.md`, decision 79).
 *
 * **"My dice" is where it always was.** Its drafts, photographs and weight are
 * in the folders they have been in since there was one personal set, and
 * nothing here moves, renames or rewrites any of them. A named set is new
 * records in a new folder, `personal-sets/<id>/` — its name, its own drafts,
 * its own weight — and its package beside every other one in `dicesets/<id>/`.
 * So the step from one personal set to several is purely additive: an install
 * that never names a set never creates the folder.
 *
 * Each set is a [MineSets] of its own, so each has its own export, its own
 * record of what its dice are made of, and its own drafts, keyed by die inside
 * a folder keyed by set. Everything every one of them writes goes through
 * `DiceSetValidator` on the way, exactly as "My dice" always has.
 *
 * @param mine "My dice", built by the application from the folders it has
 *   always used.
 * @param records where named sets keep their records, `<filesDir>/personal-sets/`.
 */
class PersonalSets(
  private val mine: MineSets,
  private val records: File,
) {
  /** The named sets read off [records], by id. Null until the first reading. */
  private var named: Map<String, MineSets>? = null

  /** "My dice" first, then the named sets by name — the order the sheet lists them in. */
  @Synchronized
  fun all(): List<MineSets> = listOf(mine) + loaded().values.sortedBy { it.name.lowercase(Locale.ROOT) }

  /** The ids of every personal set, which is what makes a row on the sets screen personal. */
  fun ids(): Set<String> = all().mapTo(LinkedHashSet(), MineSets::id)

  /** The personal set called [id], or null when there is none. */
  fun find(id: String): MineSets? = all().firstOrNull { it.id == id }

  /** Brings every personal set's package up to date ([MineSets.bringUpToDate]). */
  fun bringUpToDate() {
    all().forEach(MineSets::bringUpToDate)
  }

  /**
   * Makes a new personal set called [name], or says why not.
   *
   * The checks come first and the disk last, so a refusal writes nothing: the
   * name, the id it comes to, whether that id is anybody's already, and
   * whether the empty package it would make passes the validator. Only then
   * is the record written — into a dotted staging folder renamed into place,
   * so a reading never sees half of one — and the package built. A package
   * that will not install takes the record back out again.
   */
  @Synchronized
  fun create(name: String): NewSet {
    val called = name.trim()
    return refusal(called) ?: make(PersonalSetId.of(called), called)
  }

  /**
   * Why [called] cannot be made, or null when it can: the name, then whether
   * its id is anybody's already, then the validator on the empty package it
   * would make.
   *
   * A folder of that id in either place counts as taken even if it would not
   * read, because the answer to a clash is a different name, never a folder of
   * somebody's overwritten.
   */
  private fun refusal(called: String): NewSet? {
    val id = PersonalSetId.of(called)
    val known = id in RESERVED || id in loaded()
    val onDisk = File(mine.root, id).exists() || File(records, id).exists()
    return PersonalSetId.problemWith(called)
      ?: if (known || onDisk) {
        NewSet.Taken(id)
      } else {
        (DiceSetValidator.validate(PackageFiles.of(mine.emptyPackageOf(id, called))) as? ValidationResult.Rejected)
          ?.let { NewSet.Rejected(it.messages) }
      }
  }

  /** Writes the record, builds the package, and takes the record back out if the package will not install. */
  private fun make(
    id: String,
    called: String,
  ): NewSet {
    val folder = File(records, id)
    if (!writeRecord(records, id, called, folder)) return NewSet.NotWritten
    val made = mine.sibling(id, called, folder)
    made.bringUpToDate()
    if (!File(mine.root, id).isDirectory) {
      folder.deleteRecursively()
      return NewSet.NotWritten
    }
    named = loaded() + (id to made)
    return NewSet.Made(id, called)
  }

  /**
   * Takes the named set [id] off the phone: its package and its records.
   *
   * Removing is asked for on purpose, on the sets screen, and a package whose
   * records stayed would be rebuilt at the next reading — a set that cannot
   * be removed. "My dice" is not touched here and answers false: its records
   * are the designer's own drafts, and removing it has always meant the
   * package alone.
   */
  @Synchronized
  fun forget(id: String): Boolean {
    val set = loaded()[id] ?: return false
    set.removePackage()
    File(records, id).deleteRecursively()
    named = loaded() - id
    return true
  }

  private fun loaded(): Map<String, MineSets> = named ?: read().also { named = it }

  /** The named sets on disk. A folder with no readable name is not a set, and is left alone. */
  private fun read(): Map<String, MineSets> =
    records
      .listFiles()
      .orEmpty()
      .filter { it.isDirectory && DiceSet.IdPattern.matches(it.name) && it.name !in RESERVED }
      .mapNotNull { folder -> nameIn(folder)?.let { name -> folder.name to mine.sibling(folder.name, name, folder) } }
      .toMap()

  companion object {
    /** The folder named sets keep their records in, under the app's own files. */
    const val DIRECTORY: String = "personal-sets"

    /** The file inside a set's records that says what it is called. */
    const val NAME_FILE: String = "name.txt"

    /** The file inside a set's records that says what its dice are made of. */
    const val PHYSICAL_FILE: String = "physical.txt"

    /** Ids no named set may take: the bundled set, and "My dice". */
    private val RESERVED = setOf(DiceSet.BUILTIN_ID, DiceSet.PERSONAL_ID)
  }
}

/** The name a record folder says, or null when it says none. */
private fun nameIn(folder: File): String? =
  runCatching { File(folder, PersonalSets.NAME_FILE).readText() }
    .getOrNull()
    ?.lineSequence()
    ?.firstOrNull()
    ?.trim()
    ?.take(PersonalSetId.NAME_LIMIT)
    ?.takeIf(String::isNotEmpty)

/** Writes a record's name into a dotted staging folder in [records] and renames it to [folder]. */
private fun writeRecord(
  records: File,
  id: String,
  called: String,
  folder: File,
): Boolean {
  val staging = File(records, ".$id.writing")
  staging.deleteRecursively()
  val written =
    runCatching {
      staging.mkdirs()
      File(staging, PersonalSets.NAME_FILE).writeText(called + "\n")
      staging.renameTo(folder)
    }.getOrDefault(false)
  if (!written) staging.deleteRecursively()
  return written
}
