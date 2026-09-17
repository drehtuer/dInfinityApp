# TODO

The implementation plan. Steps run in order; within a step the boxes are
roughly ordered too. **Finished items are deleted, not ticked** — git history
and `docs/STATUS.md` are the record, and a step that is wholly done is
removed. Keep the file a plan, not a diary.

Everything here builds the specification in `README.md`, `docs/` and the
prototype in [design/](../design/). Where a step says *device*, it cannot run
on CI (see `.claude/CLAUDE.md`).

## Step 2 — CI

Everything a machine can check, on every PR. Emulator and device suites are
Step 5 and stay off CI.

- [ ] Re-enable CodeQL's `java-kotlin` analysis once the bundle supports Kotlin
      2.4.20 — the matrix entry is commented out in `.github/workflows/codeql.yml`
      with the build steps kept ready. **Tried for real against bundle 2.27.0
      (2026-09-16): still refused**, with the same
      `Kotlin version 2.4.20 is too recent` on `:core:model:compileKotlin`.

      **And the check this used to prescribe now lies.** A `v_2_4_20` *does*
      exist in `java/kotlin-extractor/src/main/kotlin/utils/versions` on
      `github/codeql`'s default branch, so listing that directory says yes while
      the shipped bundle says no — the shim is written but has not reached a
      release the action downloads. The repository's own tags are the query
      packs' (`v1.x`) rather than the bundle's, so there is no cheap way to list
      that directory *at the version the action uses*.

      So the check is now: put the matrix entry back on a branch, open a pull
      request, and read the **Analyse java-kotlin** job. It costs one CI run and
      it cannot give a false positive, which the file listing can
- [ ] *Optional:* add a `DEPENDABOT_METADATA_TOKEN` Dependabot secret so the metadata commit starts the checks by itself. Without it the automation still works, and the pull request shows an *Approve workflows to run* banner to press (`docs/build-setup.md`)
- [ ] Drop `VerifyDeviceTestResultsTask` and the `ignoreFailures` on `connectedDebugAndroidTest` once AGP stops failing runs on devices whose adb serial contains a colon (`docs/build-setup.md`)

**Done.** A pull request shows a green check per concern, Sonar decorates it
with coverage, and `v0.0.1` produced a signed `dInfinityApp-0.0.1.apk`. The
three boxes above are not blocking anything: two wait on other projects and one
is optional.

## Step 3 — Foundations

The shared layer every screen sits on. Built bottom-up, each piece tested to
completion before the screens start, because a bug here is a bug in every
screen.

**Done.** A formula is parsed, planned, simulated headless and scored from a
unit test with no UI in the picture. The last two boxes closed together: a die's
artwork now reaches the tray and is composited over its printed labels rather
than instead of them, with the two decoder-only texture checks that came with it
(`docs/dice-sets.md`, "How an atlas reaches the tray"); and a roll driven by a
recorded shake was shown on the Pixel 10a to replay to itself from
`FinishedThrow.thrown` — same faces, same step count.

## Step 4 — Screens

One section per screen. Each is a vertical slice: state, UI, tests, and the
device check it needs. The design option ids (`1a`, `9c`, …) are the labels on
the canvas — open [design/](../design/) beside the code.

- [ ] **Power-saving mode says nothing, and reads as a broken renderer.** The
      prototype has a panel for it — grey, "Power-saving mode" over "Same
      physics, no rendering. The result is identical to what the tray would
      show." The app draws no panel and no surface, so a player sees empty felt
      and a total arriving from nowhere. The first device session lost twenty
      minutes to it, convinced Filament had failed, and it is the clearest case
      of the prototype being right and the app simply not having built it
      (`docs/design-handover.md`, "What the phone showed"). The mode is read
      when the screen opens, so the screen already knows.

- [ ] **Two screens disagree about where the top of the screen is.** On the
      Pixel 10a the Roll screen's menu button sits at y≈174 px and Settings'
      at y≈64, immediately under the status bar, with its title a few pixels
      off the clock. Whatever `safeDrawingPadding` the roll screen applies is
      not reaching the screens the menu opens. Seen, not measured against a
      spec — one of them is right and the same one should be right everywhere.

Every screen follows the same four steps, so they are written out once here
rather than repeated below:

1. **State** — a `ViewModel` over the Step 3 foundations. No logic that
   belongs in `core/` leaks into the screen.
2. **UI** — Compose, Modernist tokens, matching the design option. Light and
   dark, phone widths from 360 dp up.
3. **Tests** — unit tests for the state machine; Robolectric/Compose tests for
   rendering, interaction, empty and error states; accessibility (labels,
   touch targets, TalkBack order).
4. **Docs** — update the matching document in the same PR if behaviour,
   limits or defaults move.

### 4.1 Roll screen — `feature/roll`

Home. Design `1a`–`1j`, `2a`, `3a`–`3c`, `4a`, `4b`, `6d`, `6f`, `9a`, `9c`,
`1z`. Spec: `docs/dice-notation.md`, `docs/tables.md`,
`docs/physics-and-rendering.md`.

The screen rolls. A formula is typed, validated on every keystroke, refused if
the table cannot hold it, thrown from the Roll button or a shake, simulated and drawn on
the Pixel 10a, and its total read off the faces. The picker row offers a chosen
set's dice, with a chooser under it once there is a second set installed — and
a die taken from a set that is not the default is written `brass:1d20`, so the
row can only ever write a formula that rolls what it showed.

**The formula is tapped, not filled in** (`2a`): it sits on the tray as text
with a dashed rule under it, a tap brings the field and the keyboard up, and
Enter rolls. The squiggle and the error line are in the editor where they can
be acted on; the line itself is marked in red (`6f`, `9c`).

**First launch offers all three ways in** (`9a`): throw a d20 now, go straight
to the tray, bring saved rolls in from a file or a link, or add somebody else's
dice. The last two do not dismiss it — somebody who goes to fetch something
comes back to a welcome whose count line has something new to say, and that
line now counts the sets, the saved rolls and the sessions there really are
rather than a sentence with a zero written into it.

What is below is what it does not have yet.

**From the design pass of 2026-09-17** (`docs/physics-and-rendering.md`, "What
is drawn over the table"):

- [ ] **Put the controls on plates.** The screen keeps its shape — one
      full-bleed table, everything floating on it — and every control over it
      becomes an opaque `--color-bg` plate with `--shadow-sm`, no radius, no
      border, 7 / 11 / 8 dp of padding, hugging its content. That fixes three
      things at once: the formula's dashed rule stops running the width of the
      screen and becomes an underline again, the picker and "Save a roll" stop
      sitting on bare felt, and **accent stops touching felt anywhere**, which
      is what makes six accents over a shelf of tables safe without checking
      thirty pairs
- [ ] **Build the counting plate.** Across the bottom: `COUNTING` kicker, the
      count in tabular figures, `of 20 read`, the still-possible range
      right-aligned with a `+` in accent-700 while a chain is open, and a 3 dp
      progress rule. It replaces the line of text currently sitting where
      "Rolling…" used to be — the most important unstyled thing in the app
- [ ] **Two roll states on that same plate**: *another throw earned* (`Throw 3
      more` / `Stop the chain`) and *could not settle* (`Throw those 3 again` /
      `Cancel the roll`). Both are states the screen already reaches and
      neither has a design until now; `rollState` in the prototype shows them
- [ ] **Mark the dice of a later pass** — 4 dp accent-700 outline and a
      `pass 2` label in the `dropped` slot — so a total counting twenty dice
      over a table holding three explains itself on the felt
- [ ] **Draw the total once.** Today the result is the total at ~42 dp centred
      on the felt *and* again at the right edge at x ≈ 376 dp of 411, where it
      looks clipped. The design has one result sheet; the second copy goes
- [ ] **Stagger the spawn**, 85 ms between dice, with the result sheet waiting
      `min(2400, 950 + (n − 1) × 85)` ms for the last landing. The prototype's
      collision shove is **not** to be ported: a settled die moved by another
      die is physics, a settled die moved by code is the invisible hand
- [ ] **`6` and `9` take a trailing dot** on the felt and in the designer,
      where the app prints a bar under the ambiguous one today. A d% units
      digit is dotted and its tens pair is not; an upright number in the result
      sheet is not. It is what is printed on a die, so it is `core/glyphs` and
      the built-in set rather than a layout (`docs/face-designer.md`)
- [ ] **Table view becomes a setting**, straight down by default and 22° on
      *angled*. The camera arithmetic is already a function of one constant;
      what is new is reading a setting and re-framing without restarting a roll
      (`docs/physics-and-rendering.md`, "Rendering")

- [ ] **Revisited against the device data, and left alone deliberately.** The
      two constants do different jobs: `FLOOR_SHARE` *shrinks* and `MIN_SCALE`
      *refuses*. At the engine's cap of a hundred 16 mm d6 the shrink is 0.62,
      which is nowhere near the 0.40 floor, so the refusal a player meets is
      always the body count — exactly as this bullet suspected. `MIN_SCALE`
      would not begin refusing until **241 dice**, two and a half times the cap.

      What the Step 5.3 sweep says about the shrink it does apply: at a hundred
      dice everything settles but one roll in sixty, nothing is stacked at rest,
      nothing leaves the tray and the p99 step is 3.09 ms against 8.33. So there
      is no evidence for changing either number, and changing one on no evidence
      is how a tuned constant stops meaning anything.

      Both figures are now `TableCapacityTest` assertions rather than a
      suspicion in a plan, so raising `MAX_DICE` past 241 is noticed — it would
      make the scale floor live for the first time
- [ ] **Freeze the dice that are down and let the player re-roll the ones that are not.** The user's proposal for unstacking, and worth taking seriously: a die that has landed cleanly is finished and could be lifted off the mat and shown as an overlay, leaving only the stuck ones in the tray to be thrown again. It keeps the honest rule — a settled die is never *moved*, only taken out of play once its face is read — and it turns the worst case from "the app fixes it invisibly" into "you roll again", which is what a person does at a table. Needs the design for how ninety-nine finished dice are shown; the mechanism can be decided first (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice")
- [ ] *Confirm on the phone:* a roll stranded by losing its surface is fixed (a roll now asks for frames with nowhere to draw), but whether that was what left `100d4` on "Rolling…" for ever is unproven — the physics settles that throw headlessly on eight seeds, so the hang was never in the engine
- [ ] *Judge a second shake on the phone:* a shake at dice still in the air now keeps them moving rather than doing nothing — it starts no throw, and its moments are numbered on the running roll's clock so they reach it at all, which is what was actually broken (`docs/physics-and-rendering.md`, "Shake input"). Decided along the way: a second shake is **more of the same roll**, not a throw that replaces it, because the dice are the ones already tumbling. Whether that reads as the dice answering the hand, and whether a roll can now be kept going longer than anybody wants, needs a phone
- [ ] Judge the pinch and the pan on a phone: whether `TrayView.CLOSEST` (four times in) is far enough to settle an argument about a face and near enough that the table has not gone, and whether a two-finger drag feels like moving the table rather than the camera. The arithmetic is tested; the feel is not testable (`docs/physics-and-rendering.md`)
- [ ] **Wire the one-finger touch to a hand re-throw.** The two decisions underneath it are built and tested: `TrayPick` (`render/filament`) says which die a finger is on, and `PickUp` (`core/notation`) says which dice a hand may go near — a group carrying `!` or `r n` offers none, because a die another die was thrown because of cannot be thrown again without the roll holding a die nothing asks for. The throw itself is the one an explosion already makes (`ThrowSpec.among`), so there is no second path to a number to build. What is missing is not code: it is **what the history says about a roll a die was thrown again in**, under "Open questions" below. Until that is answered the gesture stays unspent (`docs/physics-and-rendering.md`, "Picking a die up and throwing it again")
- [ ] *Judge the picker row on the phone:* the built-in set offers ten dice, and ten at a touch target worth pressing do not fit across a 360 dp screen, so the row scrolls. Whether that reads as "there are more dice over there" or as "the d20 is missing" is not something a test can answer — and the d20 is the die most people want (`design/dInfinity.dc.html`, option 1h)
- [ ] **Decided: braced notation, so a set's own dice can be typed and picked.**
      Plain notation spells `dN`, `d%` and `dF`, so `skull-d6` has nothing a
      formula could carry and `DicePicker.offeredBy` filters the row down to
      `StandardDieIds` for exactly that reason. Decision 31 refused
      `brass:skull-d6kh1` because a die id and a modifier are made of the same
      characters; **braces close the id before the modifiers start**, and
      braces are the one bracket the grammar does not already use (`[` and `]`
      are the label).

      The form is `{` then an optionally set-qualified die id then `}`:

      | written | means |
      | --- | --- |
      | `3{skull-d6}kh1` | three of the die whose own id is `skull-d6`, keep highest |
      | `3{brass:skull-d6}kh1` | the same, said to come from the set `brass` |
      | `3{skull:d6}kh1` | the `d6` **of the set `skull`** — valid, and a different thing |

      The colon inside the braces is the set separator the grammar already has
      (`brass:1d20`), so the third row is not a special case; it is what that
      spelling has always meant. Worth knowing because the die in
      `docs/dice-sets.md` is `skull-d6` — an id with a hyphen in it, in a set
      called `brass-and-bone` — so the first two rows are the ones that reach it.

      It survives decision 31's real objection, which is that the parser may not
      consult installed sets: the field re-validates on every keystroke on a
      thread that has never seen storage. A braced id is **lexed** without
      knowing whether it exists, and resolution is left to `DieResolver`, which
      already asks the catalogue.

      What it costs is that notation grows a second way to name a die, and
      notation is the specification: the grammar, `FormulaParser`,
      `NotationReference` (whose every example is parsed by a test), the
      breakdown, the history, saved rolls and every collection file anybody has
      already written

- [ ] *Judge power-saving on the phone:* it throws and reports with no tray on screen, but the screen it leaves behind is the formula, the picker and a total with nothing above them. The design shows a short progress indicator and a result sheet in the tray's place (`1z`); whether the gap reads as "instant" or as "broken" needs eyes
- [ ] *Device:* the whole of Step 5 hangs off this screen

**Done when** every example in `docs/dice-notation.md` can be typed, rolled
and read here, and the same seed gives the same result with the renderer on
and off.

### 4.2 Outcome graph — `feature/graph`

Design `1k`–`1m`, `2c`, `7a`. Spec: `docs/probability.md`.

The screen is built: the bar chart with its mean line and ±1σ band, the
`P(= k)` / `P(≥ k)` question, a tap for the exact numbers, the six statistics,
and the roll that opened it marked in the accent. It is reached from the roll
screen's **See the odds**, and under the chart are the two things to do with a
formula whose odds you have just read: **Roll this** hands it to the tray, and
**Save as roll** opens the editor with it already typed. Both carry what is in
the field rather than what the screen opened with, and neither is offered for a
formula there are no odds for.

- [ ] *Judge the chart on the phone:* whether a hundred and ten bars at three dp each reads as a distribution or as a smear, and whether the ±1σ band behind the bars is visible enough to mean anything in both themes
- [ ] The ledger (`1m`) and the stepped area (`1l`) are alternative presentations of the same numbers; `1k` is the default and the other two are not v1

### 4.3 Saved rolls — `feature/saved`

Design `1n`–`1p`, `1r`, `6e`, `7b`, `9b`, `9d`, `9f`, `9g`. Spec:
`docs/dice-notation.md` (Saved rolls).

The tables, their migration and the repository over them are built and tested:
groups nest one level and nothing can make them nest deeper, a roll always has
somewhere to be, and deleting a group moves its rolls rather than deleting
them. What is left is the screens.

The list is built: the group switcher, the row-style list in favourites-first
order, the warning on a roll whose dice are gone, the empty state, and tapping
a roll to send its formula to the tray. Groups can be made, renamed, moved and
deleted, from the switcher or from the editor — the same sheet in both places.

Rolls and groups are one repository each now, joined by `SavedRollLibrary` for
the screens that need both — the split the class size had been asking for, and
the place the table fallback below has to live.

A collection comes in from a file, a link or a **git repository**, and the
three are one path: `InstallSource` says which repository a URL means, the same
downloader fetches it under the collection's own megabyte, the same hardened
extractor unpacks it, and the one `*.dinfinity.json` at its root goes through
`CollectionReader` like anything else.

- [ ] The editor offers ten emoji as icons. The design has an icon pack; whether one is worth drawing, or emoji is the answer, is a decision rather than an omission (`docs/dice-notation.md` says "an emoji or a name from the built-in icon pack")

### 4.4 Dice sets — `feature/sets`

Design `1s`, `1t`, `5a`, `6a`, `6b`, `8c`, `9h`, `9i`. Spec: `docs/dice-sets.md`.

**Every kind of source can be checked for updates.** A forge is asked which
commit its ref is at; a plain archive is asked, with a `HEAD`, what its server
says about the file. A set installed before the second was recorded, or against
a server that has stopped sending what it once sent, is *unanswerable* rather
than badged — re-downloading a set that has not changed is the one wrong answer
here (`docs/dice-sets.md`, "Updates").

A download shows how far it has got and can be stopped (`9i`): the bar is drawn
from bytes that arrived rather than from what the server claimed, it is there
only while something is on the wire, and Cancel stops the download and says
nothing about it.

The screen is built: the installed list with each set's status and a long-press
to disable or remove it (`5a`, bundled set protected); the details behind a tap
(`6a`) with author, licence, source and commit, the dice the set defines, and
set-as-default; the validation report standing where the dice grid would be for
a package that stopped validating (`6b`), its folder kept so an update can fix
it; and installing **from a file or from a pasted `https` link** through the
validator, where a rejection lists every error (`1t`) and a download that never
arrives is refused the same way. Database version 4 holds which sets are
switched on.

**"My dice" is an ordinary package** (`8c`). The drawings on the phone are
built into `dicesets/mine/` whenever the folder is read and a drawing has
changed, so the list, the details screen, the notation and the remove button
all treat it like anything else. Its details screen is the one place that is
different: it offers the package as a zip, **shut until a licence has been
chosen**, and the choice is written into the file and into the installed folder
alike. What goes out is validated first, by the same validator a download goes
through.

**From the design pass of 2026-09-17** (`docs/dice-sets.md`, "Weight,
translucency and size, as a person sets them"):

- [ ] **A `translucency` field**, per cent and clamped, in `defaults` and per
      die, through the validator and `DieMaterial` and into the die material —
      with the **numerals held opaque** whatever it is. It is the one of the
      three that the format does not already have a spelling for
- [ ] **The Physical block on a set's detail screen** — weight in grams,
      translucency in per cent, size as a percentage of the average die.
      Weight is `density × volume`, and the volume is the one the solver
      computes for a body's mass and nobody has ever asked it for, so this
      needs a way to ask
- [ ] **Steppers on "My dice"**, at 0.1 g / 5 % / 5 %, each reading the live
      value so a rapid run of taps accumulates. Imported sets show the same
      three figures and no steppers, because their numbers came out of somebody
      else's `diceset.toml`
- [ ] **Size is 50–150 % of average** where the format clamps `size_mm` to
      8–40. The tighter bound belongs to the slider rather than to the file

- [ ] *Done, and worth knowing where:* a malicious archive is refused at every layer and a failed install leaves nothing behind. `SafeExtractorTest` has the paths that climb out, the absolute and Windows paths, the symbolic links, the entry count and the zip bomb refused at the megabyte it becomes obvious; `PackageInstallerTest` has the failed, hostile, interrupted and unwritable installs, each leaving nothing behind and each leaving an existing package alone; `dicesets/format` has the set files that lie about themselves and the images that are not images; and `HostileArchiveTest` joins them up over a real HTTPS server now that an archive can arrive from a link. A malicious **texture** is covered too, now that there is a decoder: `InstalledArtworkTest` has the paths that climb out of a package and the file over the cap, each refused before a decoder sees it, `AtlasDecoderTest` has the image refused from its bounds with nothing decoded, and `AtlasDecoderDeviceTest` has the file that passes the header check and will not decode — on a device, because Robolectric hands back a fake bitmap for bytes it cannot identify

### 4.5 Table picker — `feature/tables`

Design `1u`, `9j`. Spec: `docs/tables.md`.

Precedence is done: a saved roll's pin beats its group's, which beats the app
default. The group sheet has the field the model and the schema had been
waiting for, the rule is settled where the roll and its group are both in hand
and travels with the throw, and the tray is retold whenever the table changes.
A throw from the saved-rolls *list* carries no pin because it carries no
attribution — that tap fills the field and the player throws it.

The screen is built: every look from every installed package in one list —
tables are global, so a set never brings its own along — with the chosen one
marked, the package named beside a look only when more than one supplies
tables, and a thumbnail of the tray that look makes. Choosing one writes it to
the settings and the tray is built on it the next time it is opened, which is
immediately in the only sense that matters. A pin whose package is gone shows
the look the tray would really use, and the setting is left alone in case it
comes back.

**The thumbnails are built.** Each row is a picture of its own tray — the real
box mesh, lit the way the roll screen lights it, with a d20 standing in the
corner of it — drawn by `FilamentDiceRenderer` over `Stage`, on the roll
thread, with the engine that already outlives every visit (decision 60). What
decides *what a picture is of* is plain Kotlin with JVM tests, and what a
device answers is only whether there is a picture at all. The swatch stays as
the fallback, for a look whose picture has not arrived, for a driver that will
not read a frame back, and for power-saving mode, which creates no engine on
any screen.

- [ ] *Judgement, on a phone:* whether the thumbnail reads as a table at 44 × 64 dp. The camera is as close as a pinch may go, in the far corner, which is what makes a 16 mm die big enough to recognise — but whether two walls, a rounded corner and a d20 in a box that size is a *picture* or a smudge is not something a test can say (`docs/tables.md`, "Thumbnails")

### 4.6 Face designer — `feature/designer`

Design `1v`, `4c`, `8d`. Spec: `docs/face-designer.md`.

Drawing is built: the canvas with the face's outline masked in, marks stored as
vectors in fractions of the canvas, the guide under them that can be turned
off, three pen widths and an eraser, undo/redo and clear per face, the twelve
presets, and the face strip. The **bucket** adds a region rather than flooding
pixels — the smallest closed stroke the tap is inside, or the face — and fills
sink under the ink; **copy and paste** merge a turned or mirrored copy onto
another face in one undoable step, the turn being a whole step of the cell's
own symmetry; the **colour picker** goes past the twelve presets in hue, depth
and brightness, and the ink it makes is opaque and round-trips through the
draft file (`docs/face-designer.md`). **Drafts are on disk** — one file per
die, written after every stroke and read back when the die is opened — so a
drawing outlives the screen and each die keeps its own. That made the "start
over?" question unnecessary and it is gone: changing die no longer loses
anything. **Roll it** hands the tray the die being drawn — the die as its set
defines it, since nothing puts an atlas on one yet, and absent rather than dead
for a die plain notation cannot name (decision 31). The d4's
three-numbers-per-corner rule is **derived rather than checked** — a cell's
numbers are read from the corners it meets, so two cells sharing an edge cannot
be made to disagree along it.

**The export is built.** A drawing becomes an atlas at 256 px per cell in the
shape catalogue's own grid, with the cells nobody drew on left out so they stay
transparent, and a generated `diceset.toml` beside it; what decides where
things go is plain Kotlin and only the painting touches a `Bitmap`
(`docs/architecture.md`, decision 55). The package is validated before it is
written and again before its zip is offered, the licence is asked for first
(`8c`, in 4.4 above), and the file leaves through the share sheet the way an
exported collection does.

- [ ] *Judgement, with a finger:* the bucket calls a stroke closed when its
      ends come back within 0.08 of the canvas of each other, and fills the
      smallest shape the tap is inside. Both numbers are guesses about how
      accurately somebody draws on glass; whether a loop somebody meant to
      close is treated as closed, and whether the region that fills is the one
      they meant, can only be told by drawing on a phone
**From the design pass of 2026-09-17** (`docs/face-designer.md`):

- [ ] **Confirmed the other way: build the Solid tab.** The hand-over asked
      whether "Roll it" was the preview and the answer is no — the design wants
      the die in the hand, generated from the solid rather than modelled, each
      authored face mapped onto its real face, spinning until a drag takes over,
      the whole stage one drag surface with nothing on the die selectable, and
      the selected face outlined in accent-700 over a 16 % tint
- [ ] **Number the faces in opposite pairs summing to n + 1.** "Fill all with
      numbers" follows the pairing rather than the face order, and so does the
      built-in set. **It changes what a recorded roll reads back as**: the same
      seed puts the same face up and that face now carries a different number,
      so replays and per-face statistics taken before the change do not compare
      with ones taken after
- [ ] **The d10's tenth face prints `0`.** Its value stays 10 everywhere a
      total, a graph or a statistic is concerned; it is what is printed that
      changes, so a d10 beside its tens die reads as the percentile pair it is
- [ ] **`Fill all with eyes`** on a d6 — the standard pip patterns on a 3 × 3
      grid at `96 / 160 / 224`, `r = 24`, drawn in the canvas, the solid and the
      strip thumbnails, mutually exclusive with numerals, with `Clear eyes` to
      undo it
- [ ] **`Save to set` instead of a save**: a sheet listing the writable sets —
      never an imported one — plus a field that names a new personal set,
      created at average weight, translucency and size and in the picker
      immediately
- [ ] **The trailing dot replaces the bar** under an ambiguous `6` or `9`,
      here and on the tray. The rule that decides *which* numbers are marked is
      unchanged and still derived (`docs/dice-sets.md`, "Labels")

### 4.7 Statistics — `feature/stats`

Design `1w`, `5b`, `5c`, `8b`, `9e`. Spec: `docs/statistics.md`.

Rolls are recorded now — history rows, face counts and running summaries, one
transaction each — so these screens have something to read.

The list of every die thrown is built, with its average; choosing one opens
its overview tiles — natural highs and lows, average, throws — and its face
histogram against the fair line. It filters by set (`5b`) and rolls up across
sets (`5c`) with the fair line weighted by how often each die was thrown.
Forgetting one die's record or everything is there, each behind a confirmation.
Every saved roll's own totals sit against the exact distribution it was rolling
against (`8b`, `9e`), with the drift judged against the standard error rather
than shown bare. It exports as JSON or CSV through the share sheet: one row per
face in the flat form, and the per-die runs added in the full one, because a
run is the one thing face counts cannot give back. The list can be put in
three orders — most recently used, most thrown, highest average — with recency
the default, because that is the die somebody came about.

### 4.8 History — `feature/stats`

Design `1x`. Spec: `docs/statistics.md`.

The list is built: past rolls newest first, a tap to open one breakdown, every
die including the dropped ones, the set a roll fell back to, corrections
counted, and a natural maximum in the accent. It cuts to one session or one
saved roll, with a chooser that is not drawn until there is more than one thing
to choose between. It exports as JSON or CSV through the share sheet, carrying
everything the filter matches rather than the page on screen, and forgets those
same rolls — a session's or a saved roll's — behind a confirmation that says
what stays as well as what goes. No replay and no
seed, which the types enforce rather than the screen remembering — the export
is built from `HistoryEntry`, which has none to write. Pruning at 50,000 rows
was already done and tested in `StatisticsRepository`.

### 4.9 Sessions — `feature/stats`

Design `6c`. Spec: `docs/statistics.md`.

Built: database version 3 and its migration, the list with its roll and
natural-high counts, tap to activate, rename, create, and delete that moves the
rolls to the first session rather than deleting them. The active session is a
preference and every roll is filed under it; the history filters by it, and the
menu's header names it once there is more than one session to be in (`1q`).
Database version 5 puts the session on every face count, so the **statistics**
filter by it too — stored rather than recomputed, because a scan of fifty
thousand history rows on every draw is the thing a column is for.
`die_summary` deliberately did not grow one: counts add and streaks do not
(`docs/statistics.md`, per session).

### 4.10 Settings and menu — `feature/settings`

Design `1q`, `1y`, `2d`. Spec: `README.md`, `docs/architecture.md`.

The menu is built and **the navigation graph is connected**: every screen
carries the same menu button and the menu reaches every screen
(`docs/architecture.md`, "Screens and the states behind them").

Notation is a screen, in the App section beside Settings: the grammar in
sentences, with an example on every line that puts that formula in the tray.
It has no state — it is `NotationReference`, which lives beside the parser, with
a layout on it — and a test parses every example it offers, so it cannot show a
formula the app would refuse. That was the last row the prototype's menu had
and the app did not.

A screen can no longer be built and left unplugged. `Presenters` holds a
factory per screen with no optional fields, so adding a destination stops the
activity compiling until it says how to build one; the same object is what the
tests wire, and one of them walks every menu destination and fails on **any**
placeholder — the list of screens that had not been written is empty now, so
the assertion is simply that none of them draws one. Both halves were checked
by putting the original bug back: removing the sessions screen's dispatch turns
the list into `[sessions]`.

Appearance, the accent, shake, haptics, sound, the default rounding, power
saving, the version and the repository link are all there, and each of them does
something. Haptics and sound are one section with two switches, because they are
one answer to one question and because with both off a roll records no impacts
at all — the pair is what the saving is measured against
(`docs/physics-and-rendering.md`, "Impacts, haptics and sound").

All three defaults now behave the same way, which was the point of the item
that used to be here: the default **set**, the default **table** and the active
**session** are each chosen where the thing itself is, remembered with the
settings, and fall back when what was chosen is not there. The set and the
table fall back while leaving the setting alone, so re-installing the package
restores the choice; the session falls back where a roll is *recorded*, because
a session deleted while another screen was in front would otherwise strand
every throw filed under it (`docs/statistics.md`, per session).

The **developer toggle** is the last thing on that screen, and it is the one
setting that is off on every install. It adds a debug overlay over the tray, a
Developer row in the menu, and behind that row an anomaly log and two ways of
throwing the last roll again. What it does *not* do is the point: the history
still has no replay and still never shows a seed, and the exports have no
column for one, because neither type has a field the toggle could unhide
(`docs/architecture.md`, decisions 13 and 56;
`docs/physics-and-rendering.md`, "Debug tooling").

## Step 5 — Physics and rendering on a real phone

**Reserved, and none of it can run on CI.** This is where the app either
convinces or does not: a roll has to look like dice landing, not like an
animation of a random number. Runs first as soon as 4.1 renders, then again
after every physics change.

- [ ] **Nothing automated can see the printed numbers, and they were drawn
      reflected for the whole of `v0.1.0`.** Fixed — `MaterialBuilder.flipUV`
      defaults to `true` and was turning `v` over a second time — but fixed
      with a photograph, and a photograph is not a test.

      What makes this hard to test is what made it hard to find: **a
      reflection in `u` and a reflection in `v` differ by a half-turn, and a
      die lands at an arbitrary orientation**, so a screenshot cannot tell them
      apart. Ruling the fix in took a dump of the atlas as text (upright), a
      reading of the transform chain (no negative determinant anywhere) and
      then trying it on the phone.

      **The instrument this wants:** an instrumented test in `render/filament`
      that puts one die where the camera is aimed, turns it so a chosen face's
      normal is `-forward` and its texture-up is the camera's up — so the glyph
      is square to the screen and centred — reads the frame back through
      `Snapshot`, and asserts the ink is heavier in the half of the frame that
      `DieNumbers.fieldOf` says is the heavier half of that cell. That catches
      a flip in either axis, it needs no golden image and no projection
      arithmetic, and it is what `docs/architecture.md` decision 40 asks for,
      applied to the one part of the pipeline it was never applied to.

      It needs a way to build a quaternion from two orthonormal frames, which
      `Quaternion` does not have yet.

- [ ] **`TrayCamera.shotOn` calls `cross(up, forward)` `right`, and it is
      left.** Screen-right is `cross(forward, up)`; the code has the operands
      the other way round, so the vector is negated. It is harmless today
      because its only use is inside an `abs()` in the framing solve, which is
      why nothing caught it. Fix it with the reflection above, since anyone
      reading that file while hunting a handedness bug will stop here first.

### 5.1 Harness

**Built, and it runs on either tier.** `tools/harness.sh` rolls N throws
headlessly on the emulator or the phone, pulls back a JSON document — settle
time in steps and in milliseconds with median, p99 and worst case, corrections,
post-rest corrections, re-throws, forced settles, dice left standing on another
die, the deepest die–die overlap and the per-step wall time — and prints a
pass/fail table against the targets below (`docs/build-setup.md`, "The physics
harness"). The emulator is a minute away, so a regression need never reach the
phone.

Everything it *decides* is plain Kotlin in `simulation/harness` and is tested on
the JVM: what a run was asked for, the percentiles, the shares, the document and
the comparison. The device only rolls, times and writes two files
(`docs/architecture.md`, decision 53). It **fails** on the targets the engine
misses today, which is the plan being behind the check rather than the check
being wrong.

### 5.2 Fairness and determinism

**Done, on the Pixel 10a.** Every catalogue shape at 100,000 rolls: seven pass
chi-squared at p > 0.001 and their χ² sums to 57.63 against 55 degrees of
freedom, which is as close to "exactly as fair as chance predicts" as a number
gets. Every shape, the d18 included, keeps every face within 1 % of its share —
its worst is 0.389 %.

The **d18 is held to the worst-face bound and not to chi-squared**, which is a
decision taken rather than a check skipped: its resting basins are narrow enough
that the float32 hull's own rounding biases it, and Jolt stores hull points in
single precision whatever else is configured, so no single-precision engine can
do better for that solid. It is one shape by name in
`FairnessTest.HELD_TO_THE_FACE_BOUND`, its χ² is still printed every run, and
the app says nothing about it to the player — a number nobody can act on, about
a die fairer than the plastic one in their hand, is not worth a warning
(`docs/physics-and-rendering.md`, "The bar the d18 is held to").

- [ ] Identical outcomes for identical seeds across JVM, emulator and device — any divergence is a release blocker. The golden suite is the check and already holds for its ten cases on both ABIs; Step 5 is the same claim at ten thousand rolls and on a second phone
- [ ] **Done, on the Pixel 10a.** `ModesAgreeTest` runs every golden case both
      ways — `runToEnd`, which is power-saving mode stepping as fast as the
      processor allows, and `advance` once per displayed frame, which is the
      drawn tray — and they agree on the faces, the step count and the number of
      dice corrected. A second case runs the drawn side at a frame rate that
      keeps changing (a dropped frame, a long one, two quick ones), because that
      is what `FrameClock` exists to absorb and what would show if any of it
      reached the solver. Both were checked by making them fail, so the
      comparison discriminates rather than comparing a thing to itself

### 5.3 Capacity and corner cases

- [ ] **Swept on the Pixel 10a, and two of the eight counts do not settle.** One
      harness invocation per count — 200 rolls each up to 20 dice, 60 each above
      it:

      | dice | corrected | re-thrown | median | p99 settle | hit the cap | stacked at rest | post-rest | deepest overlap | p99 step |
      | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
      | 1 | 47.50 % | 1.00 % | 0.53 s | 0.72 s | 0 | 0 | 0 | 0.000 mm | 0.92 ms |
      | 2 | 44.75 % | 1.25 % | 0.58 s | 1.02 s | 0 | 0 | 0 | 0.567 mm | 0.33 ms |
      | 5 | 41.60 % | 1.90 % | 0.65 s | 1.27 s | 0 | 0 | 0 | 6.148 mm | 0.75 ms |
      | 8 | 42.19 % | 2.19 % | 0.68 s | 1.31 s | 0 | 0 | 0 | 6.010 mm | 1.15 ms |
      | 20 | 43.55 % | 3.50 % | 0.81 s | 1.83 s | 0 | 0 | 0 | 9.019 mm | 0.88 ms |
      | 40 | 48.46 % | 6.21 % | 1.29 s | **12.00 s** | **1** | 0 | 0 | 9.741 mm | 2.45 ms |
      | 60 | 46.36 % | 5.64 % | 1.29 s | 3.23 s | 0 | 0 | 0 | 7.245 mm | 2.44 ms |
      | 100 | 45.37 % | 6.32 % | 1.71 s | **12.00 s** | **1** | 0 | 0 | 7.171 mm | 3.09 ms |

      **What holds everywhere, including at the cap: zero dice at rest on
      another die and zero post-rest corrections.** The rule that matters most
      does not weaken with the count, and no roll produced a NaN or lost a die
      through a wall. The p99 step climbs to 3.09 ms at a hundred dice, which is
      well inside the 8.33 ms a 120 Hz step has.

      **What does not hold:** one roll in sixty at 40 dice and one in sixty at
      100 runs out of the twelve-second cap, so "all settle" is false and the
      forced settle is what ends those throws. Re-throws climb with the count
      too, 1 % at one die to 6.3 % at a hundred. Both belong to 5.5
- [ ] **A single die is corrected 47.5 % of the time, which cannot be about
      stacking.** One d20 in an empty tray has nothing to be supported by and no
      other die to overlap — the sweep measures 0.000 mm there, which is also a
      neat proof that the overlap figure really is die-on-die only. So every one
      of those corrections is the *cocked* half of the trouble check firing on a
      die that is still rolling to a stop. It is the strongest evidence yet that
      the 43–48 % is a threshold that fires too early rather than a crowding
      problem, and it says where to look: `TroubleCheck` asks whether a die
      **would** read cocked if it stopped now, fifty milliseconds after it began
      to look that way, which is well before a d20 has finished toppling from
      edge to face
- [ ] **`100d4` does not reliably settle, and never did.** The d4 is the worst case by some way — it cannot rest flat on another one, so a heap of them has no stable packing. `JoltBridgeTest` used to try eight seeds and pass; twenty-four seeds show **five running out of the twelve-second cap**, and the same twenty-four under the correlated spawn streams that preceded them showed two — a difference well inside noise at that sample size. What changed is not the physics but the sample: the eight were the easy ones. Nothing is ever touched after it has come to rest, on any seed, which is the rule that matters; the cap firing at all is a prevention problem (5.5), and the bound in the test is today's worst case written down rather than a target
- [ ] **Decide what a tilted phone should mean.** Deferred, not answered. The table is horizontal now and the gyroscope no longer turns the world, which is what stopped the dice pouring into a wall — but "tilt the phone and the dice slide" was a real idea and this is not a verdict on it. The direction is still recorded with every sample, so whichever way it goes the data is there. The three answers, unchanged: gravity always straight down and only the hand moves the dice; anchor to `TYPE_GRAVITY` and accept that a phone held upright pours everything to the bottom wall; or keep a tilt and clamp it so a tray can lean without becoming a chute
- [ ] **A shake along the phone's long axis still drives the dice into one end.** Seen as dice stuck at the bottom after a vertical shake. The table being horizontal fixes the *pouring* — the tray no longer leans — but the hand's own force still points that way, and a hundred dice pushed at one wall have nowhere else to be. Whether that is right (it is what a hand does) or wants shaping is a Step 5.6 question with a phone in it
- [ ] **Done, on the Pixel 10a, bar the two that need a person holding the
      phone.** `ExtremeInputTest` drives twenty dice with a sensor pinned at its
      maximum (every axis at `Double.MAX_VALUE / 2`, which is a broken
      accelerometer rather than a hand, and what `ShakeDriver`'s clamp exists
      for), thirty seconds of shaking, a phone turned through all three axes
      while the dice are in the air, and a shake that ends in free fall. Each
      one still reads a face for every die, leaves every die on the table,
      produces no position that is not a number, and gives the same answer
      twice.

      The thirty-second case also asserts the bound that makes it safe: a roll
      is force-settled at twelve seconds, so the record keeps 1,440 moments and
      refuses the rest rather than growing for as long as an arm does.

      **Upside down was done earlier and was broken** — the roll screen pinned
      the display to the rotation it opened at, so `PhoneAxes` was told the
      phone was upright while it was shaken the other way up. The screen holds
      its shape rather than its rotation now (`docs/tables.md`); a quarter turn
      is still refused. What is left is *vertical* and *upside down* with a real
      hand, which no test can hold
- [ ] **Done, on the Pixel 10a, and it found a roll nobody could stop.** A
      call, the home button and the lock screen all reach the app as the screen
      stopping, and `InterruptedRollTest` drives all three at a throw in the
      air: the dice land, and they land *while the app is away* rather than
      when somebody looks again. That last one is the assumption the design
      rests on — the frame callback is what steps the roll, so a backgrounded
      process that stopped getting vsync would be a roll frozen on "Rolling…"
      for good — and nothing had ever checked it.

      **What it turned up:** the tray was given back by the tray *view*, which
      is on screen only when there is something to draw. In power-saving mode
      there is not, so nothing closed the tray at all: a roll the player walked
      out on ran to the end on its worker thread, reported, and was written
      into the history for a screen nobody was on. The roll screen gives the
      tray back now, whichever kind it is, and a power-saving tray hands back
      the thread it made for itself rather than leaving one behind per visit.

      A rotation is not an interruption — the activity declares the config
      changes and the screen pins its shape — and a recreated activity loses
      the roll on purpose, which is the same rule as walking away.

      **Low memory is the process being killed** and nothing survives it to be
      tested. What makes that safe is already true: a roll is written in one
      transaction or not at all, so the worst it costs is a statistic that is
      missing, never a history that disagrees with itself

- [ ] **The corner cases are asked on the phone now, and one of them fails.**
      `CornerCasesTest` throws exactly at the cap and one over (refused before a
      body exists, which is the point of doing it in arithmetic), a hundred d4s,
      a hundred coins, the largest die a set may declare at the smallest scale
      the rule allows, every catalogue shape at once, and the same forty-dice
      throw a hundred times over to watch for drift as the phone warms. All of
      them keep every die on the table; all but one put no die on top of another.

      **A hundred coins do: four to ten of them, on every seed tried.** It is
      the shape's own doing — a coin that lands on a coin is *stable* there,
      where a cube or an icosahedron rolls off, which is what makes prevention
      work everywhere else — and at that density rung 3 cannot find the stacked
      ones clear floor to be re-thrown onto. Bounded at today's worst case so
      the next change to the spawn or the ladder improves it or is noticed, in
      the same way `100d4`'s timeouts are. Nothing is touched after coming to
      rest on any seed, which is the rule that does hold.

      The thermal run is the *outcome* half only: a hundred identical throws
      come to identical faces, so nothing drifts as the phone heats. Frame times
      need a renderer and a surface, which is Step 5.7's and the open question
      about who measures a drawn frame

### 5.4 Collisions

- [ ] **Dice go 9 mm into each other, and the bar is 0.2 mm.** Measured on the Pixel 10a the first time the harness ran: 200 throws of 20 d20s, deepest die–die overlap **9.019 mm** against a target of 0.2, on dice 16 mm across. More than half a die. It is the number the plan asked for and nobody had ever had, and it is almost certainly the same fault as the correction rate below rather than a second one: dice are spawned or corrected into each other and the solver pushes them apart afterwards, which is what a 45 % correction rate looks like from the collision side. Prevention (5.5) is where it is fixed; this is where it is measured
- [ ] **Done, on the Pixel 10a, and the old check was too kind twice over.**
      `ContainmentTest` asks the tray's own bounds — half a side, not a whole
      one, which is what `JoltBridgeTest` allowed and is twice as far out as the
      wall — of every die on every step. Four things hold: a **full tray** of a
      hundred dice never puts a centre outside the walls; **nothing tunnels out
      at the hardest shake the cap allows**, driven at four gravities in a
      direction that changes every tenth of a second; dice **driven into a
      corner and held there** all stop, which is what the rounded corners are
      for; and a **settled pile stays put** — measured creep over two undriven
      seconds is 3.4 × 10⁻⁵ mm, and the test holds it to a hundredth of a
      millimetre.

      Each assertion was checked by making it fail: dice really do reach within
      about six millimetres of the walls, so the bound is exercised rather than
      merely satisfied by dice that stayed in the middle

### 5.5 Stacking and cocking — and no invisible hand

The two failures to hunt, per `docs/physics-and-rendering.md`:

- a die left resting on another, or on its edge; and
- **a die visibly moved after it stopped**, which is worse — it turns a roll
  into an arrangement in front of the player's eyes.

**Decided, and it changes the shape of this section: there is no spreading
force, because there is no invisible hand at all.** Every lever tried so far —
more collision sub-steps, a longer bias wait, clearer floor for a re-throw,
more spawn bands — is a way of making corrections work better, and a correction
is the thing this section is named after not wanting. The answer is what a
person does at a table when the dice land in a heap:

> **Count the dice that can be read, and if anything is left to throw again,
> take the read ones off the board first and throw the rest onto the room that
> makes. Repeat until every die has been counted.**

A die is either read or thrown again. Nothing is nudged, biased, popped apart
or re-placed, so the correction rate stops being a number to tune and becomes
zero by construction — and "it does not look like it cheats" stops being a
separate claim from "it does not cheat", because there is nothing left to see.
A die taken off the board after its face is read is not *moved*: it is out of
play, which is the one thing the honest rule allows (`docs/physics-and-rendering.md`).

**Being read is not what takes a die off the board — needing room for a
re-throw is.** A pass that has nothing left to throw lifts nothing, so a roll
that settles first time leaves every die where it landed for the player to look
at. That is safe because a die is only read once the whole table has stopped,
so no reading can be knocked out of date by a die still in flight, and the only
thing that could land where a read die stands is a re-throw — which is exactly
what lifts them.

It should also terminate quickly. Each pass reads most of the dice, so what is
left shrinks fast, and a heap of a hundred becomes a handful within a few
throws rather than a twelve-second fight with the solver.

- [ ] **Built, and measured on the Pixel 10a: the bar this section exists for is
      met by construction.** 2,000 rolls of 20d20:

      | target | bar | the ladder | counting |
      | --- | --- | --- | --- |
      | dice at rest on another die | 0 | 0 | **0** |
      | corrections after rest | 0 | 0 | **0** |
      | dice corrected | 0.5 % | **44.9 %** | **0.000 %** |
      | dice re-thrown | 0.05 % | 2.9 % | 2.80 % |
      | median settle | 2 s | 1.33 s | **0.78 s** |
      | p99 settle | 4 s | 2.93 s | **1.45 s** |
      | rolls out of the 12 s cap | 0 | 3 | **0** |
      | forced settles | 0 | 106 | **0** |
      | deepest die–die overlap | 0.2 mm | 11.6 mm | 9.03 mm |

      Ten of twelve rows pass where six did. The correction rate is not 0.000 %
      because anything was tuned — there is no code left in the loop that could
      correct a die — and the rows that improved without being aimed at
      (settle times, the cap, forced settles) did so because a die thrown again
      lands on a table the counted dice have left.

- [ ] **The re-throw budget is gone, and so is the cap.** A die is thrown again
      as often as it takes, because a die thrown again lands on a table the
      counted dice have left and the budget was rationing the only dice that
      still needed the room. The twelve-second cap went with it: it ended a
      roll by force-settling every die still moving and reading it off whatever
      face it was nearest, which is a number nobody rolled.

      What is left of the twelve seconds is a **visible backstop** — the roll
      says it could not finish, shows how many dice never settled, and offers
      to throw those and only those again. Measured: **2,000 rolls of 20d20 and
      not one gave up**, so it costs nothing at ordinary loads.

      The row below is therefore about something else now, and the bar wants
      re-deciding rather than hitting:
- [ ] **Decide what the re-throw budget means now.** 2.80 % against a 0.05 %
      bar is the one row that got worse in spirit rather than better, and the
      bar is the thing to look at rather than the number. It was written when a
      re-throw was the last resort after two rungs of correction had failed;
      re-throwing **is** the mechanism now, and a budget of one die in two
      thousand is a budget for something else. What is worth bounding is
      probably how *long* a roll takes and how many passes it needs, both of
      which are measured above and both of which improved
- [ ] **The rest of the mechanism.** The simulation side. Each pass: settle,
      read every die that has a face, remove those bodies from the world, throw
      what is left. The outcome accumulates across passes and the seed stream
      has to carry through them, because the whole of it is still one roll with
      one seed. **`ThrowSpec.among` already throws dice into a tray that
      already has some** — it is what an explosion does — so the throwing half
      exists; what is new is the reading-and-removing half and the loop around
      it
- [ ] **Open, and it turns out to be load-bearing: how removed dice are shown.**
      Not a cosmetic question. A counted die leaves the simulation, so the floor
      it was standing on stops being solid — and **measured on the Pixel 10a,
      dice thrown afterwards land in that space: 33 pairs of dice sharing a
      spot across 8 seeds of 20 dice, worst overlap 10.9 mm of a 16 mm die.**
      If the tray goes on drawing a counted die where it landed, that is two
      dice in one place, which is a worse thing to watch than the stacking this
      replaced.

      So "the dice stay where they landed, lifted and dimmed" is not one of the
      options — it is the one answer the mechanism forbids. What is left: a row
      along the bottom edge that grows as dice are counted; the result sheet
      filling in die by die; or the die simply leaving the tray as it is read.
      The last is the least work and the most honest about what happened, and
      it is also the biggest change to how a roll looks, which is why it is a
      decision and not a default
- [ ] **Open: does a re-throw count as a throw for the history?** Decided for
      the *hand* re-throw — the die's history keeps every throw, the roll's
      keeps the sum — and the same answer looks right here, but this re-throw
      is the app's doing rather than the player's, and a `d6` that took four
      passes to be read would contribute four faces to its own fairness figure.
      That is either exactly right (it landed four times) or a bias worth
      naming (it landed four times *because it was hard to read*)

- [ ] **Run at 20 dice on the Pixel 10a, and the bar holds: zero.** Ten thousand
      rolls, **200,000 dice**, and not one of them came to rest standing on
      another — nor was one touched after it had stopped. Those are the two bars
      this whole section exists for and they hold at a sample fifty times larger
      than anything that had been run before.

      Three things only appear at this size, and all three are worth having:
      **three rolls in ten thousand run out of the twelve-second cap** (0.03 %,
      where two hundred rolls showed none), the deepest die-into-die overlap
      grows to **11.6 mm** from the 9.0 mm two hundred rolls found — an extreme
      value climbs with the sample, so the earlier figure was optimism rather
      than a better engine — and the median roll costs 28.6 ms of wall time,
      with a worst of 450 ms.

      The correction rate is unchanged at 44.9 %, which is the rest of this
      section.
- [ ] **And at 60 dice the bar fails — 29 dice in 600,000.** Ten thousand rolls
      of sixty, and twenty-nine of them left one die standing on another. It is
      the first time this has failed for ordinary dice rather than for a hundred
      coins, and the per-roll records say what it is made of:

      | | rolls | dice left standing |
      | --- | --- | --- |
      | ran out of the twelve-second cap | 29 | — |
      | had any forced settle | 97 | — |
      | left a die standing **and** were force-settled | 20 | 20 |
      | left a die standing having **finished cleanly** | **9** | **9** |

      So two thirds of it is the cap rather than prevention: a roll that runs
      out of time is frozen where it is, and rung 3 never gets to throw the
      stacked die again. That is a timing problem, and the same one as the
      twenty-nine caps.

      **The other nine are prevention failing outright** — rolls that settled
      properly, inside their time, with a die on a die at the end. Nine in ten
      thousand is small and it is not zero, and zero is the bar.

      What holds, at 600,000 dice: **not one post-rest correction.** Nothing
      touched a die after it had stopped, which is the rule that matters most
      and the only one of these that is inviolable rather than a target
- [ ] Fewer than 0.5 % of dice need any correction — **measured 44.9 % at
      twenty dice and 47.1 % at sixty**, over 800,000 dice, which is the number
      the rest of this section is about. The second half of the claim does hold:
      **100 % of those corrections landed while the die was still moving**,
      because not one landed after it had stopped
- [ ] **Zero post-rest corrections, and it is measured rather than asserted
      now: none in 800,000 dice.** Ten thousand rolls at twenty and ten thousand
      at sixty, on the Pixel 10a. One occurrence is a bug rather than a
      statistic, and the harness fails on it — this is the run that says the gate
      has never had to
- [ ] Re-throws (the last resort) under 0.05 % of dice — **measured 2.9 % at
      twenty and 6.1 % at sixty**, so it is sixty to a hundred and twenty times
      the budget and it grows with the count. Whether each one *looks* like a
      die being picked up and thrown again is the separate half, and needs eyes
      (5.6)
- [ ] **Two of these three are met.** At twenty dice over ten thousand rolls:
      median settle **0.78 s** against 2 s, p99 **1.81 s** against 4 s — both
      comfortable. The third is not: **three rolls in ten thousand ran out of
      the twelve-second cap**, where the bar is never. At sixty it is
      twenty-nine, and twenty of those are where the stacked dice come from,
      which is what makes the cap a stacking problem rather than a patience one
- [ ] **The nine clean stacked rolls have a signature, and it is not the cause.**
      Of the sixty-dice rolls that finished properly and still left a die
      standing, every one had **five or more re-throws** — and of 6,819 clean
      rolls with four or fewer, not one stacked:

      | re-throws | rolls | ended stacked |
      | --- | --- | --- |
      | 0–4 | 6,819 | **0** |
      | 5 | 1,187 | 3 |
      | 6 | 831 | 2 |
      | 7 | 503 | 2 |
      | 9 | 155 | 2 |

      They also take twice as long (294 steps against a median of 159) and have
      twice the re-throws (6 against 3) — but **the same number of corrections**
      (29 against 28). So it tracks rung 3 and not rung 2.

      The obvious reading was that `SpawnLayout.rethrowPlacement` drops a
      re-thrown die at a **uniformly random point**, where `addedPlacement` asks
      `ClearSpace` for floor nothing is on — the same question answered two
      ways. Both repairs were tried on the phone and **both are worse**:

      | | rolls stacking | rolls at the cap | p99 step |
      | --- | --- | --- | --- |
      | today (random point) | 0.29 % | 0.29 % | 1.69 ms |
      | the clearest point | **7.4 %** | **12.8 %** | 5.31 ms |
      | a random point, redrawn until clear | 0.2 % | **2.2 %** | 1.49 ms |

      The first fails for a reason worth keeping: rung 3 can throw **several**
      dice again in the same step, and one deterministic clearest point drops
      all of them on the same patch. An explosion may ask for it because it adds
      exactly one die. The draw is what keeps simultaneous re-throws apart.

      A fourth thing was tried on the strength of that — **more height bands at
      spawn**, five rather than three, which is rung 1 and adds separation
      instead of removing energy. It is **neutral**: 47.4 % corrected against
      47.1 %, one stacked roll in five hundred against an expected one and a
      half, re-throws a shade worse at 6.7 %. The obvious stagger lever does not
      move this, which is worth knowing before somebody spends an evening on it.

      The second keeps the draw and still makes the cap seven times worse, which
      is what says the placement was never the cause. **The re-throw count and
      the stacking are both symptoms of the same crowded roll**, not one causing
      the other — so the thing to attack is why a sixty-dice roll needs six
      re-throws at all, which is rung 1 and this section's real subject. Nothing
      in the code changed
- [ ] **Measured, on the Pixel 10a: both obvious levers work, and both pay for
      it in the same coin.** Two experiments, 200 throws of 20 d20s each, base
      seeds 1 and 7, against the sixteen-seed shaken-spread check in
      `JoltBridgeTest`:

      | change | corrected | re-thrown | deepest overlap | p99 step | shaken throws that heaped |
      | --- | --- | --- | --- | --- | --- |
      | today | 43.55 % | 3.50 % | 9.019 mm | 0.91 ms | 2 of 16 (the bound) |
      | 4 collision sub-steps | 44.10 % | 2.35 % | 3.182 mm | 0.85 ms | **8 of 16** |
      | 8 collision sub-steps | 0.38 % † | 2.43 % | **1.577 mm** | 1.32 ms | **13 of 16** |
      | bias waits 400 ms not 50 ms | **0.00 %** | 2.43 % | 9.019 mm | 0.91 ms | **4 of 16** |

      † with the longer wait also applied. 16 sub-steps is *worse* than 8
      (5.965 mm), so it is not a free knob.

      **Sub-stepping collision is the overlap fix.** A 16 mm die travelling a
      metre a second crosses half its own width in one 1/120 s step, so
      discrete detection first sees two dice already deep inside each other and
      the solver's job becomes pushing them apart rather than keeping them
      apart. Four slices cut the worst overlap by a factor of three, eight by
      nearly six, and a step still costs well under a fifth of its 8.33 ms
      budget.

      **The 43.55 % is almost entirely dice that would have sorted themselves
      out.** The bias fires after 50 ms of "nearly stopped and either leaning on
      a die or reading cocked", which a tapped throw produces constantly.
      Waiting 400 ms takes it to zero and changes *nothing else* about a tapped
      throw — same re-throws, same settle times, still zero stacked and zero
      post-rest.

      **And here is why neither shipped.** Both levers make a *shaken* throw
      pack into one end, and they do it for the same reason: the spread of a
      shaken throw today is produced by corrections and by interpenetration
      artifacts rather than by prevention. The bias always pushes a little
      upward, so it is what un-piles a heap; deep overlaps pop dice apart, so
      the solver's own error was spreading them too. Take either away and the
      dice pack, because **nothing else is spreading them**. That is exactly the
      thing this section says must stop being true, and it means the next
      attempt is not a threshold but a mechanism: a shaken throw needs a real
      spreading force, and what a sustained sideways shake *should* do to a
      tray of dice is the open question in 5.6 that has to be answered first.
      The numbers above are the starting point; nothing in the code changed
- [ ] Tune prevention (spawn spread and stagger, dice-on-dice friction, throw energy, scale) until the numbers above hold without leaning on corrections. **Where it starts:** 20 d20s at the capacity rule's scale settle in 89–132 steps on the Pixel 10a, with 9 of the 20 corrected, 0–1 re-thrown and **zero** post-rest corrections. The last figure is the one that must stay at zero and does; the correction rate is 45 % against a 0.5 % budget, and bringing it down is what this task is
- [ ] *Measured, on the Pixel 10a:* 200 throws of 20 d20s, base seed 1. **43.55 %** of dice corrected against a 0.5 % budget, **3.50 %** re-thrown against 0.05 %. What passes on the same run is every honesty bar and every timing one: **zero** dice at rest on another die, **zero** post-rest corrections, zero forced settles, no throw near the twelve-second cap, median settle 0.81 s and p99 1.83 s against 2 s and 4 s, and a p99 step of 1.00 ms against the 8.33 ms a 120 Hz step has. The engine is fast and honest and leans on corrections far too hard, which is what the rest of this section is about
- [ ] **The corrections are visible at 100 dice, and they look like popcorn.** Seen on the Pixel 10a: dice stack against a wall and then *pop* apart to unstack, and individual dice jump to find a better spot. Every one of those lands while the die is still moving, so the honest rule holds and nothing touches a die at rest — but "it does not cheat" and "it does not look like it cheats" are different claims, and this is the second one failing. It is the 45 %-against-0.5 % correction rate above, seen rather than counted, and it is the argument for prevention over correction rather than a separate task
- [ ] *With the user:* frame-by-frame review of 50 recorded 20-dice rolls — nobody can point at the moment a die was helped

### 5.6 Feel — the user's call, not a metric

- [ ] Dice respond to a shake within ~100 ms, and they move the way the hand did — the tray itself never moves, because it is the screen (`docs/physics-and-rendering.md`). The direction and the dropped-force stutter are both fixed; what is left to judge is the *start*, which read as a lag on the Pixel 10a: the dice are already travelling fast when the shake begins to reach them, so the hand seems to be catching up with dice that left without it. The 100 ms start threshold and the spawn impulse are the two numbers in it
- [ ] The tumble reads as dice: bounce height, spin decay, dice rolling on an edge before toppling. **Not yet:** on the Pixel 10a the dice do not travel far enough and the tumble does not read as dice being thrown. Throw energy and spawn spread are where that is tuned (5.5), and this is the judgement that says when it is right
- [ ] *Judge the formula editor on the phone:* whether a dashed rule under the formula reads as "you can type here", and whether a keyboard over the lower half of the tray is right or wants the tray to shift up while the editor is open (`design/dInfinity.dc.html`, option 2a)
- [ ] The rim's shadow still looks wrong — the band across the top of the wall casts something that does not read as a rim. Lighting and the shadow map, not geometry, on present evidence
- [ ] Rendering polish — shader tuning, and the optimisation pass — is deliberately **last**: it is worth doing once the dice move the way they should, and worth nothing before that. Nothing above should wait for it
- [ ] **Do the haptics land?** They fire on real impacts only and the rule is asserted rather than tuned: a change in a die's speed that the step's own gravity explains is never reported, so a die sliding and a die at rest are silent by construction. What a phone has to answer is the *feel* — whether a die hitting the tray reads as a knock rather than a rattle, whether one die landing among twenty is still felt, and whether the 45 ms rate limit turns a hundred dice into a handful of distinct knocks or into one long buzz. Listen for: a single d20 landing, then `20d6`, then `100d6`
- [ ] **Do the five tables sound like their materials?** The sounds are generated rather than recorded (`docs/physics-and-rendering.md`), so this is the first time anybody hears them. Roll the same `5d6` on `felt-green`, `oak`, `dark-glass` and `plain` and say whether each reads as its surface; then roll `2d20` and `20d6` on one table and say whether the pitch difference between a big die and a shrunk one reads as dice of different sizes or as an effect. If a preset is wrong, the four numbers behind it are in `ImpactWaveform`
- [ ] **Does power-saving mode's second read as the roll?** There are no frames there, so the impacts are replayed across about a second after the dice have stopped. Whether that sounds like a throw that happened or like a sound effect played at you is the judgement — and whether a second is the right length
- [ ] Settled faces are legible at arm's length without zooming. The *size* is settled — 16 mm reads fine on the Pixel 10a — and the numbers are drawn now. What is left to judge is one number: `DieNumbers.FACE_SHARE`, how much of the room a face has a numeral takes up. Everything else about the size is solved from the face itself, so this is the only knob and it moves every shape at once. The d4's three-to-a-triangle (`CORNER_HEIGHT`) is the second question, and the d18 is the third — its kites are long enough that its numbers are a third the size of a d6's, which is the shape question already open below
- [ ] Power-saving feels instant and gives the same answer
- [ ] *Judge an exploding roll on the phone:* `8d6!` now **waits** between links of the chain — the six earns a throw and the screen asks for a shake, rather than the app throwing it. Whether that reads as "your turn again" or as the roll having stalled needs a hand and eyes. **It works, and was seen working**: `4d6!` came up `6 6 1 1 3 5` on the Pixel 10a, six dice on the tray for a throw of four, none of them on top of another, total 22. What is left is not whether it happens but how it reads. Three things need eyes. **Does the wait read as part of the roll** — a die lands, a beat, another die drops — or as the app having stalled? **Does the added die look thrown**, given that it is dropped from 25 mm straight down rather than hurled like the first eight? And **does it ever appear to pass through a die already lying there** on its way to a stop: it cannot touch one, because there is no body for the settled dice in its world, so if it *looks* as though it did, the drop point is too close and `ClearSpace` is the number to move (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds")
- [ ] *Judge a chain that fills the tray:* roll enough exploding dice that the tray runs out of clear floor. The sheet now says which of the two ways the chain ended — "Exploding stopped at 20 dice." or "The tray had no room for another die." — under the group it happened in (`docs/dice-notation.md`, "Evaluation", step 7). What is left is a person's call: whether the stop reads as a rule or as a bug, and whether a line under the group is where the eye actually goes

### 5.7 Performance on the Pixel 10a

- [ ] 60 fps sustained at 20 dice, frame time p99 under 16.6 ms; at least 30 fps at the capacity limit
- [ ] **Done, on the Pixel 10a: 500 rolls leave 1,440 bytes behind.** Under
      three bytes a roll, which is allocator noise rather than anything anybody
      allocated. `MemoryTest` measures the **native** heap, which is the half
      that matters — a physics world is a handle into a solver the collector
      knows nothing about, so a world nobody closed would show there and nowhere
      else — and holds it to a quarter of a megabyte, a hundred and eighty times
      the measurement and still tight enough to catch ten leaked worlds, let
      alone five hundred. The JVM heap is held looser on purpose, against a
      collector that decides for itself when to shrink
- [ ] Battery cost of 100 rolls measured, then written into `docs/physics-and-rendering.md` as the budget

**Done when** every target above is met on the Pixel 10a and the user agrees
the dice look right. Numbers that turn out wrong become the new numbers in
`docs/physics-and-rendering.md` and `docs/tables.md` — the docs follow the
device, not the other way round.

## Step 6 — v1 release

- [ ] Accessibility: **walk every screen with TalkBack on a real phone.** The
      rules, the labels and the measured contrast are done and tested
      (`docs/architecture.md`, "Accessibility"); what a test cannot answer is
      whether the reading *order* is sensible, whether the announcements are
      the right length when they arrive one after another, and whether the tray
      is comprehensible with the screen curtain on. A person with the phone and
      TalkBack switched on, once
- [ ] Localisation: **the failure-reason pipeline.** Every word a screen says
      is now a string resource and `verifyTextIsAResource` keeps it that way
      (`docs/architecture.md`, "Text a person reads"). What is left is one
      thing: the sentences that say why something was refused. They are
      assembled across module boundaries — `dicesets/format`'s and
      `dicesets/install`'s `ValidationMessage`, `core/collection`'s reader, and
      the `app/` helpers that read a file or fetch a URL — and half of every
      such sentence is written in a plain-Kotlin module that may not depend on
      Android. Translating it means giving each refusal a typed reason the
      screen phrases, which is a design change rather than a string move; the
      same is true of `core/notation`'s `NotationReference`, which is the
      notation screen's whole content and sits beside the parser on purpose.
      Both are exempted by file name, with the reason, in their own build
      scripts. Nothing is wrong today: v1 ships English
- [ ] Play Store metadata, screenshots taken from the real app, privacy statement (no analytics, nothing leaves the phone)
- [ ] Tag `v1.0.0`

## After v1

Written down so the format need not change later. Not v1 scope.

- [ ] More catalogue solids, `rhombic-triacontahedron` (d30) first
- [ ] Author-supplied convex meshes, with the fairness preview they require (`docs/dice-sets.md`, "Shapes after v1")
- [ ] **Author-supplied materials.** Compiling materials at runtime (`docs/architecture.md`, decision 46) means a set *could* ship its own `.mat` rather than only values for the built-in one — iridescent dice, a proper glass d20, a table that is actually brushed metal. v1 does not allow it, and the reason is not effort: a shader is code, it runs on the GPU, and "the app never runs anything from the repository" is a rule of the format (`docs/dice-sets.md`). Turning it on needs a decision about what a shader from a stranger may do — a compile that never finishes is a hung GPU, and a driver is a large attack surface — plus a limit on compile time, a cap on instruction count, and a refusal that is as legible as the validator's other refusals. Until then a set varies a material's *parameters*, which is what `roughness`, `metallic` and the colours already are

## Coverage

Branch coverage is **70.1 %** against a floor of 62, and roughly **seven in ten
of the branches it is missing are inside `@Composable` functions**. That is not
untested UI: the Compose compiler emits a skip branch for every parameter of
every composable so that a recomposition can be avoided, and a single-pass test
can only reach one side of each. A screen with twelve controls is a hundred
branches no test will ever take.

The drift that used to come with every new screen has stopped. Three levers do
it, and between them they have held the number flat or moved it up in each of
the last six pull requests:

- **Extract the decision, test the decision.** `FaceHistogram`, `Breakdown`,
  `CollectionExport`, `GraphBars`, `underlinesOf` and `crestPath` are all
  arithmetic that used to be inside a draw lambda. Each is now a plain object
  with plain tests, and a Canvas is the one place a test genuinely cannot go.
  A composable that returns a value rather than drawing one is the same trick:
  it is not skippable, so it costs no skip branch at all.
- **A shared component gets its own test.** `ui/common`'s formula field had
  152 branches and none of them covered, because three screens each tested
  *their use* of it and nobody tested the thing. It has its own tests now,
  including the recomposition one, and sits at 62 of 98. That was a real gap
  and it looked exactly like the mechanical one in a report — which is why the
  number is worth reading rather than merely watching.
- **A recomposition test takes the other side.** Drawing a screen once takes
  the "something changed" side of every skip branch it has; recomposing around
  it with nothing changed takes the other. The roll screen has the most
  parameters of any and had no such test.

The figures are reported in every PR description either way.

- [ ] Decide whether the floor should track the drift or stay where it is. It
      has not been moved since it was set, and moving a floor to make a check
      pass is the thing `.claude/CLAUDE.md` says not to do — so this is a
      question for a person, not a change to make quietly

## Open questions

- [ ] **Does a refusal keep its words, or become a reason?** The sentences a
      validator and a downloader write end up on screen, and they are written
      in modules that have no resources — which is deliberate, because nothing
      that reads a stranger's file may depend on Android
      (`docs/architecture.md`, "Text a person reads"). Two ways out. Give every
      `ValidationMessage` a typed reason with its arguments, and let the screen
      phrase it: correct, translatable, and a change to every producer and
      every test that asserts on a message's words. Or leave the sentences
      where they are and accept that a refusal speaks English in a translated
      app, which for a rare screen a player hopes never to see is not obviously
      wrong. Worth deciding before a second language exists, not after
- [ ] **Is percent typography text?** `"0 %"`, `"< 0.1 %"` and `"%.1f %%"` are
      format patterns in `feature/graph` and `feature/stats`, and
      `verifyTextIsAResource` leaves them alone because they contain no words —
      `String.format` already follows the device's locale for the decimal
      point. But the space before the `%` is a typographic convention that
      differs by language, as is the `"—"` that stands for a value there is
      none of. Moving them into resources means `percent()` taking a
      `Resources`, which costs its JVM tests a context. Cheap either way, and
      only worth paying once a second language exists

- [ ] **A filled button's label is 3.76:1 on its own accent, and wants 4.5:1.**
      `onPrimary` is the ground colour by design, so the label on **Roll**,
      **Save group** and every other filled button is the pale ink on the
      accent. Measured against the light ground: vermilion 3.76:1, coral
      3.25:1, sky 3.11:1, moss 3.63:1, amber 3.79:1, violet 4.05:1 — all past
      3:1, none at the 4.5:1 that 13 sp semi-bold text asks for. On the dark
      ground three of the six pass. Every fix is a palette change and therefore
      a design decision: fill with the ramp's 700 step and keep the pale label,
      keep the fill and darken the label, or make the primary action an
      outlined button in the accent with ink text. `ModernistContrastTest`
      holds the floor at the measured ratios so it cannot quietly get worse
- [ ] **The divider is 2.41:1 on the light ground, and Material uses the same
      token for a control's border.** `--color-divider` is the text colour at
      40 %, which is 2.41:1 on the light ground and 3.51:1 on the dark one. As
      a rule between rows that is decoration and 3:1 does not apply; as
      `outline` it is also an `OutlinedButton`'s border, which is a control
      boundary and does. Raising the alpha to about 55 % on the light ground
      would clear it, at the cost of heavier rules everywhere — the design
      system says 40 %, so this is the design's to answer, not a test's
- [ ] **A neutral tag has no dark ground.** The prototype's dark override
      (`.dz-dark` in `design/dInfinityPhone.dc.html`) **reflects each ramp
      about its middle step** — accent 100↔900, 200↔800, 700↔300, 800↔200 and
      neutral 200↔800, 300↔700, 400↔600, with 500 its own fixed point. Eight
      steps are written out. `.tag-neutral` is `--color-neutral-100` filled and
      `-800` lettered, and **neither is among them**, so on a dark page it is a
      near-white chip with dark grey text while `tag-accent` — covered by the
      list — flips correctly to deep red. The rule says what those two should
      be and the app follows it, but the list does not say it, so the
      inference is recorded in `docs/design-handover.md` rather than taken as
      settled. `ModernistTest` asserts the eight that *are* written down, so
      the rule itself cannot drift
- [ ] **Statistics and History narrow their lists with a scrolling row of
      accent words; the prototype uses a segmented control.** `Cut`
      (`feature/stats`) draws one option of that row — no box, the chosen one
      in the accent and bold — and both screens share it now. The prototype
      draws the same choice as a `.seg`: one box with its options butted
      together, the chosen one filled. `ui/common`'s `SegmentedControl` and
      `OptionBox` between them can draw either shape, so what is missing is a
      decision rather than a component. It is a visible redesign — a bordered
      inverting box in place of a bare accent word — and a semantics change
      with it (`Role.RadioButton` via `selectable`), so it wants an eye rather
      than a refactor. The sets a player can have is unbounded, which is the
      argument for the scrolling row and against the joined box
- [ ] **The face designer's tool row is a set of `.seg-opt`s wearing button
      clothes.** Every one of its twenty-one controls goes through one `Tool`
      composable, and that composable is a two-state control: chosen is filled
      in the accent, unchosen is the **muted** ink. That is `.seg-opt`, not
      `.btn` — `ModernistButtonKind.Ghost` is the accent by definition, so
      mapping unchosen onto it would print every nib, every stamp size and
      every face of the strip in the accent at once. `SegmentedControl` draws
      `.seg-opt`s but as one joined box of options, where this is a wrapping
      row that mixes options (nibs, sizes, faces) with plain actions (copy,
      paste, clear, fill with numbers). Drawing it properly means deciding
      which of those are options and which are actions, which is a redesign
      rather than a substitution — so the row keeps Material's `TextButton`
      with a `design-system-exception` and the reason beside it. All twenty-one
      call sites go through the one composable, so it is one place whenever it
      is done
- [ ] **A tag needs a ramp, and only two of the six accents have one.** The
      design system ships exact ramps for `--color-accent` (vermilion) and
      `--color-accent-2` (coral), and `.tag-accent` is built from two ends of
      one: `-100` filled, `-800` lettered. The app lets a player choose six
      accents, and `AccentColor` already derives what it needs for the other
      four the way the design derives an ad-hoc accent
      (`design/Logo.dc.html`: `color-mix(in srgb, accent 58%, text)`) — but
      that rule makes the *deep* end only. Nothing in the design says how to
      make the pale end, and it cannot be mixed from the accent and the
      ground: `--color-accent-100` is `#fff2ef`, which is lighter in the red
      channel than either. So either a tag uses the literal ramp and stops
      following the accent the player chose, or it derives both ends and
      stops matching the prototype for the accent the prototype was drawn in.
      **Until this is answered the app has no filled accent tag**, which is
      the badge that says a dice set has an update

- [ ] **The picker is a list of rows; the prototype's `1u` is a grid of cards.**
      The thumbnails landed in the list that was already there — one 44 × 64 dp
      picture at the head of each row, in the place the swatch held — rather
      than in the two-column grid of 150 px cards the prototype draws. That was
      the smaller change and it keeps the row's other half working (the package
      name, the "Chosen" mark, **Remove** on a photo table, all of it one node
      for TalkBack), but it is not what the design shows, and a card gives a
      picture about five times the area. The alternatives are to rebuild the
      picker as the grid and find somewhere else for the four things a row
      carries, or to change `1u` to a list and keep the two halves true that
      way. Either is a design decision rather than a rendering one
      (`design/README.md`)

- [ ] **Decided: a rendered harness measures a drawn frame, and the phone can
      show its own frame rate.** The headless harness times `LiveRoll.advance`
      and has no surface, so what it scores is the simulation half of a frame
      and Step 5.7's "p99 under 16.6 ms" is about drawing. So there is to be a
      second harness — an instrumented test that opens a real surface, rolls
      twenty dice and reports its own frame times — and a **setting that puts
      the frame rate on the screen**, so the number is available to a person
      holding the phone and not only to a test. Not small: the rendered one
      needs an activity, a Filament engine and a device, and its arithmetic
      wants moving into `:simulation:harness` to be shared. Step 5.7

- [ ] **Where does a table look's texture say which package it came from?** A
      die's artwork now reaches the tray by a key of package and path
      (`docs/dice-sets.md`, "How an atlas reaches the tray"), and a
      `TableLook`'s `floor_texture` and `wall_texture` carry a path and nothing
      else — so they resolve to nothing and a table is drawn in its own
      colours, exactly as it was before. Chosen because the alternatives both
      reach a long way for a case nothing ships: the bundled package has no
      table textures and "My dice" has dice atlases only. The two ways out are
      putting the package id on `TableLook` itself, which makes every table a
      little wider for one field, and threading it through `Tray.table` and
      `Renderer.begin`, which puts it on the seam that is deliberately narrow.
      Worth deciding when a package that actually ships one exists
- [ ] **Where should a load-time texture report be shown?** `AtlasDecoder`
      produces the same `ValidationMessage` lines the validator does — a file
      that will not decode, an atlas with empty cells — and today nothing reads
      them: the die falls back to its labels and the lines are dropped. The
      obvious home is the set's details screen beside the validation report
      (design `6b`), which would mean the decode happening somewhere a screen
      can reach rather than only on the roll thread. Chosen to leave it for now
      because the fall-back is the behaviour either way and a report nobody
      asked for is not worth a second decode
- [ ] **Is a printed numeral meant to be 0.78 of its face, or 0.78 squared of
      it?** `FACE_SHARE` is applied twice on the way to a printed height: once
      to the box whose centre is solved clear of the edges, and once again to
      what is printed inside that box, so a numeral comes out at about 0.61 of
      the room its face has (`core/glyphs`' `LabelRoom.centred`). The stamp and
      "fill all with numbers" were built to match it exactly rather than to
      correct it, because the size on the Pixel 10a was judged with it in place
      and applying the share once would make every number on every die 28 %
      bigger overnight. The alternative is to apply it once and re-judge the
      fraction on the phone, which is Step 5.6's question anyway — the two
      should be answered together, and whichever way it goes the tray and the
      designer move together because they read the same number
- [ ] **Should the picker row offer "Doodle this die" as well?** Quick mode is
      a long press on a die in the **breakdown**, not on the picker row
      (`docs/face-designer.md`, "Quick mode"): the picker's long press already
      takes a die off the formula, which is a fast edit made in twos and
      threes, and putting a menu in front of it for something somebody does
      once a month would slow the common thing down for the rare one. The
      alternative is exactly that menu — a long press on a picker chip opening
      "Take one off" and "Doodle this die" together — which would make the
      shortcut reachable before a roll rather than only after one, at the cost
      of a tap on every removal. It needs a phone to judge: whether reaching
      the designer before anything has been thrown is a thing anybody wants,
      and whether a menu on the picker feels like a delay
- [ ] **Should a stamp be draggable after it is put down?** The prototype lets
      one be picked up and moved (`design/dInfinity.dc.html`, option `1v`); the
      app does not, because a stamp is a mark like a stroke and no other mark
      can be picked up — a drawing where one kind of mark moves and the others
      do not is two drawings, and the way to move one is undo and stamp again.
      The case for the prototype's answer is that a glyph is the one mark
      somebody places rather than draws, so landing it a finger's width off is
      a miss rather than a wrong drawing. It needs a phone to judge: whether
      re-stamping feels like correcting a typo or like losing work
      (`docs/face-designer.md`, "The stamp")
- [ ] **Should the anomaly log survive a restart?** It is in memory today,
      bounded to fifty entries, and goes when the app does — because an entry
      carries the seed that reproduces the roll, and a stored seed is a replay
      waiting to be written into a screen a player can reach
      (`docs/architecture.md`, decisions 13 and 56). Against that: an anomaly is
      supposed to be so rare that losing one to a restart may be losing the only
      one anybody ever sees. If it should persist, the question to answer first
      is where — a file the developer toggle owns and the ordinary app cannot
      read is a different thing from a table beside the history, and only the
      first of those is consistent with decision 13
- [ ] **Should a heavy die sound heavier?** An impact reports the change in a
      die's speed, which is impulse per unit of mass, and the sound follows that
      and the die's *size*. A die's `density` — which a set may put anywhere from
      balsa to brass — reaches the physics and does not reach the sound, so a
      brass d6 and a resin d6 of the same size land with the same noise. Adding
      it means giving the impact a mass, which means a hull volume the shape
      catalogue does not currently compute. Worth it or not is a judgement about
      how much anybody would notice (`docs/dice-sets.md`, "Size")
- [ ] `core/probability` hand-rolls its convolution and its FFT rather than
      taking a library, which `.claude/CLAUDE.md` names as a "complex part".
      The judgement was that the exact PMF *is* the domain logic and that
      pulling in a general maths library for ninety lines of transform is a
      worse trade than owning them — but it is a trade, and it is worth a
      second opinion
- [ ] Whether a die an explosion adds should be able to *collide* with the dice
      already down, as immovable furniture rather than as bodies that can move.
      Today it is thrown in a world holding only itself, which is what makes
      "nothing touches a die that has come to rest" true by construction — but
      it also means the added die cannot bounce off the pile, which is what
      would really happen on a table. Turning it on means the native bridge
      growing a second kind of body (static, never stepped, never reported),
      which cannot be tested on the JVM and cannot be verified without a phone.
      Worth deciding deliberately rather than by default
      (`docs/architecture.md`, decision 54)
- [ ] **Should an exported package carry a name, and where would it come from?**
      The `author` field is currently left out rather than filled: Android has
      no device user name an app can read without asking for contacts, and a
      field saying "You" would be a name on somebody else's phone. The
      alternatives are a text field beside the licence chooser on the "My dice"
      details screen, or a name kept in the settings and used by every export.
      Both are small; which one is wanted is a judgement about how much the
      export screen should ask for before it will share (`8c`,
      `docs/face-designer.md`)
- [ ] **A photo table is in the package before the tray can draw it.** "Use a
      photo" writes a valid, exportable `[[table]]` with its picture, and the
      tray shows it as its colours until something fills the `atlases` seam
      (Step 3, above) — which is the same state a *drawn* die's artwork is in,
      so the alternative was holding the feature until the renderer loads
      textures. I chose to ship it: the package, the validator path and the
      export are the hard parts and they are done, and the picture appearing is
      one seam away for dice and tables alike. Worth confirming that is the
      right order (`docs/tables.md`, "Your own photo")
- [ ] **How should a photo sit on the tray?** The prototype's upload sheet
      offers three fits — centre, fit width, fit height (`1u`) — and none is
      implemented: a photo table is written with `floor_tiling = [1, 1]`, which
      means the picture covers the floor once. A *fit* is a question about UV
      mapping and cropping, and nothing draws a table texture yet, so there is
      nothing to be right or wrong against. I chose the one answer that needs
      no renderer. Deciding it properly means choosing between cropping the
      photo at import (which loses pixels somebody chose) and mapping it at
      draw time (which needs the tray's aspect, and the tray's aspect changes
      with the phone's rotation)
- [ ] The face designer has no 3D preview in the prototype — "Roll it" is the preview. Confirm, then fix `docs/face-designer.md` (4.6)
- [ ] The dice picker remembers the last set per saved-roll group — confirm, then add to `docs/dice-notation.md`
- [ ] A collection imported from a git repository records nothing about where
      it came from, so there is no "check for updates" for one the way there is
      for a dice set: an import becomes rows in the database rather than a
      folder with a `.meta.json` beside it, and `RefResolver` is therefore not
      asked which commit the ref was at. If a collection should be updatable
      from its repository later, the commit is the thing to start recording
      (`docs/dice-notation.md`)
- [ ] A forge link that names a *file* inside a repository
      (`…/blob/main/goblins.dinfinity.json`) imports whatever is at the
      repository root instead, because the path after the ref is a dice set's
      subfolder and a collection is found at the root. Decide whether such a
      link should import the file it names
- [ ] Raise `sdk` in `app/src/test/resources/robolectric.properties` to 37 when Robolectric supports it
- [ ] Move the container's emulator up when an automated-test image exists
      above API 36 — the same wait as the line above, for the same reason
      (`docs/architecture.md`, decision 39)
- [ ] The devcontainer asks Docker for `/dev/kvm` unconditionally, so a
      machine without nested virtualisation cannot open the project at all.
      Confirm that is the right default rather than building the image
      without an emulator and asking for the device only when one is wanted
      (`docs/build-setup.md`)
- [ ] d18 shape: the enneagonal trapezohedron is assumed; verify it reads well at phone size
- [ ] Division rounding default is Down with a per-throw override — confirm Nearest is worth having
- [ ] The design project's `.thumbnail` is not imported; decide whether a preview image belongs in the repo
- [ ] The harness scores every run against the **same** settle bars — median
      2 s, p99 4 s — but Step 5.5 states them at twenty dice. A `100d4` run is
      therefore held to a twenty-dice bar, which is either exactly right (a
      roll is a roll, and a player waiting four seconds does not care how many
      dice they threw) or unfair to the worst case on purpose. The harness
      takes the first reading, and the bars are data, so changing it is one
      line in `HarnessTargets` — but which it should be is a decision
      (`docs/build-setup.md`, "The physics harness")
- [ ] **Decided: the die's history keeps every throw, the roll's keeps the
      sum.** A die thrown again is two facts, not one, and they belong to
      different records. The *die* is a record of faces that came up, so every
      throw of it counts — a `d6` that went `6, 6, 6, 4` contributes `6: 3` and
      `4: 1`, four readings, because the die really did land on those faces
      four times and a fairness figure that dropped three of them would be a
      lie about the die. The *roll* is a record of what the player got, which
      is one number: **22**.

      That is already how an exploding chain is counted, which is the point —
      `6, 6, 6, 4` is what `1d6!` looks like — so a hand re-throw is not a new
      rule in the history, it is the existing one applied to a throw the player
      asked for rather than one the notation did. Whatever the mechanism, a die
      contributes one reading per time it came to rest and a roll contributes
      one total.

      Unblocks the one-finger pick-up-and-throw (4.1), whose two decisions —
      `TrayPick` for which die a finger is on, `PickUp` for which dice a hand
      may go near — are built and tested and were waiting only on this
