package com.stripe.rainier.compute

import scala.annotation.tailrec

sealed trait Coefficients {
  def isEmpty: Boolean
  def size: Int
  def coefficients: Iterable[BigDecimal]
  def terms: Iterable[NonConstant]
  def toList: List[(NonConstant, BigDecimal)]
  def toMap: Map[NonConstant, BigDecimal]
  def withComplements: Iterable[(NonConstant, BigDecimal, Coefficients)]
  def mapCoefficients(fn: BigDecimal => BigDecimal): Coefficients
  def merge(other: Coefficients): Coefficients
  def +(pair: (NonConstant, BigDecimal)): Coefficients
}

object Coefficients {
  def apply(pair: (NonConstant, BigDecimal)): Coefficients =
    One(pair._1, pair._2)
  def apply(seq: Seq[(NonConstant, BigDecimal)]): Coefficients = {
    val filtered = seq.filter(_._2 != Real.BigZero)
    if (filtered.isEmpty)
      Empty
    else if (filtered.size == 1)
      apply(filtered.head)
    else
      Many(filtered.toMap, filtered.map(_._1).toList)
  }

  val Empty: Coefficients = new Coefficients {
    val isEmpty = true
    val size = 0
    val coefficients = Nil
    val terms = Nil
    val toList = Nil
    val toMap = Map.empty[NonConstant, BigDecimal]
    val withComplements = Nil
    def mapCoefficients(fn: BigDecimal => BigDecimal) = this
    def +(pair: (NonConstant, BigDecimal)) = apply(pair)
    def merge(other: Coefficients) = other
  }

  case class One(term: NonConstant, coefficient: BigDecimal)
      extends Coefficients {
    val size = 1
    val isEmpty = false
    def coefficients = List(coefficient)
    def terms = List(term)
    def toList = List((term, coefficient))
    def toMap = Map(term -> coefficient)
    def withComplements = List((term, coefficient, Empty))
    def mapCoefficients(fn: BigDecimal => BigDecimal) =
      One(term, fn(coefficient))
    def merge(other: Coefficients) = other + (term -> coefficient)
    def +(pair: (NonConstant, BigDecimal)) =
      if (pair._1 == term) {
        val newCoefficient = coefficient + pair._2
        if (newCoefficient == Real.BigZero)
          Empty
        else
          One(term, newCoefficient)
      } else {
        Coefficients(pair :: toList)
      }
  }

  class Many(val toMap: Map[NonConstant, BigDecimal],
             val terms: List[NonConstant])
      extends Coefficients {
    val isEmpty = false
    def size = toMap.size
    def coefficients = toMap.values
    def toList = terms.map { x =>
      x -> toMap(x)
    }

    def mapCoefficients(fn: BigDecimal => BigDecimal) =
      Many(toMap.map { case (x, a) => x -> fn(a) }, terms)

    def withComplements = {
      @tailrec
      def loop(
          acc: List[(NonConstant, BigDecimal, Coefficients)],
          a: List[NonConstant],
          b: List[NonConstant]): List[(NonConstant, BigDecimal, Coefficients)] =
        b match {
          case head :: tail =>
            val complementTerms =
              if (a.size > tail.size)
                tail ::: a
              else
                a ::: tail
            val complement =
              if (complementTerms.size == 1)
                One(complementTerms.head, toMap(complementTerms.head))
              else
                Many(toMap - head, complementTerms)
            loop((head, toMap(head), complement) :: acc, head :: a, tail)
          case Nil =>
            acc
        }
      loop(Nil, Nil, terms)
    }

    def merge(other: Coefficients) =
      if (other.size > size)
        other.merge(this)
      else
        other.toList.foldLeft(this: Coefficients) {
          case (acc, pair) => acc + pair
        }

    def +(pair: (NonConstant, BigDecimal)) = {
      val (term, coefficient) = pair
      if (toMap.contains(term)) {
        val newCoefficient = coefficient + toMap(term)
        if (newCoefficient == Real.BigZero) {
          val newMap = toMap - term
          val newTerms = terms.filterNot(_ == term)
          if (newTerms.size == 1)
            One(newTerms.head, newMap.values.head)
          else
            Many(newMap, newTerms)
        } else {
          Many(toMap + (term -> newCoefficient), terms)
        }
      } else {
        Many(toMap + pair, term :: terms)
      }
    }

    override lazy val hashCode = toMap.hashCode
    override def equals(other: Any) =
      other match {
        case m: Many => eq(m) || toMap == m.toMap
        case _       => false
      }
  }

  object Many {
    def apply(toMap: Map[NonConstant, BigDecimal], terms: List[NonConstant]) =
      new Many(toMap, terms)
  }
}
