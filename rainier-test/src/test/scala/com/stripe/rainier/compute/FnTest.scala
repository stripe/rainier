package com.stripe.rainier.compute

import org.scalatest._

class FnTest extends FunSuite {
  def check[T](description: String, fn: Fn[T, Real], value: T): Unit = {
    test(description) {
      val eval = PartialEvaluator(0)
      val withApply = eval(fn(value))._1
      val withEncode = eval(fn.encode(List(value)))._1
      assert(withApply == withEncode)
    }
  }

  val fDouble = Fn.double
  val fLong = Fn.long
  val fMap = Fn.double
    .keys(List("a", "b"))
    .map { m =>
      m("a") * 2 + m("b")
    }
  val fEnum = Fn
    .enum(List("a", "b", "c"))
    .map { ls =>
      val m = ls.toMap
      m("a") * 4 + m("b") * 5 + m("c") * 6
    }
  val fDoubleList = fDouble.list(3).map { ls =>
    ls(0) * 4 + ls(1) * 5 + ls(2) * 6
  }
  val fDoubleVec = fDouble.vec(3).map { ls =>
    ls(0) * 4 + ls(1) * 5 + ls(2) * 6
  }

  check("numeric[Double]", fDouble, 1.0)
  check("numeric[Long]", fLong, 1L)
  check("zip(Double, Long)",
        fDouble
          .zip(fLong)
          .map { case (a, b) => a * 2 + b },
        (2.0, 1L))
  check("Map[String,Double]", fMap, Map("a" -> 2.0, "b" -> 1.0))
  check("enum", fEnum, "b")
  check("enum.zip(map)",
        fEnum
          .zip(fMap)
          .map { case (a, b) => a * 10 + b },
        ("b", Map("a" -> 2.0, "b" -> 1.0)))
  check("List[Double]", fDoubleList, List(1.0, 2.0, 3.0))
  check("Vec[Double]", fDoubleVec, List(1.0, 2.0, 3.0))
}
