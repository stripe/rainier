package com.stripe.rainier.core

import com.stripe.rainier.compute.{If, Real}

trait Discrete extends Distribution[Int] {
  def logDensity(v: Real): Real
}

object Discrete {
  implicit val likelihood =
    Likelihood.from[Discrete, Int, Real] {
      case (d, v) => d.logDensity(v)
    }
}

/**
  * Discrete Constant (point mass) with expecation `constant`
  *
  * @param constant The integer value of the point mass
  */
final case class DiscreteConstant(constant: Real) extends Discrete {
  val generator: Generator[Int] =
    Generator.require(Set(constant)) { (c, n) =>
      n.toInt(constant)
    }

  def logDensity(v: Real): Real = {
    If(v - constant, Real.negInfinity, 0)
  }
}

/**
  * Bernoulli distribution with expectation `p`
  *
  * @param p The probability of success
  */
final case class Bernoulli(p: Real) extends Discrete {
  val generator: Generator[Int] =
    Generator.require(Set(p)) { (r, n) =>
      val u = r.standardUniform
      val l = n.toDouble(p)
      if (u <= l) 1 else 0
    }

  def logDensity(v: Real) =
    If(v, p.log, (1 - p).log)
}

/**
  * Geometric distribution with expectation `1/p`
  *
  * @param p The probability of success
  */
final case class Geometric(p: Real) extends Discrete {
  val generator: Generator[Int] =
    Generator.require(Set(p)) { (r, n) =>
      val u = r.standardUniform
      val q = n.toDouble(p)
      Math.floor(Math.log(u) / Math.log(1 - q)).toInt
    }

  def logDensity(v: Real) =
    p.log + v * (1 - p).log
}

/**
  * Negative Binomial distribution with expectation `n(1-p)/p`
  *
  * @param n Total number of failures
  * @param p Probability of success
  */
final case class NegativeBinomial(n: Real, p: Real) extends Discrete {
  val generator: Generator[Int] =
    Generator.require(Set(n, p)) { (r, m) =>
      (1 to m.toInt(n))
        .map({ x =>
          Geometric(p).generator.get(r, m)
        })
        .sum
    }

  def logDensity(v: Real) =
    Combinatorics.factorial(n + v - 1) - Combinatorics.factorial(v) -
      Combinatorics.factorial(n - 1) +
      n * p.log + v * (1 - p).log
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

/**
  * Discrete Mixture Distribution
  *
  * @param components Map of Discrete distribution and probabilities
  */
final case class DiscreteMixture(components: Map[Discrete, Real])
    extends Discrete {
  val generator: Generator[Int] =
    Categorical(components).generator.flatMap { d =>
      d.generator
    }

  def logDensity(v: Real): Real =
    Real
      .logSumExp(components.map {
        case (dist, weight) => {
          dist.logDensity(v) + weight.log
        }
      })
}

/**
  * Zero Inflated Poisson with expecation `psi*lambda`
  *
  * @param psi    The probability of non-zero count
  * @param lambda The expected (possibly non-zero) count value
  */
final case class ZeroInflatedPoisson(psi: Real, lambda: Real) extends Discrete {
  val support = DiscreteMixture(
    Map(DiscreteConstant(0) -> (1 - psi), Poisson(lambda) -> psi))
  val generator = support.generator

  def logDensity(v: Real) = support.logDensity(v)
}

/**
  * Zero Inflated Negative Binomial
  *
  * @param psi The probsbility of non-zero count
  * @param n   The number of failures
  * @param p   The probability of success
  */
final case class ZeroInflatedNegativeBinomial(psi: Real, n: Real, p: Real)
    extends Discrete {
  val support = DiscreteMixture(
    Map(DiscreteConstant(0) -> (1 - psi), NegativeBinomial(n, p) -> psi))
  val generator = support.generator

  def logDensity(v: Real) = support.logDensity(v)
}
