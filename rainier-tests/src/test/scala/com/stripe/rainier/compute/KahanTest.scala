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

  /**
    * Given an array of the form  Array(big, small, small),
    * the naive sum computes (big + small) + small
    * where the first sum truncates low-order bits and the second sum truncates
    * again. The best we can do is to compute
    * big + (small + small)
    * so that we keep all of the bits we can until the final sum which truncates
    * once.
    * In the below example
    * array.sum = 1.0000000065973433E10
    * whereas
    * big + (sum of smalls) = Kahan.sum(array) = 1.0000000065973446E10
    */
  val big = 1E10
  val small = Math.PI
  val nSmall = 21
  val oneBigManySmallArray = big +: Array.fill(nSmall)(small)
  val oneBigManySmallExpected = big + (nSmall * small)

  check(
    "Kahan.sum computes big + (small + ... + small) for an Array(big, small...., small)")(
    Kahan.sum,
    oneBigManySmallArray,
    oneBigManySmallExpected
  )

  check(
    "Kahan.nSum computes big + (small + ... + small) for an Array(big, small...., small)")(
    Kahan.nSum,
    oneBigManySmallArray,
    oneBigManySmallExpected
  )

}
