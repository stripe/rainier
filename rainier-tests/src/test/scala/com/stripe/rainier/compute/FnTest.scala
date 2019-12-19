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
  check(
    "zip(double, long)",
    Fn.numeric[Double]
        .zip(Fn.numeric[Long])
        .map {case (a, b) => a * 2 + b },
    (2.0, 1L))
}
