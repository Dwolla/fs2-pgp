package com.dwolla.testutils

import eu.timepit.refined.predicates.all._

private[testutils] trait PgpArbitrariesPlatform { self: PgpArbitraries =>
  type KeySizePred = GreaterEqual[384]

  // TODO refined macros
  protected val KeySize512: KeySize  = KeySize.unsafeFrom(512)
  protected val KeySize2048: KeySize = KeySize.unsafeFrom(2048)
  protected val KeySize4096: KeySize = KeySize.unsafeFrom(4096)
}
