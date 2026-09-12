# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-12

## Where we are

- **Phase:** design. UI prototype exists (`design/`); no application code yet.
- **Latest release:** none.
- **Branch state:** PRs #1 and #2 merged. Design import and the d3 / d%
  doc changes are in PR #3.

## Done

- Design documentation written: `README.md`, `docs/` (architecture,
  physics and rendering, dice notation, dice sets, tables, probability, face
  designer, statistics).
- Working agreements in `.claude/CLAUDE.md`.
- License chosen: GPL-2.0-or-later.
- UI prototype for every v1 screen, imported from Claude Design into
  `design/`, cross-referenced with `docs/` in both directions and linked
  from `README.md`.
- d3 dropped from the dice catalogue; `d%` documented as the alias for
  `d100`.

## In progress

- Nothing.

## Blocked / waiting on

- Nothing.

## Decisions pending

- Design decisions recorded in `design/github.md` that `docs/` does not yet
  reflect (no replay / seeds not exposed, no d30 or custom-mesh dice in v1,
  import refuses duplicate group names, per-throw rounding override, tables
  global across packages, no low-battery auto power-saving). Tracked in
  `docs/TODO.md`.

- Physics engine (Jolt vs. Bullet) — spike planned in Milestone 0.
- TOML parser choice.

## Known risks

- Physics determinism across ABIs/devices is assumed, not yet proven; the
  golden test suite in Milestone 1 is the check.
- Table capacity constants (30 % floor, 0.40 min scale) are guesses until
  tried on the Pixel 10a.
