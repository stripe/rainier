package com.stripe.rainier.core

import com.stripe.rainier.compute._

//multivariate continuous
trait Multivariate extends Distribution[Seq[Double]] {
    def k: Int
    def rv: Vec[Real]

    def logDensity(seq: Seq[Seq[Double]]) =
        Vec.from(seq).map(logDensity).columnize

    def logDensity(x: Vec[Real]): Real
}

case class MVNormal(k: Int, locations: Vec[Real], cov: Covariance) extends Multivariate {
 def rv: Vec[Real] = ???

  def logDensity(x: Vec[Real]) = 
    ((Real.Pi * 2).log + 
    cov.logDeterminant + 
    x.dot(cov.inverseMultiply(x))) / -2

  def generator = {
    val iidNormals = Normal.standard.generator.repeat(k).map(_.toArray)
    cov.choleskyGenerator.zip(iidNormals).map{
     case (a, z) => Cholesky.lowerTriangularMultiply(a, z)
    }
  }    
}

object MVNormal {
    def eta(k: Int, eta: Real): MVNormal = 
        MVNormal(k, LKJCholesky(eta, Vec.from(List.fill(k)(1))))
    def eta(k: Int, eta: Real, sigma: Continuous): MVNormal = 
        MVNormal(k, LKJCholesky(eta, sigma.vec(k)))
    def rho(k: Int, rho: Map[(Int,Int),Real]): MVNormal =
        MVNormal(k, RhoSigma(rho, Vec.from(List.fill(k)(1))))
    def rho(k: Int, rho: Map[(Int,Int),Real], sigma: Continuous): MVNormal =
        MVNormal(k, RhoSigma(rho, sigma.vec(k)))
}

trait Covariance {
    //generates in packed lower-triangular representation
    def choleskyGenerator: Generator[Array[Double]]

    def logDeterminant: Real
    def inverseMultiply(x: Vec[Real]): Vec[Real]
}

case class RhoSigma(rho: Map[(Int,Int),Real], sigmas: Vec[Real]) extends Covariance {
    def choleskyGenerator = ???

    def logDeterminant = ???
    def inverseMultiply(x: Vec[Real]) = ???
}

case class LKJCholesky(eta: Real, sigmas: Vec[Real]) extends Covariance {
    val cholesky: Cholesky = ???

    val choleskyGenerator =
        Generator(cholesky.packed).map(_.toArray)

    val logDeterminant = cholesky.logDeterminant
    def inverseMultiply(x: Vec[Real]): Vec[Real] =
        Vec.from(cholesky.inverseMultiply(x.toVector))
}