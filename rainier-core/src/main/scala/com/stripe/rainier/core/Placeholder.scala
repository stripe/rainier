package com.stripe.rainier.core

import com.stripe.rainier.compute.Real

trait Placeholder[T,U] {
  def wrap(value: T): U
  def get(placeholder: U)(implicit n: Numeric[Real]): T
}