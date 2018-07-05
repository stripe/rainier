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

  def density(model: RandomVariable[_]): Real =
    model.density

  protected def expression: Real

  val expr = expression
  val context = Context(expr)
  val vars = context.variables
  val grad = context.gradient
  val cf = context.compiler.compile(vars, expr)
  val gf = compileGradient

  @Benchmark
  def build = expression
  @Benchmark
  def gradient = Context(expr).gradient
  @Benchmark
  def compileGradient = Context(expr).compiler.compile(vars, grad)
  @Benchmark
  def run =
    cf(vars.map { _ =>
      rng.standardUniform
    }.toArray)
  @Benchmark
  def runGradient =
    gf(vars.map { _ =>
      rng.standardUniform
    }.toArray)
  @Benchmark
  def eval = {
    val evaluator = new Evaluator(vars.map { v =>
      v -> rng.standardUniform
    }.toMap)
    evaluator.toDouble(expr)
  }
  @Benchmark
  def evalGradient = {
    val evaluator = new Evaluator(vars.map { v =>
      v -> rng.standardUniform
    }.toMap)
    grad.map { g =>
      evaluator.toDouble(g)
    }
  }
}

class TrivialBenchmark extends RealBenchmark {
  def expression = Real(3)
}

class NormalBenchmark extends RealBenchmark {
  def expression: Real = {
    val x = new Variable
    Real.sum(Range.BigDecimal(0d, 2d, 0.001d).toList.map { y =>
      Normal(x, 1).logDensity(Real(y))
    })
  }
}

class PoissonBenchmark extends RealBenchmark {
  def expression: Real = {
    val x = new Variable
    Real.sum(0.to(10).toList.map { y =>
      Poisson(x).logDensity(y)
    })
  }
}

class FullNormalBenchmark extends RealBenchmark {
  def expression: Real = {
    val r = new scala.util.Random
    val trueMean = 3.0
    val trueStddev = 2.0
    val data = 1.to(1000).map { i =>
      (r.nextGaussian * trueStddev) + trueMean
    }

    val model = for {
      mean <- Uniform(0, 10).param
      stddev <- Uniform(0, 10).param
      _ <- Normal(mean, stddev).fit(data)
    } yield (mean, stddev)

    density(model)
  }
}

class BernoulliBenchmark extends RealBenchmark {
  def expression: Real = {
    val data =
      List(false, true, false, false, false, false, false, false, false, true)

    val model = for {
      theta <- Uniform.standard.param
      _ <- Categorical.boolean(theta).fit(data)
    } yield theta

    density(model)
  }
}

class FunnelBenchmark extends RealBenchmark {
  def expression: Real = {
    val model =
      for {
        y <- Normal(0, 3).param
        x <- RandomVariable.traverse(1.to(9).map { _ =>
          Normal(0, (y / 2).exp).param
        })
      } yield (x(0), y)

    density(model)
  }
}

class DLMBenchmark extends RealBenchmark {
  def expression: Real = {
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

    val fullModel = (0 until n).foldLeft(prior)(addTimePoint(_, _))

    density(fullModel)
  }
}
