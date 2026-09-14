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

Throws, totals and squares add up, so a pooled mean is the mean of everything
thrown rather than the mean of two means. **Streaks do not**: a streak is a run
within one die's own sequence, and two dice's runs do not join end to end, so a
rolled-up row claims none.

**The fair line is weighted by how often each die was thrown**
(`FaceHistogram.ofPool`). Two sets' d20s need not be labelled the same way and,
more to the point, need not have been thrown the same number of times: a fair
d6 thrown a thousand times pooled with a `1,2,3,1,2,3` thrown twice is very
nearly a fair d6, and a line drawn from the two value lists alone would call it
loaded. Each die expects `throws / sides` of its throws on each of its faces;
those expectations are summed per value and divided by the whole pool.

If any die in the pool comes from a set that is **not installed any more**, its
throws are counted in the bars while its faces are missing from the line, so
the pool is marked the same way a single uninstalled die is: the line is a
guess, and the screen says so.

### Per saved roll and per group

- Times rolled, mean total, min/max total observed, last result
- Histogram of totals, shown next to the theoretical distribution from
  `docs/probability.md` so the player can see how their Fireballs compare to
  expectation
- Rolled up per saved-roll group (a character, a game) so "Thorin's attack
  rolls this campaign" is one screen

**Only the throws made through the saved roll are counted.** The same formula
typed by hand is a different question — it is not that roll's record — and the
history can tell them apart because a throw started from a saved roll carries
its id.

**The screen refuses to draw a conclusion.** A mean two tenths above
expectation is remarkable after ten thousand throws and nothing at all after
four, so the drift is divided by the standard error of the mean (σ/√n) before
anything is said about it. Below twenty throws it says there are too few to
judge; past two standard errors it says *worth a look*, which is a long way
from *loaded*. Dice are not accused on the strength of forty throws.

**A formula that no longer graphs keeps its bars and loses its marks.** A saved
roll's formula is stored as text and the set it names can be uninstalled
afterwards (`docs/dice-notation.md`), so a roll that graphed last week may not
today. What the dice did is still the player's record and hiding it would lose
the only copy — so the ink bars are drawn with nothing to draw them against,
and the screen says which of the three things went wrong: the formula no longer
parses, the set is not installed, or the throw is too large to compute exactly.
They are different problems and one message for all three would be wrong about
two of them.

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

Room database. Version 1 is these three tables and nothing else; saved rolls,
sessions and the installed-set registry arrive with the screens that need them
(`docs/TODO.md`, Step 4), each as a migration. That is what "migrations from
day one" buys: a database that has only ever been created and never migrated
is one whose first migration gets written under pressure.

Every version's schema is exported to `data/schemas/` and checked in, and
`SchemaTest` asserts there is one for every version and a migration for every
step between them — so a version bump without a migration fails the build
rather than a player's phone. `MigrationTest` goes further and **runs** them:
it builds a database out of version 1's own exported schema, puts a roll in
it, and opens it through the real builder, which is where Room refuses a
migrated schema that does not match the entities. There is no destructive
fallback: a player's natural-20 count is not something to throw away because a
schema moved.

```text
-- version 1
roll_history(id, timestamp, session_id, saved_roll_id?, group_id?, formula, total,
             seed, input_blob, breakdown_json, anomalies)
die_stats(set_id, die_id, sides, face_value, count, dropped_count,
          PRIMARY KEY(set_id, die_id, face_value))
die_summary(set_id, die_id, sides, throws, sum, sum_sq, hi_streak, hi_streak_max,
            lo_streak, lo_streak_max, last_rolled_at)

-- version 2 (docs/dice-notation.md, "Saved rolls")
saved_roll_group(id, name, icon, parent_id?, sort_order, table_set_id?, table_id?)
saved_roll(id, group_id, name, formula, icon, colour_argb?, favourite,
           table_set_id?, table_id?, created_at, last_used_at?, use_count)

-- version 3 (per session, below)
session(id, name, started_at)

-- version 4 (docs/dice-sets.md, design 5a)
installed_set(id, enabled)
```

`installed_set` is the one table that is **not** a list of anything. Which dice
sets exist is the `dicesets/` folder's answer, read and revalidated on every
reading (`docs/dice-sets.md`); this table holds only what the player has
decided about one, so a set with no row is enabled. Rows are pruned against
what is actually on disk, because a folder can vanish without the app being
asked and a row left behind would switch a *new* package off the moment
somebody installed one under the same id.

A saved roll's formula is stored as **text**. The dice set it names may be
uninstalled later, and a roll that no longer resolves is neither deleted nor
rewritten — storing anything more resolved would let an uninstall quietly
rewrite what somebody wrote (`docs/dice-notation.md`).

Deleting a group **moves its rolls to Unfiled and lifts its child groups to
the top level** rather than cascading. The foreign key cascades, and that is
the floor under the behaviour rather than the behaviour: a group is a folder,
and removing a folder should not remove what somebody put in it.

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
