package com.stripe.rainier.compute

sealed abstract class ToReal[A] { self =>
  def apply(a: A): Real
}

trait LowPriToReal {
  implicit def numeric[N: Numeric]: ToReal[N] =
    new ToReal[N] {
      def apply(n: N): Real = {
        val double = implicitly[Numeric[N]].toDouble(n)
        if (double.isNegInfinity)
          Real.negInfinity
        else if (double.isInfinity)
          Real.infinity
        else if (double.isNaN)
          throw new ArithmeticException("Trying to convert NaN to Real")
        else
          Scalar(double)
      }
    }
}

object ToReal extends LowPriToReal {
  def apply[A](a: A)(implicit toReal: ToReal[A]): Real = toReal(a)

  implicit val fromInt: ToReal[Int] =
    new ToReal[Int] {
      def apply(r: Int): Real = Scalar(r.toDouble)
    }

  implicit val fromLong: ToReal[Long] =
    new ToReal[Long] {
      def apply(r: Long): Real = Scalar(r.toDouble)
    }

  implicit val fromReal: ToReal[Real] =
    new ToReal[Real] {
      def apply(r: Real): Real = r
    }
}
