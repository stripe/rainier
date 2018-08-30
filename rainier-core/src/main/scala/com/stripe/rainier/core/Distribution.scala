package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Basic probability distribution trait
  */
trait Distribution[T] extends Likelihood[T] {
  def generator: Generator[T]
}

object Distribution {
  private[core] type Aux[T, U] = Distribution[T] { type P = U }
}

abstract class NumericDistribution[T: Numeric] extends Distribution[T] {
  type P = Real
  def wrap(value: T): Real = Real(value)
}
