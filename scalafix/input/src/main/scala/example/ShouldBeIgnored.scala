package example
/*
rule = com.dwolla.security.crypto.V04to05
*/
trait Foo {
  def encrypt(key: String, request: Int): Unit
  def armor(): Unit
  def tagChunkSize(i: Int): Unit
}

class ShouldBeIgnored(foo: Foo) {
  import foo.*
  import Nested.*

  foo.armor()
  foo.encrypt("key", 42)
  foo.encrypt(key = "key", request = 42)
  tagChunkSize(42)

  CryptoAlg[Option]
}

object Nested {
  object CryptoAlg {
    def apply[F[_]]: Unit = ???
  }
}
