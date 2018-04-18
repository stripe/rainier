package rainier.compute

import org.scalatest._

import rainier.core._

class ASMTest extends FunSuite {

  val x = new Variable
  def compareToEvaluator(p: Real, xVal: Double): Unit = {
    val result = ASM.compileToFunction(p)(xVal)
    val actual = (new Evaluator(Map(x -> xVal))).toDouble(p)
    assert(result == actual)
    val grad = Gradient.derive(List(x), p).head
    val gradResult = ASM.compileToFunction(grad)(xVal)
    val gradActual = (new Evaluator(Map(x -> xVal))).toDouble(grad)
    assert(gradResult == gradActual)
  }

  test("handle plus") {
    compareToEvaluator(x + 11, 4.0)
  }

  test("handle exp") {
    compareToEvaluator(x.exp, 2.0)
  }

  test("handle log") {
    compareToEvaluator(x.log, 2.0)
  }

  test("handle temps") {
    val t = x * 3
    compareToEvaluator(t + t, 2.0)
  }

  test("poisson") {
    compareToEvaluator(Poisson(x).logDensities(0.to(10).toList), 2.0)
  }

  test("normal") {
    compareToEvaluator(Normal(x, 1).logDensities(0d.to(2d).by(0.001).toList),
                       2.0)
  }
}
