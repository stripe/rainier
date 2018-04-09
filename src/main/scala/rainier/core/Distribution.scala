package rainier.core

import rainier.compute.Real
import rainier.sampler.RNG

trait Distribution[T] extends Likelihood[T] { self =>
  def logDensity(t: T): Real
  def logDensities(list: Seq[T]): Real = Real.sum(list.map(logDensity))

  def generator: Generator[T]

  def fit(t: T) = RandomVariable(generator, logDensity(t))
  override def fit(list: Seq[T]) =
    RandomVariable(generator.repeat(list.size), logDensities(list))
}

case class Poisson(lambda: Real) extends Distribution[Int] {
  def logDensity(t: Int): Real = {
    lambda.log * t - lambda - Distributions.factorial(t)
  }

  val generator = Generator.from { (r, n) =>
    val l = math.exp(-n.toDouble(lambda))
    var k = 0
    var p = 1.0
    while (p > l) {
      k += 1
      p *= r.standardUniform
    }
    k - 1
  }
}

object Distributions {
  def gamma(z: Real): Real = {
    val w = z + (Real.one / ((12 * z) - (Real.one / (10 * z))))
    (Real(Math.PI * 2).log / 2) - (z.log / 2) + (z * (w.log - 1))
  }

  def beta(a: Real, b: Real): Real =
    gamma(a) + gamma(b) - gamma(a + b)

  def factorial(k: Int): Real = gamma(Real(k + 1))

  def choose(n: Int, k: Int): Real =
    factorial(n) - factorial(k) - factorial(n - k)

  def normal(x: Real, mean: Real, stddev: Real): Real = {
    val err = x - mean
    ((err * err) / (stddev * stddev * Real(-2.0))) - stddev.log
  }

  def laplace(x: Real, mean: Real, scale: Real): Real =
    (Real.one / (scale * 2.0)).log + ((x - mean).abs / (scale * -1))

  def cauchy(x: Real, x0: Real, beta: Real): Real = {
    val err = x - x0
    beta.log -
      (((err * err) + (beta * beta)) * Math.PI).log
  }

  def exponential(x: Real, lambda: Real) =
    lambda.log - (lambda * x)
}
