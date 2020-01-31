package com.stripe.rainier.compute

import scala.annotation.tailrec

sealed trait Coefficients extends Product with Serializable {
  def isEmpty: Boolean
  def size: Int
  def coefficients: Iterable[Constant]
  def terms: Iterable[NonConstant]
  def toList: List[(NonConstant, Constant)]
  def toMap: Map[NonConstant, Constant]
  def withComplements: Iterable[(NonConstant, Constant, Coefficients)]
  def mapCoefficients(fn: Constant => Constant): Coefficients
  def merge(other: Coefficients): Coefficients
  def +(pair: (NonConstant, Constant)): Coefficients
}

object Coefficients {
  def apply(term: NonConstant): Coefficients = apply(term -> Constant.One)

  def apply(pair: (NonConstant, Constant)): Coefficients =
    if (pair._2.isZero)
      Empty
    else
      One(pair._1, pair._2)

  def apply(seq: Seq[(NonConstant, Constant)]): Coefficients = {
    val filtered = seq.filterNot(_._2.isZero)
    if (filtered.isEmpty)
      Empty
    else if (filtered.size == 1)
      apply(filtered.head)
    else
      Many(filtered.toMap, filtered.map(_._1).toList)
  }

  private final case object EmptyCoefficients extends Coefficients {
    val isEmpty = true
    val size = 0
    val coefficients = Nil
    val terms = Nil
    val toList = Nil
    val toMap = Map.empty[NonConstant, Constant]
    val withComplements = Nil
    def mapCoefficients(fn: Constant => Constant) = this
    def +(pair: (NonConstant, Constant)) = apply(pair)
    def merge(other: Coefficients) = other
  }

  val Empty: Coefficients = EmptyCoefficients

  case class One(term: NonConstant, coefficient: Constant)
      extends Coefficients {
    val size = 1
    val isEmpty = false
    def coefficients = List(coefficient)
    def terms = List(term)
    def toList = List((term, coefficient))
    def toMap = Map(term -> coefficient)
    def withComplements = List((term, coefficient, Empty))
    def mapCoefficients(fn: Constant => Constant) =
      One(term, fn(coefficient))
    def merge(other: Coefficients) = other + (term -> coefficient)
    def +(pair: (NonConstant, Constant)) =
      if (pair._1 == term) {
        val newCoefficient = coefficient + pair._2
        if (newCoefficient.isZero)
          Empty
        else
          One(term, newCoefficient)
      } else {
        Coefficients(pair :: toList)
      }
  }

  case class Many(toMap: Map[NonConstant, Constant], terms: List[NonConstant])
      extends Coefficients {
    val isEmpty = false
    def size = toMap.size
    def coefficients = toMap.values
    def toList = terms.map { x =>
      x -> toMap(x)
    }

    def mapCoefficients(fn: Constant => Constant) =
      Many(toMap.map { case (x, a) => x -> fn(a) }, terms)

    def withComplements = {
      @tailrec
      def loop(
          acc: List[(NonConstant, Constant, Coefficients)],
          a: List[NonConstant],
          b: List[NonConstant]): List[(NonConstant, Constant, Coefficients)] =
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

    def +(pair: (NonConstant, Constant)) = {
      val (term, coefficient) = pair
      if (toMap.contains(term)) {
        val newCoefficient = coefficient + toMap(term)
        if (newCoefficient.isZero) {
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
  }
}
