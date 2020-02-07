package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG
import scala.annotation.tailrec

/**
  * A Continuous Distribution, with method `real` allowing conversion to a random variable.
  */
trait Continuous extends Distribution[Double] {
  private[rainier] val support: Support

  def logDensity(seq: Seq[Double]) = Vec.from(seq).map(logDensity).columnize
  def logDensity(x: Real): Real

  def scale(a: Real): Continuous = Scale(a).transform(this)
  def translate(b: Real): Continuous = Translate(b).transform(this)
  def exp: Continuous = Exp.transform(this)

  def latent: Real
  def latentVec(k: Int) = Vec.from(List.fill(k)(latent))
}

/**
  * A Continuous Distribution that inherits its transforms from a Support object.
  */
private[rainier] trait StandardContinuous extends Continuous {
  def latent: Real = {
    val x = Real.parameter { x =>
      support.logJacobian(x) + logDensity(support.transform(x))
    }
    support.transform(x)
  }
}

/**
  * Location-scale family distribution
  */
trait LocationScaleFamily { self =>
  def logDensity(x: Real): Real
  def generate(r: RNG): Double

  val standard: StandardContinuous = new StandardContinuous {
    val support: Support = UnboundedSupport

    val generator: Generator[Double] =
      Generator.from { (r, _) =>
        generate(r)
      }
    def logDensity(real: Real): Real =
      self.logDensity(real)
  }

  def apply(location: Real, scale: Real): Continuous = {
    Bounds.check(scale, "σ >= 0")(_ >= 0.0)
    standard.scale(scale).translate(location)
  }
}

/**
  * A Gaussian distribution with expectation `location` and standard deviation `scale`
  */
object Normal extends LocationScaleFamily {
  def logDensity(x: Real): Real =
    ((x * x) / -2.0) - 0.5 * Real(2 * math.Pi).log
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
  def apply(shape: Real, scale: Real): Continuous = {
    Bounds.check(scale, "θ > 0")(_ >= 0.0)
    standard(shape).scale(scale)
  }

  def meanAndScale(mean: Real, scale: Real): Continuous =
    Gamma(mean / scale, scale)

  def standard(shape: Real): StandardContinuous = {
    Bounds.check(shape, "k > 0")(_ >= 0.0)
    new StandardContinuous {
      val support = BoundedBelowSupport(Real.zero)

      def logDensity(real: Real): Real =
        Bounds.positive(real) {
          (shape - 1) * real.log -
            Combinatorics.gamma(shape) - real
        }

      def generator: Generator[Double] = Generator.require(Set(shape)) {
        (r, n) =>
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
}

/**
  * An Exponential distribution with expectation `1/rate`
  */
object Exponential {
  val standard: Continuous = Gamma.standard(1.0)
  def apply(rate: Real): Continuous = {
    Bounds.check(rate, "λ >= 0")(_ >= 0.0)
    standard.scale(Real.one / rate)
  }
}

/**
  * A Beta distribution with expectation `a/(a + b)` and variance `ab/((a + b)^2 (1 + a + b))`.
  */
final case class Beta(a: Real, b: Real) extends StandardContinuous {
  Bounds.check(a, "α >= 0")(_ >= 0.0)
  Bounds.check(b, "β >= 0")(_ >= 0.0)

  val support = new BoundedSupport(Real.zero, Real.one)

  def logDensity(real: Real): Real =
    Bounds.zeroToOne(real)(betaDensity(real))

  val generator: Generator[Double] =
    Gamma(a, 1).generator.zip(Gamma(b, 1).generator).map {
      case (z1, z2) =>
        z1 / (z1 + z2)
    }

  private def betaDensity(u: Real): Real =
    (a - 1) *
      u.log + (b - 1) *
      (1 - u).log - Combinatorics.beta(a, b)
}

object Beta {
  def meanAndPrecision(mean: Real, precision: Real): Beta =
    Beta(mean * precision, (Real.one - mean) * precision)
  def meanAndVariance(mean: Real, variance: Real): Beta =
    meanAndPrecision(mean, mean * (Real.one - mean) / variance - 1)
}

/**
  * A LogNormal distribution representing the exponential of a Gaussian random variable with expectation `location` and standard deviation `scale`. It therefore has expectation `exp(location + scale*scale/2)`.
  */
object LogNormal {
  def apply(location: Real, scale: Real): Continuous =
    Normal(location, scale).exp
}

/**
  * A Uniform distribution over `[from,to]` with expectation `(to-from)/2`.
  */
object Uniform {
  val beta11: Beta = Beta(1, 1)
  val standard: Continuous = new StandardContinuous {
    val support = beta11.support

    def logDensity(real: Real): Real = beta11.logDensity(real)
    val generator: Generator[Double] =
      Generator.from { (r, _) =>
        r.standardUniform
      }
  }

  def apply(from: Real, to: Real): Continuous =
    standard.scale(to - from).translate(from)
}

case class Mixture(components: Map[Continuous, Real]) extends Continuous {
  components.values.foreach { r =>
    Bounds.check(r, "0 <= p <= 1") { p =>
      p >= 0.0 && p <= 1.0
    }
  }

  def generator: Generator[Double] =
    Generator.categorical(components).flatMap { d =>
      d.generator
    }

  val support = Support.union(components.keys.map {
    _.support
  })

  def logDensity(real: Real): Real =
    Real
      .logSumExp(components.map {
        case (dist, weight) => {
          dist.logDensity(real) + weight.log
        }
      })

  def latent: Real = {
    val x = Real.parameter { x =>
      support.logJacobian(x) + logDensity(support.transform(x))
    }
    support.transform(x)
  }
}
