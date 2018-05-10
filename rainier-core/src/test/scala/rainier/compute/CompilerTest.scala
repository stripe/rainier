package rainier.compute
import rainier.core._

import org.scalatest.FunSuite

class CompilerTest extends FunSuite {
  val x = new Variable

  def assertMatchesEvaluator(real: Real) {
    val g = Gradient.derive(List(x), real).head
    val c = Compiler.default.compile(List(x), g)

    List(1.0, 2.0).foreach { xValue =>
      val eval = new Evaluator(Map(x -> xValue))
      val evalResult = eval.toDouble(g)

      val compilerResult = c(Array(xValue))
      assert(compilerResult == evalResult)
    }
  }

  test("x ~ normal(0,1)") {
    assertMatchesEvaluator(Normal.standard.realLogDensity(x))
  }
}