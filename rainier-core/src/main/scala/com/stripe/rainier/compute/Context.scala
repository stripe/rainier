package com.stripe.rainier.compute

case class Context(base: Real,
                   batched: Real,
                   batches: Map[Variable, Array[Double]]) {
  val compiler: Compiler = IRCompiler(200, 100)

  val variables: List[Variable] = {
    val allVariables =
      RealOps.variables(base) ++
        RealOps.variables(batched) --
        batches.keySet
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
    val placeholders = batches.keys.toList
    val cf = compiler.compile(variables ++ placeholders, batched)

    val nBatches = batches.head._2.size
    val result = { input: Array[Double] =>
      0.until(nBatches)
        .map { i =>
          val extraInputs =
            placeholders.toArray.map { v =>
              batches(v)(i)
            }
          cf(input ++ extraInputs)
        }
        .sum
    }
    result
  }

  //these are for backwards compatibility to allow HMC to keep working
  //they will not keep working or make sense in the general case going forward
  lazy val density: Real = {
    require(batches.isEmpty)
    base
  }

  lazy val gradient: List[Real] = {
    require(batches.isEmpty)
    Gradient.derive(variables, base)
  }
}

object Context {
  def apply(real: Real): Context =
    Context(real, Real.zero, Map.empty)
}
