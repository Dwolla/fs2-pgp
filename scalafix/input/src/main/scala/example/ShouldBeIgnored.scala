package example
/*
rule = com.dwolla.security.crypto.V04to05
*/
trait Foo {
  def encrypt(request: Int): Unit
}

class ShouldBeIgnored(foo: Foo) {
  def go(): Unit = foo.encrypt(42)
}
