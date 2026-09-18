# Status

Current state of the project in a few lines. Update it when a milestone moves, a
decision is taken or something is blocked; prune anything no longer current.
This is a snapshot, not a changelog — git history is the changelog.

**Last updated:** 2026-09-18

## Where we are

- **Phase:** implementation. Steps 1, 2 and **3 are done**; Step 4's screens are
  all written, connected and working, and what is left on them is polish and
  judgement. **Step 5 — physics and rendering on a real phone — is where the
  remaining hard problems are**, and its harness (5.1) is now finished too.
- **The app rolls dice on a phone, and as of #271 and #272 it looks like it.**
  Type a formula, tap Roll or shake the Pixel 10a, and the dice tumble onto a
  felt tray, come to rest, are felt and heard as they land, and their total
  appears beside them. They carry real printed numbers, the right way round,
  and a die whose author drew artwork now wears it.
- **Latest release:** `v0.1.0` — the first one that rolls dice. Signed,
  fingerprint-checked, published with its SHA-256. `v0.0.1` before it was the
  skeleton, cut to prove the release pipeline and nothing else.

  **It has two faults a release after it should not.** Nothing in it had been
  seen on a screen when it was cut, and both of them are things only a screen
  could show: a roll that settled first time cleared its own dice off the felt
  ([#271](https://github.com/drehtuer/dInfinityApp/pull/271)), and every glyph
  on every die was drawn reflected
  ([#272](https://github.com/drehtuer/dInfinityApp/pull/272)). Both are fixed
  on `main`. Releases are immutable, so the remedy is `v0.1.1` rather than a
  re-tag.

### Branch state

`main` has everything through **#278**. `v0.1.0` is cut from #268 and is two
fixes behind. In flight: the accent, on `feature/accent` — six new presets, a
colour of the player's own behind the contrast clamp, and the filled accent tag
that clamp finally makes drawable.

## Done

- **The specification.** `README.md`, `docs/` and the clickable prototype in
  `design/`, cross-referenced both ways and published at
  <https://drehtuer.github.io/dInfinityApp/>. GPL-2.0-or-later.
- **The skeleton and CI.** Devcontainer, convention plugins, the Modernist
  theme, the navigation graph. Every linter and both test tiers run on each
  pull request; SonarQube blocks on its gate and JaCoCo on a function *and*
  branch floor. Dependencies pinned by SHA-256; a `vX.Y.Z` tag cuts a signed,
  immutable release.
- **Steps 1–3, complete.** A formula parsed against the installed sets,
  graphed exactly, planned against the capacity rule, settled on Jolt 5.3.0,
  read face by face, drawn with its author's artwork, thrown by a shake, and
  written down in one transaction. A package from a stranger is validated rule
  by rule and installed without leaving anything behind if it fails.
- **Every *decision* about a roll is Kotlin over an interface**, so the rule
  that matters most — nothing touches a die that has come to rest — is proved
  by JVM tests rather than sampled on a phone. Every random number comes from
  one place, and determinism is asserted across both tiers, spawn digest
  included.
- **Step 5.1, the harness.** `tools/harness.sh` rolls N throws — or for a
  duration — headlessly on either tier, pulls back a JSON document and prints
  a pass/fail table.

## In progress

**Step 4: every screen is written and connected**, and has just had a pass
over it against the prototype. What is left on each is in `docs/TODO.md`.

**It is no longer mostly judgement.** The design pass of 2026-09-17 answered
every question the app was waiting on and decided a good deal nobody had asked
about, so each screen's list has gained work that is code rather than an eye:
the designer's Solid tab, staggered spawns, and a trailing dot on `6` and `9`.

**The roll screen's controls are on plates.** `ui/common`'s `Plate` is the
opaque `--color-bg` ground with `--shadow-sm` that every control over the table
now stands on, so the formula's dashed rule hugs its words instead of crossing
them and **no accent is drawn on felt anywhere**. The running readout has
become the counting plate — kicker, count, `of 20 read`, the still-possible
range with its `+` in the accent's 700 step, and a progress rule — and the two
states that had no drawing, *another throw earned* and *could not settle*, are
on that same plate with their buttons wired to the roll the machine already
reached. The total is drawn once (`docs/physics-and-rendering.md`, "What is
drawn over the table").

**A set says what its dice weigh**, in grams a player can hold rather than in
the density a file carries, with translucency and size beside it and steppers
on the one package this phone wrote (`docs/dice-sets.md`, "Weight,
translucency and size").

**The accent is done**: six presets, a colour of the player's own, and
`AccentRamp.clamp` between the choice and the paint — which turned the
palette's accessibility claim from six measured colours into a property that
holds for every colour there is.

**Saved rolls are done**: pinning is gone, the list is dragged into the
player's own order and is its own scroll box, a roll wears one of twelve colour
tags or one typed as a hex code, every colour goes through that same clamp, and
database version 6 carries what a phone already has across — favourites first,
then by recent use, once.

**Navigation is done.** Back off the tray is two presses with the design's
toast between them, a header chevron climbs rather than retraces, and the safe
area is applied once by the graph instead of by each screen — which is what
Settings had been forgetting to do under the Pixel 10a's status bar
(`docs/architecture.md`, "Navigation" and "One safe area, applied once").

**The dice are lit by a room**, reflect one, wear a lacquer and sit on the
felt rather than over it, and a set can say how far into a die you can see.

**The Physical block is done**: a set's detail screen quotes what a dice shop
quotes — grams a die, translucency as a per cent, size as a percentage of an
average die — from the `density`, `translucency` and `size_mm` its file
writes. The volume that turns a density into grams is closed-form arithmetic
per catalogue solid (`core/model`'s `DieVolume`), so the figure is the mass of
the body the solver throws. "My dice" carries the steppers and keeps the three
numbers in a record of its own beside the drafts; every other set is read-only,
because its numbers came out of somebody else's `diceset.toml`. One thing to
decide: the built-in dice are 16 mm **across the corners** and so weigh 0.9 g
where a shop would say 4.2 — `docs/TODO.md`, 4.4.

**Table view is done**: the camera's 22° lean is a Settings row with two
positions, straight down by default, read when the roll screen opens like the
other six. The tilt is an argument to `TrayCamera` rather than a constant, so
both positions are framed by the same JVM-tested arithmetic.

**Step 5 is the real remaining work** — see Known risks.

## Blocked / waiting on

**Nothing is blocked.** The phone came back and the UI stack has now been on a
real screen: every screen photographed through the accessibility tree, and the
two faults above found and fixed there. What that cost is the entry above — a
release was cut while this section said the opposite, and it shipped both of
them.

**The whole device tier runs, and until #274 it could not.** `./gradlew
connectedDebugAndroidTest` across every module on the Pixel 10a: **82 tests, 1
skipped, 0 failed**, 11m 25s. It used to fail however green the tests were,
because `HarnessTest` declines to run without `harness.rolls` and the runner
files an assumption as a failure. So the command `.claude/CLAUDE.md` asks a
developer to run before a PR was one nobody could pass — which is the third
thing this round found by running it rather than reasoning about it.

**Judgements that need a person and a phone.** All listed in `docs/TODO.md`.
The ones added this round: whether a chain that stopped reads as a rule or a
bug; whether a second shake reads as the dice answering the hand; whether a
blank face on a drawn die shows its printed number rather than a washed-out
patch; whether six photo tables is the right cap. None of them blocks anything
else. `screencap` on the phone answers what a screen *contains*; whether a
thing feels right is still a person's call.

## Decisions pending

- Whether the branch-coverage floor should follow the drift, or stay. Moving a
  floor to make a check pass is what `.claude/CLAUDE.md` says not to do.
- Whether the anomaly log should survive a restart (an entry carries a seed, and
  a stored seed is a replay waiting to happen — decision 13).
- Where a **table look's** texture says which package it came from. A die's
  artwork is addressed by package and path now; a table's is a bare path, so a
  photo table still draws as its colours. Nothing ships one yet.
- Three smaller ones in `docs/TODO.md`, Open questions: who measures a *drawn*
  frame, whether a stamp should be draggable, and `FACE_SHARE` being applied
  twice.
- **The designer answered, on 2026-09-17**, and the blocking question is gone:
  accent text at body size is `--color-accent-700` and both ends of the ramp
  are mixed from the accent the player picked. The filled accent tag is drawn —
  it is what says "Update available" on a dice-set row. The roll screen is **one picture**, not bands. What
  the pass did *not* answer — percent typography, whether a refusal keeps its
  words, a draggable stamp, "Doodle this die", where a table look's texture says
  its package, how a photo crops — stays open, and the design added six of its
  own. All of them are in `docs/TODO.md`.
- **The design removed the sound switch**, and the app has a whole `feedback/`
  module that generates impact sounds per table material. That is a product
  call rather than a drawing, and it is the one thing in the pass not yet
  acted on (`docs/TODO.md`, Open questions).

## Known risks

- **Nothing corrects a die any more, and the bar that existed for is met by
  construction.** A roll counts the dice that can be read, takes them off the
  table and throws the rest again until nothing is left to throw. Nothing
  biases, nudges or places a die, so there is no code left that could. Measured
  over 2,000 rolls of 20d20 on the Pixel 10a: **0.000 %** of dice corrected
  against a 0.5 % budget, no die at rest on another, no post-rest correction,
  no forced settle, nothing out of the twelve-second cap, and a median / p99
  settle of **0.78 / 1.45 s**.

  Ten of the harness's twelve rows pass. Two do not. The **re-throw budget**
  (2.80 % against 0.05 %) is a bar written for a mechanism that no longer
  exists and needs re-deciding rather than hitting. The **die-into-die overlap**
  (9.03 mm against 0.2 mm) is the solver's own error and is unchanged in kind.
- **One shaken throw in sixteen still runs the cap out.** Under a hard sideways
  shake, seed 9 of sixteen never settles at all — **no re-throws**, so it never
  reaches the point where anything is counted. A settling problem rather than a
  counting one, the same family as `100d4`, bounded in the device suite at
  today's worst case.
- **The "no invisible hand" bar** can only be judged on a device. If prevention
  cannot reach it, the fallback is a visible re-throw, which is honest but must
  not become common.
- **The d18 is a known limitation, decided and written down.** It cannot pass
  chi-squared at a hundred thousand rolls — its resting basins are narrow enough
  that the float32 hull's own rounding biases it. Held to the worst-face bound
  instead (worst measured 0.389 % against a 1 % bar), which is what a player
  would recognise.
- **`100d4` does not reliably settle, and never did.** Five seeds in twenty-four
  run out of the twelve-second cap. Nothing is touched after coming to rest on
  any seed; the cap firing at all is prevention work.
- **The capacity constants barely bite.** It would take ~240 dice to reach the
  40 % floor and the engine stops at 100, so the refusal a player meets is the
  body cap rather than the table. Step 5.3.
- Determinism holds across the two ABIs; unproven across *devices* of the same
  ABI and across time. The container's emulator has no real GPU and no display,
  so `screencap` returns black there — it answers "does this run", never "does
  this look right".
- **Branch coverage is ~72 % against a floor of 62**, and the drift has stopped:
  seven in ten missed branches are Compose skip branches a test can only take
  one side of. Function coverage ~92 % against a floor of 85.
