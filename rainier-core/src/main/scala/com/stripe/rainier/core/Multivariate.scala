package com.stripe.rainier.core

import com.stripe.rainier.compute._

//multivariate continuous
trait Multivariate extends Distribution[Seq[Double]] {
  def size: Int
  def latent: Vec[Real] = Real.parameters(size)(logDensity(_))

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

  protected def logDensity(x: Vec[Real]): Real = {
    val xn = x.zip(locations).map{case (a,b) => a-b}
    ((Real.Pi * 2).log +
      chol.logDeterminant +
      xn.dot(Vec.from(chol.inverseMultiply(xn.toVector)))) / -2
  }
  
  def generator = {
    val packedGen = Generator(chol.packed)
    val iidNormals = Normal.standard.generator.repeat(size).map(_.toArray)
    val locGen = Generator(locations)
    Generator((packedGen, iidNormals, locGen)).map{
      case (a, z, mu) =>
        Cholesky.lowerTriangularMultiply(a.toArray, z).zip(mu).map{
          case (l,r) => l+r
        }
    }
  }
}

object MVNormal {
  def apply(chol: Cholesky): MVNormal = MVNormal(Vec.from(List.fill(chol.rank)(Real.zero)), chol)
}

