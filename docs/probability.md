# Outcome graph

> **Design:** the three graph treatments are options 1k–1m of the
> [clickable design](../design/dInfinity.dc.html) ([design/](../design/)); 7a shows the graph opened
> after a roll, with the rolled total marked.

Before rolling, the app shows the probability distribution of the total.
This works for a typed formula in the editor *and* for dice picked by
tapping on the roll screen — both are the same `RollPlan` underneath. It
lets a player see at a glance that `3d6` is bell-shaped and centred on 10.5,
while `1d20` is flat, and what `2d20kh1` does to the odds.

The graph is available even for formulas the table cannot physically roll
(`500d6`); it is the *throw* that is refused, not the math.

The same arithmetic says what an ordinary formula is expected to come to
before it is thrown — the lowest, the highest and the exact average, on the
ready plate before the shake and afterwards in the result sheet's **grip**,
beside the total and above the bottom edge, so a player deciding whether to
throw again can still read it
(`docs/physics-and-rendering.md`, "What is drawn over the table").
A formula past the limits below keeps its range, which costs nothing, and
loses only its average.

## What is shown

- A bar chart of P(total = k) for every reachable k, with the mean marked and
  ±1σ shaded.
- A toggle for **"at least"** (P(total ≥ k)), which is what most game
  questions actually ask ("what are my odds of hitting AC 15?"). Tapping a bar
  shows the exact numbers.
- Mean, standard deviation, min, max, and the probability of the extreme
  results.
- The screen's title is always "Outcome graph". A formula's `[label]` is
  parsed and carried to the screen with the distribution, but nothing shows it
  yet.
- Under the chart, the two things to do with a formula whose odds you have
  just read: **Roll this**, which hands it to the tray, and **Save as roll**,
  which opens the saved-roll editor with it already typed. Both carry what is
  in the field rather than what the screen was opened with — the formula can
  be edited here, and handing on the old one would be handing on the odds you
  had stopped looking at. Neither is offered for a formula there are no odds
  for: a formula that does not parse is not one to roll or to keep, and a
  button that refuses is worse than one that is not there.

**The way in from the tray is on the result.** `See the odds` sits at the foot
of the result sheet, beside `Save as roll`, and carries the formula in the
field along with the total that just landed so the graph can mark it. It used
to be a plate of its own in the roll screen's column of controls, offered
before a throw and for a throw the table refused as well; a device session
asked for it to be part of the result, so it is offered once the dice have
landed and not before. A refusal reaches the graph through the menu
(`docs/physics-and-rendering.md`, "What is drawn over the table").

The distribution is bell-shaped for sums of many dice (central limit
theorem), but it is **not** computed as a normal approximation — that would be
wrong for a single d20 and visibly wrong for `2d6`. We compute the exact
discrete distribution.

## Computation

Every AST node produces a probability mass function (PMF): a map from integer
outcome to probability, stored as a dense array with an offset.

| Node | PMF |
| --- | --- |
| integer `n` | `{n: 1}` |
| single die with faces `f₁…fₘ` | each distinct value with probability (count / m) — face values come from the set, so a d6 labelled `1,2,1,2,1,2` gives the d2 distribution automatically |
| `NdX` | the sum of N copies of the single-die PMF, by repeated squaring (about log₂ N convolutions). Every convolution — here and for `+`/`-` — is a direct double loop up to 20,000 multiply-adds and an FFT above that (`Convolution.FFT_THRESHOLD`) |
| `+`, `-` | convolution (with negation for `-`) |
| `*`, `/` by a constant | value remap |
| `*` of two dice expressions | product distribution by enumeration over both supports (bounded by the limits in `docs/dice-notation.md`) |
| `kh n` / `kl n` / `dh n` / `dl n` | order statistics: enumerate outcomes of N dice via dynamic programming over sorted values; exact, O(N · m · support) |
| `!` (exploding) | geometric tail, truncated at the explosion depth limit; the truncated mass (under 1e-15 for a d6 at depth 20: (1/6)²⁰ ≈ 2.7e-16) is computed and carried to the screen, but not shown yet |
| `r n` (reroll once) | conditional PMF composition |
| `min n` | clamp remap |

Precision: `Double`, with probabilities renormalised after each convolution
step. For display, probabilities are percentages to one decimal place
(`12.5 %`), and one too small for that reads `< 0.1 %` rather than `0.0 %` —
only an impossible total says `0 %`. The mean and standard deviation are shown
to one decimal place as well. The extreme tails of a very
large sum are dropped rather than drawn: all five hundred dice of `500d6`
showing a six has probability 6⁻⁵⁰⁰, which is smaller than a `Double` can hold,
and there is no bar to draw for an outcome that cannot be written down.

Division uses the rounding **from Settings**, always. The graph is computed
before the throw exists, so the result sheet's per-throw override has nothing
yet to apply to (`docs/dice-notation.md`, "Division rounding").

## Limits

Exactness has a price: a distribution is an array with an entry per reachable
total, and some perfectly legal formulas reach a great many.

| Limit | Value | Behaviour when exceeded |
| --- | --- | --- |
| Totals in one distribution | 1,000,000 | The graph says it cannot be exact about this one |
| Multiply-adds in one step | 200,000,000 | Same |
| Explosion depth | 20, the same as the notation's | Chains are cut there. The truncated mass is computed but not shown on screen yet |

`1000d20` is twenty thousand totals and perfectly fine. A dice set is free to
give a d20 face *values* in the thousands, though, and a thousand of those
reach ten million — an array nobody asked for, on a phone. The
order-statistics program is the other one that bites: keeping one of two
hundred dice is cheap, keeping a hundred of them is thousands of times more
work.

So the graph says "too large" rather than either lying with an approximation
or filling memory with a set file's arithmetic. A formula the graph refuses
can still be rolled if it fits on the table, and one the table refuses can
still be graphed; the two limits have nothing to do with each other.

## Physics vs. probability

The graph assumes fair dice. The physics simulation with uniform-density
convex solids and honest tumbling *is* fair for the catalogue shapes, up to
numerical noise — with one exception, below. The headless fairness test
(`FairnessTest`, an instrumented test in `simulation/jolt`) throws every
catalogue shape and checks the counts. The ordinary device suite runs it at
200 rolls per shape, which only catches a die that never shows a face; the
statistical checks need a run asked for deliberately, with
`-Pandroid.testInstrumentationRunnerArguments.rolls=100000`. From 2,000 rolls
up it asserts two things: a chi-squared test at p = 0.001, and that no face is
off its share by more than one percentage point.

**The d18 is held to the second check alone.** The enneagonal trapezohedron
fails chi-squared at a hundred thousand throws — reproducibly, on the
reference phone — while no face of it is more than half a percent off its
share. The cause is under investigation; see `docs/physics-and-rendering.md`,
"The d18 is not fair, and the shape is not why".

Since v1's shape catalogue is closed (`docs/dice-sets.md`), every die in
every set is one of those eight tested solids, however it is painted: a
custom-looking die is exactly as fair as the solid it is built on, and the graph
is exact for all of them on the assumption that the solid is fair — which, for
the d18, is the assumption the fairness test has not yet confirmed. If author-supplied mesh shapes arrive later, the graph will have to
assume fairness it cannot verify and say so on screen — which is one more
reason they are not in v1.

## Testing

- Property tests: the PMF sums to 1 ± 1e-12; mean of `NdX` equals
  N·(X+1)/2 for standard faces; `2d20kh1` and `2d20kl1` match the closed forms
  for the maximum and the minimum of two.
- **Golden tests against the dice themselves.** Small formulas are rolled every
  possible way and each outcome scored by the same evaluator a real roll uses
  (`:core:notation`), then the tally is compared against what the chart would
  draw. The reference is deliberately not a second piece of probability theory:
  two derivations can agree and both be wrong about what the app does. This
  cannot. If the graph and the enumeration disagree, the chart is telling the
  player something the dice will not do.
- The two convolution implementations — the direct double loop and the
  transform — are checked against each other well past the size at which the
  code switches over, so the chart cannot change shape at the threshold.
