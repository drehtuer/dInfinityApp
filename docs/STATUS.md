# Status

Current state of the project in a few lines. Update it when a milestone moves, a
decision is taken or something is blocked; prune anything no longer current.
This is a snapshot, not a changelog — git history is the changelog.

**Last updated:** 2026-09-19

## Where we are

- **Phase:** implementation. Steps 1–3 are done, every Step 4 screen is written
  and connected, and **Step 5 — physics and rendering on a real phone — is
  where the remaining hard problems are**.
- **The app rolls dice on a phone.** Shake the Pixel 10a and the dice tumble
  onto a felt tray, come to rest, are felt and heard as they land, and their
  total appears beside them. They carry real printed numbers, the right way
  round, and a die whose author drew artwork wears it.
- **Latest release:** `v0.1.1`, signed and published with its SHA-256 — the
  first release whose every screen had been looked at on a phone, and the
  remedy for `v0.1.0`, which was cut before anything in it had reached one.

### Branch state

`main` has everything through **#306**, which cut `v0.1.1`.

**In flight: a stack of eleven answering the second and third device
sessions**, plus one standalone fix for the documentation site. In order:

| | |
| --- | --- |
| `fix/tray-tumble` | the dice tumble rather than landing and sticking |
| `feature/roll-screen-layout` | dice pull-down at the top, formula menu on the right, no shadow on the felt |
| `fix/tray-gestures` | two fingers to pan, and the pan reaches the walls |
| `fix/fudge-label-and-editor-prefill` | the screen stays awake on purpose, a new roll starts on the last one |
| `feature/saved-roll-colour-picker` | one colour picker for the whole app |
| `fix/d4-corner-numbers` | a d4's corners read off the solid, so every edge agrees |
| `feature/shake-is-the-roll` | the shake is the only way to throw |
| `feature/designer-icons-and-save` | the designer's tools are pictures, and the die you drew is the die that rolls |
| `feature/signed-fudge-totals` | a Fudge total carries its sign |
| `fix/rerolls-wait-and-clear` | a re-roll lands on floor nothing is standing on |
| `fix/designer-test-roll` | a test throw has a way back, and the face you drew lands where the die shows it |
| `fix/docs-site-build` | **off `main`, not in the stack** — the published site has been stale since 18 September |

**The whole device tier has been run on the Pixel 10a over the merged stack.**

## Done

- **The specification.** `README.md`, `docs/` and the clickable prototype in
  `design/`, cross-referenced both ways. GPL-2.0-or-later.
- **The skeleton and CI.** Devcontainer, convention plugins, the Modernist
  theme, the navigation graph. Every linter and both test tiers run on each
  pull request; SonarQube blocks on its gate and JaCoCo on a function *and*
  branch floor. Dependencies pinned by SHA-256; a `vX.Y.Z` tag cuts a signed,
  immutable release.
- **Steps 1–3.** A formula parsed against the installed sets, graphed exactly,
  planned against the capacity rule, settled on Jolt 5.3.0, read face by face,
  drawn with its author's artwork, thrown by a shake, and written down in one
  transaction. A package from a stranger is validated rule by rule.
- **Every *decision* about a roll is Kotlin over an interface**, so the rule
  that matters most — nothing touches a die that has come to rest — is proved
  by JVM tests rather than sampled on a phone.
- **Step 5.1, the harness**, and **5.2, fairness and determinism** on the
  Pixel 10a.

## In progress

**Step 4's screens are all built.** What is left on each is judgement, and it
is listed in `docs/TODO.md`.

**The third device session's three notes on the face designer are answered**
on `fix/designer-test-roll`: a test throw now has a way back to the designer,
a drawn face is painted where the die shows it rather than turned by up to
166°, and the fairness worry turned out to have no bug behind it — a drawn
die's `faces` array is the base die's, whole and in order, and seven tests
say so. What is left there is judgement on a phone.

**The second device session is answered in full by the stack above.** The last
open question in it — what a Fudge roll prints — is settled: a total of Fudge
dice carries its sign (`+2`, `−1`, `0`), which is how Fate writes one and the
only spelling that says the same thing about one die as about four
(`docs/dice-notation.md`, "dF, and the sign a Fudge total carries").

## Blocked / waiting on

**Nothing is blocked.**

**Three things need a person with the phone**, and they are judgement rather
than execution — every one of them is in `docs/TODO.md`:

1. Whether the roll screen still reads as a thing to shake now that no button
   says so, and whether the dice pull-down reads as "the dice are in there".
2. Whether the dice now *look* like they tumble. The figure says they turn
   1.52 times after landing against 0.89 before, but a number is not an eye.
3. Whether an exploding chain ever still looks as though a die passed through
   one lying there. Two ways it could have are closed on
   `fix/rerolls-wait-and-clear`; what is unexplained is why the session saw it
   **only on the first throws**, and the one candidate — the frame clock
   dropping steps while the engine and the material are still being built — is
   a number nothing shows yet (`LiveRoll.droppedSteps`).

**One thing needs the repository owner**, not a branch: **AGP 9.4.1 was
published on 18 September**, and Android Lint treats a newer AGP as an error
under `warningsAsErrors`. Every pull request will fail `lint` until it is
bumped, which also needs `gradle/verification-metadata.xml` regenerated
because dependencies are pinned by SHA-256. Dependabot covers Gradle weekly.

## Known risks

- **Nothing corrects a die any more.** A roll counts the dice that can be
  read, takes them off the table and throws the rest again until nothing is
  left. Measured over 2,000 rolls of 20d20 on the Pixel 10a: **0.000 %** of
  dice corrected, no die at rest on another, no post-rest correction, no
  forced settle, nothing out of the twelve-second cap.
- **Two harness bars still fail, and both fail by less than they did.** The
  re-throw share is 2.20 % against 0.05 % — a bar written for a mechanism that
  no longer exists and which needs re-deciding rather than hitting — and the
  die-into-die overlap is 5.04 mm against 0.2 mm. That was called the solver's
  own discrete-detection error and nothing else; it is now known that part of
  it was a spawn, because a pass that threw several dice again dropped each of
  them at a point drawn blind from the whole tray and two could start inside
  each other. Fixed on `fix/rerolls-wait-and-clear`; **the figure has not been
  re-measured on the phone**, and that run is what says how much of the 5.04 mm
  was this.
- **The d18 is a known limitation, decided and written down.** It cannot pass
  chi-squared at a hundred thousand rolls — its resting basins are narrow
  enough that the float32 hull's own rounding biases it. Held to the
  worst-face bound instead (worst measured 0.389 % against a 1 % bar).
- **`100d4` does not reliably settle**, and never did. Nothing is touched
  after coming to rest on any seed; the cap firing at all is prevention work.
- **The capacity constants barely bite.** It would take ~240 dice to reach the
  40 % floor and the engine stops at 100, so the refusal a player meets is the
  body cap rather than the table. Step 5.3.
- Determinism holds across the two ABIs; unproven across *devices* of the same
  ABI and across time. The container's emulator has no real GPU and no
  display, so `screencap` returns black there.
- **Branch coverage is ~71.5 % against a floor of 62**, function coverage
  ~92.7 % against a floor of 85. Seven in ten missed branches are Compose skip
  branches a test can only take one side of.

## Decisions pending

- Whether the too-many-dice refusal keeps a way through to the outcome graph.
  "See the odds" is offered only once a roll has landed now, and the argument
  for offering it on a refusal is still in `docs/architecture.md`.
- Whether the branch-coverage floor should follow the drift, or stay. Moving a
  floor to make a check pass is what `.claude/CLAUDE.md` says not to do.
- Whether the anomaly log should survive a restart (an entry carries a seed,
  and a stored seed is a replay waiting to happen — decision 13).
- Where a **table look's** texture says which package it came from.
- **The canvas draws one kite for the d10 and the d18**, whose faces are
  differently proportioned kites, so neither can be landed exactly. The
  exporter covers rather than fits, which colours the whole face at the cost
  of a drawing 1.2× (d10) or 1.4× (d18) large and clipped at the tip. The
  turn itself is fixed (`docs/TODO.md`, 4.6).
- **Several personal sets.** "Save to set" chooses between the writable sets,
  and there is exactly one today (`docs/TODO.md`, 4.6).
- **The design removed the sound switch**, and the app has a whole `feedback/`
  module that generates impact sounds per table material. That is a product
  call rather than a drawing.
