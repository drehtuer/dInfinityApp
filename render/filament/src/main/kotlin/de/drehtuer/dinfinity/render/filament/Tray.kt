package de.drehtuer.dinfinity.render.filament

import android.view.Surface
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

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
   * Whether this tray needs somewhere to draw.
   *
   * False in power-saving mode, where the screen puts no surface on the
   * screen at all rather than handing over one that nothing will draw to
   * (`design/dInfinity.dc.html`, option 1z).
   */
  val draws: Boolean get() = true

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
   * dice came to and the shake that drove it — and only for a roll that
   * actually finished: a roll abandoned because the player left the screen
   * reports nothing, because nothing landed, and its samples go with it.
   *
   * The shake comes back with the outcome rather than being asked for
   * afterwards because by then there is no roll left to ask: the roll is
   * closed the moment it is read (`WatchedRoll.drivenBy`).
   *
   * [onCounted] arrives wherever the roll is stepped, every time another die
   * is read — which is what the screen follows while a roll is going, because
   * a counted die leaves the table and the running total is all that is left
   * to watch (`docs/TODO.md`, Step 5.5). It is only called when the reading
   * changes, so a roll nobody is adding to costs nothing.
   */
  fun roll(
    start: (Renderer) -> WatchedRoll,
    onCounted: (Map<Int, Int>) -> Unit = {},
    onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
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
   * These dice are on the table, waiting to be thrown.
   *
   * Tapping a saved roll clears the board and puts its dice down rather than
   * throwing them; more can be added from the picker and the formula can be
   * edited, and the board follows along. What the player looks at before they
   * shake is what they are about to throw (`docs/TODO.md`, Step 4.1).
   *
   * **Nothing about this is a roll.** No body is made, no step is taken and no
   * face is read — these are dice drawn where [RestingPlaces] says they sit,
   * and the faces they happen to show are not a result and are never scored.
   * Passing no dice clears the board back to an empty table.
   */
  fun waiting(spec: ThrowSpec) = Unit

  /**
   * One more moment of the shake that is throwing the dice now.
   *
   * Arrives from wherever the sensors are read and is handed to the roll on
   * the thread the roll lives on. A sample with no roll to drive is dropped.
   */
  fun shake(sample: ShakeSample)

  /**
   * The player is looking somewhere else, or closer.
   *
   * The one thing that moves the camera. It frames the whole tray otherwise
   * and never moves off it on its own — not even when the dice settle — so
   * looking closer is the player's to do (`docs/physics-and-rendering.md`).
   *
   * Nothing about the roll changes. A view that would leave the table is
   * brought back to its edge rather than refused.
   */
  fun look(view: TrayView)

  /** Takes whatever is on the tray off it. */
  fun clear()
}
