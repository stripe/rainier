package com.stripe.rainier.core

import com.stripe.rainier.compute._

//multivariate continuous
trait Multivariate extends Distribution[Seq[Double]] {
  def size: Int
  def latent: Vec[Real]

  def logDensity(seq: Seq[Seq[Double]]): Real =
    Vec.from(seq).map(logDensity).columnize

  def scale(vec: Vec[Real]): Multivariate =
    transform(vec.toList.map{a => Scale(a)})

  def translate(vec: Vec[Real]): Multivariate =
    transform(vec.toList.map{a => Translate(a)})
  
  def exp: Multivariate =
    transform(List.fill(size)(Exp))

  def logDensity(x: Vec[Real]): Real

  private def transform(injections: List[Injection]) =
    MVTransformed(this, injections)
}

case class MVNormal private(chol: Cholesky) extends Multivariate {
  def size = chol.size
  def latent = {
    val iidNormals = Normal.standard.latentVec(size)
    val correlated = chol.lowerTriangularMultiply(iidNormals.toVector)
    Vec.from(correlated)
  }

  def logDensity(x: Vec[Real]): Real =
    ((Real.Pi * 2).log +
      chol.logDeterminant +
      x.dot(Vec.from(chol.inverseMultiply(x.toVector)))) / -2

  def generator = {
    val packedGen = Generator(chol.packed)
    val iidNormals = Normal.standard.generator.repeat(size).map(_.toArray)
    Generator((packedGen, iidNormals)).map {
      case (a, z) =>
        Cholesky.lowerTriangularMultiply(a.toArray, z)
    }
  }
}

object MVNormal {
  def standard(chol: Cholesky): Multivariate = apply(chol)

  def apply(location: Real, scale: Real, chol: Cholesky): Multivariate =
    MVNormal(chol)
      .scale(Vec.from(List.fill(chol.size)(scale)))
      .translate(Vec.from(List.fill(chol.size)(location)))

  def apply(locations: Vec[Real], scales: Vec[Real], chol: Cholesky): Multivariate =
    MVNormal(chol)
      .scale(scales)
      .translate(locations)
}

case class LKJCorrelation(eta: Real, size: Int) {
  require(size > 1)
  def latent: Cholesky = {
    val nParams = Cholesky.triangleNumber(size - 1)
    val params = Real.parameters(nParams){params =>
      Real.sum(params.toList.map{x => support.logJacobian(x)}) +
        logDensity(solve(params))
    }
    solve(params)
  }

  private val support = BoundedSupport(-1,1)

  private def solve(params: Vec[Parameter]): Cholesky = {
    val transformed = params.toList.map{x => support.transform(x)}
    ???
  }
  
  private def logDensity(chol: Cholesky) = ???
}

private case class MVTransformed(original: Multivariate, injections: List[Injection])
  extends Multivariate {
    def size = original.size

    def logDensity(x: Vec[Real]): Real = {
      val withInj = x.toList.zip(injections)
      val origX = Vec.from(withInj.map{case (v,inj) => inj.backwards(v)})
      val baseLogDensity = original.logDensity(origX)
      val logJacobian = Real.sum(withInj.map{case (v,inj) => inj.logJacobian(v)})
      baseLogDensity + logJacobian
    }

    def generator: Generator[Seq[Double]] = {
      val origGen = original.generator
      val allReqs = origGen.requirements ++ injections.flatMap{inj => inj.requirements}.toSet
      Generator.require(allReqs){(r, n) => 
        val orig = origGen.get(r, n)
        orig.zip(injections).map{
          case (o,inj) => inj.fastForwards(o, n)
        }
      }
    }

    def latent: Vec[Real] =
      Vec.from(original
        .latent
        .toList
        .zip(injections)
        .map{case (v,inj) => inj.forwards(v)})
}
