package com.stripe.rainier.prophet

case class PiecewiseLinear(changePoints: Seq[(Int,Real)]) {
  //we can change this to `Real` once we have a way to lookup params with a tag (which might always be int?)
  def apply(t: Int): Real
}

object PiecewiseLinear {
  def changePoints(seq: Seq[Int]): RandomVariable[PiecewiseLinear] = ???
}