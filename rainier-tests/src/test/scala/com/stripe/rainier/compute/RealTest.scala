package com.stripe.rainier.compute

import org.scalatest._
import com.stripe.rainier.core._
import scala.util.{Try, Success, Failure}
class RealTest extends FunSuite {
  def run(description: String)(fn: Real => Real): Unit = {
    test(description) {
      val x = new Variable
      val result = fn(x)
      val c = Compiler(200, 100).compile(List(x), result)
      List(1.0, 0.0, -1.0, 2.0, -2.0, 0.5, -0.5).foreach { n =>
        val constant = Try { fn(Constant(n)) } match {
          case Success(Infinity)                 => 1.0 / 0.0
          case Success(NegInfinity)              => -1.0 / 0.0
          case Success(Constant(bd))             => bd.toDouble
          case Failure(_: ArithmeticException)   => 0.0 / 0.0
          case Failure(_: NumberFormatException) => 0.0 / 0.0
          case x                                 => sys.error("Non-constant value " + x)
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
    Real.sum(Range.BigDecimal(0d, 2d, 1d).toList.map { y =>
      Normal(x, 1).logDensity(Real(y))
    })
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
    Real.sum(0.to(10).toList.map { y =>
      Poisson(x.abs + 1).logDensity(y)
    })
  }

  run("4x^3") { x =>
    (((((x + x) * x) +
      (x * x)) * x) +
      (x * x * x))
  }

  run("lookup") { x =>
    val i = x.abs * 2 //should be a non-negative whole number
    Lookup(i, Real.seq(List(0, 1, 2, 3, 4)))
  }

  val exponents = scala.util.Random.shuffle(-40.to(40))
  run("exponent sums") { x =>
    exponents.foldLeft(x) {
      case (a, e) =>
        (a + x.pow(e)) * x
    }
  }

  run("pow") { x =>
    x.pow(x)
  }

  run("<") { x =>
    Real(5.499999999999998) < x
  }
}
