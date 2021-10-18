package com.dwolla.testutils

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.predicates.all._

trait PgpArbitraries extends PgpArbitrariesPlatform {
  type KeySizePred = GreaterEqual[W.`384`.T]
  type KeySize = Int Refined KeySizePred
}

object PgpArbitraries extends PgpArbitraries
