package com.stripe.rainier.scalacheck

import com.stripe.rainier.core.Generator
import com.stripe.rainier.core.RandomVariable
import com.stripe.rainier.compute.Real
import com.stripe.rainier.sampler.RNG
import com.stripe.rainier.sampler.ScalaRNG

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

object `package` {
  implicit val arbitraryReal: Arbitrary[Real] =
    Arbitrary(arbitrary[Double].map(Real(_)))

  implicit def arbitraryGenerator[A: Arbitrary]: Arbitrary[Generator[A]] =
    Arbitrary(genGenerator)

  def genGenerator[A: Arbitrary]: Gen[Generator[A]] = {
    implicit val cogenRNG: Cogen[RNG] =
      Cogen(_ match {
        case ScalaRNG(seed) => seed
        case other          => sys.error(s"$other RNG currently unsupported")
      })

    // note: currently assuming all evaluators are pure and don't have
    // internal state (other than caching)
    implicit val cogenNumericReal: Cogen[Numeric[Real]] =
      Cogen(_.getClass.hashCode.toLong)

    Gen.oneOf(
      arbitrary[A].map(Generator.constant(_)),
      arbitrary[(RNG, Numeric[Real]) => A].map(Generator.from(_)),
      for {
        req <- arbitrary[Set[Real]]
        fn <- arbitrary[(RNG, Numeric[Real]) => A]
      } yield Generator.require(req)(fn)
    )
  }

  implicit def arbitraryRandomVariable[A: Arbitrary]
    : Arbitrary[RandomVariable[A]] =
    Arbitrary(genRandomVariable[A])

  def genRandomVariable[A: Arbitrary]: Gen[RandomVariable[A]] =
    arbitrary[A].flatMap(a =>
      arbitrary[Real].flatMap(density => RandomVariable(a, density)))

}
