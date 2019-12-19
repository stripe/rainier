package com.stripe.rainier.compute

import org.scalatest._

class FnTest extends FunSuite {
  def check[T](description: String, fn: Fn[T, Real], value: T): Unit = {
    test(description) {
      val eval = new Evaluator(Map.empty, Some(0))
      val withApply = eval.toDouble(fn(value))
      val withEncode = eval.toDouble(fn.encode(List(value)))
      assert(withApply == withEncode)
    }
  }

  check("numeric[Double]", Fn.numeric[Double], 1.0)
  check("numeric[Long]", Fn.numeric[Long], 1L)
  check("zip(Double, Long)",
        Fn.numeric[Double]
          .zip(Fn.numeric[Long])
          .map { case (a, b) => a * 2 + b },
        (2.0, 1L))
  check("Map[String,Double]",
        Fn.numeric[Double]
          .keys(List("a", "b"))
          .map { m =>
            m("a") * 2 + m("b")
          },
        Map("a" -> 2.0, "b" -> 1.0))
}
