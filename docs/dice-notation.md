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

**The app carries this reference too.** Menu → **Notation** lists everything
below in the same words, with an example on every line that you can tap to put
in the tray's field — because somebody with a phone in their hand at a table is
not reading a README. The screen is built from `NotationReference` in
`core/notation`, beside the parser, and a test puts every one of its examples
through that parser: an example the app would refuse fails the build rather
than the player (`docs/architecture.md`).

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
| Dice in the tray, including the ones explosions add | table capacity, hard cap 100 | The chain stops there, noted in the breakdown. An added die is dropped into clear floor, and a tray with none left cannot take one (`docs/tables.md`) |
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
   otherwise the default set — chosen on that set's own details screen
   (`docs/dice-sets.md`, design `6a`) and remembered with the settings —
   falling back to the built-in set **per die** when the default set lacks that
   one (so a set with no d12 still rolls `1d20 + 1d12`). The breakdown names
   the set each die came from and says when it fell back.

   A default that is **not installed, switched off, or no longer valid** is not
   a default: the built-in set stands in for the whole catalogue rather than
   per die, because a catalogue pointing at a set nobody has cannot resolve a
   plain `d20` at all. The setting is left as it was — a set switched off for
   an evening is still the one the player chose.
3. Run the table capacity check on the total die count (including the dice
   that a first explosion could add). Refuse with a message if it fails.
4. All dice from all groups go into **one** physics throw. The breakdown
   attributes each physical die back to its group.
5. Exploding and re-rolled dice: each extra die is a throw of its own, made
   once the last one has come to rest, into the same tray. It drops into the
   clear floor the settled dice leave, among the dice that set it off, and the
   player watches it land — the dice already down are drawn where they stopped
   and nothing moves them, because nothing in the new throw can reach them
   (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds").
   A chain stops at the depth limit, or sooner if the tray has no room left for
   another die. In power-saving mode the same throws happen with nothing drawn.
6. Apply the group's modifiers in the fixed order below, then the arithmetic.
   See Division rounding below.
7. Produce a `RollResult` with the total and a per-die breakdown including
   dropped dice (shown struck through). The result screen shows the total
   large, then each group's subtotal and the individual dice, then the
   modifiers — the user never has to add anything up.

   **A chain that stopped says so, under its group.** Everything else a die
   has to say about itself is already legible on the row above — a dropped die
   is struck through, a rerolled one stands beside its replacement, an exploded
   one is simply another die in the row. The two ways a chain *ends* are the
   exception, because what they describe is a die that is **not there**: an
   `8d6!` that reached the depth limit and an `8d6!` that ran out of table look
   identical on the sheet, and both read as an explosion that never happened.
   So the group carries a line saying which it was — "Exploding stopped at 20
   dice." or "The tray had no room for another die." A reroll the tray had no
   room for reaches the second line too, because it is the same fact about the
   tray. Each line appears at most once per group, whatever the number of dice
   that hit the limit, and in a fixed order rather than the order they happened
   in.

   The modifiers are the plain numbers the formula adds or takes away, each on
   a row of its own and in the order they were written. They are **the
   top-level sum only**: the `3` in `(2d6 + 3) * 2` is multiplied along with
   the dice, so listing it as "+ 3" would be adding up to the wrong number in
   front of the player. A formula like that gets no such rows, and its total is
   the formula's own arithmetic — `RollResult.itemised` is what says which of
   the two a result is, and it is checked rather than assumed, because "the
   rows add up" is the one claim on that screen a reader cannot verify at a
   glance. It is the same rule the picker's badges follow (see "Picking dice
   without typing"): edit or itemise only what can be read back.

### The order modifiers are applied in

Modifiers take effect in this order whatever order they were written in, so
`4d6r1dl1` and `4d6dl1r1` mean the same thing:

1. **`r n`** — a die showing `n` or less is thrown once more. Once: the
   replacement stands however low it is. Both dice stay in the breakdown, the
   first struck through. A reroll the tray has no room for does not happen and
   the die stands as it fell, marked in the breakdown — the alternative being a
   die dropped onto dice that have already been read.
2. **`!`** — a die showing its highest face throws another of the same die.
   The new die joins *that die's* chain rather than the group at large, so
   `2d6!kh1` keeps the better of two chains, which is what a player means by
   it. A chain stops after the explosion depth limit, or when the tray has no
   clear floor left for another die; either way the die that would have exploded
   again is marked in the breakdown.
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

**Which set the row offers** is chosen under it, once there is a second set
installed — one entry is furniture, so the chooser is not drawn until it has
something to choose between. It is not the same question as the default set:
which set a bare `d20` means is a preference chosen where the sets are
(`docs/dice-sets.md`, design `6a`), and somebody whose default is their own set
still reaches for a borrowed d20. A die taken from a set that is *not* the
default is written with it in front — `brass:1d20` — so the row can only write
a formula that rolls what it showed; taken from the default set it is written
bare, because that is what a person would type. Changing the chooser leaves the
formula exactly as it is: what is already written was written on purpose.

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
  badge, and tapping it puts the formula in the field like any other — where
  the error appears under it. **It does not fall back to the built-in set.**
  The per-die fallback in step 2 above is the *default* set's; a `setref:` gets
  none, because somebody who wrote `brass:1d20` asked for brass and quietly
  handing them a different d20 would be changing their dice without saying so.
  The roll is left exactly as written: the set may be re-installed tomorrow.
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
- **Import** from a file, from a pasted link, or from a **git repository** —
  the same sources a dice set has (`docs/dice-sets.md`), recognised in the same
  place, so a community can keep a repo of "stat blocks for monster manual X"
  and a player can paste its URL. The file picker offers every file rather than
  only `application/json`: a collection mailed through three apps arrives as
  `text/plain` as often as not, and a picker that hides the file somebody is
  looking at is worse than one that lets them choose the wrong thing and be
  told so.
- **A link is the app's one outward request**, and what comes back is treated
  as exactly what it is: bytes a stranger chose. It goes through the same
  downloader a dice set does — `https` only, a redirect that would leave
  `https` refused, and the bytes that actually arrive counted rather than the
  `Content-Length` believed — capped at the same megabyte a file is, so a
  server cannot spend somebody's data allowance proving that it should not
  have. What arrives is then read by exactly the rules below, because there is
  one validator and no path around it. A download that does not arrive is said
  differently from a collection that does not read: "no collection came back
  from that link" and "this is not a collection" are different things to be
  told, and only one of them is worth going and fixing the file over. A
  repository that arrives and holds no collection, or holds two, is said the
  first way for the same reason: there is no file there to go and fix a line
  of, and what is wrong is named in the line beneath.
  `android.permission.INTERNET` has been in the merged manifest all along,
  contributed by okhttp's own manifest, so nothing about this asks the player
  anything new.
- **A repository holds one collection, at its root, named the way the app
  names one.** A dice set is a folder and a collection is a single file, so
  something has to say which file in a repository is meant: it is the one whose
  name ends in `.dinfinity.json`, at the root. That is the same shape
  `docs/dice-sets.md` gives a package — a well-known name marking the thing —
  and it is a rule that can be followed without reading this: export from the
  app, commit the file the app wrote, push.

  | What is in the repository | What happens |
  | --- | --- |
  | one `*.dinfinity.json` at the root | it is imported |
  | none at the root, one deeper | refused, naming where it found one |
  | more than one at the root | refused, naming them all |
  | none anywhere | refused, saying what a collection is called |

  At the root rather than anywhere, because a repository of stat blocks is
  full of JSON and "anywhere" would mean guessing; one rather than several,
  because a link names a repository and not a file, and an import that quietly
  picked one of two would be picking for somebody. A forge's tarball wraps
  everything one folder deep in `repo-<sha>/`, and so does anybody who zips a
  directory rather than its contents, so that one wrapper is seen through —
  but only that one, because "the root" has to mean something definite.
- **The repository takes the same road as the link, with an unpacking in the
  middle.** Which repository a URL means is decided once, by the same code that
  decides it for a dice set — GitHub, GitLab, Codeberg/Gitea, or any `https`
  link to a `.zip` or `.tar.gz`, with the branch or tag the URL named. The
  tarball is fetched by the same downloader under the **same one-megabyte cap**
  as the collection itself, and unpacked by the same hardened extractor a dice
  set goes through: absolute paths and `..` refused in either slash direction,
  links and devices refused, the entry count and the expanded size counted as
  the archive is read. Two things are narrowed for a collection: only `.json`
  is written at all, and the whole archive may expand to no more than the
  megabyte the collection itself is allowed. The app is here for one page of
  JSON, and a repository that expands to more than the file it carries is
  asking the phone to unpack a library to read a page.
- **Nothing is kept from a repository but the collection.** The archive and
  everything unpacked from it live in a folder of that fetch's own, deleted
  whether the import succeeded or not; what is unpacked is read and never
  installed. As with a file, the database is written only after the reader has
  passed the collection, so a repository that turns out to be hostile costs
  the download and nothing else.
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
