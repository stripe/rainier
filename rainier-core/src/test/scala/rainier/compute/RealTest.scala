package rainier.compute

import org.scalatest._
import rainier.compute
import rainier.core._

class RealTest extends FunSuite {
  def run(description: String)(fn: Real => Real): Unit = {
    test(description) {
      val x = new Variable
      val result = fn(x)
      val c = Compiler.default.compile(List(x), result)
      List(1.0, 0.0, -1.0, 2.0, -2.0, 0.5, -0.5).foreach { n =>
        val constant = fn(Constant(n))
        assert(constant.isInstanceOf[Constant], s"[n=$n]")
        val eval = new Evaluator(Map(x -> n))
        val withVar = eval.toDouble(result)
        assertWithinEpsilon(constant.asInstanceOf[Constant].value,
                            withVar,
                            s"[n=$n]")
        val compiled = c(Array(n))
        assertWithinEpsilon(withVar, compiled, s"[ir, n=$n]")
      }
    }
  }

  def assertWithinEpsilon(x: Double, y: Double, clue: String): Unit = {
    assert(x.isNaN && y.isNaN || x == y || (x - y).abs < 0.000000001, clue)
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
    Normal(x, 1).logDensities(0d.to(2d).by(1).toList)
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
}
