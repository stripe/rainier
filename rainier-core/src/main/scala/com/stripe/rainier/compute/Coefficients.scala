package com.stripe.rainier.compute

private trait Coefficients {
  def isEmpty: Boolean
  def coefficients: Seq[BigDecimal]
  def terms: Seq[NonConstant]
  def toList: List[(NonConstant, BigDecimal)]
  def single: Option[(NonConstant, BigDecimal)]
  def -(term: NonConstant): Coefficients
  def +(pair: (NonConstant, BigDecimal)): Coefficients
  def mapCoefficients(fn: BigDecimal => BigDecimal): Coefficients
  def merge(other: Coefficients): Coefficients
}

private object Coefficients {
  def apply(pair: (NonConstant, BigDecimal)): Coefficients =
    Single(pair._1, pair._2)
  def apply(seq: Seq[(NonConstant, BigDecimal)]): Coefficients =
    if(seq.isEmpty)
      empty
    else if(seq.size == 1)
      apply(seq.head)
    else
      Many(ListMap(seq: _*))

  val empty: Coefficients = new Coefficients {
    val isEmpty = true
    val coefficients = Nil
    val terms = Nil
    val toList = Nil
    val single = None
    def -(term: NonConstant) = this
    def +(pair: (NonConstant, BigDecimal)) = apply(pair)
    def mapCoefficients(fn: BigDecimal => BigDecimal) = this
    def merge(other: Coefficients) = other
  }

  private case class Single(term: NonConstant, coefficient: BigDecimal)
      extends Coefficients {
    val isEmpty = false
    def coefficients = List(coefficient)
    def terms = List(term)
    def toList = List((term, coefficient))
    def single = Some((term, coefficient))
    def -(term: NonConstant) =
      if (term == this.term)
        empty
      else
        this
    def +(pair: (NonConstant, BigDecimal)) =
      if (pair._1 == term)
        Single(term, coefficient + pair._2)
      else
        apply(pair :: toList)
    def mapCoefficients(fn: BigDecimal => BigDecimal) =
      Single(term, fn(coefficient))
    def merge(other: Coefficients) = other + (term -> coefficient)
  }

  private class Many(map: ListMap[NonConstant, Coefficients])
    extends Coefficients {
      val isEmpty = false
      def coefficients = map.values
      def terms = map.keys
      def toList = map.toList
      val single = None
      def -(term: NonConstant) = ???
      def +(pair: (NonConstant, BigDecimal)) = ???
      def mapCoefficients(fn: BigDecimal => BigDecimal) =
        Many(map.map{case (x,a) => x -> fn(a)})
      def merge(other: Coefficients) = ???
  }
}
