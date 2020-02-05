package com.stripe.rainier.core

import com.stripe.rainier.compute._

case class MVNormal(k: Int, chol: Cholesky) extends Distribution[Seq[Double]] {
 def logDensity(seq: Seq[Seq[Double]]) =
    Vec.from(seq).map{x =>
        ((Real.Pi * 2).log + 
        chol.logDeterminant + 
        x.dot(Vec.from(chol.inverseMultiply(x.toVector)))) / -2
    }.columnize

  def generator = Generator(chol.packed).zip(Generator(Normal.standard).repeat(k)).map{
     case (a, z) => Cholesky.lowerTriangularMultiply(a.toArray, z.toArray)
 }
}
