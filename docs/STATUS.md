# Status

Current state of the project in a few lines. Update it when a milestone moves, a
decision is taken or something is blocked; prune anything no longer current.
This is a snapshot, not a changelog — git history is the changelog.

**Last updated:** 2026-09-18

## Where we are

- **Phase:** implementation. Steps 1, 2 and **3 are done**; Step 4's screens are
  all written, connected and working, and what is left on them is polish and
  judgement. **Step 5 — physics and rendering on a real phone — is where the
  remaining hard problems are**, and its harness (5.1) is finished too.
- **The app rolls dice on a phone, and it looks like it.** Type a formula, tap
  Roll or shake the Pixel 10a, and the dice tumble onto a felt tray, come to
  rest, are felt and heard as they land, and their total appears beside them.
  They carry real printed numbers, the right way round, and a die whose author
  drew artwork wears it.
- **Latest release:** `v0.1.1` — the first one whose every screen has been
  looked at on a phone. Signed, fingerprint-checked, published with its
  SHA-256.

  It is what `v0.1.0` should have been: that one was cut before anything in it
  had reached a screen, and it shipped two faults only a screen could show —
  a roll that cleared its own dice off the felt, and every glyph on every die
  drawn reflected. Releases are immutable, so this is the remedy rather than
  a re-tag.

### Branch state

`main` has everything through **#306**, which cut `v0.1.1`. In flight: a stack
answering the second device session — the physics that makes the dice tumble,
the roll screen's layout, and the table camera's two-finger pan.

**The last device run covered the stack through #290** — the whole tier on the
Pixel 10a, 87 tests, 1 skipped, 0 failed, 7m 47s. Everything merged since is
verified on the JVM and by Robolectric only. **That is the next thing a phone
should be pointed at.**

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
- **Step 4's screens and the design round.** Plates over the table and no
  accent on felt, the pull-up result sheet, the accent picker with
  `AccentRamp.clamp` between choice and paint, saved rolls in the player's own
  order with colour tags, Settings as single rows, Table view as a setting,
  thumbnails drawn from real trays, a set's Physical block, the face
  designer's Solid tab, dice lit by a room, and navigation with one safe area
  applied once. Git history has the detail.

## In progress

**The roll screen's table camera** (`fix/tray-gestures`), from device testing
of `v0.1.1`. Two fingers pan rather than one, so the single finger is free
again for picking a die up; the pan limit grows with the zoom until, at
`TrayView.CLOSEST`, the middle of the screen reaches the corner of the floor,
so a die lying against a wall can be brought to where somebody is looking; a
pinch happens about the fingers rather than the middle of the screen; and the
gesture reads where the camera is from the screen, so the first touch after a
throw no longer snaps it back to the last roll's corner. JVM and Robolectric
only so far — what the looser limit trades away is that the frame shows wall
beyond the floor's edge, and whether that reads as the table or as having
fallen off it is a phone's answer (`docs/TODO.md`).

**The design pass is built.** It answered every question the app was waiting on
and decided a good deal nobody had asked about, and the round that followed
built almost all of it. What it decided and this has *not* done is in
`docs/TODO.md`: the dice still arrive all at once rather than one at a time,
and the sound switch the design removed is still there, because taking a
feature out is a product call rather than a drawing.

**The roll screen's layout answers the second device session**, and the stack
of plates along the bottom is gone with it. (Every control over the table is
still one of `ui/common`'s plates, and no accent is drawn on felt anywhere.) The dice are a pull-down at the
top with the set chooser folded inside them; the formula is an expanding menu
on the right under the menu button; `See the odds` and `Save as roll` are at
the foot of the result sheet, so they go down with it; and the tray casts no
shadow on its own felt any more, while the dice still cast theirs. What is
left along the bottom is the saved rolls and the Roll button. **Nobody has
seen it on a phone yet** — what needs an eye is in `docs/TODO.md`, 5.6.

**The face designer has a Solid tab.** A Face / Solid pair on the screen, the
flat editor unchanged under the first of them and the real polyhedron under the
second — generated from `simulation/api`'s own solids rather than modelled, and
from the same grouping of corners onto faces the renderer's mesh is now built
from, so there is one account of a die's geometry instead of two. The picture is
Compose: turn, project, drop the faces pointing away, sort the rest
furthest-first and fill them, all of it plain Kotlin under a JVM test. It draws
each face's background, numerals and pips and says plainly that it does not draw
pen strokes. **Nobody has looked at it on a screen yet** — the phone is off the
network — and what needs an eye is written down in `docs/TODO.md`, 4.6.

**The result is a pull-up sheet.** It comes up from the bottom edge once the
dice have been read, is pushed down by its grip until the whole table is
visible and pulled back up the same way, and never goes away — parked, it still
carries the total. It used to be a full-width plate across the middle of the
tray, which with the straight-down table view meant a die could land under it
and stay there until the next throw. Where it rests, what a drag does to that
and what a flick settles to are plain Kotlin under JVM tests (`SheetSlide`);
Compose has the gesture and the drawing. **Nobody has seen it on a phone yet** —
what needs an eye is in `docs/TODO.md`, 4.1.

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

**Settings is rows, not stacks**: each setting is the design's single row now —
name and sentence on the left, the control on the right and centred against
them — which halves the length of the screen. Where the control has no room
beside the text it goes under it, which is the one thing the drawing does not
say (`docs/architecture.md`, "Settings").

**Step 5 is the real remaining work** — see Known risks.

**The table camera takes two fingers.** One finger no longer pans and consumes
nothing, so it is free for the pick-up that `TrayPick` already has the
arithmetic for; a pinch zooms about the fingers rather than the middle of the
screen; and the pan limit grows with the zoom until, at the closest the camera
may get, any point of the table — the corners included — can be brought to the
middle. What that gives up is that past the old limit the frame shows the wall
rising beyond the floor's edge, which is a picture of the table rather than the
void beside it. The table thumbnails keep their old framing, asked for
explicitly rather than inherited. A stale-view bug went with it: the first
touch after a throw used to snap the camera back to where it had been.
**Nobody has had a finger on it yet** (`docs/TODO.md`, 5.6).

## Blocked / waiting on

**Nothing is blocked.** The whole device tier runs — `./gradlew
connectedDebugAndroidTest` across every module on the Pixel 10a: **87 tests, 1
skipped, 0 failed** — and until #274 it could not, because `HarnessTest`
declines to run without `harness.rolls` and the runner files an assumption as a
failure.

**Judgements that need a person and a phone** are all listed in
`docs/TODO.md`. None of them blocks anything else. `screencap` answers what a
screen *contains*; whether a thing feels right is still a person's call.

## Decisions pending

- Whether the branch-coverage floor should follow the drift, or stay. Moving a
  floor to make a check pass is what `.claude/CLAUDE.md` says not to do.
- Whether the anomaly log should survive a restart (an entry carries a seed, and
  a stored seed is a replay waiting to happen — decision 13).
- Where a **table look's** texture says which package it came from. A die's
  artwork is addressed by package and path now; a table's is a bare path, so a
  photo table still draws as its colours. Nothing ships one yet.
- **Whether the sound switch stays.** The design removed it and the app has a
  whole `feedback/` module behind it. A product call, and the one thing in the
  pass not yet acted on.
- Everything else is in `docs/TODO.md`, "Open questions": percent typography,
  whether a refusal keeps its words, a draggable stamp, "Doodle this die", how
  a photo crops, who measures a *drawn* frame, and `FACE_SHARE` applied twice.
- **Three things the design round found and did not fix**, each written down
  with its numbers rather than worked around: a six-level cubemap this driver
  refuses at level one against arithmetic that checks out on both sides; the
  atlas exporter turning a cell differently from the canvas, by up to 60° on a
  d20, which cannot be corrected without repainting every published die; and
  opposite-face numbering meaning a seed recorded before it reads back a
  different number after.

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
