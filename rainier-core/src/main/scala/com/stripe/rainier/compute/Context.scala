package com.stripe.rainier.compute

case class Context(base: Real, batched: Seq[Target]) {
  val compiler: Compiler = IRCompiler(200, 100)

  val variables: List[Variable] = {
    val allVariables =
      batched.foldLeft(RealOps.variables(base)) {
        case (set, target) =>
          set ++ target.variables
      }
    allVariables.toList.sortBy(_.param.sym.id)
  }

  //convenience method for simple cases like Walkers
  def compileDensity: Array[Double] => Double = ???

  //these are for backwards compatibility to allow HMC to keep working
  //they will not make sense in the general case going forward
  lazy val density: Real =
    batched.foldLeft(base) { case (r, t) => r + t.inlined }

  lazy val gradient: List[Real] =
    Gradient.derive(variables, density)
}

object Context {
  def apply(real: Real): Context = Context(real, Nil)
  def apply(targets: Iterable[Target]): Context = ???
}
