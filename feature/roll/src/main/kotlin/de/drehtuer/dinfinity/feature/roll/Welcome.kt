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
 * Two ways out and both of them are forward: throw a d20 right now, or go
 * straight to the tray. Neither is a "skip", and there is nothing to agree to,
 * nothing to sign in to and nothing to download.
 *
 * Shown once. It is dismissed by either button and remembered, because a
 * welcome that comes back is a welcome that was not read the first time
 * either.
 */
@Composable
internal fun Welcome(
  sets: Int,
  onRollNow: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
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
    // The one count there is anything to count. Saved rolls and sessions join
    // it when there is somewhere for them to be kept (`docs/TODO.md`, 4.3).
    Text(
      text = pluralStringResource(R.plurals.roll_welcome_sets, sets, sets),
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
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().testTag(RollTestTags.WELCOME_DISMISS),
      ) {
        Text(stringResource(R.string.roll_welcome_dismiss))
      }
    }
  }
}
