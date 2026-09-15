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

### Where the rolls are going

The menu's header names the session new rolls are filed under, beside the app's
own name (`design/dInfinity.dc.html`, option 1q). It says so **only once there
is more than one session to be in**: every install starts with exactly one — the
one the rolls made before anybody thought about sessions belong to — and naming
it would be a line that never changes and never tells anybody anything.

The menu rather than the roll screen, for the same reason it is a preference at
all: it outlives every screen, nothing on the tray should have to carry it, and
the menu is where a player already looks to find out where they are.

### Filtering what you are looking at

The history can be cut two ways: to one **session** — which is what sessions
are for — or to one **saved roll**, wherever its throws were made. They are not
combined: "Fireball on Tuesday" is a report rather than a list, and offering it
would put two choosers on a screen whose whole job is to be scrollable.

The chooser is not drawn until there is more than one thing to choose between.

A filter that leaves nothing shows *that*, and not the message for a history
with nothing in it: "you have never rolled anything" is wrong and discouraging
in front of somebody who has rolled hundreds of times and picked a quiet
session. Changing the filter closes any open breakdown, because the row that
was open is not the row under the finger in the next list.

The **statistics** screen can be cut to a session too, and that chooser is not
exclusive with the set one — see "Per session" below for what a session's
numbers are, and the one number a session cannot have.

### Per saved roll and per group

A throw carries the saved roll it came from and the group that roll is in,
recorded with it. That is what makes "Thorin's attack rolls this campaign" a
query rather than a guess — and without it every throw belongs to nothing and
these screens have nothing to count.

The attribution is **dropped by any edit**. A formula that arrived from the
strip and has since been typed over, or had a die tapped onto it, is not that
roll's throw: the picker goes through the same `type` a keystroke does, so it
loses the attribution the same way. A saved roll's record is what was thrown
*as* that saved roll, not what was thrown starting from it.

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

A session is a user-defined bucket ("Tuesday campaign"). Every roll is filed
under the active one, which is chosen on the **Sessions** screen and remembered
with the settings; the menu's header names it once there is more than one to be
in. The first session is called "First rolls" and cannot be deleted — it is
where the rolls made before anybody thought about sessions belong, and where
the rolls of a deleted session go.

**And where a roll goes when the active session is not there any more.** Which
session is active is a preference, and a preference outlives the thing it
names: deleting the active session on the Sessions screen puts the setting
right, but a session deleted while another screen is in front — or one already
gone when the app was last opened — would otherwise leave every throw filed
under an id nothing can find. Such a roll would be in the history and in the
face counts and visible in neither, because both are read through the list of
sessions. So the session is checked where the roll is recorded, against the
sessions there actually are.

**The history and the statistics can both be cut to a session.** On the
statistics screen the chooser sits above the set chooser rather than in it,
because the two cut across each other: "the brass d20, this campaign" is a
sentence a player would say, where "all my d20s, but only the brass ones" is
not. It is not drawn until there are two sessions to choose between.

`die_stats` carries the session; `die_summary` does not. The counts are stored
rather than recomputed — reading a campaign's histogram out of
`roll_history.breakdown_json` is a scan of up to fifty thousand rows every time
the screen draws, and a row per face per die per session buys a lookup instead.
The asymmetry between the two tables is the streaks:

- **Counts add.** Every all-time number in `die_stats` is the sum of its
  sessions, so splitting it loses nothing and duplicates nothing — and a
  session's throws, sum, mean and spread come straight off it, exactly the
  numbers they would have been had they been counted separately all along.
- **A streak does not.** Five twenties in a row are five twenties in a row
  whether or not somebody started a new campaign in the middle of them. Keyed
  by session, the all-time longest run would become the longest run *within* a
  session and would quietly come out short — so `die_summary` stays what it has
  always been, and a session's rows carry no streaks rather than wrong ones.

The export is the whole record whatever is on screen, for the same reason: a
file of what every die has done is not where anybody looks for one campaign,
and a file written while a session was chosen would carry runs of zero.

A session with nothing in it says *that*, and not the message for somebody who
has never rolled anything — the same rule the history follows.

### Anomalies (debug)

Counts of in-flight corrections, re-thrown dice and forced settles (see
`docs/physics-and-rendering.md`). Hidden behind a developer toggle.

## Screens

- **Overview:** big tiles for the currently selected die type — natural
  highs, natural lows, average, total rolls — with a face histogram and a
  faint line for the expected uniform frequency.
- **All dice:** table of every die ever rolled. Three orders: most recently
  used (the default — a player comes here about a die they have just been
  rolling), most thrown, and highest average. No ascending/descending toggle:
  each of those has an interesting end and it is the top, and the other end is
  the bottom of the same list. A die nobody has thrown sorts last by average
  rather than lowest — it has not come out low, it has not come out.
- **Saved rolls:** per-formula history with expected vs. observed graph.
- **History:** scrollable list of past rolls with breakdowns. A past roll is
  a record, not something to re-run: there is no replay action and the seed
  is never shown. Re-rolling a formula means rolling it again.
- **Sessions:** create/rename/delete. Deleting one moves its rolls to the
  first session rather than deleting them, so a session can be tidied away
  without losing what was rolled in it. ("Unfiled" is the saved-roll *group*
  default, and a different thing.)

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
          PRIMARY KEY(set_id, die_id, face_value))   -- re-keyed in v5
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

-- version 5 (per session, above): die_stats grows a session and is re-keyed
die_stats(set_id, die_id, session_id, sides, face_value, count, dropped_count,
          PRIMARY KEY(set_id, die_id, session_id, face_value))
```

Version 5 is the first migration to reshape a table somebody already has rows
in, so it is the dance SQLite requires for a new primary key: build the table
beside the old one, copy the rows across, drop the old one, rename. Every
existing row becomes a row of the **first session** — not a default standing in
for something unknown, but because the rolls those counts came from are already
filed there in `roll_history`, so it is the same answer written in a second
place. `die_summary` is untouched.

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

- **The history exports as JSON or CSV**, through the share sheet the way a
  collection does. Two formats because they answer different questions: JSON
  keeps the breakdown and is the one to keep, CSV is one row per roll and is
  the one a spreadsheet can draw.
- **What is exported is what the list is filtered to** — one session, one saved
  roll, or everything — but not what is *paged* to. The screen asks for two
  hundred rolls because nobody scrolls further; the file carries every roll the
  filter matches, because a file quietly missing all but the newest page is
  worse than no file, nothing about it having said so.
- **No seed is ever written, and that is structural rather than remembered.**
  The export is built from `HistoryEntry`, which has no seed on it — the column
  exists in `roll_history` and is dropped on the way out of the repository. A
  past roll is a record, not something to re-run, and a record carrying its
  seed is a replay waiting to be written.
- Times are ISO-8601 in UTC, not the way the screen shows them: a file outlives
  the phone it was made on, and a localised date is one a spreadsheet has to
  guess at. CSV is RFC 4180, so a formula with a comma in it stays one column.
- **The statistics export too**, and the two formats carry different things.
  The flat form is one row per *face* — `set, die, sides, face, count,
  dropped` — because the question somebody exports statistics to answer is
  *are my dice fair*, and that is asked of face counts. The full form adds each
  die's summary, because a **run** is the one thing the counts cannot give
  back: how often a die came up highest in a row is a fact about the order it
  was thrown in, and a histogram has forgotten that. Everything else in the
  summary *is* derivable from the counts — the throws are their sum, the mean
  their weighted average — so it is not repeated down every row.
- A set filter is carried into the file; **the roll-up is not.** A pooled row
  stands for every d20 in every set at once and so belongs to no set, which is
  right on a screen and wrong in a file, where the set is what makes a record
  checkable. The per-die rows are also the ones a roll-up can be recomputed
  from, and the reverse is not true, so the file keeps the half that can give
  back the other.
- Both exports are **snapshots**, read once when the player asks: a file is a
  copy taken at a moment, and a screen that re-exported itself every time a
  roll landed would be opening share sheets.
- **Reset per die, per session, per saved roll and everything**, each behind a
  confirmation. Per die and everything are on the statistics screen; per
  session and per saved roll are on the **history**, beside Export and only
  with that filter on — the two are the same act on the same rolls, keep a copy
  of what you are looking at or be rid of it. "Forget the entire history" is
  not offered there: it is a bigger thing than a filter being off, and
  offering it beside a filter would make it look the same size.
- **The two records are separate, and a reset says which one it forgets.**
  Forgetting a die's aggregate leaves its rolls in the history; forgetting a
  session's rolls leaves what each die has done, because `die_stats` and
  `die_summary` carry no session to subtract from (see Storage). The
  confirmation says so — a "forget" that half forgets, silently, would send
  somebody back to the statistics wondering why nothing moved.
- Nothing is uploaded anywhere. There is no analytics backend; the
  "statistics" in this document are the player's, on the player's phone. The
  share sheet is the player handing a copy on, which is a different act.
