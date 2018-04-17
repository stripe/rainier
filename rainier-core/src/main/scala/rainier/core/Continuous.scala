package rainier.core

import rainier.compute.{Real, Variable}
import rainier.sampler.RNG

trait Continuous extends Distribution[Double] {
  def param: RandomVariable[Real]
}

case class Normal(mean: Real, stddev: Real) extends Continuous {

  def logDensity(t: Double) =
    Distributions.normal(Real(t), mean, stddev)

  override def logDensities(list: Seq[Double]) = {
    val squaredErrors = list.map { t =>
      val err = t - mean
      err * err
    }
    (Real.sum(squaredErrors) / (stddev * stddev * Real(-2.0))) -
      (stddev.log * Real(list.size))
  }

  val generator = Generator.from { (r, n) =>
    r.standardNormal * n.toDouble(stddev) + n.toDouble(mean)
  }

  def param =
    Unbounded.param.flatMap { x =>
      val translated = x + mean
      RandomVariable(translated, Distributions.normal(translated, mean, stddev))
    }
}

case class Cauchy(x0: Real, beta: Real) extends Continuous {
  def logDensity(x: Double): Real =
    Distributions.cauchy(Real(x), x0, beta)

  def param =
    Unbounded.param.flatMap { x =>
      val translated = x + x0
      RandomVariable(translated, Distributions.cauchy(translated, x0, beta))
    }

  def generator = ???
}

case class LogNormal(mean: Real, stddev: Real) extends Continuous {
  def logDensity(t: Double) = {
    val err = Real(t).log - mean
    ((err * err) / (stddev * stddev * Real(-2.0))) - Real(t).log - stddev.log
  }

  def param = Normal(mean, stddev).param.map(_.exp)

  def generator = ???
}

case class Exponential(lambda: Real) extends Continuous {
  def logDensity(t: Double) = Distributions.exponential(Real(t), lambda)

  def param =
    NonNegative.param.flatMap { x =>
      val scaled = x / lambda
      RandomVariable(scaled, Distributions.exponential(scaled, lambda))
    }

  def generator = ???
}

case class StudentsT(nu: Real, mu: Real, sigma: Real) extends Continuous {

  /**
    * pdf(x) = (top / bottom) * rest
    *        = [ Γ((ν+1)/2) / ( Γ(ν/2) sqrt(πν) σ ) ] (1 + (1/ν)((x - μ) / σ)²)^{ -(ν+1)/2 }
    */
  def logDensity(t: Double) = {
    val top = Distributions.gamma((nu + 1) / 2)
    val bottom = Distributions.gamma(nu / 2) + Distributions.gamma(nu * Math.PI) / 2 + sigma.log
    val err = (t - mu) / sigma
    val rest = (Real.zero - ((nu + 1) / 2)) * (1 + (1 / nu) * err * err).log
    (top / bottom) * rest
  }

  def generator = ???
  def param = ???
}

case class Laplace(mean: Real, scale: Real) extends Continuous {
  def logDensity(t: Double) =
    Distributions.laplace(Real(t), mean, scale)

  def param =
    Unbounded.param.flatMap { x =>
      val translated = x + mean
      RandomVariable(translated, Distributions.laplace(translated, mean, scale))
    }

  def generator = ???
}

object Unbounded extends Continuous {
  def param = RandomVariable(new Variable)
  def logDensity(t: Double) = Real.one
  def generator = ???
}

object NonNegative extends Continuous {
  /*
   * To get a parameter which is only defined above zero, we use an unbounded parameter x
   * and transform it to e^x. Any non-linear transformation on a parameter that we are going to
   * later apply a prior to needs to add a correction term (a "jacobian"), which we include here
   * as the density. The correction comes from the change of variables formula from calculus.
   */
  def param = Unbounded.param.flatMap { x =>
    RandomVariable(x.exp, x)
  }

  def logDensity(t: Double) =
    if (t < 0)
      Real.zero
    else
      Real.one

  def generator = ???
}

case class Uniform(from: Real, to: Real) extends Continuous {
  /*
   * As with nonNegative, if we want a constrained parameter we need to transform an unbounded
   * one and then add a jacobian correction. Here we use a logistic function to produce
   * a parameter in (0,1).
   */
  def param =
    Unbounded.param.flatMap { x =>
      val standard = Real.one / (Real.one + (x * -1).exp)
      val density = (standard * (Real.one - standard)).log
      RandomVariable(from + (standard * (to - from)), density)
    }

  def logDensity(t: Double) =
    (Real.one / (to - from)).log

  def generator = ???
}
