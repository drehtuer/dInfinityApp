# Status

Current state of the project in a few lines. Update it when a milestone
moves, a decision is taken or something is blocked; prune anything that is no
longer current. This is a snapshot, not a changelog — git history is the
changelog.

**Last updated:** 2026-09-11

## Where we are

- **Phase:** design. No application code yet.
- **Latest release:** none.
- **Branch state:** `main` has an empty root commit; the design docs are on
  `docs/initial-design`, open as PR #1.

## Done

- Design documentation written: `README.md`, `docs/` (architecture,
  physics and rendering, dice notation, dice sets, tables, probability, face
  designer, statistics).
- Working agreements in `.claude/CLAUDE.md`.
- License chosen: GPL-2.0-or-later.

## In progress

- Nothing.

## Blocked / waiting on

- Nothing.

## Decisions pending

- Physics engine (Jolt vs. Bullet) — spike planned in Milestone 0.
- TOML parser choice.

## Known risks

- Physics determinism across ABIs/devices is assumed, not yet proven; the
  golden test suite in Milestone 1 is the check.
- Table capacity constants (30 % floor, 0.40 min scale) are guesses until
  tried on the Pixel 10a.
