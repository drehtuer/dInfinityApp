// One function per setting is what this file *is*, so the count grows with the
// settings and splitting it would be the same list behind two names — the same
// argument `JoltNative` makes about its JNI surface.
@file:Suppress("TooManyFunctions")

package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TablePin

/*
 * The settings, one named change each.
 *
 * Extensions rather than interface members, so that adding a setting is one
 * function here rather than a method every implementation has to grow — and so
 * that a call site still says what it means rather than passing a lambda
 * around.
 */

/** The colour the whole app spends (`design/dInfinity.dc.html`, option 1q). */
suspend fun SettingsRepository.setAccentColor(accent: AccentColor) = update { it.copy(accentColor = accent) }

/** Light, dark, or whatever the phone is doing. */
suspend fun SettingsRepository.setAppearance(appearance: Appearance) = update { it.copy(appearance = appearance) }

/** Turns drawing the dice off, or back on (`docs/physics-and-rendering.md`). */
suspend fun SettingsRepository.setPowerSaving(on: Boolean) = update { it.copy(powerSaving = on) }

/**
 * Whether the phone ticks when a die hits something
 * (`docs/physics-and-rendering.md`, "Haptics and sound").
 *
 * With sound, off means a roll records no impacts at all rather than recording
 * them and throwing them away.
 */
suspend fun SettingsRepository.setHaptics(on: Boolean) = update { it.copy(haptics = on) }

/** Whether a die hitting something makes a noise (`docs/physics-and-rendering.md`). */
suspend fun SettingsRepository.setSound(on: Boolean) = update { it.copy(sound = on) }

/** Whether shaking the phone throws the dice. Off means the sensors are never registered. */
suspend fun SettingsRepository.setShakeToRoll(on: Boolean) = update { it.copy(shakeToRoll = on) }

/** Which way division rounds unless a throw says otherwise (`docs/dice-notation.md`). */
suspend fun SettingsRepository.setRounding(rounding: Rounding) = update { it.copy(rounding = rounding) }

/** The player has been past the first-launch screen, and will not see it again. */
suspend fun SettingsRepository.setWelcomeSeen() = update { it.copy(welcomeSeen = true) }

/** The group of saved rolls the app is in (`docs/dice-notation.md`). */
suspend fun SettingsRepository.setActiveGroup(groupId: String) = update { it.copy(activeGroupId = groupId) }

/** The session new rolls are filed under (`docs/statistics.md`). */
suspend fun SettingsRepository.setActiveSession(sessionId: String) = update { it.copy(activeSessionId = sessionId) }

/**
 * The set plain notation resolves against first
 * (`docs/dice-sets.md`, design `6a`).
 *
 * Stored whatever it names. Whether that set is actually installed is a
 * question for the moment a formula is resolved, not for the moment somebody
 * taps a button: a set switched off for an evening and switched back on should
 * still be the default when it is.
 */
suspend fun SettingsRepository.setDefaultSet(setId: String) = update { it.copy(defaultSetId = setId) }

/**
 * The table every roll happens on, unless something more specific pins one
 * (`docs/tables.md`, "Selecting a table").
 *
 * Null puts it back to whatever the bundled package ships first, which is
 * where a new install starts.
 */
suspend fun SettingsRepository.setDefaultTable(pin: TablePin?) = update { it.copy(defaultTable = pin) }
