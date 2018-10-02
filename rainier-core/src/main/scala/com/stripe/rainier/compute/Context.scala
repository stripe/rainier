package com.stripe.rainier.compute

import com.stripe.rainier.sampler.DensityFunction

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
  def compileDensity: Array[Double] => Double = {
    val df = compiler.compileTargets(base, batched.toList, false, 4)
    return { inputs: Array[Double] =>
      val globals = new Array[Double](df.numGlobals)
      val fullInputs = new Array[Double](df.numInputs)
      val outputs = new Array[Double](1)
      System.arraycopy(inputs, 0, fullInputs, 0, df.numParamInputs)
      df(fullInputs, globals, outputs)
      outputs(0)
    }
  }

  def compileGradient: Array[Double] => Array[Double] = ???

  def densityFunction: DensityFunction = new DensityFunction {
    def nVars = variables.size
    def update(vars: Array[Double]): Unit = ???
    def density: Double = ???
    def gradient(index: Int): Double = ???
  }

  //these are for backwards compatibility to allow HMC to keep working
  //they will not make sense in the general case going forward
  lazy val density: Real =
    batched.foldLeft(base) { case (r, t) => r + t.inlined }

  lazy val gradient: List[Real] =
    Gradient.derive(variables, density)
}

object Context {
  def apply(real: Real): Context = Context(real, Nil)
  def apply(targets: Iterable[Target]): Context = {
    val (base, batched) =
      targets.foldLeft((Real.zero, List.empty[Target])) {
        case ((b, l), t) =>
          t.maybeInlined match {
            case Some(r) => ((b + r), l)
            case None    => (b, t :: l)
          }
      }
    Context(base, batched)
  }
}
