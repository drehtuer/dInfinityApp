# Dice notation

> **Design:** the notation field (option 2a), the three dice pickers (1h–1j),
> the parse-error state (9c) and the saved-roll screens (1n–1p, editor 1r,
> import 9f–9g) are in the [clickable design](../design/dInfinity.dc.html) ([design/](../design/)).

dInfinity accepts the tabletop notation most players already know, e.g.
`3d6 + 1d20 - 4`. This document defines exactly what is accepted and how it
is evaluated.

## Examples

| Input | Meaning |
| --- | --- |
| `d20` | one twenty-sided die |
| `3d6` | three six-sided dice, summed |
| `3d6 + 1d20 - 4` | sum of both groups minus 4 |
| `2d20kh1` | roll two d20, keep the highest (advantage) |
| `2d20kl1` | keep the lowest (disadvantage) |
| `4d6dl1` | roll four d6, drop the lowest (stat generation) |
| `d100` or `d%` | percentile: two d10 as tens and units (`d%` is an alias for `d100`) |
| `8d6!` | exploding d6: each 6 rolls an additional d6 |
| `2 * (1d8 + 3)` | arithmetic and grouping |
| `1d6 + 1d4 [Fire]` | trailing label, ignored for math, shown in breakdown |
| `brass:1d20` | use the d20 from the installed dice set with id `brass` |

## Grammar

```ebnf
formula   := expr label?
expr      := term (("+" | "-") term)*
term      := factor (("*" | "/") factor)*
factor    := ("-")? atom
atom      := dice | integer | "(" expr ")"
dice      := (setref ":")? count? "d" sides modifier*
count     := integer                     ; default 1, max 1000 (see Limits)
sides     := integer | "%" | "F"          ; "%" is an alias for 100 (as 2d10), "F" = fudge/fate die
setref    := identifier                  ; installed dice set id
modifier  := "kh" integer                ; keep highest n
           | "kl" integer                ; keep lowest n
           | "dh" integer                ; drop highest n
           | "dl" integer                ; drop lowest n
           | "!"                         ; explode on max face
           | "r" integer                 ; reroll once on value <= n
           | "min" integer               ; clamp each die to at least n
label     := "[" text "]"
integer   := [0-9]+
identifier:= [a-z][a-z0-9_-]*
```

Whitespace is ignored between tokens. Notation is case-insensitive except for
set ids.

## Limits

To keep the simulation and the probability graph tractable:

| Limit | Value | Behaviour when exceeded |
| --- | --- | --- |
| Dice per formula (parse) | 1,000 | Parse error, shown inline. This bound exists so the outcome graph stays cheap. |
| Dice per *roll* | table capacity (`docs/tables.md`), hard cap 100 | Roll button disabled with the reason; the graph still works |
| Sides per die | must exist in a set | Parse error naming the missing die |
| Explosion depth | 20 | Further explosions ignored, noted in breakdown |
| Nested parentheses | 8 | Parse error |
| Result magnitude | fits in 64-bit | Overflow is a parse-time error via the PMF bound |

A formula like `500d6` can be typed and graphed but cannot be rolled: the
dice would not fit on the table with room to move, and a physics engine with
that many bodies crammed in a box produces garbage rather than a roll. The
capacity check happens before any body is created and the UI explains it
("500 dice don't fit on the table; up to 72 do").

## Evaluation

1. Parse into an AST.
2. Resolve each `dice` node to concrete `Die` definitions: `setref` if given,
   otherwise the default set from Settings, falling back to the built-in set
   **per die** when the default set lacks that one (so a set with no d12 still
   rolls `1d20 + 1d12`). The breakdown names the set each die came from and
   says when it fell back.
3. Run the table capacity check on the total die count (including the dice
   that a first explosion could add). Refuse with a message if it fails.
4. All dice from all groups go into **one** physics throw. The breakdown
   attributes each physical die back to its group.
5. Exploding dice: extra dice are thrown in a *second* throw after the first
   settles, and so on, up to the depth limit. In power-saving mode this is
   invisible; in normal mode the extra dice drop into the tray.
6. Apply the group's modifiers in the fixed order below, then the arithmetic.
   See Division rounding below.
7. Produce a `RollResult` with the total and a per-die breakdown including
   dropped dice (shown struck through). The result screen shows the total
   large, then each group's subtotal and the individual dice, then the
   modifiers — the user never has to add anything up.

### The order modifiers are applied in

Modifiers take effect in this order whatever order they were written in, so
`4d6r1dl1` and `4d6dl1r1` mean the same thing:

1. **`r n`** — a die showing `n` or less is thrown once more. Once: the
   replacement stands however low it is. Both dice stay in the breakdown, the
   first struck through.
2. **`!`** — a die showing its highest face throws another of the same die.
   The new die joins *that die's* chain rather than the group at large, so
   `2d6!kh1` keeps the better of two chains, which is what a player means by
   it. A chain stops after the explosion depth limit, and the die that would
   have exploded again is marked in the breakdown.
3. **`min n`** — a die below `n` counts as `n`, per die. The face it actually
   landed on is still what the breakdown shows; only its contribution changes.
4. **`kh` / `kl` / `dh` / `dl`** — whole chains are kept or dropped, ranked by
   what each chain came to together. A percentile pair counts as one unit, so
   `2d%kh1` keeps the better of two 1–100 results.

A group may carry each modifier at most once, and may keep **or** drop, not
both: `4d6dl1dl1` and `4d6kh1dl1` are refused rather than quietly meaning
something. Keeping or dropping more dice than the group rolls is refused too.

### What is settled before the dice are thrown

Everything that can be known without rolling is checked while the formula is
still text, so a roll in front of a player never fails halfway through:

- a die the set does not have, with the nearest one it does have offered;
- a `kh`/`kl`/`dh`/`dl` count the group cannot satisfy;
- a `!` on a die whose every face is its highest, which would never stop;
- a division whose divisor could be zero — `1d6 / 1dF` is refused, `1d6 / 1d4`
  is not;
- a result that could not be added up in 64 bits.

## Division rounding

Division rounds **down** by default (`7/2 = 3`), which is what most game
rules say. The default is a setting, and the result sheet offers Down /
Nearest / Up for the throw in front of you — the total is recomputed from the
same dice, which stay as they landed. Nearest rounds `.5` up and anything
below `.5` down.

The per-throw override is not remembered: the next roll uses the setting
again. The outcome graph always uses the setting, since it is computed before
the throw exists.

## Picking dice without typing

The roll screen also has a **dice picker**: tap a d6 three times and a d20
once and you have `3d6 + 1d20`. A tap writes into the formula field, so what
comes out is a formula somebody could have typed — which is what makes the
outcome graph, the breakdown and statistics identical either way. A picked
roll can be turned into a saved roll with one tap.

The rules the picker follows, all of which fall out of "a tap writes a
formula and never throws one away":

- **A tap counts up a group that is already there**, so a second d6 turns
  `1d6` into `2d6` rather than writing `1d6 + 1d6`.
- **A new group is written in front of the first plain number**: tapping a d20
  on `3d6 - 4` gives `3d6 + 1d20 - 4`, because a formula reads as dice and then
  arithmetic.
- **The count badge counts the top-level sum only, and only groups that are
  added and carry no modifiers.** `4d6dl1` shows no badge: the picker could
  not take a die away from it without silently changing what `dl1` drops.
  Dice inside brackets and dice being subtracted are likewise not the
  picker's to change.
- **A long press takes one die off**, and takes the group away with it when it
  was the last one. A press with nothing to remove does nothing.
- **Everything else in the formula is left exactly as written.** Edits are
  spliced into the text, not re-printed from the parse tree, so modifiers,
  brackets, set references and a trailing `[label]` come back spelled the way
  they were typed. Only the spacing around the top-level `+` and `-` is
  normalised, because that is the part being cut into.
- **A formula that does not parse has no badges and cannot be added to.**
  There are no counts to show and nothing to splice into.

The row offers the **standard dice the selected set defines** — `dN`, `d%`
and `dF`. A set's own die ids (`skull-d6`) are not on it, because plain
notation has no spelling for them (`docs/architecture.md`, decision 31), and
a button whose taps could not be written into the field would be a button
whose taps disappear.

## d100 and d%

`d%` is an alias for `d100` everywhere: in typed formulas, saved rolls,
imported collections and the breakdown (which always prints `d100`).
Both are always resolved to a tens d10 (faces 00–90) and a units
d10 (0–9) from the same set, marked as a pair. Result = tens + units, with
00+0 = 100. If the set has a `d100-tens` die it is used; otherwise the normal
d10 is used with its face values multiplied by ten in the breakdown. Sets can
also define a true 100-face die but it is never chosen by `d100` implicitly.

## d2

`d2` uses the set's coin if present, otherwise a d6 with face values
`1,2,1,2,1,2`. Which one was used is visible in the breakdown.

## Saved rolls

A saved roll is a named formula with an icon, living in a group:

```text
SavedRollGroup {
  id, name ("Curse of Strahd" / "Thorin"), icon,
  parentId?              // one level of nesting: game → character
  sortOrder
}

SavedRoll {
  id, groupId,
  name ("Fireball"), icon (from icon pack or emoji),
  formula ("8d6 [Fire]"),
  colour tag,
  favourite flag,
  createdAt, lastUsedAt, useCount
}
```

- Groups are how players organise rolls per game, per character, per
  monster stat block — whatever they want. Groups nest one level deep
  (`D&D / Thorin`, `Pathfinder / Ezren`); deeper trees are not worth the UI.
  One level is checked from both ends: a group cannot be put inside one that
  is already inside another, and a group that has groups inside it cannot be
  put inside anything.
- **A group's name is its own.** Two groups may not share a name, ignoring
  case. This is the same rule an import enforces when it refuses a collection
  whose group name is taken; a name the app itself let you duplicate would
  make that refusal arbitrary.
- Deleting a group never deletes a roll. Its rolls move to Unfiled and its
  child groups are lifted to the top level.
- The home screen shows the **active group** as tiles; tap to roll,
  long-press to edit. A tap *throws* there, unlike a tap on the saved-rolls
  list, which only puts the formula in the field: the tray is already on
  screen, and arriving at it with the throw already over would be a roll nobody
  watched. Favourites are pinned first, the rest ordered by recent
  use. Switching the active group is one tap in the top bar, and the active
  group also sets the default statistics session (`docs/statistics.md`).
- The formula is re-validated when displayed, because the dice set it
  references might have been uninstalled. A broken saved roll shows a warning
  badge and falls back to the built-in set when rolled.
- Statistics are tracked per saved roll and per group.

### Export and import

Saved rolls travel as a **collection**: a JSON file describing groups and
rolls.

```json
{
  "format": 1,
  "name": "Thorin, level 5 fighter",
  "groups": [
    { "id": "thorin", "name": "Thorin", "icon": "⚔️", "parent": null }
  ],
  "rolls": [
    { "group": "thorin", "name": "Longsword", "icon": "🗡️", "formula": "1d20 + 7 [Attack]" },
    { "group": "thorin", "name": "Longsword damage", "icon": "💥", "formula": "1d8 + 4 [Slashing]" }
  ]
}
```

- **Export** a group (with its subgroups) or everything via the share sheet.
  The file is named after what is in it — `curse-of-strahd.dinfinity.json` —
  and is a copy in the cache, handed over through a content URI granted for
  one use. Nothing the app holds is made readable to do it.
- **Import** from a file, from a pasted URL, or from a git repository (same
  sources as dice sets, see `docs/dice-sets.md`). A community can keep a
  repo of "stat blocks for monster manual X" this way. The file picker offers
  every file rather than only `application/json`: a collection mailed through
  three apps arrives as `text/plain` as often as not, and a picker that hides
  the file somebody is looking at is worse than one that lets them choose the
  wrong thing and be told so.
- Import **never merges and never deletes**. A collection whose group name
  already exists is refused outright, naming the clash; rename the group in
  the file (or the one in the app) and import again. Everything else is added
  as new groups and rolls. There is no conflict-resolution UI to get wrong,
  and an import can never damage what is already there. The clash is checked
  ignoring case, and the name reported is the one the *file* spells, since that
  is the one to go and change. The file's own ids are not reused: they are
  stable inside the file, which is what lets somebody edit one by hand, and say
  nothing about what this app already uses.
- Every formula goes through the parser and limits above. Unknown dice set
  references are kept but flagged; icons are restricted to emoji or names
  from the built-in icon pack (no image files in collections).
- **Ids are slugs** — lower-case letters, digits, `-` and `_` — not UUIDs,
  because a collection is a file people edit by hand: `"group": "thorin"` is
  something a person can type where a UUID is something a person mistypes.
  Exporting re-derives them from the group names, so exporting the same
  collection twice gives the same file.
- **Two groups in one collection may not share a name**, ignoring case. It is
  the same rule the app itself keeps, so a file that could never be imported
  says so when it is read rather than when it is refused.
- **A `format` this version does not know is refused**, not read as best it
  can. Reading a newer file anyway is how a format silently drops whatever the
  newer version added.
- A collection that fails is refused **entirely**, and the report lists
  everything wrong with it rather than the first thing: somebody fixing a file
  by hand wants the whole list, and each line says where in the file it is
  (`rolls[3].formula`).
- What travels is what somebody wrote — names, formulas, marks, groups and
  favourites. What the app made of it does not: no use counts, no timestamps,
  no seeds, no colour tags from the app's own palette, and no table pin naming
  a package the other phone has never heard of.
- Limits: 500 rolls and 50 groups per collection, 1 MiB file (counted in
  bytes, not characters). Bigger files are rejected with a message, without
  being parsed. Names are capped at 100 characters, icons at 16 and formulas
  at 500 — limits on what a file can do to a screen rather than on what anyone
  will write.

## Error messages

Parse errors point at the offending character range and are shown inline
under the formula field: **the formula is printed again with a squiggle under
the characters that are wrong**, and the message after it
(`design/dInfinity.dc.html`, options 6f and 9c).

```text
3d6 + 1d7 - 4 — this set has no d7
       ~~~
```

Printed again rather than marked up in the field itself. The field is where
somebody is typing, and a squiggle that moves under the cursor as they type is
a squiggle that fights them. A wave rather than a straight underline, because
a straight underline under text reads as a link.

The blame is clamped to a character that exists: a formula that stops in the
middle of something (`3d6 +`) is blamed on a position one past its end, and
there is nothing there to underline.

Suggestions are offered when the intent is obvious (`d7` → nearest available,
`3 d 6` → `3d6`), as one tap under the message. Where there is no honest
reading there is no suggestion: a guess that is wrong is one tap away from
replacing a formula somebody meant.
