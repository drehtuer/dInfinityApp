package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding

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
