package com.stripe.rainier.compute

case class Context(density: Real) {
  val compiler: Compiler = IRCompiler(this, 200, 100, false)

  val (variables, gradient) =
    Gradient.derive(this).unzip

  def newTable[T]: Table[T] = new Table[T] {
    val map = new java.util.IdentityHashMap[Real, T]
    def get(real: Real) = Option(map.get(real))
    def update(real: Real, value: T) = {
      map.put(real, value)
      ()
    }
  }
}

trait Table[T] {
  def get(real: Real): Option[T]
  def update(real: Real, value: T): Unit
}
