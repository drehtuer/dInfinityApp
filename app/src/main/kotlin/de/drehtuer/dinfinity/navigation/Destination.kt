package de.drehtuer.dinfinity.navigation

/**
 * Every screen the app has. One entry per screen in the plan's Step 4, so the
 * navigation graph is complete before any screen is built and adding a screen
 * is a change in one place.
 *
 * [Roll] is home; everything else is reached from the menu
 * (`design/dInfinity.dc.html`, option 1q).
 */
enum class Destination(
  val route: String,
  val title: String,
  /** The section of the menu this screen sits in. */
  val group: MenuGroup,
  /**
   * What this screen is opened *with*, as query arguments on its route.
   *
   * Every one of them is optional and defaults to empty, so a destination can
   * always be reached by its bare route — from the menu, from a restored back
   * stack, from a test. A screen that could only be opened with an argument
   * would be a screen the menu could not open (`docs/TODO.md`, Step 4.10).
   */
  val arguments: List<String> = emptyList(),
) {
  Roll("roll", "Roll", MenuGroup.Play),
  Graph("graph", "Outcome graph", MenuGroup.Play, arguments = listOf(GraphArgument.FORMULA, GraphArgument.TOTAL)),
  SavedRolls("saved", "Saved rolls", MenuGroup.Play),

  Statistics("stats", "Statistics", MenuGroup.LookBack),
  History("history", "History", MenuGroup.LookBack),
  Sessions("sessions", "Sessions", MenuGroup.LookBack),

  DiceSets("sets", "Dice sets", MenuGroup.Customise),
  Tables("tables", "Table", MenuGroup.Customise),
  FaceDesigner("designer", "Face designer", MenuGroup.Customise),

  Settings("settings", "Settings", MenuGroup.App),
  ;

  /**
   * The route pattern the navigation graph registers, arguments and all.
   *
   * `graph?formula={formula}&total={total}` for a screen with arguments, and
   * the bare route for one without.
   */
  val pattern: String
    get() =
      if (arguments.isEmpty()) {
        route
      } else {
        arguments.joinToString(separator = "&", prefix = "$route?") { "$it={$it}" }
      }

  companion object {
    val home: Destination = Roll

    /**
     * The destination [route] names, or `null` for a route nothing serves.
     *
     * Arguments are not part of which screen it is, so a concrete route with
     * them on — `graph?formula=3d6` — resolves to the same destination as the
     * bare one.
     */
    fun ofRoute(route: String): Destination? = entries.firstOrNull { it.route == route.substringBefore('?') }
  }
}

/**
 * What the outcome graph is opened with.
 *
 * Its own object rather than constants on [Destination], because an enum's
 * companion is not initialised when its entries are
 * (`design/dInfinity.dc.html`, option 7a).
 */
object GraphArgument {
  /** The formula the graph is about. */
  const val FORMULA: String = "formula"

  /** The total that was rolled, to mark on the chart. Empty for no mark. */
  const val TOTAL: String = "total"
}

/** The menu's four groups (`design/dInfinity.dc.html`, option 1q). */
enum class MenuGroup(
  val title: String,
) {
  Play("Play"),
  LookBack("Look back"),
  Customise("Customise"),
  App("App"),
}
