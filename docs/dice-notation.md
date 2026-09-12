# Dice notation

> **Design:** the notation field (option 2a), the three dice pickers (1h–1j),
> the parse-error state (9c) and the saved-roll screens (1n–1p, editor 1r,
> import 9f–9g) are in the [clickable design](https://claude.ai/design/p/5cee69c8-e516-4414-a446-7fd89bb7c706?file=dInfinity.dc.html) ([design/](../design/)).

dInfinity accepts the tabletop notation most players already know, e.g.
`3d6 + 1d20 - 4`. This document defines exactly what is accepted and how it
is evaluated.

## Examples

| Input | Meaning |
|---|---|
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

```
formula   := expr label?
expr      := term (("+" | "-") term)*
term      := factor (("*" | "/") factor)*
factor    := ("-")? atom
atom      := dice | integer | "(" expr ")"
dice      := (setref ":")? count? "d" sides modifier*
count     := integer                     ; default 1, max 200
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
|---|---|---|
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
6. Apply keep/drop/reroll/min per group, then arithmetic. See Division
   rounding below.
7. Produce a `RollResult` with the total and a per-die breakdown including
   dropped dice (shown struck through). The result screen shows the total
   large, then each group's subtotal and the individual dice, then the
   modifiers — the user never has to add anything up.

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
once and you have `3d6 + 1d20`. Internally this is the same `RollPlan`, so
the outcome graph, the breakdown and statistics work identically for picked
dice and typed formulas. A picked roll can be turned into a saved roll with
one tap.

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

```
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
- The home screen shows the **active group** as tiles; tap to roll,
  long-press to edit. Favourites are pinned first, the rest ordered by recent
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
- **Import** from a file, from a pasted URL, or from a git repository (same
  sources as dice sets, see `docs/dice-sets.md`). A community can keep a
  repo of "stat blocks for monster manual X" this way.
- Import **never merges and never deletes**. A collection whose group name
  already exists is refused outright, naming the clash; rename the group in
  the file (or the one in the app) and import again. Everything else is added
  as new groups and rolls. There is no conflict-resolution UI to get wrong,
  and an import can never damage what is already there.
- Every formula goes through the parser and limits above. Unknown dice set
  references are kept but flagged; icons are restricted to emoji or names
  from the built-in icon pack (no image files in collections).
- Limits: 500 rolls and 50 groups per collection, 1 MiB file. Bigger files
  are rejected with a message.

## Error messages

Parse errors point at the offending character range and are shown inline
under the formula field, e.g.

```
3d6 + 1d7 - 4
       ^^ no d7 in set "builtin"
```

Suggestions are offered when the intent is obvious (`d7` → nearest available,
`3 d 6` → `3d6`).
