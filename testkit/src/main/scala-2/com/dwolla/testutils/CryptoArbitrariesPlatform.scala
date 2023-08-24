package com.dwolla.testutils

import eu.timepit.refined.auto.*
import eu.timepit.refined.types.all.*

private[testutils] trait CryptoArbitrariesPlatform { self: CryptoArbitraries =>
  protected val PosInt1024: PosInt = 1024
  protected val PosInt4096: PosInt = 4096
}
