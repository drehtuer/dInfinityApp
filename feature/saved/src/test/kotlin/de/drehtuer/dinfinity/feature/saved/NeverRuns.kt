package de.drehtuer.dinfinity.feature.saved

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A dispatcher that never runs what it is given.
 *
 * What "the database has not answered yet" looks like when a test needs it to
 * stay that way: the in-memory database answers at once on `Unconfined`, so a
 * state that lasts a frame on a phone would otherwise not last long enough to
 * look at.
 */
internal object NeverRuns : CoroutineDispatcher() {
  override fun dispatch(
    context: CoroutineContext,
    block: Runnable,
  ) = Unit
}
