package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Distribution[T] {
  def logDensity(seq: Seq[T]): Real
  def generator: Generator[T]
}
