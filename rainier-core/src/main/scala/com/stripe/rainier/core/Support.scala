package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * A trait for objects representing the support of a continuous distribution.
  * Specifies a function to transform a real-valued variable to this range,
  * and its log-jacobian.
  */
private[rainier] sealed trait Support {
  def transform(v: Variable): Real

  def logJacobian(v: Variable): Real

  /**
    * Unions two supports together.
    * We make the important assumption that the resulting support is contiguous.
    * @param that The other support to be unioned. Order is not important.
    * @return The support that is the union of the two arguments. 
    */
  def union(that: Support): Support = (this, that) match {
    case (UnboundedSupport, _) => UnboundedSupport
    case (_, UnboundedSupport) => UnboundedSupport

    case (BoundedBelowSupport(thisMin), BoundedBelowSupport(thatMin)) =>
      BoundedBelowSupport(thisMin.min(thatMin))
    case (BoundedBelowSupport(_), BoundedAboveSupport(_)) => UnboundedSupport
    case (BoundedBelowSupport(thisMin), BoundedSupport(thatMin, _)) =>
      BoundedBelowSupport(thisMin.min(thatMin))

    case (BoundedAboveSupport(_), BoundedBelowSupport(_)) => UnboundedSupport
    case (BoundedAboveSupport(thisMax), BoundedAboveSupport(thatMax)) =>
      BoundedAboveSupport(thisMax.max(thatMax))
    case (BoundedAboveSupport(thisMax), BoundedSupport(_, thatMax)) =>
      BoundedAboveSupport(thisMax.max(thatMax))

    case (BoundedSupport(thisMin, _), BoundedBelowSupport(thatMin)) =>
      BoundedBelowSupport(thisMin.min(thatMin))
    case (BoundedSupport(_, thisMax), BoundedAboveSupport(thatMax)) =>
      BoundedAboveSupport(thisMax.max(thatMax))
    case (BoundedSupport(thisMin, thisMax), BoundedSupport(thatMin, thatMax)) =>
      BoundedSupport(thisMin.min(thatMin), thisMax.max(thatMax))
  }

  def isDefinedAt(real: Real): Real
}

object Support {
  def union(supports: Iterable[Support]): Support = supports.reduce {
    (a: Support, b: Support) =>
      a.union(b)
  }
}

/**
  * A support representing the whole real line.
  */
object UnboundedSupport extends Support {
  def transform(v: Variable): Real = v

  def logJacobian(v: Variable): Real = Real.zero

  def isDefinedAt(real: Real): Real = Real.one
}

/**
  * A support representing a bounded (min, max) interval.
  */
case class BoundedSupport(min: Real, max: Real) extends Support {
  def transform(v: Variable): Real =
    (Real.one / (Real.one + (v * -1).exp)) * (max - min) + min

  def logJacobian(v: Variable): Real =
    transform(v).log + (1 - transform(v)).log + (max - min).log

  def isDefinedAt(real: Real): Real = (real > min) * (real < max)
}

/**
  * A support representing an open-above {r > k} interval.
  * @param min The lower bound of the distribution
  */
case class BoundedBelowSupport(min: Real = Real.zero) extends Support {
  def transform(v: Variable): Real =
    v.exp + min

  def logJacobian(v: Variable): Real = v

  def isDefinedAt(real: Real): Real = (real > min)
}

/**
  * A support representing an open-below {r < k} interval.
  * @param max The upper bound of the distribution
  */
case class BoundedAboveSupport(max: Real = Real.zero) extends Support {
  def transform(v: Variable): Real =
    max - (-1 * v).exp

  def logJacobian(v: Variable): Real = v

  def isDefinedAt(real: Real): Real = (real < max)
}
