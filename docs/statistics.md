# Statistics

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

Counts of nudges, forced settles and snapped faces (see
`docs/physics-and-rendering.md`). Hidden behind a developer toggle.

## Screens

- **Overview:** big tiles for the currently selected die type — natural
  highs, natural lows, average, total rolls — with a face histogram and a
  faint line for the expected uniform frequency.
- **All dice:** table of every die ever rolled, sortable.
- **Saved rolls:** per-formula history with expected vs. observed graph.
- **History:** scrollable list of past rolls with breakdowns; tap to replay
  the physics from the stored seed.
- **Sessions:** create/rename/delete, and a share sheet for a session
  summary as text ("Tuesday: 214 rolls, 11 nat 20s, 9 nat 1s, d20 avg
  10.7").

## Storage

Room database, three tables:

```
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

`input_blob` holds the quantised shake samples (or the default throw
parameters) so that any historical roll can be replayed exactly.

## Export and reset

- Export everything as JSON or CSV via the share sheet.
- Reset per die, per saved roll, per session, or everything, each with a
  confirmation dialog.
- Nothing is uploaded anywhere. There is no analytics backend; the
  "statistics" in this document are the player's, on the player's phone.
