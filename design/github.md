repo: drehtuer/dInfinityApp
branch: main

## Last sync
date: 2026-09-11T21:03:47Z
### Updated in this project
- Read the full spec (README + docs/) — repo is design-phase, no UI code yet
- Built dInfinityPhone.dc.html: every v1 screen as a live prototype with working notation parser, capacity rule and exact PMF
- Built dInfinity.dc.html: options board (layout, sheet density, picker, graph, tile style variants)

## Design decisions to feed back into docs/
- Drop d3 (triangular-prism) from README, docs/dice-notation.md, docs/dice-sets.md
- Seeds are never exposed; no replay from history (docs/statistics.md "tap to replay" → remove)
- Plain dN resolves to the default set, falls back to built-in per die (as docs/dice-notation.md already says)
- Sets never override the table; any mix of sets rolls on the one selected table (a group may still pin one)
- Saved-roll collection import refuses on duplicate group name (no merge / conflict UI)
- Division rounding: default down; overridable per throw from the result sheet; Nearest = .5 rounds up
- No d3, no d30, no custom-mesh dice in v1 — catalogue is d2, d4, d6, d8, d10, d12, d18, d20, d100
- Tables from any package are global (listed in every table picker); groups and saved rolls may pin one
- Add an `examples/` dice set to the repo (built-in has no export)
- No auto power-saving on low battery; no session share sheet

## Screen map
| Screen | Repo files |
|---|---|
| Roll (tray, picker, result sheet) | README.md, docs/dice-notation.md, docs/tables.md, docs/physics-and-rendering.md |
| Outcome graph | docs/probability.md, docs/dice-notation.md |
| Saved rolls + editor | docs/dice-notation.md (Saved rolls) |
| Dice sets / install | docs/dice-sets.md |
| Table picker | docs/tables.md |
| Face designer | docs/face-designer.md |
| Statistics, History | docs/statistics.md |
| Settings, Menu | README.md, docs/architecture.md |
