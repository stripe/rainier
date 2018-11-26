package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Likelihood[L, T] {
  type P
  def wrapping(l: L): Wrapping[T, P]
  def logDensity(l: L, value: P): Real
}
