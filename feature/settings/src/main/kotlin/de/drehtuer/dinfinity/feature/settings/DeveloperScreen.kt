package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.simulation.api.Anomaly
import de.drehtuer.dinfinity.simulation.api.AnomalyReport

/**
 * The developer screen: the anomaly log, and the two ways of throwing the last
 * roll again (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * Reached only from the menu, and only while `AppSettings.developerTools` is
 * on. **It is a separate surface**: no screen a player uses gains a field, a
 * seed or a replay because this exists, and the history still has none of the
 * three (`docs/architecture.md`, decisions 13 and 53).
 *
 * Deliberately plain. It is a tool for whoever is debugging the physics, its
 * readers are developers, and the numbers on it are more useful than any
 * arrangement of them would be.
 *
 * @param onShare hands the log to whatever the phone shares text with. The
 *   screen decides what is in it; how a phone shares anything is `:app`'s.
 */
@Composable
fun DeveloperScreen(
  presenter: DeveloperPresenter,
  modifier: Modifier = Modifier,
  onShare: (String) -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .verticalScroll(rememberScrollState())
          .padding(24.dp)
          .testTag(DeveloperTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.developer_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        menu()
      }
      Text(
        text = stringResource(R.string.developer_explanation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      ReplaySection(presenter)
      AnomalySection(presenter, onShare)
    }
  }
}

/**
 * Throwing the last roll again — under its own seed, or under another.
 *
 * Both replay a `ThrowSpec`, because that is what a throw is: the dice, the
 * table, the scale, the seed *and* the shake that drove it. A seed on its own
 * reproduces nothing, and the sentence under the box says so rather than
 * letting somebody find out (`Replay`).
 */
@Composable
private fun ReplaySection(presenter: DeveloperPresenter) {
  Section(
    heading = stringResource(R.string.developer_replay_heading),
    explanation = stringResource(R.string.developer_replay_explanation),
  ) {
    Standing(presenter.state)
    Button(
      onClick = presenter::replayLast,
      enabled = presenter.canReplay,
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = TOUCH_TARGET)
          .testTag(DeveloperTestTags.REPLAY_LAST),
    ) {
      Text(stringResource(R.string.developer_replay_last))
    }
    FromASeed(presenter)
  }
}

/** Where the replay has got to: nothing to do, ready, running, or done. */
@Composable
private fun Standing(state: ReplayState) {
  when (state) {
    ReplayState.Nothing ->
      Line(
        text = stringResource(R.string.developer_replay_nothing),
        colour = MaterialTheme.colorScheme.onSurfaceVariant,
        tag = DeveloperTestTags.NOTHING,
      )

    is ReplayState.Ready ->
      Line(
        text = stringResource(R.string.developer_replay_ready, state.diceCount, state.seed),
        colour = MaterialTheme.colorScheme.onBackground,
        tag = DeveloperTestTags.READY,
      )

    ReplayState.Running ->
      Line(
        text = stringResource(R.string.developer_replay_running),
        colour = MaterialTheme.colorScheme.onBackground,
        tag = DeveloperTestTags.RUNNING,
      )

    is ReplayState.Replayed -> Replayed(state)
  }
}

/**
 * The seed box and the button beside it.
 *
 * Both halves gate the button: there has to be a throw to put a seed into, and
 * the seed has to be one. A half-typed seed throws nothing rather than
 * something else.
 */
@Composable
private fun FromASeed(presenter: DeveloperPresenter) {
  OutlinedTextField(
    value = presenter.seedText,
    onValueChange = presenter::typeSeed,
    label = { Text(stringResource(R.string.developer_seed_label)) },
    singleLine = true,
    // A seed is a signed 64-bit number, so the sign is part of the keyboard.
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier =
      Modifier
        .fillMaxWidth()
        .testTag(DeveloperTestTags.SEED),
  )
  Button(
    onClick = presenter::replayFromSeed,
    enabled = presenter.canReplay && presenter.typedSeed != null,
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = TOUCH_TARGET)
        .testTag(DeveloperTestTags.REPLAY_SEED),
  ) {
    Text(stringResource(R.string.developer_replay_from_seed))
  }
}

/** What a replay came to, and whether it agreed with the throw it replays. */
@Composable
private fun Replayed(state: ReplayState.Replayed) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text =
        stringResource(
          R.string.developer_replay_result,
          state.seed,
          state.faces.joinToString(separator = " "),
          state.outcome.steps,
        ),
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.testTag(DeveloperTestTags.RESULT),
    )
    // Only for a replay under the same seed. A different seed is not supposed
    // to agree with anything, so saying it did not would be noise.
    state.reproduced?.let { same ->
      Text(
        text =
          stringResource(
            if (same) R.string.developer_replay_same else R.string.developer_replay_different,
          ),
        style = MaterialTheme.typography.bodyMedium,
        color = if (same) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(DeveloperTestTags.VERDICT),
      )
    }
  }
}

/** One line of plain state, tagged so a test can read it. */
@Composable
private fun Line(
  text: String,
  colour: Color,
  tag: String,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = colour,
    modifier = Modifier.testTag(tag),
  )
}

/**
 * The anomaly log.
 *
 * Every entry here is a bug, and the screen says so rather than presenting it
 * as a statistic: a forced settle means the twelve-second cap fired, and a
 * post-rest correction means something moved a die after it had stopped, which
 * is the one thing this app promises never happens (`.claude/CLAUDE.md`).
 * An empty log is the expected state and is worded as such.
 */
@Composable
private fun AnomalySection(
  presenter: DeveloperPresenter,
  onShare: (String) -> Unit,
) {
  Section(
    heading = stringResource(R.string.developer_anomalies_heading),
    explanation = stringResource(R.string.developer_anomalies_explanation),
  ) {
    if (!presenter.hasAnomalies) {
      Text(
        text = AnomalyReport.NOTHING,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(DeveloperTestTags.NO_ANOMALIES),
      )
      return@Section
    }
    presenter.anomalies.forEach { anomaly -> AnomalyRow(anomaly) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(
        onClick = { onShare(presenter.report) },
        modifier = Modifier.heightIn(min = TOUCH_TARGET).testTag(DeveloperTestTags.SHARE),
      ) {
        Text(stringResource(R.string.developer_anomalies_share))
      }
      TextButton(
        onClick = presenter::clear,
        modifier = Modifier.heightIn(min = TOUCH_TARGET).testTag(DeveloperTestTags.CLEAR),
      ) {
        Text(stringResource(R.string.developer_anomalies_clear))
      }
    }
  }
}

@Composable
private fun AnomalyRow(anomaly: Anomaly) {
  Text(
    text = AnomalyReport.line(anomaly),
    style = MaterialTheme.typography.labelSmall,
    fontFamily = FontFamily.Monospace,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.testTag(DeveloperTestTags.anomalyOf(anomaly.seed)),
  )
}

/** Android's own minimum, so a button here is as pressable as one anywhere. */
private val TOUCH_TARGET = 48.dp

/** Stable handles for tests, so a wording change does not break them. */
object DeveloperTestTags {
  const val SCREEN: String = "developer:screen"

  /** Throwing the last roll again (`docs/physics-and-rendering.md`). */
  const val REPLAY_LAST: String = "developer:replay-last"
  const val REPLAY_SEED: String = "developer:replay-seed"
  const val SEED: String = "developer:seed"
  const val NOTHING: String = "developer:nothing"
  const val READY: String = "developer:ready"
  const val RUNNING: String = "developer:running"
  const val RESULT: String = "developer:result"
  const val VERDICT: String = "developer:verdict"

  /** The log that should always be empty. */
  const val NO_ANOMALIES: String = "developer:no-anomalies"
  const val SHARE: String = "developer:share"
  const val CLEAR: String = "developer:clear"

  fun anomalyOf(seed: Long): String = "developer:anomaly:$seed"
}
