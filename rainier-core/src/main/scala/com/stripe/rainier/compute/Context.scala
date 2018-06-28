package com.stripe.rainier.compute

case class Context(targets: Set[Target]) {
  val compiler: Compiler = IRCompiler(200, 100)

  val variables: List[Variable] = {
    val allVariables: List[Variable] = targets.flatMap(_.variables).toList
    allVariables.sortBy(_.param.sym.id)
  }

  private val withoutPlaceholders =
    targets.filter(_.placeholders.isEmpty)
  private val withPlaceholders =
    targets.filterNot(_.placeholders.isEmpty)

  //convenience method for simple cases like Walkers
  def compileDensity: Array[Double] => Double = {
    val baseDensity = Real.sum(withoutPlaceholders.map(_.toReal).toList)
    val baseCF = compiler.compile(variables, baseDensity)
    val phCFs = withPlaceholders.map { t =>
      compileTarget(t)
    }
    val result = { input: Array[Double] =>
      baseCF(input) + phCFs.map { cf =>
        cf(input)
      }.sum
    }
    result
  }

  //not terribly efficient
  private def compileTarget(target: Target): Array[Double] => Double = {
    val placeholders = target.placeholders.keys.toList
    val cf = compiler.compile(variables ++ placeholders, target.toReal)

    val nBatches = target.placeholders.head._2.size
    val result = { input: Array[Double] =>
      0.until(nBatches)
        .map { i =>
          val extraInputs =
            placeholders.toArray.map { v =>
              target.placeholders(v)(i)
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
    require(withPlaceholders.isEmpty)
    Real.sum(targets.map(_.toReal).toList)
  }

  lazy val gradient: List[Real] = {
    require(withPlaceholders.isEmpty)
    targets
      .map { t =>
        Gradient.derive(variables, t.toReal)
      }
      .reduce { (l1, l2) =>
        l1.zip(l2).map { case (g1, g2) => g1 + g2 }
      }
  }
}

class Target(val toReal: Real, val placeholders: Map[Variable, Array[Double]]) {
  val variables: Set[Variable] =
    RealOps.variables(toReal) -- placeholders.keySet
}
