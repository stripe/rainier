package rainier.compute

/**
  * We use this typeclass to avoid totally general
  * implicit conversions to real which we might
  * otherwise reach for in RandomVariable
  */
sealed abstract class ToReal[A] {
  def apply(a: A): Real
}

object ToReal {
  def apply[A](a: A)(implicit toReal: ToReal[A]): Real = toReal(a)

  implicit def numeric[N: Numeric]: ToReal[N] =
    new ToReal[N] {
      def apply(n: N): Real = Constant(implicitly[Numeric[N]].toDouble(n))
    }

  implicit val fromReal: ToReal[Real] =
    new ToReal[Real] {
      def apply(r: Real): Real = r
    }
}
