package com.stripe.rainier.sampler

/**
  * Compute a distribution of number of leapfrog steps required to make
  * a u-turn using a tuned value of the step size eps
  * @param l0 the initial number of leapfrog steps
  * @param eps the tuned leapfrog step size
  */
object LongestBatch {

  /**
    * Calculate the longest-step size until a u-turn
    */
  def longestBatch(params: Array[Double],
                   l0: Int,
                   eps: Double,
                   lf: LeapFrog) = {

    val initTheta = lf.variables(params)
    var out = params
    var pq = params
    var l = 0
    while (LongestBatch.uturnCondition(initTheta,
                                       lf.variables(pq),
                                       lf.momentum(pq))) {
      l += 1
      val pqPlus = lf.leapfrogs(1, eps, pq)
      if (l == l0) {
        out = pqPlus
        pq = pqPlus
      } else {
        pq = pqPlus
      }

    }
    (out, l)
  }

  /**
    * Perform a single step of the longest batch step algorithm
    */
  def longestBatchStep(l0: Int, eps: Double, lf: LeapFrog)(
      thetal: (Array[Double], Int))(implicit rng: RNG): (Array[Double], Int) = {

    val params = thetal._1
    lf.initializePs(params)
    val (tp, l) = longestBatch(params, l0, eps, lf)
    val prop = if (l < l0) {
      lf.leapfrogs(l0 - l, eps, tp)
    } else {
      tp
    }
    val u = rng.standardUniform
    val a = lf.logAcceptanceProb(params, prop)
    if (math.log(u) < a) {
      (prop, l)
    } else {
      (params, l)
    }
  }

  /**
    * Calculate a vector representing the empirical distribution
    * of the steps taken until a u-turn
    */
  def run(l0: Int,
          eps: Double,
          iterations: Int,
          lf: LeapFrog,
          params: Array[Double])(implicit rng: RNG): Vector[Int] = {

    Vector
      .iterate((params, l0), iterations)(longestBatchStep(l0, eps, lf))
      .map(_._2)
  }

  def uturnCondition(theta: Array[Double],
                     thetaP: Array[Double],
                     phiP: Array[Double]): Boolean = {

    var out = 0.0
    var i = 0
    while (i < theta.size) {
      out += (thetaP(i) - theta(i)) * phiP(i)
      i += 1
    }

    if (out.isNaN)
      false
    else
      out >= 0
  }

  def discreteUniform(min: Int, max: Int)(implicit rng: RNG) = {
    math.floor(rng.standardUniform * (max - min) + min).toInt
  }
}
