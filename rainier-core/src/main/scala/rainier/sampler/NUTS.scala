package rainier.sampler

import rainier.compute._

case class NUTS(treeParams: TreeParams,
                logSlice: Double,
                depth: Int,
                stepSize: Double,
                integrator: HamiltonianIntegrator,
                maxDepth: Int)(implicit rng: RNG) {

  def next: NUTS = {

    val direction = if (rng.standardUniform < 0.5) Backward else Forward
    val nextTreeParams = NUTSTree.buildNextTree(treeParams.minus,
                                                treeParams.plus,
                                                logSlice,
                                                direction,
                                                stepSize,
                                                depth,
                                                integrator)
    val nextCandidates = if (nextTreeParams.keepGoing) {
      treeParams.candidates.union(nextTreeParams.candidates)
    } else { treeParams.candidates }
    copy(
      treeParams = nextTreeParams.copy(
        keepGoing =
          nextTreeParams.keepGoing
            && nextTreeParams.noUTurn
            && depth <= maxDepth,
        candidates = nextCandidates
      ),
      depth = depth + 1
    )
  }

  def toStream: Stream[NUTS] = this #:: next.toStream

  def run(implicit rng: RNG): HParams = {
    val finalNUTS =
      toStream.dropWhile(_.treeParams.keepGoing).head
    val candidates = finalNUTS.treeParams.candidates
    val index = (rng.standardUniform * candidates.size).toInt
    candidates.toList(index)
  }
}

object NUTS {

  def apply(hParams: HParams,
            stepSize: Double,
            integrator: HamiltonianIntegrator,
            maxDepth: Int)(implicit rng: RNG): NUTS = {
    NUTS(
      treeParams = TreeParams(hParams, hParams, Set(hParams), true),
      logSlice = Math.log(rng.standardUniform) + hParams.hamiltonian,
      depth = 0,
      stepSize = stepSize,
      integrator = integrator,
      maxDepth = maxDepth
    )
  }
}
