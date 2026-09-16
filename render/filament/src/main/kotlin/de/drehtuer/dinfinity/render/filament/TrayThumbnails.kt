package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook

/**
 * Pictures of tables, drawn where the engine is
 * (`docs/architecture.md`, decision 60; `docs/tables.md`, "Thumbnails").
 *
 * **On the roll thread, with the roll screen's engine.** Filament takes calls
 * only from the thread that made the engine (decision 49), so a thumbnail
 * renderer that opened one of its own on the thread Compose draws on would be
 * a bug that works until it does not. Opening a *second* engine on a second
 * thread would work and costs the thing decision 50 exists to avoid: the dice
 * material is compiled on the device for the driver that is actually there and
 * that takes long enough to watch, so a second engine is that cost paid twice
 * and two graphics contexts held for one app. [RollThread] already outlives a
 * visit to any screen and already has one engine on it; [on] posts to it.
 * What a thumbnail owns is a swap chain and a scene, and both are given back
 * before the post returns.
 *
 * Which is why what it is *given* is a way to post and a way to get a stage,
 * rather than a thread and an engine. The thread is named in one factory
 * method, so everything this class actually does — when a look is drawn, when
 * it is not drawn again, what happens to a frame that came back empty, and
 * what happens to a device where the engine will not open — is a plain test
 * over a stage that draws nothing (`FakeStage`), which is the same line
 * decisions 40 and 47 draw.
 *
 * A picture that cannot be drawn is not an error anybody is told about. The
 * table picker's swatch is the fallback and stays in that screen for exactly
 * this: a device whose driver will not open an engine, or will not read a
 * frame back, shows the two colours a look is made of and the list works.
 *
 * @param plan how big a picture is and what it is a picture of.
 * @param post how work reaches the thread the engine lives on. Everything
 *   below happens inside one of these, the cache included — which is what lets
 *   the cache be a plain map with no lock in it.
 * @param stages somewhere to draw one picture, given back when it is drawn.
 */
class TrayThumbnails(
  private val plan: ThumbnailPlan,
  private val post: (() -> Unit) -> Unit,
  private val cache: ThumbnailCache = ThumbnailCache(),
  private val stages: () -> Stage,
) {
  /**
   * Why this device has no thumbnails, once it has turned out not to have any.
   *
   * Read by tests and by nobody who is drawing a screen: a player is shown the
   * swatch, not an explanation of a graphics driver.
   */
  @Volatile
  var refusedBecause: String? = null
    private set

  /**
   * Asks for a picture of [look], and calls [onDrawn] on the drawing thread
   * when there is one.
   *
   * Not called back at all when there is no picture to give — a look that has
   * been tried and could not be drawn, a device with no working engine, or a
   * package that ships no d20 to stand on the table. Whoever wanted one goes
   * on showing what they were showing.
   *
   * @param key what this look is remembered by, spelt by the caller. Two
   *   packages may each ship a `green-felt`, so a picture filed under a look's
   *   own id would be the wrong table's floor (`docs/tables.md`, "Selecting a
   *   table").
   * @param die the die standing on the table, or null to draw the table with
   *   nothing on it.
   */
  fun of(
    key: String,
    look: TableLook,
    die: ThumbnailDie?,
    onDrawn: (Snapshot) -> Unit,
  ) {
    post {
      if (!cache.asked(key)) cache.put(key, drawn(look, die))
      cache.of(key)?.let(onDrawn)
    }
  }

  /** Forgets every picture, so the next ask draws again. On the drawing thread. */
  fun forget() {
    post { cache.clear() }
  }

  /**
   * One picture, drawn and read back, or null when this device cannot.
   *
   * The scene is [FilamentDiceRenderer]'s own, built exactly as a throw builds
   * one — the tray mesh from the look, the same lights, the same material and
   * the die drawn by the same code with the same artwork. A second way of
   * building it would be a second thing to keep in step, and the first time
   * they drifted the picker would be showing a table the tray does not draw.
   *
   * A frame that comes back all one colour is thrown away rather than shown.
   * Some drivers render correctly to a screen and hand back an empty buffer
   * when asked to read one — the emulator's software backend is one — and a
   * black rectangle in a list of tables is worse than the swatch it replaced.
   */
  private fun drawn(
    look: TableLook,
    die: ThumbnailDie?,
  ): Snapshot? {
    if (refusedBecause != null) return null
    return try {
      stages().use { stage ->
        val renderer = FilamentDiceRenderer(stage)
        paint(renderer, look, die)
        val picture = stage.capture()
        renderer.end()
        picture?.takeUnless(Snapshot::uniform)
      }
    } catch (unavailable: IllegalStateException) {
      // Filament refusing to build something: no engine, or a material that
      // did not compile on this driver.
      refusedBecause = unavailable.message ?: unavailable.toString()
      null
    } catch (missing: LinkageError) {
      // Its native library, on a device or a runtime that has none.
      refusedBecause = missing.message ?: missing.toString()
      null
    }
  }

  /**
   * The tray, the die on it and the camera in the corner.
   *
   * Two frames rather than one, and deliberately: the renderer places a die in
   * the act of drawing it, so [FilamentDiceRenderer.show] puts the die where
   * the plan says and the frame after it is the one that is read back. A scene
   * that is not moving costs nothing to draw twice, and the alternative is a
   * place-without-drawing hole in a renderer that has no other reason for one.
   */
  private fun paint(
    renderer: FilamentDiceRenderer,
    look: TableLook,
    die: ThumbnailDie?,
  ) {
    if (die == null) {
      renderer.table(plan.geometry, look, plan.view)
      return
    }
    renderer.begin(plan.spec(look, die), plan.geometry, look)
    renderer.look(plan.view)
    renderer.show(plan.standing(die))
  }

  companion object {
    /**
     * Thumbnails on the application's roll thread, drawn with its engine.
     *
     * The one place a thread and a graphics engine are named. The stage is
     * made inside the post, because [RollThread.filament] may only be called
     * where the engine is.
     *
     * A swap chain made from no surface, flagged readable, which is what a
     * picture nobody is watching wants. Post-processing is left on, unlike the
     * device suite's readback: it is Filament's tone mapping, and a tray drawn
     * without it is a white rectangle rather than a table — a thumbnail has to
     * look like what the roll screen draws or it is not a preview of anything.
     *
     * One stage per picture, closed with the scene inside it. A stage kept
     * between pictures would be a swap chain held for as long as the app runs,
     * for a screen somebody visits twice.
     */
    fun on(
      host: RollThread,
      plan: ThumbnailPlan,
    ): TrayThumbnails =
      TrayThumbnails(
        plan = plan,
        post = { work -> host.handler.post(work) },
        stages = { host.filament().stage(surface = null, width = plan.widthPx, height = plan.heightPx) },
      )
  }
}
