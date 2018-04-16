package rainier.compute

import org.scalatest._

class ASMTest extends FunSuite {

  def compileAndRun(p: AST, x1: Double, x2: Double): Double = {
    val classNode = ASM.compile(p)
    val bytes = ASM.writeBytecode(classNode)
    val parentClassloader = this.getClass.getClassLoader
    val classLoader =
      new SingleClassClassLoader("Foo", bytes, parentClassloader)
    val cls = classLoader.clazz
    val inst = cls.newInstance()
    val method = cls.getMethod("foo_method", classOf[Double], classOf[Double])
    val result = method
      .invoke(inst, new java.lang.Double(x1), new java.lang.Double(x2))
      .asInstanceOf[java.lang.Double]
    result
  }

  test("handle plus") {
    val result =
      compileAndRun(Plus(Plus(ParamRef(0), Const(11.0)), ParamRef(1)),
                    x1 = 1.0,
                    x2 = 4.0)
    assert(result == (11.0 + 1.0 /*x1*/ ) + 4.0 /*x2*/ )
  }

  test("handle exp") {
    val result = compileAndRun(Exp(ParamRef(0)), x1 = 2.0, x2 = 0.0)
    assert(result == Math.exp(2.0))
  }

  test("handle log") {
    val result = compileAndRun(Log(ParamRef(0)), x1 = 2.0, x2 = 0.0)
    assert(result == Math.log(2.0))
  }
}
