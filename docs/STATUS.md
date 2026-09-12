# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-12

## Where we are

- **Phase:** design. UI prototype exists (`design/`); no application code yet.
- **Latest release:** none.
- **Branch state:** PRs #1–#4 merged. The implementation plan is in PR #5.
  No code yet — Step 1 of the plan is the next thing to do.

## Done

- Design documentation written: `README.md`, `docs/` (architecture,
  physics and rendering, dice notation, dice sets, tables, probability, face
  designer, statistics).
- Working agreements in `.claude/CLAUDE.md`.
- License chosen: GPL-2.0-or-later.
- UI prototype for every v1 screen, imported from Claude Design into
  `design/`, cross-referenced with `docs/` in both directions and linked
  from `README.md`.
- The design's decisions are in `docs/`: v1's shape catalogue closed at nine
  solids (no d3, no d30, no author meshes), `d%` an alias for `d100`, seeds
  never exposed and no replay, tables global, import refuses duplicate
  groups, per-throw rounding override, manual-only power-saving.
- Implementation plan in `docs/TODO.md`: skeleton, CI with SonarQube,
  foundations, ten screens, an on-device physics step, release.
- Anti-stacking policy rewritten: nothing touches a resting die; a cocked die
  is re-thrown rather than nudged.

## In progress

- Nothing. `docs/TODO.md` Step 1 (skeleton) is the next step.

## Blocked / waiting on

- Nothing.

## Decisions pending

- Two smaller decisions from the prototype are not yet in `docs/` (designer
  3D preview, picker remembering the last set per group) — see `docs/TODO.md`.
- Physics engine (Jolt vs. Bullet) — spike planned in Milestone 0.
- TOML parser choice.

## Known risks

- The "no invisible hand" bar (zero post-rest corrections, zero stacked dice)
  is the hardest thing in the plan and can only be judged on a device. If
  prevention cannot get there, the fallback is a visible re-throw — which is
  honest but must not become common.

- Physics determinism across ABIs/devices is assumed, not yet proven; the
  golden test suite in Milestone 1 is the check.
- Table capacity constants (30 % floor, 0.40 min scale) are guesses until
  tried on the Pixel 10a.
