package rainier.compute

import org.scalatest.FunSuite
import org.scalatest.prop.{Checkers, GeneratorDrivenPropertyChecks}
import org.scalacheck._
import Arbitrary.arbitrary

class IRScalaCheckTest
    extends FunSuite
    with Checkers
    /*with GeneratorDrivenPropertyChecks*/ {

  val xParam = new Variable
  val yParam = new Variable

  private val genParam = Gen.oneOf(xParam, yParam)
  private val genConstant = for {
    c <- arbitrary[Double]
  } yield Constant(c)
  private val genLeaf = Gen.oneOf(genParam, genConstant)

  class GenerateNodes(interleave: Option[Gen[Real]]) {
    // the return type is `Gen[Real]` instead of `BinaryReal` because `BinaryReal.apply` returns `Real`
    def genBinaryReal(sz: Int): Gen[Real] =
      for {
        left <- sizedReal(sz / 2)
        right <- sizedReal(sz / 2)
        op <- Gen.oneOf(AddOp, SubtractOp, MultiplyOp, DivideOp)
      } yield BinaryReal(left, right, op)

    def genUnaryReal(sz: Int): Gen[Real] =
      for {
        original <- sizedReal(sz / 2)
        op <- Gen.oneOf(ExpOp, LogOp, AbsOp)
      } yield UnaryReal(original, op)

    def genInternal(sz: Int): Gen[Real] =
      Gen.oneOf(genBinaryReal(sz), genUnaryReal(sz))

    def sizedReal(sz: Int): Gen[Real] = {
//      println(s"sizedReal: $sz")
      if (sz <= 0) genLeaf
      else {
        interleave match {
          case None => Gen.frequency((1, genLeaf), (3, genInternal(sz)))
          case Some(interleaveGen) =>
            Gen.frequency((1, genLeaf),
                          (3, genInternal(sz)),
                          (1, interleaveGen))
        }
      }
    }
  }

  lazy val treeNodes: GenerateNodes =
    new GenerateNodes(None)

  implicit def arbReal: Arbitrary[Real] = Arbitrary {
    Gen.sized { size =>
      val dagSamples =
        Gen.listOfN(1 + size / 10, treeNodes.sizedReal(size)).sample
      println(s"samples = $dagSamples")
      val generateDag: GenerateNodes =
        new GenerateNodes(dagSamples.map(samples => Gen.oneOf(samples)))
      generateDag.sizedReal(size)
    }
  }

  def compareToEvaluator(p: Real, xVal: Double, yVal: Double = 0.0): Unit = {
    val c = asm.IRCompiler.compile(List(xParam, yParam), p)
    val result = c(Array(xVal, yVal))
    val actual =
      (new Evaluator(Map(xParam -> xVal, yParam -> yVal))).toDouble(p)

//    assert(result == actual)
//    val grad = Gradient.derive(List(xParam, yParam), p).head
//    val gradResult =
//      asm.IRCompiler.compile(List(xParam, yParam), grad)(Array(xVal, yVal))
//    val gradActual = (new Evaluator(Map(xParam -> xVal, yParam -> yVal))).toDouble(grad)
//    assert(gradResult == gradActual)
  }

//  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
//    PropertyCheckConfig(minSize = 10, maxSize = 1000)

  test("basic scalacheck") {
    check(
      { (exp: Real, x: Double, y: Double) =>
        println(s"testing $exp")
        val c = asm.IRCompiler.compile(List(xParam, yParam), exp)
        val result = c(Array(x, y))
        val actual =
          (new Evaluator(Map(xParam -> x, yParam -> y))).toDouble(exp)
//      println(s"result = $result, actual = $actual")
        floatEquals(result, actual)
      }
    )
  }

  def floatEquals(x: Double, y: Double): Boolean = {
    (x == y) || (x.isNaN && y.isNaN)
  }

}
