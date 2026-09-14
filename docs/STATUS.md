# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-14

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
- **Branch state:** everything up to #108 is merged and `main` is green; no
  Dependabot PRs are open.

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

**Step 4 is where the work is: seven of the ten screens do something.**

- **4.1 Roll.** The tray from the moment the screen opens, a live-validated
  formula field with a squiggle under the part that is wrong and a one-tap fix,
  the dice picker row, the saved-roll strip, roll from the button or a shake, a
  refusal for a throw the table cannot hold, the total and its breakdown,
  Down / Nearest / Up, pinch and pan, power-saving, first launch. Missing: the
  set dropdown and numbers on the faces. Two things the phone found: a shake
  with the phone upside down pooled the dice at the wrong end, because the
  screen pinned the display to the rotation it opened at and the shake map
  reads that rotation — it now holds its *shape* either way up; and coming back
  from the menu showed a black tray, because the Filament engine was rebuilt
  per visit — the roll thread and the engine now outlive one.
- **4.2 Outcome graph.** The exact distribution as bars with its mean line and
  ±1σ band, `P(= k)` / `P(≥ k)`, a tap for the numbers, the roll that opened it
  marked. Reached from "See the odds" — the first navigation carrying an
  argument.
- **4.3 Saved rolls.** Database version 2, the list, the editor, and groups
  made, renamed, moved and deleted from one sheet. Collections travel as JSON
  through `core/collection`: exported through the share sheet, imported from a
  file — read before anything is written, every line wrong with a bad file
  listed, and a duplicate group name refused outright with nothing merged.
  Importing from a URL is not built yet — and is not waiting on the `INTERNET`
  permission, which okhttp already puts in the merged manifest.
- **4.4 Dice sets.** The list is on screen. `InstalledSets` reads the
  `dicesets/` folder and validates every package again on each reading, so a
  set that stopped being valid shows its report instead of vanishing; database
  version 4 adds the registry that says whether a set is switched on, and a set
  with no row is on. Long-press switches one off or removes it, and the bundled
  set is offered neither. Tapping one opens its details: who wrote it, under
  what licence, the link it came from with its commit, and the dice it defines
  — or, for a package that stopped validating, the report standing where the
  dice would. A set installs from a file: picked, copied bounded into the app's
  cache, extracted and validated before anything is written, and a refusal
  lists every error — and **its dice can then be rolled**: the catalogue a
  formula resolves against is rebuilt from what is on disk and switched on, so
  installing a set is the whole of what it sounds like, and one of them can be
  made the set a plain `d20` comes from. Still to come:
  installing from a URL, update checking, and "my dice".
- **4.7 Statistics, 4.8 History, 4.9 Sessions.** Every throw is written down —
  a history row, a face count per die and a running summary, in one transaction
  — which the tables had been waiting for since version 1. The history lists
  every roll with the breakdown it was made of, and can be cut to one session
  or one saved roll; the statistics show each die
  against what a fair one would do — by set, or every set's dice of a kind
  pooled together with the fair line weighted by how often each was thrown;
  every saved roll's own totals sit against the exact distribution it was
  rolling against, with the drift judged against the standard error rather than
  shown bare; database version 3 adds sessions, and the
  migration names the one the old rolls already belonged to. The sessions
  screen was finished but never plugged in — `MainActivity` passed no presenter
  for it, so the app drew a placeholder and every roll was filed under the
  first session whatever the player picked. Both are fixed.
- **4.10 Settings and the menu.** The navigation graph is connected: every
  screen carries the same button and the menu reaches every screen. Settings
  has appearance, the accent, shake, the default rounding, power saving, the
  version and a link to the source. Haptics and sound are deliberately absent —
  nothing plays anything yet, and a row that does nothing is a lie.
- **The screens' state machines are written down.** `docs/architecture.md`
  carries every one as a diagram and a control table, so a transition nobody
  thought about is visible rather than latent.
- **`ui/common`** holds the furniture more than one screen needs: the
  live-validated formula field and the die silhouettes. Three screens agreeing
  about a mistake is the whole reason it exists.

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
- **Branch coverage sits near 70 % against a floor of 62 and has drifted down
  as the screens landed.** Seven in ten of the missed branches are inside
  `@Composable` functions, where the compiler emits a skip branch a test can
  only take one side of. The answer has been to lift decisions out of draw
  lambdas and test those; whether the floor should follow the drift is a
  question in `docs/TODO.md` for a person.
