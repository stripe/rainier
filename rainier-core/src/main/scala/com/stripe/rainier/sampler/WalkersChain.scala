package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final private case class WalkersChain(cf: Array[Double] => Double,
                                      walkers: Vector[Array[Double]],
                                      scores: Vector[Double],
                                      accepted: Boolean,
                                      walker: Int)(implicit rng: RNG) {

  val variables: Array[Double] = walkers(walker)
  val score: Double = scores(walker)

  def next: WalkersChain = {
    var target = rng.int(walkers.size)
    while (target == walker) target = rng.int(walkers.size)
    val (newWalker, newScore, newAccepted) = update(walkers(target))
    val nextWalker = (walker + 1) % walkers.size
    copy(walker = nextWalker,
         walkers = walkers.updated(walker, newWalker),
         scores = scores.updated(walker, newScore),
         accepted = newAccepted)
  }

  private def update(
      targetVariables: Array[Double]): (Array[Double], Double, Boolean) = {
    val z = Math.pow(rng.standardUniform + 1.0, 2) / 2.0

    val newVariables =
      variables.zip(targetVariables).map {
        case (from, to) =>
          to + (z * (from - to))
      }

    val newScore = cf(newVariables)
    val diff = (Math.log(z) * (variables.size - 1)) + newScore - score

    val accepted = Math.log(rng.standardUniform) < diff
    if (accepted)
      (newVariables, newScore, accepted)
    else
      (variables, score, accepted)
  }
}

private object WalkersChain {
  def apply(context: Context, nWalkers: Int)(
      implicit rng: RNG): WalkersChain = {
    val cf = context.compileDensity
    val walkers = 1
      .to(nWalkers)
      .map { _ =>
        1.to(context.variables.size)
          .map { _ =>
            rng.standardNormal
          }
          .toArray
      }
      .toVector
    val scores = walkers.map(cf)
    WalkersChain(cf, walkers, scores, true, 0)
  }
}
