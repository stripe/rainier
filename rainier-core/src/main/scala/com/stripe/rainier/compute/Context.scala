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
  def compileDensity: Array[Double] => Double = {
    val batchedVariables = batched.flatMap(_.placeholderVariables)
    val updaters = batched.map(_.updater)
    val updaterVariables = updaters.flatMap(_._2)
    val inputVars = variables ++ batchedVariables ++ updaterVariables
    val outputs = base :: batched.map(_.real).toList ++ updaters
      .map(_._1)
      .toList
    val cf = compiler.compileUnsafe(inputVars, outputs)

    { inputs: Array[Double] =>
      val globals = new Array[Double](cf.numGlobals)
      //collect the log-prior from the `base` output
      var output = cf.output(inputs, globals, 0)
      val fullInputs = new Array[Double](inputVars.size)
      System.arraycopy(inputs, 0, fullInputs, 0, inputs.size)
      //for each of the targets, in order, collect the log-likelihood from the
      //first observation, thereby also inializing any needed global state
      batched.zipWithIndex.foreach {
        case (target, j) =>
          var i = inputs.size
          target.placeholderVariables.foreach { v =>
            fullInputs(i) = target.placeholders(v)(0)
            i += 1
          }
          output += cf.output(fullInputs, globals, j + 1)
      }
      //for each of the targets, in turn, collect the log-likelihood from each of
      //their remaining observations
      batched.zipWithIndex.foreach {
        case (target, j) =>
          var k = 1
          while (k < target.nRows) {
            var i = inputs.size + batchedVariables.size
            target.placeholderVariables.foreach { v =>
              fullInputs(i) = target.placeholders(v)(k)
              i += 1
            }
            output += cf.output(fullInputs, globals, j + batched.size + 1)
            k += 1
          }
      }
      output
    }
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
