# TODO

Open tasks, roughly in the order they should happen. Done items are removed,
not ticked — history lives in git and `docs/STATUS.md`. Keep this file short: if a
section grows past a screen, split it into milestones or prune.

## Milestone 0 — repository bootstrap

- [ ] Devcontainer: JDK, Android SDK/NDK (API 37), Gradle, ktlint, detekt, Android Lint
- [ ] Gradle multi-module skeleton matching `docs/architecture.md`
- [ ] CI: build, JVM unit tests, Robolectric tests, linters; tag `vX.Y.Z` → release build + docs build
- [ ] APK naming (`dInfinityApp-<version>[-debug].apk`) wired into the build
- [ ] Decide physics engine for real: Jolt JNI binding spike vs. Bullet; record result in `docs/architecture.md`
- [ ] Decide TOML parser (`tomlkt` vs. hand-written strict reader)

## Milestone 1 — a d6 rolls

- [ ] `core/model`: Die, DiceSet, Face, RollPlan, RollResult
- [ ] `simulation/api`: DiceSimulator interface, table geometry, capacity rule, settle + face read
- [ ] Physics bridge with fixed 120 Hz timestep, seeded, deterministic
- [ ] Golden determinism test suite (seed + inputs → outcome)
- [ ] Filament scene: table box, one cube, lights, camera
- [ ] Roll screen: tap to roll a single d6, show result
- [ ] Power-saving (headless) path producing the same result for the same seed

## Milestone 2 — notation, sets, graph

- [ ] `core/notation`: parser + evaluator per `docs/dice-notation.md`, incl. limits and error ranges
- [ ] Built-in dice set as a real `diceset.toml` package with all catalogue shapes
- [ ] Dice set validator + sandboxed loader
- [ ] `core/probability`: exact PMF incl. keep/drop/explode; outcome graph screen
- [ ] Dice picker on the roll screen
- [ ] Stacked/cocked detection and nudge loop

## Milestone 3 — the rest of v1

- [ ] Shake input (tray driven by acceleration), haptics, impact sounds
- [ ] Saved rolls with groups, JSON collection import/export
- [ ] Install from URL: git forges, https archives, local files; update check
- [ ] Table looks (`[[table]]`), built-in tables, "use a photo"
- [ ] Face designer
- [ ] Statistics screens and Room schema
- [ ] Fairness preview for custom-mesh dice
- [ ] Battery-based auto power-saving

## Reconcile docs with the design prototype

`design/github.md` lists decisions taken in the UI design that `docs/` does
not yet reflect. Each is a small docs PR once confirmed:

- [ ] Seeds are never exposed; no "tap to replay" from history (`docs/statistics.md`)
- [ ] No d30 and no custom-mesh dice in v1; catalogue is d2, d4, d6, d8, d10, d12, d18, d20, d100 (`docs/dice-sets.md`)
- [ ] Saved-roll collection import refuses on a duplicate group name instead of merging (`docs/dice-notation.md`)
- [ ] Division rounding overridable per throw from the result sheet; Nearest rounds .5 up (`docs/dice-notation.md`)
- [ ] Tables from any package are global; sets never override the table (`docs/tables.md`)
- [ ] No auto power-saving on low battery; no session share sheet (`docs/physics-and-rendering.md`, `docs/statistics.md`)
- [ ] Add an `examples/` dice set to the repo; built-in set has no export (`docs/dice-sets.md`)

## Open questions

- Should `minSdk` stay at Android 17, or drop lower once v1 is out?
- d18 shape: enneagonal trapezohedron is assumed; verify it reads well at phone size
- `.thumbnail` from the design project is not imported (binary); decide whether a
  preview image belongs in the repo at all
- Division rounding default (floor) — confirm against the games players actually use
