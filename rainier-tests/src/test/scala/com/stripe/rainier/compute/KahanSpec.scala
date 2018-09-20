package com.stripe.rainier.compute

import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}

class KahanSpec extends PropSpec with Matchers with PropertyChecks {

  /**
    *  Make a list of digits ds into a Double strictly between 0 and 10
    *  with ds.size significant digits e.g. List(1,2,3) becomes 1.23
    */
  def digitsToDouble(digits: List[Int]): Double =
    digits.zipWithIndex
      .map { case (a, b) => (a.toDouble, b.toDouble) }
      .foldLeft(0.0) {
        case (accum, (digit, i)) => accum + (digit * Math.pow(10, -i))
      }

  /**
    * Generator for doubles strictly between 0 and 10
    * with numDigits significant digits.
    */
  def smallGen(numDigits: Int): Gen[Double] =
    Gen
      .listOfN(numDigits, Gen.choose(1, 9))
      .map(digitsToDouble)

  def arrayOfSmallsGen(numSmalls: Int, numDigits: Int): Gen[Array[Double]] =
    Gen.listOfN(numSmalls, smallGen(numDigits)).map(_.toArray)

  /**
    * Adding smalls to bigs in this range will truncate
    * some bits of the smalls but not all of them.
    */
  def oneBigManySmallsGen(numSmalls: Int, numDigits: Int): Gen[Array[Double]] =
    for {
      big <- Gen.choose(1E10, 1E11)
      smalls <- arrayOfSmallsGen(numSmalls, numDigits)
    } yield big +: smalls

  /**
    * Naively adding smalls to bigs in this range will truncate
    * all bits of the smalls so big + small = big.
    */
  def bigSmallsMinusBigGen(numSmalls: Int, numDigits: Int): Gen[Array[Double]] =
    for {
      big <- Gen.choose(1E20, 1E200)
      smalls <- arrayOfSmallsGen(numSmalls, numDigits)
    } yield big +: smalls :+ (-big)

  property("Kahan summation agrees with naive sum for (sum of smalls)") {
    forAll(arrayOfSmallsGen(1000, 5)) { array =>
      val kahanSum = Kahan.sum(array)
      val neumaierSum = Kahan.nSum(array)
      val naiveSum = array.sum
      val kahanDiff = Math.abs(kahanSum - naiveSum)
      val neumaierDiff = Math.abs(neumaierSum - naiveSum)
      kahanDiff < 1E-10 shouldBe true
      neumaierDiff < 1E-10 shouldBe true
    }
  }

  property(
    "Kahan summation computes big + (sum of smalls) for an Array(big, small,...,small)") {
    forAll(oneBigManySmallsGen(10, 15)) { array =>
      val expected = array(0) + (array.drop(1).sum)
      val kahanSum = Kahan.sum(array)
      val neumaierSum = Kahan.nSum(array)
      val naiveSum = array.sum
      val kahanDiff = Math.abs(expected - kahanSum)
      val neumaierDiff = Math.abs(expected - neumaierSum)
      val naiveDiff = Math.abs(expected - naiveSum)
      kahanSum shouldBe expected
      neumaierSum shouldBe expected
      if (naiveDiff > 0) {
        kahanDiff < naiveDiff && neumaierDiff < naiveDiff shouldBe true
      }
    }
  }

  property(
    "Kahan summation computes (sum of smalls) for an Array(big, small, ..., small, -big)") {
    forAll(bigSmallsMinusBigGen(10, 5)) { array =>
      val expected = array.slice(1, array.size - 1).sum
      val kahanSum = Kahan.sum(array)
      val neumaierSum = Kahan.nSum(array)
      val naiveSum = array.sum
      naiveSum shouldBe 0.0
      kahanSum shouldBe 0.0
      neumaierSum shouldBe expected
    }
  }
}
