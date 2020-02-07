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

object Multivariate {
  def lkjCorrelation(sigmas: Vec[Real], eta: Real): Cholesky = ???
}

case class MVNormal(locations: Vec[Real], chol: Cholesky)
    extends Multivariate {
  require(chol.rank == locations.size)

  def size = locations.size
  def latent: Vec[Real] = ???

  protected def logDensity(x: Vec[Real]): Real =
    ((Real.Pi * 2).log +
      chol.logDeterminant +
      x.dot(Vec.from(chol.inverseMultiply(x.toVector)))) / -2

  def generator = {
    val iidNormals = Normal.standard.generator.repeat(size).map(_.toArray)
    val packed = Generator(chol.packed)
    packed.zip(iidNormals).map {
      case (a, z) => Cholesky.lowerTriangularMultiply(a.toArray, z)
    }
  }
}

object MVNormal {
  def apply(chol: Cholesky): MVNormal = MVNormal(Vec.from(List.fill(chol.rank)(Real.zero)), chol)
}

