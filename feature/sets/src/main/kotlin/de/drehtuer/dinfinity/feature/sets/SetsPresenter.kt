package de.drehtuer.dinfinity.feature.sets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.dicesets.install.InstalledPackage
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.dicesets.install.PackageMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * What a row's one line under the name is about.
 *
 * An enum rather than a string chosen inside the composable, because the
 * *order* is a rule and rules are worth testing without a screen: a package
 * that will not load says so before anything else, since nothing else about it
 * matters until that is fixed, and "switched off" comes before "built in"
 * because it is the one the player just did.
 */
enum class SetStatus {
  /** Did not pass validation on this reading. The remedy is an update (`6b`). */
  Broken,

  /** The player switched it off. The remedy is a tap (`5a`). */
  Off,

  /** The set that ships inside the app. */
  Bundled,

  /** Installed, on, and valid: the line says how many dice it has. */
  Ready,
}

/**
 * One row of the dice-set list (`design/dInfinity.dc.html`, option `5a`).
 *
 * A view of a package rather than the package itself, for one reason worth
 * naming: the **bundled set has no folder**. It lives inside the APK, it cannot
 * be removed and it cannot be switched off, and a row shape that insisted on a
 * folder would have to invent one for it. So a folder is what a row may not
 * have, and [bundled] is what says why.
 *
 * It carries the set and the report rather than counts taken from them, because
 * the details screen wants both and re-reading the disk on a tap would be a
 * validation pass per row (`6a`, `6b`).
 */
data class SetRow(
  val id: String,
  val name: String,
  val set: DiceSet?,
  val report: List<ValidationMessage>,
  val meta: PackageMeta,
  val folder: File?,
  val enabled: Boolean,
  val bundled: Boolean = false,
) {
  /** True when the package did not pass validation on this reading (`6b`). */
  val broken: Boolean get() = set == null

  /**
   * True when there is a forge to ask about this set.
   *
   * Both halves are needed: where it came from, and which commit arrived. A
   * package installed from a file has neither and a plain archive has no
   * commits to tell apart, so neither can be checked — which is a fact about
   * the source rather than a failure ([RefResolver]).
   */
  val checkable: Boolean get() = meta.source != null && meta.commit != null

  /** How many things went wrong. The list itself belongs to the details screen (`6b`). */
  val problems: Int get() = report.size

  /** How many dice it defines. A broken package defines nothing usable. */
  val dice: Int get() = set?.dice?.size ?: 0

  /** The version it declares, where it is readable enough to declare one. */
  val version: String? get() = set?.version ?: meta.version

  /**
   * The one thing the row says under the name.
   *
   * Ordered by what the player can do something about; see [SetStatus].
   */
  val status: SetStatus
    get() =
      when {
        broken -> SetStatus.Broken
        !enabled -> SetStatus.Off
        bundled -> SetStatus.Bundled
        else -> SetStatus.Ready
      }

  /**
   * Whether its dice are actually available to a formula.
   *
   * A set can fail to be usable two ways, and the row says which: switched off
   * by the player, or no longer valid. They are not the same thing and the
   * remedies are opposite — one is a tap, the other is an update.
   */
  val usable: Boolean get() = enabled && !broken

  companion object {
    /** A package read off the disk, with the player's opinion of it. */
    fun of(
      pack: InstalledPackage,
      enabled: Boolean,
    ): SetRow =
      SetRow(
        id = pack.id,
        name = (pack as? InstalledPackage.Ready)?.set?.name ?: pack.id,
        set = (pack as? InstalledPackage.Ready)?.set,
        report = (pack as? InstalledPackage.Broken)?.report.orEmpty(),
        meta = pack.meta,
        folder = pack.folder,
        enabled = enabled,
      )

    /** The set that ships inside the app: no folder, always on, never removable. */
    fun bundled(set: DiceSet): SetRow =
      SetRow(
        id = set.id,
        name = set.name,
        set = set,
        report = emptyList(),
        meta = PackageMeta.Unknown,
        folder = null,
        enabled = true,
        bundled = true,
      )
  }
}

/** What the dice-set screen is showing. */
data class SetsState(
  val sets: List<SetRow> = emptyList(),
  val acting: SetRow? = null,
  val loaded: Boolean = false,
  val installing: Boolean = false,
  val outcome: PackageInstaller.Result? = null,
  /** True while the forges are being asked what their sets are at (`9h`). */
  val checking: Boolean = false,
  /**
   * The sets whose forge is at a different commit than the one installed.
   *
   * Ids rather than rows, because the rows are rebuilt every time the disk is
   * read and a row held here would go stale the moment anything else happened.
   */
  val outdated: Set<String> = emptySet(),
  /**
   * What the last check said, when it has nothing to show on a row.
   *
   * A check that found everything up to date and a check that could not reach
   * anything look identical on the list — nothing is badged either way — so the
   * screen says which it was.
   */
  val checked: UpdateCheck? = null,
) {
  /** Whether asking is worth offering: something has to have come from somewhere. */
  val checkable: Boolean get() = sets.any { it.checkable }

  /**
   * True once the disk has been read and found to hold nothing.
   *
   * The bundled row is always there, so "nothing installed" is one row rather
   * than none — and saying so needs [loaded], or the message would flash up on
   * every visit before the first reading finishes.
   *
   * It adds a note rather than replacing the list: the bundled set is a row
   * like any other and there is always something to show.
   */
  val empty: Boolean get() = loaded && sets.size <= 1
}

/**
 * The dice sets that are installed, and what may be done to them
 * (`docs/dice-sets.md`; `design/dInfinity.dc.html`, option `5a`).
 *
 * Everything about *what the sets are* is [SetLibrary]'s; what is here is what
 * the screen is doing — which row the sheet is open on, and whether the first
 * reading has finished.
 */
class SetsPresenter(
  private val library: SetLibrary,
  private val scope: CoroutineScope,
  private val download: suspend (String) -> FetchedPackage = { FetchedPackage.Failed(NO_NETWORK) },
  /**
   * Asks a forge which commit the ref a set was installed from is at now
   * (`docs/dice-sets.md`, "Updates"; design `9h`).
   *
   * A function rather than something this module builds, for the reason the
   * download is one: it is an HTTP request, and a screen that lists dice sets
   * should not carry a client to make one.
   */
  private val latestCommit: suspend (String) -> LatestCommit = { LatestCommit.Unknown },
) {
  /** What the screen draws. */
  var state: SetsState by mutableStateOf(SetsState())
    private set

  init {
    refresh()
  }

  /**
   * Reads the disk again.
   *
   * Called when the screen opens and after anything that changes what is on
   * it. There is no watching: a `dicesets/` folder does not change while
   * nobody is installing anything, and a file observer over a tree of packages
   * would cost more than the tap it saves.
   */
  fun refresh() {
    scope.launch {
      state = state.copy(sets = library.all(), loaded = true)
    }
  }

  /** A long press. The sheet that offers disable and remove opens on this (`5a`). */
  fun act(on: SetRow?) {
    // The bundled set has nothing the sheet could offer, so it does not open.
    state = state.copy(acting = on?.takeUnless { it.bundled })
  }

  /** Switches a set on or off, and closes the sheet. */
  fun setEnabled(
    row: SetRow,
    enabled: Boolean,
  ) {
    state = state.copy(acting = null)
    scope.launch {
      library.setEnabled(row, enabled)
      refresh()
    }
  }

  /** Takes a set off the phone, and closes the sheet. */
  fun remove(row: SetRow) {
    state = state.copy(acting = null)
    scope.launch {
      library.remove(row)
      refresh()
    }
  }

  /**
   * Installs the package in [archive] and shows what came of it (design `1t`).
   *
   * [onDone] is called however it ends, including when it throws, and is where
   * the temporary copy of the archive is deleted. The bytes belong to whoever
   * chose the file; the copy exists only so the installer has something to
   * open, and leaving it behind on a failure would be the one case that
   * mattered.
   *
   * A refusal shows **every** error rather than the first. An author fixing a
   * set wants the whole list, and there is room for it.
   */
  fun install(
    archive: File,
    onDone: () -> Unit = {},
  ) {
    if (state.installing) return
    state = state.copy(installing = true, outcome = null)
    scope.launch { unpack(archive, onDone) }
  }

  /**
   * Downloads the package at [url] and installs it (design `1t`;
   * `docs/dice-sets.md`, "Installing from a URL or file").
   *
   * The same install, with a download in front of it. What arrives goes
   * through the validator rule for rule — there is one validator and no path
   * around it (`.claude/CLAUDE.md`) — and a download that does not arrive is
   * reported the way a refused file is, because to the player it is the same
   * sentence: nothing was installed, and here is why.
   *
   * The downloaded copy is deleted however it ends, including when the
   * installer throws. It is a stranger's archive sitting in a cache nobody
   * empties, and the set it held is on disk by the time anybody wants it again.
   *
   * **An update is this and nothing else** (`docs/dice-sets.md`, "Updates"):
   * the same fetch from the source the install recorded, through the same
   * validator, over the top of the folder that is there. `PackageInstaller`
   * replaces a package it recognises and leaves the existing one alone if the
   * new one is refused, so an update that fails costs nothing — which is why
   * there is no separate "update" here to keep in step with this one.
   */
  fun installFrom(url: String) {
    val link = url.trim()
    if (state.installing || link.isEmpty()) return
    state = state.copy(installing = true, outcome = null)
    scope.launch {
      when (val fetched = download(link)) {
        is FetchedPackage.Failed ->
          state = state.copy(installing = false, outcome = PackageInstaller.Result.Failed(fetched.reason))

        is FetchedPackage.Archive -> unpack(fetched.file) { if (!fetched.file.delete()) fetched.file.deleteOnExit() }
      }
    }
  }

  /** The install itself, which is the same whether the archive was chosen or fetched. */
  private suspend fun unpack(
    archive: File,
    onDone: () -> Unit,
  ) {
    // The installer answers Failed for everything it anticipates, so a throw
    // here means the filesystem did something it was not asked about. It is
    // still a refusal to the player, and saying so beats taking the screen
    // down with them.
    val result =
      runCatching { library.install(archive) }
        .getOrElse { cause ->
          PackageInstaller.Result.Failed(cause.message ?: "the package could not be installed")
        }
    state = state.copy(installing = false, outcome = result)
    onDone()
    refresh()
  }

  /**
   * The file could not even be opened, so no install was attempted.
   *
   * Shown the same way a refusal from the validator is, because to the player
   * it is the same sentence: nothing was installed, and here is why. What
   * differs is that the app never got as far as looking inside.
   */
  fun refused(why: String) {
    state = state.copy(installing = false, outcome = PackageInstaller.Result.Failed(why))
  }

  /**
   * Asks every forge whether it has moved on (`9h`).
   *
   * Only sets that came from one and recorded the commit that arrived: a
   * package installed from a file has nothing to compare, and a plain archive
   * has no commits to tell apart. A forge that cannot be reached is not an
   * error to put in front of somebody — it is one set that could not be
   * checked, and the count of those is what the screen says.
   */
  fun checkForUpdates() {
    if (state.checking || state.installing) return
    state = state.copy(checking = true, checked = null)
    scope.launch {
      val checkable = state.sets.filter(SetRow::checkable)
      val outdated = mutableSetOf<String>()
      var unreachable = 0
      checkable.forEach { row ->
        when (val latest = latestCommit(requireNotNull(row.meta.source))) {
          is LatestCommit.Unknown -> unreachable++
          is LatestCommit.At -> if (latest.sha != row.meta.commit) outdated += row.id
        }
      }
      state =
        state.copy(
          checking = false,
          outdated = outdated,
          checked = UpdateCheck(asked = checkable.size, outdated = outdated.size, unreachable = unreachable),
        )
    }
  }

  /** Puts away whatever the last install said. */
  fun dismiss() {
    state = state.copy(outcome = null, checked = null)
  }

  private companion object {
    /**
     * What a screen with no downloader says.
     *
     * The download arrives as a function rather than as something this module
     * builds, so that fetching stays where the platform is — a cache directory
     * and an HTTP client. A caller that supplies none cannot reach the network,
     * and saying so is better than a button that does nothing.
     */
    const val NO_NETWORK = "this build cannot reach the network"
  }
}

/** What a forge said about the ref a set was installed from. */
sealed interface LatestCommit {
  /** @param sha the commit that ref is at now, as the forge reported it. */
  data class At(
    val sha: String,
  ) : LatestCommit

  /**
   * There is no answer: not a forge, or the forge could not be reached.
   *
   * One thing and not two on purpose. To somebody looking at a list of sets,
   * "this one has no commits to compare" and "this one's server did not answer"
   * are the same sentence — *nothing can be said about this set* — and the
   * screen says how many of those there were rather than why each one was.
   */
  data object Unknown : LatestCommit
}

/** What a run of [SetsPresenter.checkForUpdates] came to. */
data class UpdateCheck(
  val asked: Int,
  val outdated: Int,
  val unreachable: Int,
) {
  /** True when every set that could be asked answered, and none had moved on. */
  val allCurrent: Boolean get() = outdated == 0 && unreachable == 0
}

/** What a download of a package came to. */
sealed interface FetchedPackage {
  /** The archive, on disk. Not yet known to be a dice set — that is the validator's word. */
  data class Archive(
    val file: File,
  ) : FetchedPackage

  /** It did not arrive, and this is what to tell somebody. */
  data class Failed(
    val reason: String,
  ) : FetchedPackage
}
