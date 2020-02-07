package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG

trait Discrete extends Distribution[Long] { self: Discrete =>
  def logDensity(seq: Seq[Long]) =
    Vec.from(seq.map(_.toDouble)).map(logDensity).columnize
  def logDensity(x: Real): Real

  def zeroInflated(psi: Real): DiscreteMixture =
    constantInflated(0.0, psi)

  def constantInflated(constant: Real, psi: Real): DiscreteMixture =
    DiscreteMixture(Map(DiscreteConstant(constant) -> psi, self -> (1 - psi)))
}

/**
  * Discrete Constant (point mass) with expecation `constant`
  *
  * @param constant The integer value of the point mass
  */
final case class DiscreteConstant(constant: Real) extends Discrete {
  val generator: Generator[Long] =
    Generator.require(Set(constant)) { (_, n) =>
      n.toLong(constant)
    }

  def logDensity(v: Real): Real =
    Real.eq(v, constant, 0, Real.negInfinity)
}

/**
  * Bernoulli distribution with expectation `p`
  *
  * @param p The probability of success
  */
final case class Bernoulli(p: Real) extends Discrete {
  Bounds.check(p, "0 <= p <= 1") { v =>
    v >= 0.0 && v <= 1.0
  }

  val generator: Generator[Long] =
    Generator.require(Set(p)) { (r, n) =>
      val u = r.standardUniform
      val l = n.toDouble(p)
      if (u <= l) 1 else 0
    }

  def logDensity(v: Real) =
    Real.eq(v, Real.zero, (1 - p).log, p.log)
}

/**
  * Geometric distribution with expectation `1/p`
  *
  * @param p The probability of success
  */
final case class Geometric(p: Real) extends Discrete {
  Bounds.check(p, "0 <= p <= 1") { v =>
    v >= 0.0 && v <= 1.0
  }

  val generator: Generator[Long] =
    Generator.require(Set(p)) { (r, n) =>
      val u = r.standardUniform
      val q = n.toDouble(p)
      Math.floor(Math.log(u) / Math.log(1 - q)).toLong
    }

  def logDensity(v: Real) =
    p.log + v * (1 - p).log
}

/**
  * Negative Binomial distribution with expectation `n*p/(1-p)`
  *
  * @param n Total number of failures
  * @param p Probability of success
  */
final case class NegativeBinomial(p: Real, n: Real) extends Discrete {
  Bounds.check(p, "0 <= p <= 1") { v =>
    v >= 0.0 && v <= 1.0
  }
  Bounds.check(n, "n >= 0")(_ >= 0.0)

  val generator: Generator[Long] = {
    val nbGenerator = Generator.require(Set(n, p)) { (r, m) =>
      (1L to m.toLong(n))
        .map({ _ =>
          Geometric(1 - p).generator.get(r, m)
        })
        .sum
    }
    val normalGenerator =
      Normal(n * p / (1 - p), (n * p).pow(1.0 / 2.0) / (1 - p)).generator
        .map(_.toLong.max(0))

    Generator.from {
      case (r, m) =>
        val pDouble = m.toDouble(p)
        val nDouble = m.toDouble(n)
        if (pDouble < -100 / nDouble + 1 && pDouble > 100 / nDouble - .25) {
          normalGenerator.get(r, m)
        } else {
          nbGenerator.get(r, m)
        }
    }
  }

  def logDensity(v: Real) =
    Combinatorics.factorial(n + v - 1) - Combinatorics.factorial(v) -
      Combinatorics.factorial(n - 1) +
      n * (1 - p).log + v * p.log
}

/**
  * Poisson distribution with expectation `lambda`
  *
  * @param lambda The mean of the Poisson distribution
  */
final case class Poisson(lambda: Real) extends Discrete {
  Bounds.check(lambda, "λ >= 0")(_ >= 0.0)

  val Step = 500
  val eStep = math.exp(Step.toDouble)

  val generator: Generator[Long] =
    Generator.require(Set(lambda)) { (r, n) =>
      val lambdaDouble = n.toDouble(lambda)
      if (lambdaDouble < 30.0)
        Poisson.small(lambdaDouble, r).toLong
      else
        Poisson.large(lambdaDouble, r)
    }

  def logDensity(v: Real) =
    lambda.log * v - lambda - Combinatorics.factorial(v)
}

object Poisson {
  private def small(lambda: Double, r: RNG): Int = {
    val l = math.exp(-lambda)
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

  //from https://www.johndcook.com/blog/2010/06/14/generating-poisson-random-values/
  private def large(lambda: Double, r: RNG): Long = {
    val c = 0.767 - 3.36 / lambda
    val beta = math.Pi / math.sqrt(3.0 * lambda)
    val alpha = beta * lambda
    val k = math.log(c) - lambda - math.log(beta)

    while (true) {
      val u = r.standardUniform
      val x = (alpha - math.log((1.0 - u) / u)) / beta
      val n = math.floor(x + 0.5).toLong
      if (n >= 0) {
        val v = r.standardUniform
        val y = alpha - beta * x
        val lhs = y + math.log(v / math.pow(1.0 + math.exp(y), 2))
        val rhs = k + n * math.log(lambda) - logFactorial(n)
        if (lhs <= rhs)
          return n
      }
    }

    0
  }

  //from https://www.johndcook.com/blog/2010/08/16/how-to-compute-log-factorial/
  //we're using the roughest approximation here because we're only using this for
  //large lambda, which leads to generally large n, where the approximation works
  private def logFactorial(n: Long): Double = {
    val x = (n + 1).toDouble
    ((x - 0.5) * math.log(x)) - x + (0.5 * math.log(2 * math.Pi))
  }
}

/**
  * A Binomial distribution with expectation `k*p`
  *
  * @param p The probability of success
  * @param k The number of trials
  */
final case class Binomial(p: Real, k: Real) extends Discrete {
  Bounds.check(p, "0 <= p <= 1") { v =>
    v >= 0.0 && v <= 1.0
  }
  Bounds.check(k, "k >= 0")(_ >= 0.0)

  val multi: Multinomial[Boolean] =
    Multinomial(Map(true -> p, false -> (1 - p)), k)

  def generator: Generator[Long] = {
    val kGenerator = Generator.real(k)
    lazy val poissonGenerator =
      Poisson(p * k).generator
        .zip(kGenerator)
        .map { case (x, kd) => x.min(kd.toLong) }
    lazy val normalGenerator =
      Normal(k * p, (k * p * (1 - p)).pow(0.5)).generator
        .zip(kGenerator)
        .map {
          case (x, kd) => x.toLong.max(0).min(kd.toLong)
        }
    lazy val binomialGenerator = multi.generator.map { m =>
      m.getOrElse(true, 0L)
    }

    Generator.require(Set(p, k)) {
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
    multi.logDensity(Map((true -> v), (false -> (k - v))))
}

/**
  * A Beta-binomial distribution with expectation `a/(a + b) * k`
  *
  */
final case class BetaBinomial(a: Real, b: Real, k: Real) extends Discrete {
  val generator: Generator[Long] =
    Beta(a, b).generator.flatMap { p =>
      Binomial(p, k).generator
    }

  def logDensity(v: Real) =
    Combinatorics.choose(k, v) +
      Combinatorics.beta(a + v, k - v + b) -
      Combinatorics.beta(a, b)
}

object BetaBinomial {
  def meanAndPrecision(mean: Real, precision: Real, k: Real): BetaBinomial =
    BetaBinomial(mean * precision, (Real.one - mean) * precision, k)
}

/**
  * Discrete Mixture Distribution
  *
  * @param components Map of Discrete distribution and probabilities
  */
final case class DiscreteMixture(components: Map[Discrete, Real])
    extends Discrete {
  components.values.foreach { r =>
    Bounds.check(r, "0 <= p <= 1") { p =>
      p >= 0.0 && p <= 1.0
    }
  }

  val generator: Generator[Long] =
    Generator.categorical(components).flatMap { d =>
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
