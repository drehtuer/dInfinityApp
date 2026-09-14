package de.drehtuer.dinfinity.feature.roll

import java.security.SecureRandom

/**
 * The two things a throw takes from outside itself: a seed and the time.
 *
 * Together because they are the same kind of thing — the part of a roll that a
 * test replaces so that a throw is reproducible and a timestamp is not today's.
 * Separately they were two more constructor parameters on a class that already
 * had enough of them.
 *
 * The seed is a [SecureRandom] draw rather than a clock-seeded one. A roll is
 * not a cryptographic operation, but a dice app whose next result can be
 * predicted from the last is a dice app somebody will eventually notice
 * (`docs/architecture.md`, goal 1).
 */
class Outside(
  val seeds: () -> Long = { SecureRandom().nextLong() },
  val clock: () -> Long = System::currentTimeMillis,
)
