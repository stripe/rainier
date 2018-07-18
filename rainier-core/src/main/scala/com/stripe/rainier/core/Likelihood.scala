package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Likelihood[L, T] {
  def logDensity(pdf: L, value: T): Real
}

abstract class PlaceholderLikelihood[L, T, U](
    implicit val ph: Placeholder[T, U]) {
  def logDensity(pdf: L, placeholder: U): Real
  def wrap(pdf: L, value: T): Real =
    logDensity(pdf, ph.wrap(value))
}
