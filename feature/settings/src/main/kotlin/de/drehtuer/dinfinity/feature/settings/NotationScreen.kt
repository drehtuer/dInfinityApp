package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.notation.NotationEntry
import de.drehtuer.dinfinity.core.notation.NotationReference

/**
 * What you can type, looked up inside the app (`docs/dice-notation.md`;
 * `docs/TODO.md`, 4.10).
 *
 * The formula field is the app's one piece of syntax, and until now the only
 * place it was written down was the README — which is exactly where somebody
 * with a phone in their hand at a table is not looking. So it is a screen.
 *
 * **Every example is a button.** Tapping one puts that formula in the tray's
 * field, which is the difference between a reference and a manual: you find
 * out what `kh1` means by rolling it. `NotationReference` guarantees they are
 * all formulas the parser accepts, so none of these buttons can lead to an
 * error message.
 *
 * Stateless, and drawn from [NotationReference] rather than from strings of
 * its own — the text lives beside the parser it describes, where a test holds
 * the two together.
 */
@Composable
fun NotationScreen(
  modifier: Modifier = Modifier,
  onRoll: (String) -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState())
        .padding(24.dp)
        .testTag(NotationTestTags.SCREEN),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(R.string.notation_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      menu()
    }
    Text(
      text = stringResource(R.string.notation_blurb),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Inline rather than a `Section` composable, for the reason `Limits` gives
    // below: a `@Composable` costs skip branches per parameter whether or not
    // anything ever calls it twice, and a heading with a loop under it is not
    // worth a function. `Entry` stays one because a row is the thing that
    // repeats and the thing a test points at.
    NotationReference.sections.forEach { section ->
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = section.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = section.blurb,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        section.entries.forEach { entry -> Entry(entry = entry, onRoll = onRoll) }
      }
    }
    Limits()
  }
}

/**
 * One thing you can write, and the button that tries it.
 *
 * The whole row is the target rather than the example alone: a tappable run of
 * monospaced text four characters wide is under the 48 dp a finger needs, and
 * there is nothing else on the row to tap by accident.
 */
@Composable
private fun Entry(
  entry: NotationEntry,
  onRoll: (String) -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .clickable(role = Role.Button, onClick = { onRoll(entry.example) })
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .testTag(NotationTestTags.entryOf(entry.syntax)),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = entry.syntax,
      style = MaterialTheme.typography.titleSmall,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(SYNTAX_SHARE),
    )
    Text(
      text = entry.meaning,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(MEANING_SHARE),
    )
  }
}

/**
 * What the notation will not do, and what it says when you ask.
 *
 * One composable for the whole list rather than one per row: a `@Composable`
 * costs a skip branch per parameter whether or not anything ever calls it
 * twice, and two `Text`s are not worth a function.
 */
@Composable
private fun Limits() {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(NotationTestTags.LIMITS)) {
    Text(
      text = stringResource(R.string.notation_limits),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    NotationReference.limits.forEach { limit ->
      Column {
        Text(
          text = "${limit.what}: ${limit.value}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = limit.then,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** How the row is split between what you write and what it means. */
private const val SYNTAX_SHARE = 0.32f
private const val MEANING_SHARE = 0.68f

/** Handles for the tests (`docs/architecture.md`, "Testing"). */
object NotationTestTags {
  const val SCREEN: String = "notation:screen"
  const val LIMITS: String = "notation:limits"

  fun entryOf(syntax: String): String = "notation:entry:$syntax"
}
