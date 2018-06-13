package com.stripe.rainier.compute

case class Context(density: Real) {
  val compiler: Compiler = IRCompiler(200, 100, false)
  val variables: List[Variable] = RealOps.variables(density).toList
  lazy val gradient: List[Real] = Gradient.derive(variables, density).toList
}
