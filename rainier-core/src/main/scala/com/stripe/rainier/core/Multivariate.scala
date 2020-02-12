package com.stripe.rainier.core

import com.stripe.rainier.compute._

//multivariate continuous
trait Multivariate extends Distribution[Seq[Double]] {
  def size: Int
  def latent: Vec[Real]

  def logDensity(seq: Seq[Seq[Double]]): Real =
    Vec.from(seq).map(logDensity).columnize

  def scale(vec: Vec[Real]): Multivariate =
    transform(vec.toList.map { a =>
      Scale(a)
    })

  def translate(vec: Vec[Real]): Multivariate =
    transform(vec.toList.map { a =>
      Translate(a)
    })

  def exp: Multivariate =
    transform(List.fill(size)(Exp))

  def logDensity(x: Vec[Real]): Real

  private def transform(injections: List[Injection]) =
    MVTransformed(this, injections)
}

case class Bivariate private (mv: Multivariate)
    extends Distribution[(Double, Double)] {
  def latent: (Real, Real) = {
    val vec2 = mv.latent
    (vec2(0), vec2(1))
  }

  def logDensity(seq: Seq[(Double, Double)]): Real =
    mv.logDensity(seq.map { case (a, b) => List(a, b) })

  def generator: Generator[(Double, Double)] =
    mv.generator.map { seq =>
      (seq(0), seq(1))
    }
}

case class MVNormal private (chol: Cholesky) extends Multivariate {
  def size = chol.size
  def latent = {
    val iidNormals = Normal.standard.latentVec(size)
    val correlated = chol.lowerTriangularMultiply(iidNormals.toVector)
    Vec.from(correlated)
  }

  def logDensity(x: Vec[Real]): Real =
    (size * ((Real.Pi * 2).log) +
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

  def apply(locations: Vec[Real],
            scales: Vec[Real],
            chol: Cholesky): Multivariate =
    MVNormal(chol)
      .scale(scales)
      .translate(locations)
}

object MVLogNormal {
  def standard(chol: Cholesky): Multivariate = MVNormal(chol).exp

  def apply(location: Real, scale: Real, chol: Cholesky): Multivariate =
    MVNormal(location, scale, chol).exp

  def apply(locations: Vec[Real],
            scales: Vec[Real],
            chol: Cholesky): Multivariate =
    MVNormal(locations, scales, chol).exp
}

object BivariateNormal {
  def standard(corr: Real): Bivariate = apply((0, 0), (1, 1), corr)
  def apply(locations: (Real, Real),
            scales: (Real, Real),
            corr: Real): Bivariate = {
    val diag = (Real.one - corr * corr).pow(0.5)
    val chol = Cholesky(Vector(Real.one, corr, diag))
    Bivariate(
      MVNormal(Vec(locations._1, locations._2),
               Vec(scales._1, scales._2),
               chol))
  }
}

case class LKJCorrelation(eta: Real, size: Int) {
  require(size > 1)
  def latent: Cholesky = {
    val nParams = Cholesky.triangleNumber(size - 1)
    val params = Real.parameters(nParams) { params =>
      Real.sum(params.toList.map { x =>
        support.logJacobian(x)
      }) +
        logDensity(solve(params))
    }
    solve(params)
  }

  private val support = BoundedSupport(-1, 1)

  private def solve(params: Vector[Parameter]): Cholesky = {
    val transformed = params.map { x =>
      support.transform(x)
    }
    val (_, packed) =
      0.until(size).toList.foldLeft((transformed, Vector.empty[Real])) {
        case ((in, out), i) =>
          val row = in.take(i)
          val diag = (Real.one - Real.sum(row.map(_.pow(2)))).pow(0.5)
          (in.drop(i), out ++ row :+ diag)
      }
    Cholesky(packed)
  }

  private def logDensity(chol: Cholesky) =
    Real.sum(chol.diagonals.zipWithIndex.tail.map {
      case (d, i) =>
        ((eta * 2) + size - i - 3) * d.log
    })
}

private case class MVTransformed(original: Multivariate,
                                 injections: List[Injection])
    extends Multivariate {
  def size = original.size

  def logDensity(x: Vec[Real]): Real = {
    val withInj = x.toList.zip(injections)
    val origX = Vec.from(withInj.map { case (v, inj) => inj.backwards(v) })
    val baseLogDensity = original.logDensity(origX)
    val logJacobian = Real.sum(withInj.map {
      case (v, inj) => inj.logJacobian(v)
    })
    baseLogDensity + logJacobian
  }

  def generator: Generator[Seq[Double]] = {
    val origGen = original.generator
    val allReqs = origGen.requirements ++ injections.flatMap { inj =>
      inj.requirements
    }.toSet
    Generator.require(allReqs) { (r, n) =>
      val orig = origGen.get(r, n)
      orig.zip(injections).map {
        case (o, inj) => inj.fastForwards(o, n)
      }
    }
  }

  def latent: Vec[Real] =
    Vec.from(
      original.latent.toList
        .zip(injections)
        .map { case (v, inj) => inj.forwards(v) })
}
