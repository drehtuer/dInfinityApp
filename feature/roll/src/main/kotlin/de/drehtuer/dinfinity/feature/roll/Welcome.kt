package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The first thing a new install shows (`design/dInfinity.dc.html`, option 9a).
 *
 * It exists to say one thing — **there is nothing to set up** — and then get
 * out of the way. The dice are already installed, the table is already laid
 * out, and the one thing nobody would guess is that shaking the phone rolls,
 * so that is what it says and then offers to prove.
 *
 * Four ways out and every one of them is forward: throw a d20 right now, go
 * straight to the tray, bring somebody's saved rolls in, or add somebody's
 * dice. Neither of the first two is a "skip", and there is nothing to agree
 * to, nothing to sign in to and nothing that has to be downloaded.
 *
 * **The two imports do not dismiss it.** Going to fetch something and coming
 * back to a tray that has forgotten it ever said hello would leave somebody
 * wondering what they were meant to do next — and the count line has something
 * new to say when they return, which is the whole point of its being a count
 * rather than a sentence.
 *
 * Shown once. It is dismissed by either of the first two buttons and
 * remembered, because a welcome that comes back is a welcome that was not read
 * the first time either.
 */
@Composable
internal fun Welcome(
  what: WhatIsThere,
  onRollNow: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  onImport: () -> Unit = {},
  onAddSets: () -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .padding(24.dp)
        .testTag(RollTestTags.WELCOME),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = stringResource(R.string.roll_welcome_eyebrow),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = stringResource(R.string.roll_welcome_title),
      style = MaterialTheme.typography.displaySmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.roll_welcome_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // What a fresh install already has, which is more than nothing and less
    // than it will be. Three counts rather than one, because two of them are
    // what the buttons below change (`design/dInfinity.dc.html`, option 9a).
    Text(
      text = countsOf(what),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag(RollTestTags.WELCOME_SETS),
    )

    Column(
      verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
      modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
      Button(
        onClick = onRollNow,
        modifier = Modifier.fillMaxWidth().testTag(RollTestTags.WELCOME_ROLL),
      ) {
        Text(stringResource(R.string.roll_welcome_roll_now))
      }
      // The other three are the same button three times, and written as one:
      // going to the tray, and the two that go and fetch something.
      ways(onDismiss = onDismiss, onImport = onImport, onAddSets = onAddSets).forEach { way ->
        TextButton(
          onClick = way.take,
          modifier = Modifier.fillMaxWidth().testTag(way.tag),
        ) {
          Text(stringResource(way.label))
        }
      }
    }
  }
}

/**
 * The count line: what a fresh install already has, as one sentence.
 *
 * A composable that returns text rather than one that draws it, so it costs no
 * skip branch — and because what is interesting about it is the wording, which
 * a caller can put wherever it likes (`docs/TODO.md`, Coverage).
 *
 * Three plurals joined rather than one string with the numbers in it: a
 * language that pluralises differently from English still reads, and the
 * sentence cannot go on claiming a zero the way the old one did.
 */
@Composable
private fun countsOf(what: WhatIsThere): String =
  listOf(
    pluralStringResource(R.plurals.roll_welcome_sets, what.sets, what.sets),
    pluralStringResource(R.plurals.roll_welcome_saved, what.savedRolls, what.savedRolls),
    pluralStringResource(R.plurals.roll_welcome_sessions, what.sessions, what.sessions),
  ).joinToString(stringResource(R.string.roll_welcome_counts_separator))

/** One of the three buttons that are alike: a label, a tag and what it does. */
private class Way(
  val label: Int,
  val tag: String,
  val take: () -> Unit,
)

private fun ways(
  onDismiss: () -> Unit,
  onImport: () -> Unit,
  onAddSets: () -> Unit,
): List<Way> =
  listOf(
    Way(R.string.roll_welcome_dismiss, RollTestTags.WELCOME_DISMISS, onDismiss),
    Way(R.string.roll_welcome_import, RollTestTags.WELCOME_IMPORT, onImport),
    Way(R.string.roll_welcome_sets_add, RollTestTags.WELCOME_SETS_ADD, onAddSets),
  )

/**
 * What a fresh install already has (`design/dInfinity.dc.html`, option 9a).
 *
 * Three numbers rather than three parameters, because they are one line on the
 * screen and because two of them change while the welcome is still up: going
 * off to import saved rolls and coming back should find the line saying so.
 */
data class WhatIsThere(
  val sets: Int = 0,
  val savedRolls: Int = 0,
  val sessions: Int = 0,
)
