# Status

Current state of the project in a few lines. Update it when a milestone moves, a
decision is taken or something is blocked; prune anything no longer current.
This is a snapshot, not a changelog — git history is the changelog.

**Last updated:** 2026-10-24

## Where we are

- **Phase:** implementation. Steps 1–3 are done, every Step 4 screen is built
  and connected, and **Step 5 — physics and rendering on a real phone — is
  where the remaining hard problems are**.
- **The app rolls dice on a phone.** Shake the Pixel 10a and the dice tumble
  onto a felt tray, come to rest, are felt and heard as they land, and their
  total appears beside them, with printed numbers the right way round and an
  author's artwork where a set has some.
- **Latest release:** `v0.3.0`, signed and published with its SHA-256.
- **`v0.3.0` is `main` at #416**: the realistic-rendering work
  (merged as #410, 2026-10-21): lighting from a photographed room (decision
  89), refracting resin (90), rounded edges drawn at the solver's radius (91),
  felt and oak from CC0 pictures with their sheen taken out of their colour
  (92), no reflection of the dice in dark glass (104), a rougher lacquer so
  faces do not blink (103), and satin, unlacquered metal dice (106, which also
  ended a mirror coat on every uncoated surface). The face designer is three
  steps with six materials, an opacity slider and edge rounding up to 6 %
  (94, 97–102); the "Preparing the dice" plate covers shader compiles (95);
  four sample sets are in `sample-sets/`, and come installed on the first
  launch, resin, glass and metal on every shape (decision 107). On the Pixel 10a
  every library device suite passes, and the owner finds the throws look right.
- **Light past white is bent, not cut** (decision 108): a tone pass of our
  own, the identity below 0.8, about 1 ms of GPU a frame; metal highlights
  are bright cream instead of flat white.
- **The d4 is a long die** (decisions 109 and 110): a square prism with two
  short points, read from the face on top, a quarter longer than its set's
  `size_mm`. A set file naming the tetrahedron is refused — a breaking change
  to the set format, so the next release is **v1.0.0**. `100d4` no longer
  gives up, though long d4s are re-thrown more often than tetrahedra were
  (`docs/TODO.md` 5.3). In flight: the bigger d4 (`feature/bigger-d4`).
- **Every release is shown fair**: `tools/fairness.sh` throws every shape
  100,000 times on the phone into `fairness/record.txt`, tied to the physics
  by a fingerprint, and the release workflow refuses a tag without a passing
  record of its own. The long d4's axis split is a question for a second run
  (`docs/TODO.md` 5.2).
- **Next:** glass and resin as the two materials they are, with lighter
  shadows (`docs/TODO.md` 5.9), and the lag the owner still sees on some
  throws (5.6).
- **The hand tosses the dice** (decision 105, `docs/TODO.md` 5.8): shaken
  in sight as before, and tossed when the hand stops, after a comparison with
  slow-motion film of real dice. Adopted by the owner on 2026-10-20 — without
  it the dice settle almost as the shaking stops. The constants wait for the
  owner's recordings of real throws; a shake along the phone's length still
  hops now and then.

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

## Last device runs — Pixel 10a

- **2026-10-21, `feature/realistic-rendering` after #406–#409:** every
  library module's device suite — `designer`, `dicesets:install`,
  `feature:roll`, `simulation:jolt`, `render:filament` — 128 passed, 0
  failed, 3 skipped (the gallery and the two harnesses, which run only when
  asked). `:app`'s suite was not run, because it writes into the owner's real
  history. Floors straight down, before decision 106: green felt
  (30, 92, 57), black felt (30, 29, 29), oak (92, 62, 40).
- **2026-10-04, the overnight stack tip (#349):** 102 tests, 101 passed, 0
  failed, 1 skipped (`HarnessTest`, which declines without a roll count).
  The harness and rendered-harness figures below are from that run.
- **Harness, 10,000 rolls of 20d20:** no die read while standing on another,
  none gave up, settle 0.95 s median and 1.96 s p99, **2.70 turns after
  landing**. Fails, as before: re-throws 1.21 % against 0.05 %, overlap
  7.76 mm against 0.2 mm (four and eight collision steps measured and not
  adopted, decision 77).
- **Rendered harness:** 60.3 fps at 20d20 (p99 work 8.3 ms, GPU 12.6 ms, no
  step dropped) and 57.3 fps at the 100-dice limit — both frame targets met.
- **Rounded corners fixed** (decision 81): over 50,000 rolls of 60d20 the
  give-ups fell from 9 to 2 and re-throws from 2.6 % to 2.2 %. What is left is
  a die pinched between two others spinning on the line through them, which
  friction cannot reach — a question for the owner (`docs/TODO.md`, 5.5).
- **10,000 rolls of 60d20 before that fix:** two gave up; the 1–100 d6 sweep under counting:
  none gave up at any count (`docs/TODO.md`, 5.3 and 5.5).

## Blocked / waiting on

**Nothing is blocked.** What needs a person with the phone is judgement, all
of it in `docs/TODO.md` (4.1 and 5.6). The most useful three:

1. Whether the dice now travel at the right speed: the tumble was approved,
   the travel was still a little fast, so `RollPace.WATCHED` went from 0.5 to
   0.4 (#357) — and whether `1d20` a dozen times still feels
   prompt.
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
- **One throw in sixteen under a hard sideways shake** can run out the
  twelve-second cap, where the roll gives up rather than invent an answer
  (#319 stopped it taking the app with it). `100d4` used to as well; the long
  d4 settles every seed.
- **A board die landing on another may show the solver's overlap.** The same
  5–8 mm die-into-die penetration the harness measures for a throw can show
  as interpenetration when a dropped board die lands on a standing one; the
  board has no correction, by design, so it would stay until the shake.
- **The d18 cannot pass chi-squared** — decided and written down; it is held
  to the worst-face bound (0.389 % against 1 %).
- **The capacity rule barely bites**: the 100-body cap refuses long before the
  table's floor would (~241 dice).
- **The emulator can no longer read a frame back.** Every
  `render/filament` test that reads pixels aborts there with Filament's
  `blitLow` program failing to compile (seen 2026-10-10); the phone runs them.
  `docs/TODO.md`, Step 2.
- Determinism holds across both ABIs; unproven across devices of one ABI.
- **Coverage:** branch ~75.4 % against a floor of 62, function ~92.8 % against
  85, after a pass that pruned about forty redundant tests and added two
  hundred. Most missed branches are Compose skip branches.

## Decisions pending

All in `docs/TODO.md`; the ones that block code first.

- **Does the impact sound go?** The design has no switch for it.
- **What the re-throw bar should bound** — duration and passes, probably.
- **The built-in set's `size_mm`**, which changes every die's mass and how
  many fit.
- Whether the too-many-dice refusal offers a way to the outcome graph (the
  argument is in `docs/architecture.md`).
- Filled-button label contrast (3.76:1 against 4.5:1), the coverage floor,
  the anomaly log surviving a restart, more than one personal set.
