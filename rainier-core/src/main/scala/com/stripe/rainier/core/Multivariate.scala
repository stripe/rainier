package com.stripe.rainier.core

import com.stripe.rainier.compute._

case class MVNormal(k: Int, cov: Covariance) extends Distribution[Seq[Double]] {
 def logDensity(seq: Seq[Seq[Double]]) =
    Vec.from(seq).map(logDensity).columnize

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

trait Covariance {
    //generates in packed lower-triangular representation
    def choleskyGenerator: Generator[Array[Double]]

    def logDeterminant: Real
    def inverseMultiply(x: Vec[Real]): Vec[Real]
}

case class LKJCholesky(rank: Int, eta: Real) extends Covariance {
    val cholesky: Cholesky = ???

    val choleskyGenerator =
        Generator(cholesky.packed).map(_.toArray)

    val logDeterminant = cholesky.logDeterminant
    def inverseMultiply(x: Vec[Real]): Vec[Real] =
        Vec.from(cholesky.inverseMultiply(x.toVector))
}