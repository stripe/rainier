package com.stripe.rainier.compute

import org.scalatest._
import com.stripe.rainier.core._
import scala.util.{Try, Success, Failure}
class RealTest extends FunSuite {
  def run(description: String)(fn: Real => Real): Unit = {
    test(description) {
      val x = new Variable
      val result = fn(x)
      val c = Compiler.default.compile(List(x), result)
      List(1.0, 0.0, -1.0, 2.0, -2.0, 0.5, -0.5).foreach { n =>
        val constant = Try { fn(Constant(n)) } match {
          case Success(Infinity)               => 1.0 / 0.0
          case Success(NegInfinity)            => -1.0 / 0.0
          case Success(Constant(bd))           => bd.toDouble
          case Failure(_: ArithmeticException) => 0.0 / 0.0
          case _                               => sys.error("Non-constant value")
        }
        val eval = new Evaluator(Map(x -> n))
        val withVar = eval.toDouble(result)
        assertWithinEpsilon(constant, withVar, s"[c/ev, n=$n]")
        val compiled = c(Array(n))
        assertWithinEpsilon(withVar, compiled, s"[ev/ir, n=$n]")
      }
    }
  }

  def assertWithinEpsilon(x: Double, y: Double, clue: String): Unit = {
    val relativeError = ((x - y).abs / x)
    if (!(x.isNaN && y.isNaN || relativeError < 0.001))
      assert(x == y, clue)
    ()
  }

  run("plus") { x =>
    x + 1
  }
  run("exp") { x =>
    x.exp
  }
  run("square") { x =>
    x * x
  }
  run("log") { x =>
    x.abs.log
  }
  run("temp") { x =>
    val t = x * 3
    t + t
  }
  run("normal") { x =>
    Normal(x, 1).logDensities(
      Range.BigDecimal(0d, 2d, 1d).map(_.toDouble).toList)
  }

  run("logistic") { x =>
    val logistic = Real.one / (Real.one + (x * -1).exp)
    (logistic * (Real.one - logistic)).log
  }

  run("minimal logistic") { x =>
    Real.one / (x.exp + 1)
  }

  run("log x^2") { x =>
    x.pow(2).log
  }

  run("if") { x =>
    If(x, x * 2, x * 3) * 5
  }
  run("poisson") { x =>
    Poisson(x.abs + 1).logDensities(0.to(10).toList)
  }

  run("4x^3") { x =>
    (((((x + x) * x) +
      (x * x)) * x) +
      (x * x * x))
  }

  val exponents = scala.util.Random.shuffle(-40.to(40))
  run("exponent sums") { x =>
    exponents.foldLeft(x) {
      case (a, e) =>
        (a + x.pow(e)) * x
    }
  }
}
