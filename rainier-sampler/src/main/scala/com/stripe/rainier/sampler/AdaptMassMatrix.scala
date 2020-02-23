package com.stripe.rainier.sampler

case class AdaptFullMassMatrix(iterations: Int) extends Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit = {
    state.startPhase("Adapting mass matrix", iterations)

    val covEst = new CovarianceEstimator(state.nVars)
    var i = 0
    val buf = new Array[Double](state.nVars)
    while (i < iterations) {
      state.step()
      state.variables(buf)
      covEst.update(buf)
      i += 1
    }
    state.updateMetric(EuclideanMetric(covEst.close()))
  }
}

case class AdaptDiagonalMassMatrix(iterations: Int) extends Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit = {
    state.startPhase("Adapting mass matrix", iterations)

    val varEst = new VarianceEstimator(state.nVars)
    var i = 0
    val buf = new Array[Double](state.nVars)
    while (i < iterations) {
      state.step()
      state.variables(buf)
      varEst.update(buf)
      i += 1
    }
    state.updateMetric(DiagonalMetric(varEst.close()))
  }
}
