package com.stripe.rainier.compute

case class Context(density: Real, placeholders: Seq[Variable]) {
  val compiler: Compiler = IRCompiler(200, 100, false)
  val variables: List[Variable] =
    RealOps.variables(density).toList.filterNot(placeholders.toSet)
  lazy val gradient: List[Real] = Gradient.derive(variables, density).toList
}

object Context {
  def apply(density: Real): Context = Context(density, Nil)
}
