package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.ui.common.Ink

/**
 * The debug overlay: what the roll is doing, over the tray it is doing it on
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * Drawn only when the developer toggle is on, which it is on no install until
 * somebody turns it on (`AppSettings.developerTools`).
 *
 * **It is a view and changes nothing.** What it draws comes from
 * [RollDiagnostics], which a roll builds when it is asked and hands over
 * through a watcher that returns nothing — the same promise `Renderer` makes
 * (`docs/architecture.md`, decision 38). The same seed comes to the same faces
 * with this on screen and with it absent, and `simulation/jolt`'s
 * `RollDiagnosticsTest` says so rather than assuming it.
 *
 * **It is a plan, not a wireframe.** The dice are drawn by Filament in
 * perspective from a tilted camera; this is the tray from straight above, in
 * its own panel, in Compose. Registering a wireframe to the rendered picture
 * would mean reproducing the projection, the pinch and the pan on the near
 * side of `Stage` — new code behind the line no JVM test can reach, to draw
 * outlines over pictures that already show where the dice are. A plan says
 * what the pictures cannot: which die is standing on another, which is against
 * a wall, and which has not stopped yet ([TrayPlan]).
 *
 * @param diagnostics the latest snapshot. [RollDiagnostics.NONE] draws an
 *   empty tray, which is what there is before the first throw.
 */
@Composable
fun DebugOverlay(
  diagnostics: RollDiagnostics,
  geometry: TableGeometry,
  modifier: Modifier = Modifier,
) {
  val anomalies = diagnostics.forcedSettles + diagnostics.postRestCorrections
  val overlayLabel = stringResource(R.string.roll_debug_overlay)
  Column(
    modifier =
      modifier
        .width(PANEL_WIDTH)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = PANEL_ALPHA))
        .padding(8.dp)
        .semantics(mergeDescendants = true) {
          contentDescription = overlayLabel
        }.testTag(DebugTestTags.OVERLAY),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Line(
      text =
        stringResource(
          R.string.roll_debug_counters,
          diagnostics.steps,
          diagnostics.atRest,
          diagnostics.diceCount,
        ),
      colour = MaterialTheme.colorScheme.onSurface,
      tag = DebugTestTags.COUNTERS,
    )
    Line(
      text =
        stringResource(
          R.string.roll_debug_ladder,
          diagnostics.corrections,
          diagnostics.rethrows,
          diagnostics.contacts.size,
        ),
      colour = MaterialTheme.colorScheme.onSurface,
      tag = DebugTestTags.LADDER,
    )
    // Only when there is one, and in the error colour when there is: this is
    // the line that says the app has done the thing it promises never to do,
    // and it should not be one row among five that are always there
    // (`docs/TODO.md`, Step 5.5 — zero post-rest corrections).
    if (anomalies > 0) {
      Line(
        text =
          stringResource(
            R.string.roll_debug_anomaly,
            diagnostics.forcedSettles,
            diagnostics.postRestCorrections,
          ),
        colour = MaterialTheme.colorScheme.error,
        tag = DebugTestTags.ANOMALY,
      )
    }
    TrayPlanView(diagnostics = diagnostics, geometry = geometry)
  }
}

/**
 * The tray from above, with a footprint per die and a dot per recent contact.
 *
 * Everything drawn here was decided before it got here: where a die goes, how
 * big it is, how far its rest timer has filled and which of the three states it
 * is in are all [TrayPlan]'s and are tested on a JVM. What is left in the
 * `Canvas` lambda is a rectangle, a circle and a colour.
 */
@Composable
private fun TrayPlanView(
  diagnostics: RollDiagnostics,
  geometry: TableGeometry,
) {
  val wall = MaterialTheme.colorScheme.outline
  // Three tints that are three colours. The palette has one red and the theme
  // maps `error` onto it, so "at rest" and "in trouble" both used to come out
  // accent — and the one that should shout was the one that did not. Ink for a
  // die that has stopped, the muted ink for one still moving, and the accent
  // kept for the only state that is a bug.
  val tints =
    mapOf(
      PlanTint.Still to MaterialTheme.colorScheme.onSurface,
      PlanTint.Moving to Ink.muted,
      PlanTint.Trouble to MaterialTheme.colorScheme.error,
    )
  val dice = diagnostics.dice.map { die -> TrayPlan.markOf(die, geometry) }
  val contacts = diagnostics.contacts.map { contact -> TrayPlan.markOf(contact, geometry) }
  // The plan says which die is which by tinting its box, which is a mark only
  // an eye can read. The counts are the whole of what the picture claims, so a
  // `Canvas` that would otherwise be silent says them
  // (`docs/architecture.md`, "Accessibility").
  val tally = TrayPlan.tally(dice)
  val label =
    pluralStringResource(
      R.plurals.roll_debug_plan,
      dice.size,
      dice.size,
      tally.getValue(PlanTint.Still),
      tally.getValue(PlanTint.Moving),
      tally.getValue(PlanTint.Trouble),
    )
  Canvas(
    modifier =
      Modifier
        .width(PANEL_WIDTH - 16.dp)
        // The tray's own shape, long side vertical, so the plan and the tray
        // are the same way up.
        .aspectRatio(TrayPlan.aspect(geometry).toFloat())
        .semantics { contentDescription = label }
        .testTag(DebugTestTags.PLAN),
  ) {
    drawRect(color = wall, style = Stroke(width = 1.dp.toPx()))
    dice.forEach { mark -> drawDie(mark, tints.getValue(mark.tint)) }
    contacts.forEach { mark ->
      drawDot(mark, if (mark.onADie) tints.getValue(PlanTint.Trouble) else tints.getValue(PlanTint.Moving))
    }
  }
}

/** One die's box, outlined and filled as far as its rest timer has got. */
private fun DrawScope.drawDie(
  mark: DieMark,
  colour: Color,
) {
  val corner = Offset((mark.left * size.width).toFloat(), (mark.top * size.height).toFloat())
  val box = Size((mark.width * size.width).toFloat(), (mark.height * size.height).toFloat())
  drawRect(color = colour, topLeft = corner, size = box, style = Stroke(width = 1.dp.toPx()))
  if (mark.fill > 0.0) drawRect(color = colour.copy(alpha = mark.fill.toFloat()), topLeft = corner, size = box)
}

/** One contact, as a dot where the die that made it was. */
private fun DrawScope.drawDot(
  mark: ContactMark,
  colour: Color,
) {
  drawCircle(
    color = colour,
    radius = CONTACT_DOT.toPx() * mark.size.toFloat(),
    center = Offset((mark.across * size.width).toFloat(), (mark.along * size.height).toFloat()),
  )
}

/** One row of the panel. Monospaced, because the numbers change every frame. */
@Composable
private fun Line(
  text: String,
  colour: Color,
  tag: String,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    fontFamily = FontFamily.Monospace,
    color = colour,
    modifier = Modifier.testTag(tag),
  )
}

/** Narrow: it sits over the tray and the tray is the screen. */
private val PANEL_WIDTH = 168.dp

/** The dot a contact is drawn as, at its hardest. */
private val CONTACT_DOT = 3.dp

/** Readable over the dice without hiding them. */
private const val PANEL_ALPHA = 0.85f

/** What a test reaches the overlay by. */
object DebugTestTags {
  const val OVERLAY: String = "roll:debug"
  const val COUNTERS: String = "roll:debug:counters"
  const val LADDER: String = "roll:debug:ladder"

  /** Shown only when something has gone wrong, which should be never. */
  const val ANOMALY: String = "roll:debug:anomaly"
  const val PLAN: String = "roll:debug:plan"
}
