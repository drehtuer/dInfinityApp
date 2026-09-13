package de.drehtuer.dinfinity.render.filament

import android.view.Surface
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry

/**
 * Somewhere to throw dice and watch them land.
 *
 * The same seam [Stage] draws between deciding a picture and drawing one, one
 * level up: everything on this side is a screen saying *throw these* and
 * *there is somewhere to draw*, and everything on the far side is a thread, a
 * GPU and a physics world. [TrayDriver] is the one implementation that ships
 * (`docs/architecture.md`, decision 40).
 *
 * It is an interface because a screen's own logic — which throw goes to the
 * tray, what comes back, and what the player is shown while they wait — is
 * worth testing without starting a thread and opening an engine to do it.
 */
interface Tray : AutoCloseable {
  /**
   * There is somewhere to draw, this big. Called again with a new size when
   * the view is resized or the phone is turned.
   */
  fun surfaceAvailable(
    surface: Surface,
    width: Int,
    height: Int,
  )

  /**
   * The surface is being taken away. Returns only once nothing is drawing to
   * it, because a `Surface` may not be touched after the callback that
   * withdrew it has returned.
   */
  fun surfaceLost()

  /**
   * Throws the dice.
   *
   * [start] is run wherever the roll is going to be stepped and is handed the
   * renderer to watch with. [onSettled] is called once, there, with what the
   * dice came to — and only for a roll that actually finished: a roll
   * abandoned because the player left the screen reports nothing, because
   * nothing landed.
   */
  fun roll(
    start: (Renderer) -> WatchedRoll,
    onSettled: (SimulationOutcome) -> Unit,
  )

  /**
   * There is a table, and nothing has been thrown onto it yet.
   *
   * Said when the screen opens, and again whenever the look changes. Without
   * it a tray has no scene until the first throw, and a player arriving at the
   * screen is shown a black rectangle instead of a table waiting
   * (`docs/TODO.md`, Step 4.1).
   *
   * It is remembered, so a surface that arrives afterwards — or arrives again
   * after a rotation — is given the table too.
   */
  fun table(
    geometry: TableGeometry,
    look: TableLook,
  )

  /**
   * One more moment of the shake that is throwing the dice now.
   *
   * Arrives from wherever the sensors are read and is handed to the roll on
   * the thread the roll lives on. A sample with no roll to drive is dropped.
   */
  fun shake(sample: ShakeSample)

  /** Takes whatever is on the tray off it. */
  fun clear()
}
