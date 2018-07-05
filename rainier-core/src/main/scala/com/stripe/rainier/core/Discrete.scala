package com.stripe.rainier.core

import com.stripe.rainier.compute.Real

trait Discrete extends Distribution[Int] {
  def logDensity(v: Real): Real
}

object Discrete {
  implicit val likelihood =
    Likelihood.placeholder[Discrete, Int, Real] {
      case (d, v) => d.logDensity(v)
    }
}

/**
  * Poisson distribution with expectation `lambda`
  *
  * @param lambda The mean of the Poisson distribution
  */
final case class Poisson(lambda: Real) extends Discrete {
  val generator: Generator[Int] =
    Generator.require(Set(lambda)) { (r, n) =>
      val l = math.exp(-n.toDouble(lambda))
      if (l >= 1.0) { 0 } else {
        var k = 0
        var p = 1.0
        while (p > l) {
          k += 1
          p *= r.standardUniform
        }
        k - 1
      }
    }

  def logDensity(v: Real) =
    lambda.log * v - lambda - Combinatorics.factorial(v)
}

/**
  * A Binomial distribution with expectation `k*p`
  *
  * @param p The probability of success
  * @param k The number of trials
  */
final case class Binomial(p: Real, k: Real) extends Discrete {
  val multi: Multinomial[Boolean] =
    Multinomial(Map(true -> p, false -> (1 - p)), k)

  def generator: Generator[Int] = {
    val kGenerator = Generator.real(k)
    val poissonGenerator =
      Poisson(p * k).generator
        .zip(kGenerator)
        .map { case (x, kd) => x.min(kd.toInt) }
    val normalGenerator =
      Normal(k * p, k * p * (1 - p)).generator
        .zip(kGenerator)
        .map {
          case (x, kd) => x.toInt.max(0).min(kd.toInt)
        }
    val binomialGenerator = multi.generator.map { m =>
      m.getOrElse(true, 0)
    }

    Generator.from {
      case (r, n) =>
        val pDouble = n.toDouble(p)
        val kDouble = n.toDouble(k)
        if (kDouble >= 100 && kDouble * pDouble <= 10) {
          poissonGenerator.get(r, n)
        } else if (kDouble >= 100 && kDouble * pDouble >= 9 && kDouble * (1.0 - pDouble) >= 9) {
          normalGenerator.get(r, n)
        } else { binomialGenerator.get(r, n) }
    }

  }

  def logDensity(v: Real): Real =
    Multinomial.logDensity(multi, Map(true -> v, false -> (k - v)))
}

/**
  * A Beta-binomial distribution with expectation `a/(a + b) * k`
  *
  */
final case class BetaBinomial(a: Real, b: Real, k: Real) extends Discrete {
  val generator: Generator[Int] =
    Beta(a, b).generator.flatMap { p =>
      Binomial(p, k).generator
    }

  def logDensity(v: Real) =
    Combinatorics.choose(k, v) +
      Combinatorics.beta(a + v, k - v + b) -
      Combinatorics.beta(a, b)
}
