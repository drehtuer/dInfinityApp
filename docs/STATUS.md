# Status

Current state of the project in a few lines. Update it when a milestone moves, a
decision is taken or something is blocked; prune anything no longer current.
This is a snapshot, not a changelog — git history is the changelog.

**Last updated:** 2026-10-04

## Where we are

- **Phase:** implementation. Steps 1–3 are done, every Step 4 screen is built
  and connected, and **Step 5 — physics and rendering on a real phone — is
  where the remaining hard problems are**.
- **The app rolls dice on a phone.** Shake the Pixel 10a and the dice tumble
  onto a felt tray, come to rest, are felt and heard as they land, and their
  total appears beside them, with printed numbers the right way round and an
  author's artwork where a set has some.
- **Latest release:** `v0.1.1`, signed and published with its SHA-256.
- **`main` is at #336** — a shake is the only way to start a roll (decision
  66), the dice waiting to be thrown drop under real Jolt physics (decision
  67), and a die picked by finger will be thrown by a shake (decision 68,
  decided, not built).
- **In flight, one stack, in merge order** (every PR checked on the Pixel 10a):
  - #337 — added dice leave one spot over the middle of the tray a tenth of a
    second apart (decision 69);
  - #338 — a die that lands cocked or on another waits where it lies for the
    player's shake (decision 70);
  - #339 — more spin (60–120 rad/s) and bouncier dice (restitution 0.55);
  - #340 — the re-throw code nothing calls is gone, and `FairnessTest` counts
    a give-up instead of stopping;
  - #341 — the debug overlay shows the frame rate and dropped steps (72);
  - #342 — the statistics name a face by its label (73);
  - #343 — no shake is heard while the welcome is up (74);
  - #344 — braced notation for a set's own dice, offered on the picker (75);
  - #345 — a device test reads the printed numbers off a frame, and table
    thumbnails are the right way up again;
  - #346 — four and eight collision steps measured, two kept (77);
  - #347 — the saved-rolls list scrolls while a row is dragged at its edge
    (78);
  - #348 — more than one personal set in the face designer (79);
  - then the hand re-throw: a finger picks a landed die, the next shake throws
    it (decisions 68 and 76).

## Done

- **The specification** — `README.md`, `docs/` and the clickable prototype in
  `design/`, cross-referenced both ways.
- **The skeleton and CI** — devcontainer, convention plugins, every linter and
  both test tiers on each pull request, SonarQube's gate, a function *and*
  branch coverage floor, SHA-256-pinned dependencies, signed immutable
  releases from a `vX.Y.Z` tag.
- **Steps 1–3, 5.1 and the fairness half of 5.2** — a formula parsed, graphed
  exactly, planned against the capacity rule, settled on Jolt 5.3.0, read face
  by face, drawn, thrown by a shake and written down; a stranger's package
  validated rule by rule; the harness; fairness on the Pixel 10a.
- **Every decision about a roll is Kotlin over an interface**, so "nothing
  touches a die that has come to rest" is proved by JVM tests, not sampled.

## Last device run — Pixel 10a, 2026-10-04, the overnight stack tip (#349)

- **Device suite:** 102 tests, **101 passed, 0 failed**, 1 skipped
  (`HarnessTest`, which declines without a roll count).
- **Harness, 10,000 rolls of 20d20:** no die read while standing on another,
  none gave up, settle 0.95 s median and 1.96 s p99, **2.70 turns after
  landing**. Fails, as before: re-throws 1.21 % against 0.05 %, overlap
  7.76 mm against 0.2 mm (four and eight collision steps measured and not
  adopted, decision 77).
- **10,000 rolls of 60d20:** two gave up; the 1–100 d6 sweep under counting:
  none gave up at any count (`docs/TODO.md`, 5.3 and 5.5).

## Blocked / waiting on

**Nothing is blocked.** What needs a person with the phone is judgement, all
of it in `docs/TODO.md` (4.1 and 5.6). The most useful three:

1. Whether the livelier tumble now reads as dice rolling, whether
   `RollPace.WATCHED` 0.5 is slow enough or 0.4 is wanted, and whether `1d20`
   a dozen times still feels prompt.
2. Whether the five tables sound like their materials, and whether the
   haptics read as knocks.
3. Whether an exploding chain still looks wrong on the first throws after a
   cold start — `LiveRoll.droppedSteps` is the number, and nothing shows it yet.

## Known risks

- **Die-into-die overlap is the solver's.** #321 fixed a re-throw spawn that
  could start two dice inside each other, and the re-measure shows it was not
  the cause: 5.04 mm before, 5.29 mm after; bouncier dice take it to 7.76 mm. Next is four or eight collision
  sub-steps against the current throw (Step 5.4).
- **The re-throw bar measures a mechanism that no longer exists.** 2.65 %
  against 0.05 %; since decision 70 it is the share of dice a player is asked
  to shake for again, so the bar needs re-deciding rather than hitting
  (Step 5.5).
- **`100d4` does not reliably settle**, nor does one throw in sixteen under a
  hard sideways shake: both can run out the twelve-second cap, where the roll
  gives up rather than invent an answer (#319 stopped it taking the app with
  it).
- **A board die landing on another may show the solver's overlap.** The same
  5–8 mm die-into-die penetration the harness measures for a throw can show
  as interpenetration when a dropped board die lands on a standing one; the
  board has no correction, by design, so it would stay until the shake.
- **The d18 cannot pass chi-squared** — decided and written down; it is held
  to the worst-face bound (0.389 % against 1 %).
- **The capacity rule barely bites**: the 100-body cap refuses long before the
  table's floor would (~241 dice).
- Determinism holds across both ABIs; unproven across devices of one ABI.
- **Coverage:** branch ~71.4 % against a floor of 62, function ~92.7 % against
  85. Most missed branches are Compose skip branches.

## Decisions pending

All in `docs/TODO.md`; the ones that block code first.

- **Does the impact sound go?** The design has no switch for it.
- **What the re-throw bar should bound** — duration and passes, probably.
- **The built-in set's `size_mm`**, which changes every die's mass and how
  many fit.
- Whether the too-many-dice refusal offers a way to the outcome graph (the
  argument is in `docs/architecture.md`).
- Filled-button label contrast (3.65:1 against 4.5:1), the coverage floor,
  the anomaly log surviving a restart, where a table texture names its
  package, one kite outline for two kites, more than one personal set.
