package com.stripe.rainier.core

import com.stripe.rainier.sbc._
import org.scalatest.FunSuite

final case class SBCTest[L, T](description: String, samples: List[Double], model: SBCModel[L, T])(implicit ev: L <:< Distribution[T]) extends FunSuite{
  test(description){
    val newSamples = model.priors.param.flatMap(model.fn(_).param).sample(10)
    assert(newSamples == samples)
  }
}
