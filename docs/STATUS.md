# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-13

## Where we are

- **Phase:** implementation. Steps 1 and 2 of `docs/TODO.md` are done. Step 3
  is done but for the SDF numbers, the atlases and the database migrations,
  each of which now arrives with the screen that needs it. **Step 4.1, the
  roll screen, is where the work is.**
- **The app rolls dice on a phone.** Type a formula, tap Roll or shake the
  Pixel 10a, and the dice tumble onto a green felt tray, come to rest, and
  their total appears. That is the first end of the app meeting the other.
- **Latest release:** `v0.0.1` — the skeleton, cut to prove the release
  pipeline. Signed, fingerprint-checked, published with its SHA-256.
- **Branch state:** everything up to #75 is merged and `main` is green.

## Done

- **The specification.** `README.md`, `docs/` and the clickable prototype in
  `design/`, cross-referenced both ways and published at
  <https://drehtuer.github.io/dInfinityApp/>. GPL-2.0-or-later.
- **The skeleton and CI.** Devcontainer, convention plugins, 24 modules, the
  Modernist theme, the navigation graph. Every linter and both test tiers run
  on each pull request; SonarQube blocks on its gate and JaCoCo on a function
  *and* branch floor. Dependencies pinned by SHA-256; a `vX.Y.Z` tag cuts a
  signed, immutable release.
- **All three testing tiers are reachable and each is run every time.** JVM
  and Robolectric on CI; the devcontainer's emulator (API 36, `x86_64`); the
  Pixel 10a over wireless debugging (API 37, `arm64-v8a`). Between them they
  cover the API the app targets and the ABI it ships.
- **Step 3's foundations.** A formula parsed and resolved against the
  installed sets, graphed exactly, planned against the capacity rule, settled
  and read face by face, watched by a renderer that cannot touch it, thrown by
  a shake, and written down in one transaction. A package from a stranger is
  validated rule by rule and installed without leaving anything behind if it
  fails; the bundled dice go through that same validator on every launch.
- **The physics.** Jolt 5.3.0, chosen by building both candidates against this
  project's own toolchain. Every *decision* about a roll is Kotlin over an
  interface, so the rule that matters most — nothing touches a die that has
  come to rest — is proved by JVM tests rather than sampled on a phone.
- **Determinism is asserted, not assumed.** Ten (seed, formula, input) cases
  carry what they came to; the emulator and the Pixel 10a agree bit for bit,
  spawn digest included. The trigonometry upstream of the engine went to
  `StrictMath` first, so that agreement rests on a guarantee rather than on
  two libms happening to match.
- **The renderer draws a roll.** Filament opens on the device's own driver and
  compiles its material there; the tray, the dice, the lights and the camera
  are all built from the same geometry the solver collides. One thread owns
  the physics world, the engine and the frame callback.

## In progress

- **Step 4.1.** What the screen has: the tray — drawn from the moment the
  screen opens, with nothing on it, rather than black until the first throw —
  a live-validated formula field, roll from the Roll button or a shake, a
  refusal for a throw the table cannot hold, a total, the breakdown under it
  with every die that landed, Down / Nearest / Up for a throw that divides,
  pinch-to-zoom and two-finger pan over a camera that never moves on its own,
  a dice picker row that taps dice into the formula field, and
  a squiggle under the part of a bad formula that is wrong, with a one-tap fix
  where the mistake has an obvious reading. What it has not: the set dropdown,
  the power-saving path, the first-launch state, and numbers on the faces. All
  listed in `docs/TODO.md`.
- **The screens' state machines are written down.** `docs/architecture.md` now
  carries the navigation graph, `RollState`, the shake, the tray and the
  Settings screen as diagrams and control tables, so a transition nobody
  thought about is visible rather than latent.

## Blocked / waiting on

- **Judgements that need a person and a phone**, all in `docs/TODO.md`: whether
  the dice now have weight; whether 16 mm dice read too small on a screen;
  whether the empty table looks like a table worth rolling on; whether turning
  the phone is now seamless; and whether four times in is the right limit on
  the pinch. None of them blocks anything else, and none can be answered here —
  the container's emulator has no real GPU and `screencap` returns black.

## Decisions pending

- Two smaller decisions from the prototype are not yet in `docs/` (designer
  3D preview, picker remembering the last set per group) — see `docs/TODO.md`.

## Known risks

- The **"no invisible hand"** bar — zero post-rest corrections, zero stacked
  dice — is the hardest thing in the plan and can only be judged on a device.
  If prevention cannot get there, the fallback is a visible re-throw, which is
  honest but must not become common.
- **The correction ladder leans on corrections far too hard.** Nine of twenty
  dice get a nudge, against a budget of one in two hundred. Every one lands
  while the die is still moving and post-rest corrections are zero, so the
  honest rule holds — but Step 5.5 is where prevention has to get good enough
  that the ladder is rarely reached.
- **The capacity constants now barely bite.** Since a die is sized by its
  width rather than by its edge, it would take about 240 dice to reach the
  40 % floor and the engine stops at 100 — so the refusal a player meets is
  the body cap, not the table. Whether 30 % and 40 % are still the right
  numbers is a Step 5.3 question, with a device.
- Determinism holds across the two ABIs. What is unproven is determinism
  across *devices* of the same ABI and across time, which is the same suite
  run somewhere else.
- The container's emulator is an automated-test image with no real GPU and no
  display, so `screencap` returns black. It answers "does this run", never
  "does this look right" — the phone is the only answer to the second.
