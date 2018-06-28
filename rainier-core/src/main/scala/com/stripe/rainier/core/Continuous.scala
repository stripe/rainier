package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG
import scala.annotation.tailrec

/**
  * A Continuous Distribution, with method `param` allowing conversion to a RandomVariable.
  */
trait BaseContinuous extends Distribution[Double] {
  def logDensity(t: Double): Real =
    realLogDensity(Real(t))

  def scale(a: Real): BaseContinuous = Scale(a).transform(this)
  def translate(b: Real): BaseContinuous = Translate(b).transform(this)
  def exp: BaseContinuous = Exp.transform(this)

  private[rainier] def realLogDensity(real: Real): Real

  def param: RandomVariable[Real]
}

/**
  * A Continuous Distribution that inherits its transforms from a Support object.
  */
trait Continuous extends BaseContinuous {
  private[rainier] val support: Support

  def param: RandomVariable[Real] = {
    val x = new Variable

    val transformed = support.transform(x)

    val logDensity = support.logJacobian(x) + realLogDensity(transformed)

    RandomVariable(transformed, logDensity)
  }
}

/**
  * Location-scale family distribution
  */
trait LocationScaleFamily { self =>
  def logDensity(x: Real): Real
  def generate(r: RNG): Double

  val standard: Continuous = new Continuous {
    val support: Support = RealSupport

    val generator: Generator[Double] =
      Generator.from { (r, n) =>
        generate(r)
      }
    def realLogDensity(real: Real): Real =
      self.logDensity(real)
  }

  def apply(location: Real, scale: Real): BaseContinuous =
    standard.scale(scale).translate(location)
}

/**
  * A Gaussian distribution with expectation `location` and standard deviation `scale`
  */
object Normal extends LocationScaleFamily {
  def logDensity(x: Real): Real =
    (x * x) / -2.0
  def generate(r: RNG): Double = r.standardNormal
}

/**
  * A Cauchy distribution with mode `location` and scaling relative to standard Cauchy of `scale`
  */
object Cauchy extends LocationScaleFamily {
  def logDensity(x: Real): Real =
    (((x * x) + 1) * Math.PI).log * -1
  def generate(r: RNG): Double =
    r.standardNormal / r.standardNormal
}

/**
  * A Laplace distribution with expectation `location` and variance `2*scale*scale`
  */
object Laplace extends LocationScaleFamily {
  def logDensity(x: Real): Real =
    Real(0.5).log - x.abs
  def generate(r: RNG): Double = {
    val u = r.standardUniform - 0.5
    Math.signum(u) * -1 * Math.log(1 - (2 * Math.abs(u)))
  }
}

/**
  * A Gamma distribution with expectation `shape*scale` and variance `shape*scale*scale`. N.B. It is parameterised with *scale* rather than *rate*, as is more typical in statistics texts.
  */
object Gamma {
  def apply(shape: Real, scale: Real): BaseContinuous =
    standard(shape).scale(scale)

  def standard(shape: Real): Continuous = new Continuous {
    val support = PositiveSupport

    def realLogDensity(real: Real): Real =
      If(real > 0,
         (shape - 1) * real.log -
           Combinatorics.gamma(shape) - real,
         Real.zero.log)

    def generator: Generator[Double] = Generator.require(Set(shape)) { (r, n) =>
      val a = n.toDouble(shape)
      if (a < 1) {
        val u = r.standardUniform
        generate(a + 1, r) * Math.pow(u, 1.0 / a)
      } else
        generate(a, r)
    }

    @tailrec
    private def generate(a: Double, r: RNG): Double = {
      val d = a - 1.0 / 3.0
      val c = (1.0 / 3.0) / Math.sqrt(d)

      var x = r.standardNormal
      var v = 1.0 + c * x
      while (v <= 0) {
        x = r.standardNormal
        v = 1.0 + c * x
      }

      val v3 = v * v * v
      val u = r.standardUniform

      if ((u < 1 - 0.0331 * x * x * x * x) ||
          (Math.log(u) < 0.5 * x * x + d * (1 - v3 + Math.log(v3))))
        d * v3
      else
        generate(a, r)
    }
  }
}

/**
  * An Exponential distribution with expectation `1/rate`
  */
object Exponential {
  val standard: Continuous = Gamma.standard(1.0)
  def apply(rate: Real): BaseContinuous =
    standard.scale(Real.one / rate)
}

/**
  * A Beta distribution with expectation `a/(a + b)` and variance `ab/((a + b)^2 (1 + a + b))`.
  */
final case class Beta(a: Real, b: Real) extends Continuous {
  val support = OpenUnitSupport

  def realLogDensity(real: Real): Real =
    If(real >= 0,
       If(real <= 1, betaDensity(real), Real.negInfinity),
       Real.negInfinity)

  val generator: Generator[Double] =
    Gamma(a, 1).generator.zip(Gamma(b, 1).generator).map {
      case (z1, z2) =>
        z1 / (z1 + z2)
    }

  private def betaDensity(u: Real): Real =
    (a - 1) *
      u.log + (b - 1) *
      (1 - u).log - Combinatorics.beta(a, b)

  def binomial: Predictor[Int, Int, BetaBinomial] = Predictor.from { k: Int =>
    BetaBinomial(a, b, k)
  }
}

object Beta {
  def meanAndPrecision(mean: Real, precision: Real) =
    Beta(mean * precision, (Real.one - mean) * precision)
  def meanAndVariance(mean: Real, variance: Real) =
    meanAndPrecision(mean, mean * (Real.one - mean) / variance - 1)
}

/**
  * A LogNormal distribution representing the exponential of a Gaussian random variable with expectation `location` and standard deviation `scale`. It therefore has expectation `exp(location + scale*scale/2)`.
  */
object LogNormal {
  def apply(location: Real, scale: Real): BaseContinuous =
    Normal(location, scale).exp
}

/**
  * A Uniform distribution over `[from,to]` with expectation `(to-from)/2`.
  */
object Uniform {
  val beta11 = Beta(1, 1)
  val standard: Continuous = new Continuous {
    val support = beta11.support

    def realLogDensity(real: Real): Real = beta11.realLogDensity(real)

    val generator: Generator[Double] =
      Generator.from { (r, n) =>
        r.standardUniform
      }
  }

  def apply(from: Real, to: Real): BaseContinuous =
    standard.scale(to - from).translate(from)
}
