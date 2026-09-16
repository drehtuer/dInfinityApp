# Status

Current state of the project in a few lines. Update it when a milestone moves, a
decision is taken or something is blocked; prune anything no longer current.
This is a snapshot, not a changelog — git history is the changelog.

**Last updated:** 2026-09-16

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

`main` has everything up to **#191** and is green. In flight, stacked in this
order, each based on the one before it:

| PR | What |
| --- | --- |
| #193 | The breakdown says **why an exploding chain stopped** — depth limit or a full tray |
| #194 | **A second shake reaches the dice it is shaken at**; its moments were numbered on a clock the running roll had passed |
| #195 | The harness **rolls for minutes** (`--soak`), paces frames, and scores "not measured" rather than passing on a figure nobody took |
| #196 | Proof on the phone that a **shake-driven roll replays to itself** — the last device box in Step 3 |
| #197 | **A die's artwork reaches the tray**, composited over its printed labels rather than instead of them |
| #198 | The designer **stamps the tray's own numbers, from the tray's own solve** — one placement, so a drawn die and a printed one agree |
| #199 | **A photograph becomes a table**, the long way round: downsized, written into the personal package, validated like any other |

## Done

- **The specification.** `README.md`, `docs/` and the clickable prototype in
  `design/`, cross-referenced both ways and published at
  <https://drehtuer.github.io/dInfinityApp/>. GPL-2.0-or-later.
- **The skeleton and CI.** Devcontainer, convention plugins, the Modernist
  theme, the navigation graph. Every linter and both test tiers run on each
  pull request; SonarQube blocks on its gate and JaCoCo on a function *and*
  branch floor. Dependencies pinned by SHA-256; a `vX.Y.Z` tag cuts a signed,
  immutable release.
- **Step 3's foundations, complete.** A formula parsed and resolved against the
  installed sets, graphed exactly, planned against the capacity rule, settled,
  read face by face, drawn with its author's artwork over its printed labels,
  thrown by a shake that replays to itself, and written down in one
  transaction. A package from a stranger is validated rule by rule and
  installed without leaving anything behind if it fails.
- **The physics.** Jolt 5.3.0. Every *decision* about a roll is Kotlin over an
  interface, so the rule that matters most — nothing touches a die that has
  come to rest — is proved by JVM tests rather than sampled on a phone. Every
  random number comes from one place.
- **Determinism is asserted, not assumed.** Ten (seed, formula, input) cases;
  the emulator and the Pixel 10a agree bit for bit, spawn digest included. A
  shake-driven roll now also replays to itself on hardware.
- **Step 5.1, the harness.** `tools/harness.sh` rolls N throws — or rolls for a
  duration — headlessly on either tier, pulls back a JSON document and prints a
  pass/fail table. Everything it decides is plain Kotlin, tested on the JVM.

## In progress

**Step 4: every screen is written and connected.** What is left on each is in
`docs/TODO.md`, and most of it is judgement with a phone in hand rather than
code. 4.10 Settings is finished; 4.6's designer gained the glyph stamp and
"fill all with numbers"; 4.5's table picker gained "use a photo".

**Step 5 is the real remaining work** — see Known risks.

## Blocked / waiting on

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

## Known risks

- **The correction ladder leans on corrections far too hard**, and at sixty dice
  the honesty bar breaks for the first time. Ten thousand rolls on the Pixel
  10a, at each of twenty and sixty dice:

  | | 20 dice (200,000) | 60 dice (600,000) |
  | --- | --- | --- |
  | dice at rest on another die | **0** | **29** |
  | post-rest corrections | **0** | **0** |
  | dice corrected (budget 0.5 %) | 44.9 % | 47.1 % |
  | dice re-thrown (budget 0.05 %) | 2.9 % | 6.1 % |
  | rolls out of the 12 s cap | 3 | 29 |
  | deepest die–die overlap (bar 0.2 mm) | 11.6 mm | 10.1 mm |

  Of the twenty-nine stacked dice, **twenty are in rolls the cap froze** — rung
  3 never got to throw them again — and **nine finished cleanly**, which is
  prevention failing outright. **Not one post-rest correction in 800,000 dice**,
  which is the rule that is inviolable rather than a target.

  Note the overlap: 11.6 mm at twenty dice against the 9.0 mm two hundred rolls
  first found. Nothing got worse — an extreme value climbs with the sample, so
  the old figure was optimism about how long anybody had looked.
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
- Determinism holds across the two ABIs. Unproven across *devices* of the same
  ABI and across time.
- The container's emulator has no real GPU and no display, so `screencap`
  returns black. It answers "does this run", never "does this look right".
- **Branch coverage is ~70 % against a floor of 62**, and the drift has stopped:
  seven in ten missed branches are Compose skip branches a test can only take
  one side of. Function coverage ~92 % against a floor of 85.
