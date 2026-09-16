package de.drehtuer.dinfinity.navigation

import androidx.annotation.StringRes
import de.drehtuer.dinfinity.R

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
  /** What the screen is called, as a resource — never as a literal. */
  @field:StringRes val title: Int,
  /**
   * One line saying what the screen is for, shown under its name in the menu,
   * or `null` for a screen the menu does not list.
   *
   * Nullable rather than an empty string: "this screen has no line" and "this
   * screen's line is blank" are different things, and only the first is true
   * of the four screens reached from a row rather than from the list. A blank
   * string resource would also be a blank string for a translator to wonder
   * about (`docs/architecture.md`, "Text a person reads").
   */
  @field:StringRes val description: Int? = null,
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
  /**
   * Whether this screen is listed only while `AppSettings.developerTools` is
   * on (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * The *route* is always registered, because a route that came and went would
   * be a back stack that could not be restored. What the toggle governs is
   * whether the menu offers it — so with the toggle off the screen is
   * unlisted, and the app a player uses has no way to it at all.
   */
  val developerOnly: Boolean = false,
) {
  Roll(
    "roll",
    R.string.screen_roll,
    R.string.screen_roll_description,
    MenuGroup.Play,
    arguments = listOf(GraphArgument.FORMULA),
  ),
  Graph(
    "graph",
    R.string.screen_graph,
    R.string.screen_graph_description,
    MenuGroup.Play,
    arguments = listOf(GraphArgument.FORMULA, GraphArgument.TOTAL),
  ),
  SavedRolls("saved", R.string.screen_saved, R.string.screen_saved_description, MenuGroup.Play),

  Statistics("stats", R.string.screen_stats, R.string.screen_stats_description, MenuGroup.LookBack),
  History("history", R.string.screen_history, R.string.screen_history_description, MenuGroup.LookBack),
  Sessions("sessions", R.string.screen_sessions, R.string.screen_sessions_description, MenuGroup.LookBack),
  SavedRollStats(
    "savedstats",
    R.string.screen_savedstats,
    R.string.screen_savedstats_description,
    MenuGroup.LookBack,
  ),

  DiceSets("sets", R.string.screen_sets, R.string.screen_sets_description, MenuGroup.Customise),
  Tables("tables", R.string.screen_tables, R.string.screen_tables_description, MenuGroup.Customise),
  FaceDesigner(
    "designer",
    R.string.screen_designer,
    R.string.screen_designer_description,
    MenuGroup.Customise,
    arguments = listOf(DesignerArgument.DIE),
  ),

  // No haptics in the list: nothing plays anything yet, and a menu row is as
  // able to promise something that is not there as a settings row is.
  Settings("settings", R.string.screen_settings, R.string.screen_settings_description, MenuGroup.App),

  /**
   * The debugging tools: the anomaly log and the two replays
   * (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * In the App section beside Settings, and **listed only while the developer
   * toggle is on** — which it is on no install until somebody turns it on. It
   * is a surface of its own rather than a flag that unhides fields elsewhere:
   * the history still has no replay and still never shows a seed with the
   * toggle on (`docs/architecture.md`, decisions 13 and 56).
   */
  Developer(
    "developer",
    R.string.screen_developer,
    R.string.screen_developer_description,
    MenuGroup.App,
    developerOnly = true,
  ),

  /**
   * What the formula field understands (`docs/dice-notation.md`).
   *
   * In the App section rather than under Play, because it is a thing to look
   * up rather than a thing to do — and because somebody reaching for it is
   * usually in the middle of something else.
   */
  Notation("notation", R.string.screen_notation, R.string.screen_notation_description, MenuGroup.App),

  /** Everything above, in a list. Not in the menu, being the menu. */
  Menu("menu", R.string.app_name, group = null),

  /**
   * Writing down one saved roll.
   *
   * Not in the menu: it is about a roll, and the way to it is a row on the
   * saved-rolls screen or its "New" button (`design/dInfinity.dc.html`,
   * option 1r).
   */
  SavedRollEditor(
    "editor",
    R.string.screen_editor,
    group = null,
    arguments = listOf(EditorArgument.ROLL, EditorArgument.FORMULA),
  ),

  /**
   * Taking a collection of saved rolls in
   * (`design/dInfinity.dc.html`, options 9f and 9g).
   *
   * Not in the menu, for the same reason the editor is not: it is about saved
   * rolls, and the way to it is the saved-rolls screen's own control. A menu
   * row for it would be a row that means nothing until somebody has a file.
   */
  CollectionImport("import", R.string.screen_import, group = null),

  /**
   * One dice set, in detail (`design/dInfinity.dc.html`, options `6a`
   * and `6b`).
   *
   * Not in the menu: it is about *a* set, and the way to it is a row on the
   * dice-set screen. Reached with no argument it shows the bundled set, which
   * is the one set that is always installed — so the route is still a route
   * rather than a screen that can fail to open.
   */
  SetDetail("setdetail", R.string.screen_setdetail, group = null, arguments = listOf(SetArgument.SET)),
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
     * Every screen the menu lists for an ordinary install, in the order it
     * lists them.
     *
     * The developer screen is not one of them, because the toggle behind it is
     * off on every install — [inTheMenu] with an argument is what a menu
     * actually builds from.
     */
    val inTheMenu: List<Destination> get() = inTheMenu(developerTools = false)

    /**
     * The same, for an install where the developer toggle is [developerTools].
     *
     * One list rather than a filter at each call site: which screens the menu
     * offers is a property of the destinations, and a caller that forgot the
     * filter would be a caller that offered a debugging tool to a player.
     */
    fun inTheMenu(developerTools: Boolean): List<Destination> =
      entries.filter { it.group != null && (developerTools || !it.developerOnly) }

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

/**
 * What the face designer is opened with.
 *
 * Its own object rather than constants on [Destination], for the reason
 * [GraphArgument] gives: an enum's companion is not initialised when its
 * entries are.
 */
object DesignerArgument {
  /**
   * The die to draw on, by id. Empty opens on the usual one.
   *
   * What "Doodle this die" carries from the roll screen
   * (`docs/face-designer.md`, "Quick mode"). Empty is the menu's way in, and
   * an id nothing answers to opens on the usual die rather than on nothing —
   * a package can be removed while its result is still on the tray
   * (`designer`'s `OpeningDie`).
   */
  const val DIE: String = "die"
}

/** What the saved-roll editor is opened with. */
object EditorArgument {
  /** The roll being edited, or empty for a new one. */
  const val ROLL: String = "roll"

  /**
   * A formula to start a new roll from, or empty to start from nothing.
   *
   * What the outcome graph's "Save as roll" carries: somebody who has been
   * looking at a formula's odds and decides to keep it should not have to
   * retype it (`design/dInfinity.dc.html`, option 7a).
   *
   * Ignored when [ROLL] names a roll that already exists — that roll has a
   * formula of its own, and the one in the link would be overwriting it.
   */
  const val FORMULA: String = "formula"
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
  @field:StringRes val title: Int,
) {
  Play(R.string.menu_group_play),
  LookBack(R.string.menu_group_look_back),
  Customise(R.string.menu_group_customise),
  App(R.string.menu_group_app),
}
