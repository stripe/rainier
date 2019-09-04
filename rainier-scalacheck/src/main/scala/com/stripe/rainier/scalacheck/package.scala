package com.stripe.rainier.scalacheck

import com.stripe.rainier.core.{Categorical, Generator, RandomVariable}
import com.stripe.rainier.compute.Real
import com.stripe.rainier.sampler.{RNG, ScalaRNG}
import org.scalacheck.{Arbitrary, Cogen, Gen}

object `package` {
  import Arbitrary.arbitrary

  implicit val cogenRNG: Cogen[RNG] =
    Cogen(_ match {
      case ScalaRNG(seed) => seed
      case other          => sys.error(s"$other RNG currently unsupported")
    })

  // note: currently assuming all evaluators are pure and don't have
  // internal state (other than caching)
  implicit val cogenNumericReal: Cogen[Numeric[Real]] =
    Cogen(_.getClass.hashCode.toLong)

  implicit val genReal: Gen[Real] = arbitrary[Double].map(Real(_))

  def genGenerator[A: Arbitrary]: Gen[Generator[A]] =
    Gen.oneOf(
      arbitrary[A].map(Generator.constant(_)),
      arbitrary[(RNG, Numeric[Real]) => A].map(Generator.from(_)),
      for {
        req <- arbitrary[Set[Real]]
        fn <- arbitrary[(RNG, Numeric[Real]) => A]
      } yield Generator.require(req)(fn)
    )

  def genCategorical[A: Arbitrary]: Gen[Categorical[A]] =
    arbitrary[Map[A, Real]].map(Categorical(_))

  def genRandomVariable[A: Arbitrary]: Gen[RandomVariable[A]] =
    for {
      a <- arbitrary[A]
      density <- arbitrary[Real]
    } yield RandomVariable(a, density)

  implicit def arbitraryReal: Arbitrary[Real] =
    Arbitrary(genReal)

  implicit def arbitraryGenerator[A: Arbitrary]: Arbitrary[Generator[A]] =
    Arbitrary(genGenerator)

  implicit def arbitraryRandomVariable[A: Arbitrary]
    : Arbitrary[RandomVariable[A]] =
    Arbitrary(genRandomVariable[A])

  implicit def arbitraryCategorical[A: Arbitrary]: Arbitrary[Categorical[A]] =
    Arbitrary(genCategorical)
}
