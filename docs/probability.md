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
(`500d6`); the roll button is what gets disabled, not the math.

## What is shown

- A bar chart of P(total = k) for every reachable k, with the mean marked and
  ±1σ shaded.
- A toggle for **"at least"** (P(total ≥ k)), which is what most game
  questions actually ask ("what are my odds of hitting AC 15?"). Tapping a bar
  shows the exact numbers.
- Mean, standard deviation, min, max, and the probability of the extreme
  results.
- For formulas with a label, the label is used as the chart title.

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
| `NdX` | convolve the single-die PMF with itself N times (FFT above ~64 dice, plain O(N·m²) below) |
| `+`, `-` | convolution (with negation for `-`) |
| `*`, `/` by a constant | value remap |
| `*` of two dice expressions | product distribution by enumeration over both supports (bounded by the limits in `docs/dice-notation.md`) |
| `kh n` / `kl n` / `dh n` / `dl n` | order statistics: enumerate outcomes of N dice via dynamic programming over sorted values; exact, O(N · m · support) |
| `!` (exploding) | geometric tail, truncated at the explosion depth limit; the truncated mass (< 1e-9 for d6 at depth 20) is reported in the tooltip |
| `r n` (reroll once) | conditional PMF composition |
| `min n` | clamp remap |

Precision: `Double`, with probabilities renormalised after each convolution
step. For display we show 4 significant digits. The extreme tails of a very
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
| Explosion depth | 20, the same as the notation's | The truncated mass is reported with the graph |

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
numerical noise; the built-in test suite rolls each catalogue die 100,000
times in power-saving mode and asserts a chi-squared test passes.

Since v1's shape catalogue is closed (`docs/dice-sets.md`), every die in
every set is one of those eight tested solids, however it is painted: a
custom-looking die is still a fair die, and the graph is exact for all of
them. If author-supplied mesh shapes arrive later, the graph will have to
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
