package de.drehtuer.dinfinity.feature.tables

import de.drehtuer.dinfinity.core.model.TablePin
import java.io.InputStream

/**
 * A picture the system picker handed back (`docs/tables.md`, "Your own photo").
 *
 * Carried rather than read. [open] is a lambda `:app` builds out of a content
 * URI, and it is opened **twice** — once for the header, once for the pixels —
 * which is why it is a way of opening rather than a stream. This screen never
 * looks inside it: a photo is a stranger's file even when it came off the
 * player's own phone, and everything that reads one is behind the seam.
 *
 * @param label what the file was called, for the sheet to show and for the
 *   name field to start from.
 */
class PickedPhoto(
  val label: String,
  val open: () -> InputStream?,
)

/** What came of asking for a photo to become a table. */
sealed interface PhotoOutcome {
  /** It is in the personal package, listed like any other look. */
  data class Added(
    val pin: TablePin,
  ) : PhotoOutcome

  /**
   * It is not, and these are the reasons.
   *
   * A list rather than a sentence, because the usual reason is a validation
   * report and a report is a list — the same lines a refused install shows
   * (`docs/dice-sets.md`, "Validation"). An empty list is a refusal with
   * nothing more to say than its title.
   */
  data class Refused(
    val reasons: List<String>,
  ) : PhotoOutcome
}

/**
 * Making a table out of a photograph, and taking one away again.
 *
 * The seam `feature/roll`'s `ThrowRecorder` draws, for the same reason: a
 * screen that lists tables has no business decoding a JPEG, writing into the
 * `dicesets/` folder or running the validator. `:app` implements this over the
 * personal package, which is where every other "the app's own package" rule
 * already lives (`docs/architecture.md`, Modules).
 *
 * Suspending because both ends of it are disk: a photo is decoded, scaled,
 * encoded, written and then validated, and none of that belongs on the thread
 * Compose draws on.
 */
interface TablePhotos {
  /** [photo] as a table called [name], or the reason it is not one. */
  suspend fun add(
    photo: PickedPhoto,
    name: String,
  ): PhotoOutcome

  /** Takes the photo table [pin] names off the phone. */
  suspend fun remove(pin: TablePin)
}
