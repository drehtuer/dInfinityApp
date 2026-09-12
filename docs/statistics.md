# Statistics

> **Design:** statistics (option 1w), history (1x), per-set filtering
> (5b–5c), sessions (6c) and saved-roll statistics (8b) are in the
> [clickable design](../design/dInfinity.dc.html) ([design/](../design/)).

Players care about how their dice behave even though every face is equally
likely. dInfinity records enough to answer "how many natural 20s have I
rolled this campaign?" without turning into a spreadsheet.

## What is recorded

Every completed roll writes one `RollHistory` row and updates aggregate
counters. Rolls that are cancelled (app closed mid-tumble) are discarded.

### Per die (keyed by `setId` + `dieId`)

- Total number of times this die was thrown
- Count per face value (a histogram)
- **Lowest-face count** and **highest-face count** — the "natural 1" and
  "natural 20" numbers, surfaced prominently
- Mean result, running variance
- Current and longest streaks of highest-face and lowest-face results
- Last rolled timestamp

Dice with duplicated face values (a d6 labelled 1,2,3,1,2,3) count by face
*value*, so lowest/highest are 1 and 3.

Dropped dice (from `kh`/`dl` etc.) are still counted in the per-die stats —
the die was thrown and landed on that face — but flagged in the history row.

### Per standard die type (aggregated across sets)

The same counters rolled up by *sides*, so "all my d20s" is one line even if
some rolls used the brass set and some the doodled one. Sets with non-standard
dice appear only in the per-die view.

### Per saved roll and per group

- Times rolled, mean total, min/max total observed, last result
- Histogram of totals, shown next to the theoretical distribution from
  `docs/probability.md` so the player can see how their Fireballs compare to
  expectation
- Rolled up per saved-roll group (a character, a game) so "Thorin's attack
  rolls this campaign" is one screen

### Per session

A session is a user-defined bucket ("Tuesday campaign"). All stats above are
also available filtered by session. The current session is selectable from
the home screen; by default the active saved-roll group's name is used as
the session, and "Unfiled" when no group is active.

### Anomalies (debug)

Counts of in-flight corrections, re-thrown dice and forced settles (see
`docs/physics-and-rendering.md`). Hidden behind a developer toggle.

## Screens

- **Overview:** big tiles for the currently selected die type — natural
  highs, natural lows, average, total rolls — with a face histogram and a
  faint line for the expected uniform frequency.
- **All dice:** table of every die ever rolled, sortable.
- **Saved rolls:** per-formula history with expected vs. observed graph.
- **History:** scrollable list of past rolls with breakdowns. A past roll is
  a record, not something to re-run: there is no replay action and the seed
  is never shown. Re-rolling a formula means rolling it again.
- **Sessions:** create/rename/delete. Deleting one moves its rolls to
  Unfiled.

## Storage

Room database, three tables:

```text
roll_history(id, timestamp, session_id, saved_roll_id?, group_id?, formula, total,
             seed, input_blob, breakdown_json, anomalies)
die_stats(set_id, die_id, sides, face_value, count, dropped_count,
          PRIMARY KEY(set_id, die_id, face_value))
die_summary(set_id, die_id, sides, throws, sum, sum_sq, hi_streak, hi_streak_max,
            lo_streak, lo_streak_max, last_rolled_at)
```

Streaks and sums are updated in the same transaction as the history insert.
`roll_history` is capped at 50,000 rows by default (oldest pruned); aggregates
are never pruned. Uninstalling a dice set keeps its rows.

`seed` and `input_blob` (the quantised shake samples, or the default throw
parameters) are kept so a roll can be reproduced exactly when a bug report
needs it. They are **internal**: no screen shows them, and the export leaves
them out. Reproducing a stored roll is a developer action
(`docs/physics-and-rendering.md`, Debug tooling), not a feature of the app.

## Export and reset

- Export everything as JSON or CSV via the share sheet.
- Reset per die, per saved roll, per session, or everything, each with a
  confirmation dialog.
- Nothing is uploaded anywhere. There is no analytics backend; the
  "statistics" in this document are the player's, on the player's phone.
