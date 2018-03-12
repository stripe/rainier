package rainier.sampler

import rainier.compute._

case class TreeParams(minus: HParams,
                      plus: HParams,
                      candidates: Set[HParams],
                      keepGoing: Boolean) {
  def noUTurn: Boolean = NUTSTree.noUTurn(minus, plus)
}

sealed trait Direction { def toDouble: Double }
case object Forward extends Direction { def toDouble = 1D }
case object Backward extends Direction { def toDouble = -1D }

object NUTSTree {

  def dot(v: Seq[Double], w: Seq[Double]) =
    v.zip(w).map { case (a, b) => a * b }.sum

  def noUTurn(minusParams: HParams, plusParams: HParams): Boolean = {
    val dQ = plusParams.qs.zip(minusParams.qs).map { case (a, b) => a - b }
    (dot(dQ, minusParams.ps) >= 0) && (dot(dQ, plusParams.ps) >= 0)
  }

  def buildTreeRoot(hParams: HParams,
                    logSlice: Double,
                    direction: Direction,
                    stepSize: Double,
                    integrator: HamiltonianIntegrator): TreeParams = {
    val nextParams = integrator.step(hParams, direction.toDouble * stepSize)
    val candidates =
      if (logSlice < nextParams.hamiltonian) { Set(nextParams) } else {
        Set[HParams]()
      }
    val keepGoing = logSlice < nextParams.hamiltonian + 1000
    TreeParams(nextParams, nextParams, candidates, keepGoing)
  }

  def buildNextTree(minus: HParams,
                    plus: HParams,
                    logSlice: Double,
                    direction: Direction,
                    stepSize: Double,
                    depth: Int,
                    integrator: HamiltonianIntegrator): TreeParams = {
    direction match {
      case Backward =>
        buildTree(minus, logSlice, direction, stepSize, depth, integrator)
          .copy(plus = plus)
      case Forward =>
        buildTree(plus, logSlice, direction, stepSize, depth, integrator)
          .copy(minus = minus)
    }
  }

  def buildTree(hParams: HParams,
                logSlice: Double,
                direction: Direction,
                stepSize: Double,
                depth: Int,
                integrator: HamiltonianIntegrator): TreeParams = {
    if (depth == 0) {
      buildTreeRoot(hParams, logSlice, direction, stepSize, integrator)
    } else {
      val treeParams =
        buildTree(hParams, logSlice, direction, stepSize, depth - 1, integrator)
      if (treeParams.keepGoing) {
        val nextTreeParams =
          buildNextTree(treeParams.minus,
                        treeParams.plus,
                        logSlice,
                        direction,
                        stepSize,
                        depth - 1,
                        integrator)
        nextTreeParams.copy(
          candidates = treeParams.candidates.union(nextTreeParams.candidates),
          keepGoing =
            treeParams.keepGoing
              && nextTreeParams.keepGoing
              && nextTreeParams.noUTurn
        )
      } else { treeParams }
    }
  }
}
