package com.stripe.rainier.core

import com.stripe.rainier.compute._

//multivariate continuous
trait Multivariate extends Distribution[Seq[Double]] {
  def size: Int
  def latent: Vec[Real]

  def logDensity(seq: Seq[Seq[Double]]): Real =
    Vec.from(seq).map(logDensity).columnize

  protected def logDensity(x: Vec[Real]): Real
}

class MVNormal private(val size: Int, locations: Vec[Real], cov: Covariance)
    extends Multivariate {
  require(cov.size == size)
  require(locations.size == size)

  def latent: Vec[Real] = ???

  protected def logDensity(x: Vec[Real]): Real =
    ((Real.Pi * 2).log +
      cov.logDeterminant +
      x.dot(cov.inverseMultiply(x))) / -2

  def generator = {
    val iidNormals = Normal.standard.generator.repeat(size).map(_.toArray)
    cov.choleskyGenerator.zip(iidNormals).map {
      case (a, z) => Cholesky.lowerTriangularMultiply(a, z)
    }
  }
}

object MVNormal {
  case class Builder(k: Int, locations: Vec[Real], scales: Vec[Real]) {
    def lkjCorrelation(eta: Real): MVNormal =
      new MVNormal(k, locations, LKJCholesky(eta, scales))
    def correlations(rho: Map[(Int,Int), Real]): MVNormal =
      new MVNormal(k, locations, RhoSigma(rho, scales))
  }

  def standard(k: Int): Builder = apply(k, 0, 1)
  def apply(k: Int, location: Real, scale: Real): Builder = 
    apply(k, Vec.from(List.fill(k)(location)), Vec.from(List.fill(k)(scale)))
  def apply(k: Int, locations: Continuous, scales: Continuous): Builder =
    apply(k, locations.latentVec(k), scales.latentVec(k))
  def apply(k: Int, locations: Vec[Real], scales: Vec[Real]): Builder =
    Builder(k, locations, scales)
}

private sealed trait Covariance {
  def size: Int

  //generates in packed lower-triangular representation
  def choleskyGenerator: Generator[Array[Double]]

  def logDeterminant: Real
  def inverseMultiply(x: Vec[Real]): Vec[Real]
}

private case class RhoSigma(rho: Map[(Int, Int), Real], sigmas: Vec[Real])
    extends Covariance {
  def size = sigmas.size

  def choleskyGenerator = ???

  def logDeterminant = ???
  def inverseMultiply(x: Vec[Real]) = ???
}

private case class LKJCholesky(eta: Real, sigmas: Vec[Real]) extends Covariance {
  def size = sigmas.size

  def cholesky: Cholesky = ???

  def choleskyGenerator = ???

  def logDeterminant = cholesky.logDeterminant
  def inverseMultiply(x: Vec[Real]): Vec[Real] =
    Vec.from(cholesky.inverseMultiply(x.toVector))
}