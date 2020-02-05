package com.stripe.rainier.core

import com.stripe.rainier.compute._

case class MVNormal(k: Int, chol: Cholesky) extends Distribution[Seq[Double]] {
 def logDensity(seq: Seq[Seq[Double]]) = ???   
 def generator = Generator(chol.packed).zip(Generator(Normal.standard).repeat(k)).map{
     case (a, z) => Cholesky.lowerTriangularMultiply(a.toArray, z.toArray)
 }
}
