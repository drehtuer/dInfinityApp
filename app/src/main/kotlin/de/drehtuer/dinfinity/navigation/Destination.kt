package de.drehtuer.dinfinity.navigation

/**
 * Every screen the app has. One entry per screen in the plan's Step 4, so the
 * navigation graph is complete before any screen is built and adding a screen
 * is a change in one place — plus the few that belong *to* one of those steps
 * rather than being one, like the saved-roll statistics inside Step 4.7.
 *
 * [Roll] is home; everything else is reached from the menu
 * (`design/dInfinity.dc.html`, option 1q).
 */
enum class Destination(
  val route: String,
  val title: String,
  /**
   * One line saying what the screen is for, shown under its name in the menu.
   *
   * English here beside [title], which is where the English already was.
   * Extracting both is Step 6's localisation pass (`docs/TODO.md`).
   */
  val description: String,
  /**
   * The section of the menu this screen sits in, or `null` for one that is
   * not in the menu — which is the menu.
   */
  val group: MenuGroup?,
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
  Roll(
    "roll",
    "Roll",
    "The tray. Shake it, or pick dice and press Roll.",
    MenuGroup.Play,
    arguments = listOf(GraphArgument.FORMULA),
  ),
  Graph(
    "graph",
    "Outcome graph",
    "Exact odds before you roll, with the mean and σ.",
    MenuGroup.Play,
    arguments = listOf(GraphArgument.FORMULA, GraphArgument.TOTAL),
  ),
  SavedRolls("saved", "Saved rolls", "Groups per game and per character.", MenuGroup.Play),

  Statistics("stats", "Statistics", "Natural highs and lows, averages — per die and per set.", MenuGroup.LookBack),
  History("history", "History", "Every roll with its breakdown. No replays: a roll is a roll.", MenuGroup.LookBack),
  Sessions("sessions", "Sessions", "Buckets for statistics, and where collections are imported.", MenuGroup.LookBack),
  SavedRollStats(
    "savedstats",
    "Saved-roll statistics",
    "What each saved roll has come to, against what it should.",
    MenuGroup.LookBack,
  ),

  DiceSets("sets", "Dice sets", "What is installed, and how to install more.", MenuGroup.Customise),
  Tables("tables", "Table", "Felt, wood, glass or your own photo. Same tray.", MenuGroup.Customise),
  FaceDesigner("designer", "Face designer", "Draw die faces with a finger, then roll them.", MenuGroup.Customise),

  // No haptics in the list: nothing plays anything yet, and a menu row is as
  // able to promise something that is not there as a settings row is.
  Settings("settings", "Settings", "Appearance, shake, rounding, power saving.", MenuGroup.App),

  /** Everything above, in a list. Not in the menu, being the menu. */
  Menu("menu", "dInfinity", "", group = null),

  /**
   * Writing down one saved roll.
   *
   * Not in the menu: it is about a roll, and the way to it is a row on the
   * saved-rolls screen or its "New" button (`design/dInfinity.dc.html`,
   * option 1r).
   */
  SavedRollEditor("editor", "Saved roll", "", group = null, arguments = listOf(EditorArgument.ROLL)),

  /**
   * Taking a collection of saved rolls in
   * (`design/dInfinity.dc.html`, options 9f and 9g).
   *
   * Not in the menu, for the same reason the editor is not: it is about saved
   * rolls, and the way to it is the saved-rolls screen's own control. A menu
   * row for it would be a row that means nothing until somebody has a file.
   */
  CollectionImport("import", "Import a collection", "", group = null),

  /**
   * One dice set, in detail (`design/dInfinity.dc.html`, options `6a`
   * and `6b`).
   *
   * Not in the menu: it is about *a* set, and the way to it is a row on the
   * dice-set screen. Reached with no argument it shows the bundled set, which
   * is the one set that is always installed — so the route is still a route
   * rather than a screen that can fail to open.
   */
  SetDetail("setdetail", "Dice set", "", group = null, arguments = listOf(SetArgument.SET)),
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

    /** Every screen the menu lists, in the order it lists them. */
    val inTheMenu: List<Destination> get() = entries.filter { it.group != null }

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
  /**
   * The formula a screen is about.
   *
   * The outcome graph is opened with one; so is the roll screen, when a saved
   * roll or the graph sends a formula to the tray.
   */
  const val FORMULA: String = "formula"

  /** The total that was rolled, to mark on the chart. Empty for no mark. */
  const val TOTAL: String = "total"
}

/** What the saved-roll editor is opened with. */
object EditorArgument {
  /** The roll being edited, or empty for a new one. */
  const val ROLL: String = "roll"
}

/**
 * What the set details screen is opened with.
 *
 * Its own object rather than constants on [Destination], for the reason
 * [GraphArgument] gives: an enum's companion is not initialised when its
 * entries are.
 */
object SetArgument {
  /** The folder name of the set being shown. Empty means the bundled one. */
  const val SET: String = "set"
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
