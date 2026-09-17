package de.drehtuer.dinfinity.feature.sets

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.designer.ExportResult
import de.drehtuer.dinfinity.designer.MineSets
import de.drehtuer.dinfinity.designer.SetLicense
import de.drehtuer.dinfinity.dicesets.install.InstalledPackage
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The dice sets, as a screen wants them (`docs/dice-sets.md`).
 *
 * **The one place the two halves are joined.** [InstalledSets] says what is on
 * disk and whether it still validates; [InstalledSetRepository] says what the
 * player has switched off. Neither knows about the other, and the rule that
 * relates them — a package with no row is enabled — is written here once
 * rather than in every screen that needs a list.
 *
 * The bundled set is handed in rather than reached for, so `feature/sets` never
 * learns that `dicesets:builtin` exists. It is the only set with no folder, and
 * every path that would touch one refuses it first.
 *
 * Reading means validating every package: TOML parsing and image headers over
 * real files. That is [io]'s job and not the caller's to remember, which is why
 * the dispatcher lives here and not in the presenters
 * (`docs/architecture.md`, "Threading"). It has no default: which thread the
 * disk is touched on is a wiring decision, and it belongs where the other
 * wiring decisions are rather than hidden in a parameter list.
 *
 * Seven collaborators, and each of them is one of the things this exists to
 * join: the bundled set, the folder, the registry, the dispatcher, the
 * installer, the default and the drawings. Grouping any of them into a holder
 * would be a type whose only purpose is to make a counter smaller, so the
 * warning is suppressed rather than designed around.
 */
@Suppress("LongParameterList")
class SetLibrary(
  private val bundled: DiceSet,
  private val installed: InstalledSets,
  private val registry: InstalledSetRepository,
  private val io: CoroutineDispatcher,
  private val installer: PackageInstaller,
  private val defaultSetId: () -> String,
  /**
   * The personal package, built from the drawings on the phone
   * (`docs/face-designer.md`; design `8c`).
   *
   * Here because this is already the place where "what is on disk" is
   * answered, and "My dice" is a folder in the same `dicesets/` as everything
   * else — so the one thing that has to happen is that it is written *before*
   * the folder is read. Every other screen then sees an ordinary package and
   * needs to know nothing about drawings.
   *
   * Null for a library with no designer behind it, which is what the tests and
   * a bundled-only install are.
   */
  private val personal: MineSets? = null,
) {
  /**
   * The sets whose dice may be handed out: the bundled one, and every
   * installed package that is **on and valid**.
   *
   * The same question [SetRow.usable] answers for a row, because a set the
   * player switched off and a set that stopped validating are both sets whose
   * dice must not be offered.
   *
   * Rebuilt by [all], and only by [all]. Reading the folder and deciding what
   * a `d20` means are the same facts, and keeping them apart is how a set
   * comes to be listed as installed and still not roll.
   *
   * Starts as the bundled set alone: that is what is true before anything has
   * been read, and it is the floor everything falls back to in any case.
   */
  @Volatile
  private var usable: List<DiceSet> = listOf(bundled)

  /**
   * Which set plain notation reaches for first (`docs/dice-notation.md`;
   * design `6a`).
   *
   * The setting [catalogue] is built from, said out loud so a row can be
   * badged with it. It is asked rather than cached for the reason the
   * catalogue is built on the way out: it is a setting somebody changes on
   * another screen without touching a folder.
   */
  val defaultId: String get() = defaultSetId()

  /**
   * What a formula resolves against (`docs/dice-notation.md`).
   *
   * Built on the way out rather than cached, because it has two inputs that
   * change at different times: which packages are on disk, which only [all]
   * knows, and which of them is the *default*, which is a setting somebody can
   * change on another screen without touching a folder.
   *
   * **A default that is not installed is not a default.** A set can be removed
   * or switched off while it is still named in the settings, and
   * `DiceCatalog.of` refuses a default it cannot find — rightly, since a
   * catalogue pointing at a set nobody has is a catalogue that cannot resolve
   * `d20`. So the bundled set stands in, and the setting is left alone: a set
   * switched off for an evening should still be the default when it comes back.
   */
  val catalogue: DiceCatalog
    get() {
      val sets = usable
      val wanted = defaultSetId()
      val default = if (sets.any { it.id == wanted }) wanted else DiceSet.BUILTIN_ID
      return DiceCatalog.of(sets, default)
    }

  /**
   * Every set, the bundled one first and the rest by name.
   *
   * By name rather than by id, because the name is what a row shows: two sets
   * whose folders sort one way and whose names sort the other would read as an
   * unsorted list.
   *
   * Reading is also when the registry is reconciled with the disk — the only
   * moment both are in hand. A row about a folder that has gone would switch a
   * *new* package off the moment somebody installed one under the same id.
   */
  suspend fun all(): List<SetRow> {
    val packages =
      withContext(io) {
        personal?.bringUpToDate()
        installed.scan()
      }
    registry.keepOnly(packages.map(InstalledPackage::id))
    val off = registry.disabled()
    val rows =
      listOf(SetRow.bundled(bundled)) +
        packages
          .map { pack -> SetRow.of(pack, enabled = pack.id !in off) }
          .sortedBy { it.name.lowercase() }
    usable = rows.filter(SetRow::usable).mapNotNull(SetRow::set).distinctBy(DiceSet::id)
    return rows
  }

  /**
   * Installs the package in [archive], or leaves the phone exactly as it was
   * (`docs/dice-sets.md`, "Installing from a URL or file"; design `1t`).
   *
   * Nothing here decides whether the package is any good. [PackageInstaller]
   * fetches into a temporary folder, extracts into another, validates *there*,
   * and only then moves the folder into place — so there is no partially
   * installed state to recover from and nothing for this layer to undo.
   *
   * A package that installs under an id already present replaces it, and says
   * so. That is an update, and it is the same operation.
   */
  suspend fun install(archive: File): PackageInstaller.Result = withContext(io) { installer.installFrom(archive) }

  /** The one set called [id], or null when nothing is installed under that name. */
  suspend fun one(id: String): SetRow? {
    if (id == bundled.id) return SetRow.bundled(bundled)
    val pack =
      withContext(io) {
        personal?.bringUpToDate()
        installed.find(id)
      } ?: return null
    return SetRow.of(pack, enabled = pack.id !in registry.disabled())
  }

  /**
   * The personal package as a zip, under the licence its author chose
   * (design `8c`).
   *
   * It goes through the validator on the way out, like every other package —
   * a set the app itself wrote is not a privileged path, and a package that
   * does not validate is a bug caught here rather than an install failure on
   * somebody else's phone (`docs/dice-sets.md`, "Validation").
   *
   * The licence is written into the installed folder as well as into the file,
   * so the details screen goes on saying what was chosen after the share sheet
   * has closed.
   */
  suspend fun exportPersonal(license: SetLicense): ExportResult =
    withContext(io) { personal?.export(license) ?: ExportResult.Empty }

  /**
   * Switches a set on or off.
   *
   * The folder is untouched, and so is every statistic recorded against the
   * set's dice. That is what makes it the reversible one of the two things a
   * player can do to a set.
   */
  suspend fun setEnabled(
    row: SetRow,
    enabled: Boolean,
  ) {
    if (row.bundled) return
    registry.setEnabled(row.id, enabled)
  }

  /**
   * Takes a set off the phone: the folder, then the registry row.
   *
   * The folder first, because a row about a folder that is still there is a
   * set the player was told had gone and has not; the other order leaves a row
   * that the next reading prunes anyway.
   *
   * Statistics are kept. They are keyed by set id and die id rather than by
   * anything on disk, so the rolls this set made last week stay the player's
   * (`docs/statistics.md`).
   */
  suspend fun remove(row: SetRow) {
    if (row.bundled) return
    withContext(io) { installed.remove(row.id) }
    registry.forget(row.id)
  }
}
