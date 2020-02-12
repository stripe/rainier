package com.stripe.rainier.compute

import com.stripe.rainier.core._
import scala.util.{Try, Success, Failure}
import Double.{PositiveInfinity => Inf, NegativeInfinity => NegInf, NaN}

class RealTest extends ComputeTest {
  def run(description: String,
          defined: Double => Boolean = _ => true,
          derivable: Double => Boolean = _ => true,
          reference: Double => Double = null)(fn: Real => Real): Unit = {
    test(description) {
      val x = Real.parameter()
      val result = fn(x)
      val deriv = Gradient.derive(List(x), result).head

      def evalAt(d: Double): Double = Try { fn(Real(d)) } match {
        case Success(Scalar(bd))             => bd.toDouble
        case Failure(_: ArithmeticException) => NaN
        case Failure(e)                      => throw e
        case x                               => sys.error("Non-constant value " + x)
      }

      val c = Compiler(200, 100).compile(List(x), result)
      val dc = Compiler(200, 100).compile(List(x), deriv)
      List(1.0, 0.0, -1.0, 2.0, -2.0, 0.5, -0.5, NegInf, Inf)
        .filter(defined)
        .foreach { n =>
          val constant = evalAt(n)
          if (reference != null) {
            assertWithinEpsilon(constant, reference(n), s"[c/ref, n=$n]")
          }
          val eval = new Evaluator(Map(x -> n))
          val withVar = eval.toDouble(result)
          assertWithinEpsilon(constant, withVar, s"[c/ev, n=$n]")
          val compiled = c(Array(n))
          assertWithinEpsilon(withVar, compiled, s"[ev/ir, n=$n]")

          // derivatives of automated differentiation vs numeric differentiation
          // exclude infinite values for which numeric differentiation does not make sense
          if (derivable(n) && !n.isInfinite) {
            val dx = 10E-6
            val numDiff = (evalAt(n + dx) - evalAt(n - dx)) / (dx * 2)
            val diffWithVar = eval.toDouble(deriv)
            assertWithinEpsilon(numDiff,
                                diffWithVar,
                                s"[numDiff/diffWithVar, n=$n]")
            val diffCompiled = dc(Array(n))
            assertWithinEpsilon(diffWithVar,
                                diffCompiled,
                                s"[diffWithVar/diffCompiled, n=$n]")
          }
        }
    }
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
  run("sin", defined = !_.isInfinite, reference = math.sin) { x =>
    x.sin
  }
  run("cos", defined = !_.isInfinite, reference = math.cos) { x =>
    x.cos
  }
  run("tan", defined = !_.isInfinite, reference = math.tan) { x =>
    x.tan
  }
  run("asin", defined = x => x > -1 && x < 1, reference = math.asin) { x =>
    x.asin
  }
  run("acos", defined = x => x > -1 && x < 1, reference = math.acos) { x =>
    x.acos
  }
  run("atan", reference = math.atan) { x =>
    x.atan
  }
  run("sinh", reference = math.sinh) { x =>
    x.sinh
  }
  run("cosh", reference = math.cosh) { x =>
    x.cosh
  }
  run("tanh", defined = !_.isInfinite, reference = math.tanh) { x =>
    x.tanh
  }
  run("tanh at infty") { x =>
    // tanh does have limits at infinities but the current explicit representation does not handle it (results in inf/inf = NaN)
    // this additional test simply ensure that results are consistent among all implementations,
    // without checking against the more informed reference implementation in the JDK
    x.tanh
  }
  run("cos(x^2)", defined = !_.isInfinite) { x =>
    (x * x).cos
  }
  run("temp") { x =>
    val t = x * 3
    t + t
  }
  run("abs") { x =>
    x.abs
  }
  run("max(x, 0)", derivable = _ != 0) { x =>
    x.max(0)
  }
  run("max(x, x)") { x =>
    x.max(x)
  }
  run("x > 0 ? x^2 : 1", derivable = _ != 0) { x =>
    Real.gt(x, 0, x * x, 1)
  }
  run("x > 0 ? 1 : x + 1", derivable = _ != 0) { x =>
    Real.gt(x, 0, 1, x + 1)
  }
  run("x > 0 ? x^2 : x + 1", derivable = _ != 0) { x =>
    Real.gt(x, 0, x * x, x + 1)
  }

  run("normal", defined = !_.isPosInfinity) { x =>
    // FIXME: letting this be defined at +infinity results in the following error:
    //     -Infinity did not equal NaN [c/ev, n=Infinity] (RealTest.scala:67)
    // I.e. two different results from constant folding and evaluation.
    // same applies to the "normal sum" test case below
    Normal(x, 1).logDensity(Real(1d))
  }

  run("normal sum", defined = !_.isPosInfinity) { x =>
    Real.sum(Range.BigDecimal(0d, 2d, 1d).toList.map { y =>
      Normal(x, 1).logDensity(Real(y))
    })
  }

  run("logistic") { x =>
    val logistic = Real.one / (Real.one + (x * -1).exp)
    (logistic * (Real.one - logistic)).log
  }

  run("minimal logistic", reference = x => 1d / (math.exp(x) + 1)) { x =>
    Real.one / (x.exp + 1)
  }

  run("log x^2", derivable = _ != 0, reference = x => math.log(x * x)) { x =>
    x.pow(2).log
  }

  run("poisson") { x =>
    Real.sum(0.to(10).toList.map { y =>
      Poisson(x.abs + 1).logDensity(y)
    })
  }

  run("4x^3", reference = x => 4 * x * x * x) { x =>
    (((((x + x) * x) +
      (x * x)) * x) +
      (x * x * x))
  }
  /*
  run("x^(10(2/10 + 1/10))") { x =>
    x.pow((Real.one / 10 + Real.two / 10) * 10)
  }

  run("x^((4/3 + 1)*3)") { x =>
    x.pow((Real(4) / 3 + 1) * 3)
  }
   */
  run("lookup",
      defined = x => x.abs <= 2 && (x.abs * 2).isValidInt,
      derivable = _ => false,
      reference = _.abs * 2) { x =>
    val i = x.abs * 2 //should be a non-negative whole number
    Lookup(i, Real.seq(List(0, 1, 2, 3, 4)))
  }

  val exponents = scala.util.Random.shuffle(-40.to(40))
  run("exponent sums", derivable = _ != 0) { x =>
    exponents.foldLeft(x) {
      case (a, e) =>
        (a + x.pow(e)) * x
    }
  }

  run("cancelling x^2 then distributing", defined = x => x != 0 && x.isValidInt) {
    x =>
      (x.pow(2) * 2) / (x.pow(2)) + x
  }

  run("pow", defined = _ >= 0) { x =>
    x.pow(x)
  }

  run("gamma fit") { x =>
    Real.sum(List(1d, 2d, 3d).map { y =>
      Gamma.standard(x.abs).logDensity(y)
    })
  }
}
