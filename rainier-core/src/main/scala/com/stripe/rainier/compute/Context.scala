package com.stripe.rainier.compute

case class Context(base: Real, batched: Target) {
  val compiler: Compiler = IRCompiler(200, 100)

  val variables: List[Variable] = {
    val allVariables =
      RealOps.variables(base) ++
        RealOps.variables(batched.real) --
        batched.placeholderVariables
    allVariables.toList.sortBy(_.param.sym.id)
  }

  //convenience method for simple cases like Walkers
  def compileDensity: Array[Double] => Double = {
    val baseCF = compiler.compile(variables, base)
    val batchCF = compileBatched

    val result = { input: Array[Double] =>
      baseCF(input) + batchCF(input)
    }
    result
  }

  //not terribly efficient
  private def compileBatched: Array[Double] => Double = {
    if (batched.nRows == 0) {
      compiler.compile(variables, base + batched.real)
    } else {
      val cf = compiler.compile(variables ++ batched.placeholderVariables,
                                batched.real)

      val result = { input: Array[Double] =>
        0.until(batched.nRows)
          .map { i =>
            val extraInputs =
              batched.placeholderVariables.map { v =>
                batched.placeholders(v)(i)
              }.toArray
            cf(input ++ extraInputs)
          }
          .sum
      }
      result
    }
  }

  //these are for backwards compatibility to allow HMC to keep working
  //they will not make sense in the general case going forward
  lazy val density: Real = base + batched.inlined

  lazy val gradient: List[Real] =
    Gradient.derive(variables, density)
}

object Context {
  def apply(real: Real): Context = Context(real, Target.empty)
  def apply(nBatches: Int, targets: Iterable[Target]): Context = {
    val (base, batched, batches) =
      targets
        .foldLeft((Real.zero, Real.zero, Map.empty[Variable, Array[Double]])) {
          case ((oldBase, oldBatched, oldBatches), target) =>
            val (batchedTarget, remainder) = target.batched(nBatches)
            (oldBase + remainder,
             oldBatched + batchedTarget.real,
             oldBatches ++ batchedTarget.placeholders)
        }
    Context(base, new Target(batched, batches))
  }
}
