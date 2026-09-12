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
) {
    Roll("roll", "Roll", MenuGroup.Play),
    Graph("graph", "Outcome graph", MenuGroup.Play),
    SavedRolls("saved", "Saved rolls", MenuGroup.Play),

    Statistics("stats", "Statistics", MenuGroup.LookBack),
    History("history", "History", MenuGroup.LookBack),
    Sessions("sessions", "Sessions", MenuGroup.LookBack),

    DiceSets("sets", "Dice sets", MenuGroup.Customise),
    Tables("tables", "Table", MenuGroup.Customise),
    FaceDesigner("designer", "Face designer", MenuGroup.Customise),

    Settings("settings", "Settings", MenuGroup.App),
    ;

    companion object {
        val home: Destination = Roll

        fun ofRoute(route: String): Destination? = entries.firstOrNull { it.route == route }
    }
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
