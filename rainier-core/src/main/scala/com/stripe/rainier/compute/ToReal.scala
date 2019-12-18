package com.stripe.rainier.compute

/**
  * We use this typeclass to avoid totally general
  * implicit conversions to real which we might
  * otherwise reach for in RandomVariable
  */
sealed abstract class ToReal[A] { self =>
  def apply(a: A): Real

  def contramap[B](f: B => A): ToReal[B] = new ToReal[B] {
    def apply(b: B): Real = self.apply(f(b))
  }
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
          Constant(Decimal(double))
      }
    }
}

object ToReal extends LowPriToReal {
  def apply[A](a: A)(implicit toReal: ToReal[A]): Real = toReal(a)

  implicit val fromDecimal: ToReal[Decimal] =
    new ToReal[Decimal] {
      def apply(r: Decimal): Real = Constant(r)
    }

  implicit val fromReal: ToReal[Real] =
    new ToReal[Real] {
      def apply(r: Real): Real = r
    }
}
