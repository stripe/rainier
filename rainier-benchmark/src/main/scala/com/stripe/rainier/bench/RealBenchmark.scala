package com.stripe.rainier.bench

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@Threads(4)
@State(Scope.Benchmark)
abstract class RealBenchmark {
  implicit val rng: RNG = RNG.default

  def model: RandomVariable[_]

  val m = model
  val d = m.density
  val v = m.variables

  @Benchmark
  def build = model

  @Benchmark
  def compile = m.density

  @Benchmark
  def run =
    d.update(v.map { _ =>
      rng.standardUniform
    }.toArray)
}

abstract class ExpressionBenchmark extends RealBenchmark {
  def model = RandomVariable.fromDensity(expression)
  def expression: Real
}

class TrivialBenchmark extends ExpressionBenchmark {
  def expression = Real(3)
}

class NormalBenchmark extends ExpressionBenchmark {
  def expression: Real = {
    val x = new Variable
    Real.sum(Range.BigDecimal(0d, 2d, 0.001d).toList.map { y =>
      Normal(x, 1).logDensity(Real(y))
    })
  }
}

class PoissonBenchmark extends ExpressionBenchmark {
  def expression: Real = {
    val x = new Variable
    Real.sum(0.to(10).toList.map { y =>
      Poisson(x).logDensity(y)
    })
  }
}

class FullNormalBenchmark extends RealBenchmark {
  def model = {
    val r = new scala.util.Random
    val trueMean = 3.0
    val trueStddev = 2.0
    val data = 1.to(1000).map { i =>
      (r.nextGaussian * trueStddev) + trueMean
    }

    for {
      mean <- Uniform(0, 10).param
      stddev <- Uniform(0, 10).param
      _ <- Normal(mean, stddev).fit(data)
    } yield (mean, stddev)
  }
}

class BernoulliBenchmark extends RealBenchmark {
  def model = {
    val data =
      List(false, true, false, false, false, false, false, false, false, true)

    for {
      theta <- Uniform.standard.param
      _ <- Categorical.boolean(theta).fit(data)
    } yield theta
  }
}

class FunnelBenchmark extends RealBenchmark {
  def model =
    for {
      y <- Normal(0, 3).param
      x <- RandomVariable.traverse(1.to(9).map { _ =>
        Normal(0, (y / 2).exp).param
      })
    } yield (x(0), y)
}

class ElOne100ErrorBenchmark extends ExpressionBenchmark {
  def expression: Real = {
    val r = new scala.util.Random
    val x = new Variable
    val data = 1.to(100).map(_ * r.nextGaussian)
    data
      .map { i =>
        (x - i).abs
      }
      .reduce(_ + _) / data.size
  }
}

class ElOne1000ErrorBenchmark extends ExpressionBenchmark {
  def expression: Real = {
    val r = new scala.util.Random
    val x = new Variable
    val data = 1.to(1000).map(_ * r.nextGaussian)
    data
      .map { i =>
        (x - i).abs
      }
      .reduce(_ + _) / data.size
  }
}

class ElTwo100ErrorBenchmark extends ExpressionBenchmark {
  def expression: Real = {
    val r = new scala.util.Random
    val x = new Variable
    val data = 1.to(100).map(_ * r.nextGaussian)
    data
      .map { i =>
        (x - i).pow(2)
      }
      .reduce(_ + _) / data.size
  }
}

class ElTwo1000ErrorBenchmark extends ExpressionBenchmark {
  def expression: Real = {
    val r = new scala.util.Random
    val x = new Variable
    val data = 1.to(1000).map(_ * r.nextGaussian)
    data
      .map { i =>
        (x - i).pow(2)
      }
      .reduce(_ + _) / data.size
  }
}

class DLMBenchmark extends RealBenchmark {
  def model = {
    implicit val rng = ScalaRNG(4)
    val n = 50
    val mu = 3.0 // AR(1) mean
    val a = 0.95 // auto-regressive parameter
    val sig = 1.0 // AR(1) SD
    val sigD = 3.0 // observational SD
    val state = Stream
      .iterate(0.0)(x => mu + (x - mu) * a + sig * rng.standardNormal)
      .take(n)
      .toVector
    val obs = state.map(_ + sigD * rng.standardNormal)

    // build and fit model
    case class Static(mu: Real, a: Real, sig: Real, sigD: Real)

    val prior = for {
      mu <- Normal(5, 10).param
      a <- Normal(1, 0.2).param
      sig <- LogNormal(0, 2).param
      sigD <- LogNormal(1, 4).param
      sp <- Normal(0, 50).param
    } yield (Static(mu, a, sig, sigD), List(sp))

    def addTimePoint(current: RandomVariable[(Static, List[Real])],
                     i: Int): RandomVariable[(Static, List[Real])] =
      for {
        tup <- current
        static = tup._1
        states = tup._2
        os = states.head
        ns <- Normal(((Real.one - static.a) * static.mu) + (static.a * os),
                     static.sig).param
        _ <- Normal(ns, static.sigD).fit(obs(i))
      } yield (static, ns :: states)

    (0 until n).foldLeft(prior)(addTimePoint(_, _))
  }
}
