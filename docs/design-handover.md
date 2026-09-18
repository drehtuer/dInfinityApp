# The app and the design, and where they still differ

The prototype in [design/](../design/) and the documents in `docs/` are two
halves of one specification. This is the list of places where the two halves do
not yet say the same thing, kept so that the next pass over either one starts
from what is actually true.

It was written on 2026-09-17 as a hand-over to the designer, eleven questions
long. **The pass came back the same day and answered all eleven.** What is left
here is what that pass did not cover, plus the questions it asked back.

## What was answered, and where the answer lives now

A decision lives in `docs/` once it is taken, so none of these are specified
here any more — this table is only a map from the question to its home.

| Question | Answer | Written down in |
| --- | --- | --- |
| Does the roll screen keep its bands, or become one picture? | One picture. Every control is an opaque floating plate | `docs/physics-and-rendering.md`, "What is drawn over the table" |
| Where does the running readout go? | Its own plate across the bottom, with the count, the still-possible range and a progress rule | same |
| Should last-pass dice be marked? | Yes — accent outline and a `pass 2` label | same |
| What does the accent do over felt? | Nothing. Accent only ever appears on a plate | same |
| Typography for the `+` on an open chain | `--color-accent-700`, like every accent-coloured run of text at body size | same |
| Six accent swatches wrap 5 + 1 | Four across, 44 dp: six presets and a custom swatch come out 4 + 3 | `docs/architecture.md`, "Settings" |
| A filled accent tag needs both ends of a ramp | Both ends are mixed from the accent the player picked, against text and ground rather than black and white. Built: `ui/common`'s `TagKind.Accent`, and it is what says "Update available" on a dice-set row | `docs/architecture.md`, "Settings" |
| Is there a 3D preview in the designer? | There is now: a **Solid** tab | `docs/face-designer.md`, "The solid, not just the face" |
| The designer's tool row should be icons, undo and redo in the app bar, the strip 52 dp thumbnails | All three, from the prototype's own sprite; the thumbnail keeps its label, which is the pairing this suggested | `docs/face-designer.md`, "The tools are pictures" |
| Where does the total go, and how many times? | Once, in the result sheet | `docs/TODO.md`, 4.1 |

The pass also decided a good deal nobody had asked about — per-set weight,
translucency and size; a table-view setting; staggered drops; opposite-face
numbering; dragged saved rolls; the two-stage back gesture; and the removal of
the sound switch. All of it is in `docs/` beside the rest, and `docs/TODO.md`
has the work each one makes.

## Still open

### The design asked six of its own

They are in `docs/TODO.md` under "Open questions": uppercase on a session-name
kicker, whether Statistics splits its three axes, whether History groups by day
or by saved roll, whether a set row carries make-default, whether the d18 keeps
its true shape, and whether an oversized stamp on a d4 shrinks to fit.

### Two the app is waiting on

1. **Is percent typography text?** `"0 %"`, `"< 0.1 %"` and `"%.1f %%"` are
   three renderings of the same idea, and the space before the `%` is a
   convention that differs by language.
2. **Does a refusal keep its words, or become a reason?** Refusal sentences are
   assembled in plain-Kotlin modules that may not depend on Android. Giving
   each refusal a typed reason the screen phrases is a design change as much as
   a code one, and it is what translation waits on.

### Five the pass did not reach

- **The table picker is a list of rows**, where the prototype `1u` is a
  two-column grid of cards. Deliberate — a row fits a 44 × 64 dp thumbnail and
  a name at a touch target worth pressing — but a real divergence, and a card
  gives a picture about five times the area.
- **How does a photograph sit on the tray?** Cropped at import, losing pixels
  somebody chose, or mapped at draw time, which needs the tray's aspect — and
  the tray's aspect changes with the phone.
- **Does the picker row grow a brace form?** A set's own dice (`skull-d6`) are
  becoming typable as `3{skull-d6}kh1`; the row still offers only the ten
  standard dice.
- **The sprite has no paste and no mirror.** The face designer has copy, turn,
  mirror and paste, and none of the four is in the prototype at all. Copy is
  `#ic-copy` and the turn keeps its words, because `Turn 3/4` is a count. The
  other two are drawn in the sprite's own idiom — a 24 × 24 box,
  `stroke-width: 2`, round caps and joins — as a clipboard with its clip and
  as an axis with a shape either side of it. They are the two glyphs in the
  app that the prototype cannot confirm, and they are in
  `feature/designer`'s `DesignerIcons` marked as such. Adopt them, replace
  them, or say the controls do not belong.
- **Does "Save to set" want a glyph?** The footer's two actions are lettered,
  because the prototype's footer is a lettered `.btn-primary` and because
  `ModernistButton` takes a word by design. `#ic-download` is the sprite's
  "out of the app" mark and would fit a save; whether a footer action should
  carry one is the design's call.

### The roll screen's top, after the second device session

The app has moved ahead of the prototype here, on a device session's word
rather than a drawing's, and the prototype has not been re-imported since. The
divergence is written down rather than drawn because the prototype is
generated: it is edited in the Claude Design project and re-imported whole
("Editing", `design/README.md`), so a hand edit to
`design/dInfinityPhone.dc.html` would be undone by the next sync.

What the app does now, and what the prototype still draws
(`docs/physics-and-rendering.md`, "What is drawn over the table"):

| | The app | `dInfinityPhone.dc.html` |
| --- | --- | --- |
| The dice | a pull-down at the top of the table: a `Dice` head with the count and a chevron, opening onto the picker row | the picker strip, always out, in the slot at `order:{{ pickerOrder }}` |
| The set chooser | folded inside that pull-down | a dropdown off the strip's **Set** button (`setMenuOpen`) — the same idea, one level further in |
| The formula | a **tab** on the right edge under the menu button, with the field sliding in horizontally behind it; the formula itself is not on the table | a plate in the top **left** corner, `plateLeft` / `plateTop`, with the formula printed on it |
| The saved rolls | a **pull-up** on the bottom edge, parked by default | option `1c` says "saved rolls behind a pull-up" in one line; `dInfinityPhone.dc.html` still draws the strip in the column |
| The empty-tray hint | none | a hint block in the bottom-left corner |
| The expected range | on the ready plate, on the two waiting plates, and in the result sheet's **grip** | not drawn at all |
| The tray's own shadow | none: the wall and the rim cast nothing, the dice cast | the fake bezel, `trayBorder: 6px solid {{ tbl.wall }}` |
| `See the odds`, `Save as roll` | at the foot of the result sheet | already at the foot of the result sheet, as `Graph` and `Save` — **the app has caught up here**, and only the wording differs |

Three questions for the next pass over the prototype:

1. **Is a pull-down right for the dice at all?** The design's strip is always
   out; the device session asked for it to be put away. The count on the head
   is the app's answer to "then how do I know what is in the throw" — is it
   enough?
2. **The formula moved from the left corner to the right.** It is under the
   menu button because that is where the session asked for it, which leaves
   the left corner to the dice. Two menus hanging off one edge is a shape the
   prototype does not draw.
3. **`Graph` and `Save` against "See the odds" and "Save as roll".** The app's
   wording is the wording it uses everywhere else for those two acts; the
   prototype's is shorter. One of them should give.
4. **What the shut formula tab should look like.** The app draws the word
   `Formula` and a chevron pointing inwards, on a plate, mirroring `Dice` at
   the other end of the corner, and turns both red when the formula does not
   read. The prototype has no shut state to compare it with: a tab flush with
   the edge, an icon alone, or something narrower would all be defensible, and
   the choice is a drawing rather than a rule.
5. **Two pull-ups on one bottom edge.** The result arrives by itself and the
   saved rolls are pulled up by hand; they may not both be up, and parked they
   stack — the result's grip on the edge, the saved rolls' directly above it.
   Option `1c` asks for the saved rolls behind a pull-up and `1e`–`1g` for the
   result as one, but nothing draws the two of them together. How tall the
   saved rolls' grip should be, and whether `SAVED ROLLS` belongs on it, is
   the part a drawing would settle.
6. **An empty tray now says nothing.** `Type a formula, or open Dice at the
   top.` is gone at the session's request, and the formula it pointed at is no
   longer on the felt either. On a fresh install the first-launch screen still
   says what to do; on a tray somebody has just cleared, nothing does. That may
   be right — it is the state where the felt is all there is — but it is a
   deliberate silence rather than an oversight, and the next pass should
   confirm it.

### One that has been closed

**The saved-roll editor's colour tag.** The prototype `1r` has always drawn
thirteen swatches — the twelve tags and a custom one that opens the browser's
own colour picker — with the hex printed beside them. The app had a hex
*field* there instead, which is what a phone found awkward on v0.1.1. It is
the prototype's shape now: a thirteenth swatch opening `ui/common`'s
`ColourPicker`, and the hex as a readout rather than an input. What is left of
the difference is where the hex sits — the prototype keeps it inline at the
end of the swatch row, the app puts it on the line below, because thirteen
48 dp targets already wrap on a phone.

## The shake is the throw, and the prototype half-says so

The Roll button is gone from the app: shaking the phone is the only way to
throw (`docs/physics-and-rendering.md`, "Starting a roll"). The prototype was
already close — it has no Roll button either, and a tap on the tray stands in
for the shake a browser cannot make — so most of the change was deleting
things.

**Hand-edited, and in step:** the `earned` plate has lost `Throw {{ n }} more`
and `Stop the chain` and now says to shake again; the `stuck` plate has lost
`Throw those {{ n }} again` and keeps `Cancel the roll`; and the result sheet
has lost `Again` (compact) and `Roll again` (poster). Its `rollAgain` handler
is left in the script, unreferenced, rather than unpicked by hand.

**Not done, and wanted from the design:**

- **What the ready plate says now.** The button's label was the only place the
  screen said what a throw would be worth, and the app has put the expected
  range there instead: a kicker, the low-to-high range with its `+` for an
  open chain, and `avg 10.5`. It is the counting plate's own `Range` reused,
  so the figures before the throw and the figures during it are one
  calculation — but the *plate* is the app's invention and the prototype has
  no such block. What should it look like, and should the hint and the range
  share one plate or sit in two?
- **The same line on the result sheet.** The app prints it under the
  breakdown, so the total is a number in a range. The prototype's poster sheet
  has no equivalent and has just lost a button from that action row.
- **A toast that says how many dice a shake will throw.** It is the
  back-arming toast's component, over the tray, raised when a chain earns a
  throw and when a roll gives up. The prototype has the component and does not
  raise it here.
- **The prototype's tray tap is not the app's.** Tapping the tray in the app
  deliberately does *not* roll — that gesture is kept for picking a die up —
  so the prototype's stand-in reads as a specification it is not. Worth a word
  on the board saying it is a browser's substitute for a shake.

## Screens whose shape still differs

None of these was changed by either side. They are structure, and structure is
the design's.

**Statistics** (`screen="stats"`) is one screen in the prototype: a set
segmented control, a die segmented control, a grid of four figures, a vertical
face histogram with a dashed "expected if fair" line, a streak sentence, a table
of every die, and two buttons out. The app is *a list of dice you open one at a
time*, with the filters as text buttons and the histogram one row per face. The
histogram has a stated reason — a d100 is a hundred bars on a 360 dp screen —
but the rest is a different screen. The design's own question 2 is about this
screen, so it is being looked at from both ends.

**Saved-roll statistics** (`rollstats`) likewise: the prototype has four
figures, the totals chart, a verdict and a ranked Rolled / Mean / Expected / Δ
table; the app has a roll list and a stack of sentences. Is the sentence form
deliberate?

**History** groups by session where the prototype groups by day — `Tue 12 Mar ·
14 rolls` against a heading whenever the session changes, with no count. A day
is a fact about when; a session is a thing somebody named. The design's own
question 3 asks the same thing.

**Sessions** is missing the prototype's whole lower half — the inline "New
session" field, the note that deleting moves rolls to Unfiled, and the entire
"Import and export" block.

**Dice set details** has a two-tier header (a 20 dp bar, then the set's name at
**28 dp/800**), provenance as a two-column table with rules, dice as a 4-up grid
of rendered pictures, and a footer row of primary/secondary/ghost actions. The
app merges the header, stacks the provenance, lists the dice and scatters the
actions up the page. **28 dp is not on the type scale** — the steps either side
are 25 and 32. Which did you mean?

## Things the design system does not yet say

1. **A neutral tag has no dark ground.** `.dz-dark` in
   [dInfinityPhone.dc.html](../design/dInfinityPhone.dc.html) **reflects each
   ramp about its middle step** — `--color-accent-100` becomes the light
   `-900`, `-200` becomes `-800`, `-700` becomes `-300`, `-800` becomes `-200`,
   and the neutrals do the same, `-200`↔`-800`, `-300`↔`-700`, `-400`↔`-600`,
   with `-500` its own fixed point. A step is a *depth* rather than a pigment,
   which is a genuinely elegant rule, and the app follows it.

   **It is applied to eight steps, and `tag-neutral` is made of the two it
   misses.** `.tag-neutral` is `--color-neutral-100` filled and `-800`
   lettered, and neither is in the override list — so on a dark page it comes
   out as a near-white chip with dark grey text, an inverted badge among tags
   that follow the ground. `tag-accent` is the question that no longer arises:
   the app mixes both of its ends from the accent the player picked, against
   the ground's own page and ink, so it follows the page by construction rather
   than by being on the override list (`core/model/AccentRamp`). The app
   extends the reflection to the two missing steps, because the rule is
   unambiguous where the list is silent — but it is an inference, and it is the
   one thing here that would change a drawn screen if you meant the other. Its
   contrast is measured in a test rather than eyeballed: 9.3:1 on paper and
   11.4:1 on the dark ground.
2. **The design system's dialog and the prototype's sheet disagree, and the
   prototype wins.** `.dialog-backdrop` in `styles.css` centres its card and
   `.dialog-actions` puts the buttons at `flex-end`; every sheet in
   `dInfinityPhone.dc.html` overrides all three — `align-items: end`,
   `width: 100%`, `justify-content: flex-start`. The app follows the phone,
   because the thumb is at the bottom of a phone. Worth making the export say
   the same thing, so the next person reading the stylesheet alone does not
   build the centred one again. There is one shared `Sheet` in the app, so it
   is a one-line change when you decide it.
3. **One tag says something other than what the prototype writes.** The
   dice-set rows are badged `default` and, for a set somebody has turned off,
   **`switched off`** where the prototype writes `disabled`.

   The reason is not a preference. On Android, "disabled" is the word a screen
   reader says about a control that **cannot be operated**, and these rows very
   much can be — tap to open, long-press to act. A listener hearing "Brass,
   1.0.0, 5 dice, disabled" would take it as a dead row. It is the one place
   where the app says something the prototype does not, and it is flagged here
   so that nobody "fixes" it back.
4. **Does a destructive action get a colour?** The prototype draws Remove as a
   plain `.btn-secondary`, and fills the destructive confirm with
   `.btn-primary` while giving the ghost to "Keep it" — so the destructive
   action is the loudest button on the sheet. The app follows both, and the
   "this is the one that takes something away" signal has gone with it. A
   system with one red and no other colour has only weight and wording left to
   say "careful".
5. **The slider has no design.** Hue, depth and brightness in the designer use
   Material's, which has a circular thumb and a rounded track, in a system with
   no round anything.
6. **Uppercase.** The prototype sets kickers and column headings in
   `text-transform: uppercase` with wide tracking. The tracking is applied; the
   case is not, because Compose has no text transform, so applying it means
   uppercasing the string itself and changing what a screen reader says. There
   is one shared kicker and it does not, so the app is consistent — and one
   reason it does not is that a kicker is sometimes a name somebody typed,
   which is the design's own first open question seen from this side.
7. **The designer's canvas paper is white** — a literal, not a token. Is that
   the die's real painted ground, or chrome that should follow the theme?
8. **`gap: 6px` in the face strip** is not on the 4/8/12 scale. Deliberate?
9. **A settings row has no narrow state.** It is a flex row of a
   `min-width: 0` text column and a `flex: none` segmented control, which in a
   browser means the control keeps its width and the page overflows sideways.
   A phone has nowhere to overflow to, so the app puts the control **under**
   the text once less than 120 dp is left for the sentence
   (`docs/architecture.md`, "Settings"). It is the one place on that screen
   where the app answers a question the drawing does not ask, and it is worth
   drawing so that nobody invents a third answer.

## Where the screens live in the code

| Screen | Module |
| --- | --- |
| Roll, result sheet, picker, tray | `feature/roll` |
| Outcome graph | `feature/graph` |
| Saved rolls, editor | `feature/saved` |
| Dice sets, set detail | `feature/sets` |
| Table picker | `feature/tables` |
| Face designer | `feature/designer` |
| Statistics, history, sessions | `feature/stats` |
| Settings, menu, notation reference | `feature/settings` |
| Shared widgets, **and the design tokens** | `ui/common` |
| Palette, type and shapes as Material's roles | `app/src/main/kotlin/de/drehtuer/dinfinity/theme` |

A test in `ui/common` reads `styles.css` and proves the tokens still agree with
it, so the two sides of the specification cannot drift apart silently.
