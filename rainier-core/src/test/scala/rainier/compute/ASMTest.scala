package rainier.compute

import org.scalatest._

class ASMTest extends FunSuite {

  def compileAndRun(p: Real, x: Double): Double = {
    ASM.compileToFunction(p)(x)
  }

  val x = new Variable
  test("handle plus") {
    val result =
      compileAndRun(x + 11, 4.0)
    assert(result == (4.0 + 11.0))
  }

  test("handle exp") {
    val result = compileAndRun(x.exp, 2.0)
    assert(result == Math.exp(2.0))
  }

  test("handle log") {
    val result = compileAndRun(x.log, 2.0)
    assert(result == Math.log(2.0))
  }
}
