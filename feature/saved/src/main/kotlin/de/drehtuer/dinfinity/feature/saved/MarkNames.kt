package de.drehtuer.dinfinity.feature.saved

import androidx.annotation.StringRes

/**
 * What each mark is a picture *of*.
 *
 * A mark is stored and drawn as the emoji itself, which is what a player sees
 * and what the saved-roll format carries (`docs/dice-notation.md`). A screen
 * reader cannot read that: left to itself it announces whatever the platform
 * calls the codepoint, or nothing at all, and a row of eighteen unlabelled
 * buttons is what the picker was before this existed.
 *
 * **One name per picture, not one per picker.** The roll editor and the group
 * sheet offer overlapping sets — `⚔️` and `🎲` are in both — and a mark means
 * the same thing in either: it is a picture somebody chose to recognise their
 * own roll by. Naming them twice would be two places for the same word to
 * drift.
 *
 * A mark with no name here falls back to none rather than to the emoji, so a
 * mark added to a list without a string is a silent button rather than a
 * screen reader spelling out "crossed swords emoji variation selector".
 */
@StringRes
internal fun markName(mark: String): Int? = MARK_NAMES[mark]

private val MARK_NAMES: Map<String, Int> =
  mapOf(
    "⚔️" to R.string.mark_crossed_swords,
    "🏹" to R.string.mark_bow,
    "🔥" to R.string.mark_fire,
    "🛡️" to R.string.mark_shield,
    "✨" to R.string.mark_sparkles,
    "💀" to R.string.mark_skull,
    "🗡️" to R.string.mark_dagger,
    "💥" to R.string.mark_burst,
    "🎲" to R.string.mark_dice,
    "🧪" to R.string.mark_potion,
    "🐉" to R.string.mark_dragon,
    "🏰" to R.string.mark_castle,
    "🗺️" to R.string.mark_map,
    "🧙" to R.string.mark_wizard,
    "🌲" to R.string.mark_tree,
    "🚀" to R.string.mark_rocket,
    "📕" to R.string.mark_book,
    "⭐" to R.string.mark_star,
  )
