package example

trait Foo {
  def encrypt(request: Int): Unit
}

class ShouldBeIgnored(foo: Foo) {
  def go(): Unit = foo.encrypt(42)
}
