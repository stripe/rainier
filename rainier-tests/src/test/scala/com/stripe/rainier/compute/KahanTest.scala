package com.stripe.rainier.compute

import org.scalatest._

class KahanTest extends FunSuite {

  def check(description: String)(fn: Array[Double] => Double,
                                 input: Array[Double],
                                 expected: Double): Unit =
    test(description) {
      assert(fn(input) == expected)
    }

  val smallArray = 0.to(10000).toArray.map(_.toDouble)
  val smallExpected = smallArray.sum

  val bigAndSmallArray = Array(1.0, 1.0E100, 1.0, -1.0E100)
  val bigAndSmallExpected = 2.0

  check("Kahan.sum agrees with Array.sum for small inputs")(
    Kahan.sum,
    smallArray,
    smallExpected
  )

  check("Kahan.nSum agrees with Array.sum for small inputs")(
    Kahan.nSum,
    smallArray,
    smallExpected
  )

  check("Kahan.sum returns 0 for array with large canceling terms")(
    Kahan.sum,
    bigAndSmallArray,
    0.0
  )

  check("Kahan.nSum is correct for array with large canceling terms")(
    Kahan.nSum,
    bigAndSmallArray,
    bigAndSmallExpected
  )

}
