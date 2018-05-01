package rainier.compute

import org.scalatest._
import rainier.compute
import rainier.compute.asm.IR
import rainier.core._

class IRTest extends FunSuite {

  val x = new compute.Variable
  val y = new compute.Variable
  def compareToEvaluator(p: Real, xVal: Double, yVal: Double = 0.0): Unit = {
    val c = asm.IRCompiler.compile(List(x, y), p)
    val result = c(Array(xVal, yVal))
    val actual = (new Evaluator(Map(x -> xVal, y -> yVal))).toDouble(p)
    assertWithinEpsilon(result, actual)
    val grad = Gradient.derive(List(x, y), p).head
    val gradResult =
      asm.IRCompiler.compile(List(x, y), grad)(Array(xVal, yVal))
    val gradActual = (new Evaluator(Map(x -> xVal, y -> yVal))).toDouble(grad)
    assertWithinEpsilon(gradResult, gradActual)
  }

  def assertWithinEpsilon(x: Double, y: Double): Unit = {
    val delta = x - y
    val err = delta.abs / y
    assert(err < 0.00001)
  }
  /*
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
   */
  test("normal") {
    compareToEvaluator(Normal(x, 1).logDensities(0d.to(2d).by(1).toList), 2.0)
  }
  /*
  test("two args") {
    compareToEvaluator(x + y, 1.0, 2.0)
  }
 */
}
