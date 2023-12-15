package com.dwolla.testutils

import eu.timepit.refined.W
import eu.timepit.refined.auto.*
import eu.timepit.refined.predicates.all.*

private[testutils] trait PgpArbitrariesPlatform { self: PgpArbitraries =>
  type KeySizePred = GreaterEqual[W.`384`.T]

  protected val KeySize512: KeySize = 512
  protected val KeySize2048: KeySize = 2048
  protected val KeySize4096: KeySize = 4096
}
