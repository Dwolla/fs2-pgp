package com.dwolla.testutils

import eu.timepit.refined.types.all.*

private[testutils] trait CryptoArbitrariesPlatform { self: CryptoArbitraries =>

  // TODO refined macro
  protected val PosInt1024: PosInt = PosInt.unsafeFrom(1024)
  protected val PosInt4096: PosInt = PosInt.unsafeFrom(4096)
}
