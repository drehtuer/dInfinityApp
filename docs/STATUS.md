# Status

Current state of the project in a few lines. Update it when a milestone moves, a
decision is taken or something is blocked; prune anything no longer current.
This is a snapshot, not a changelog — git history is the changelog.

**Last updated:** 2026-09-17

## Where we are

- **Phase:** implementation. Steps 1, 2 and **3 are done**; Step 4's screens are
  all written, connected and working, and what is left on them is polish and
  judgement. **Step 5 — physics and rendering on a real phone — is where the
  remaining hard problems are**, and its harness (5.1) is now finished too.
- **The app rolls dice on a phone.** Type a formula, tap Roll or shake the
  Pixel 10a, and the dice tumble onto a felt tray, come to rest, are felt and
  heard as they land, and their total appears. They carry real printed numbers,
  and a die whose author drew artwork now wears it.
- **Latest release:** `v0.0.1` — the skeleton, cut to prove the release
  pipeline. Signed, fingerprint-checked, published with its SHA-256.

### Branch state

`main` has everything through **#260**: all of Step 3, every screen of Step 4,
the harness, the ten-thousand-roll measurements below, count-and-clear rolling,
shake-to-throw, the `3 to 21+` ceiling and the visible backstop.

**In flight:** six stacked pull requests, #261–#266, from a pass over the UI
against the prototype. In order: the hand-over to the designer; the drift put
back and a build check so it cannot return; the design tokens moved out of
`:app` into `ui/common`, where every screen can reach them, so six copies of
the same scale became one; the last Material pills replaced by the square
options and segmented controls the system actually draws; the accent kickers
that are the only thing grouping a screen into blocks; and every dialog turned
into the bottom sheet the prototype has always shown.

Nothing in the stack changes behaviour. The one visible pixel change is the
outcome graph's out-of-σ bars, which are a step lighter on a dark page because
they now name the ramp step instead of thinning the ink.

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
over it against the prototype. What is left on each is in `docs/TODO.md`, and
most of it is judgement with a phone in hand rather than code.

**Step 5 is the real remaining work** — see Known risks.

## Blocked / waiting on

**The phone is off the network**, so nothing in the UI stack above has been
seen on a real screen. It needs wireless debugging switched on again and its
new address; everything else about a device run is automatic from here.

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
- **Eight questions for the designer**, in `docs/design-handover.md`. The two
  that block work: the dark ramp does not redefine the two steps a neutral tag
  is made of, and the accent ramp exists for two accents where the app offers
  six — so the app still cannot draw the badge that says a dice set has an
  update.

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
