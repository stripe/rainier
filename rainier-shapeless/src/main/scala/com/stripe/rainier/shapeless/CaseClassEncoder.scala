package com.stripe.rainier.shapeless

import com.stripe.rainier.compute.{Encoder, Variable}

trait LameThinger[A, B] {
  def toDoubleList(v: A): List[Double]
  def fromValue(v: A): B
  def withVariables: (B, List[Variable])
}

class CrappyCaseClassEncoder[In, Out](thinger: LameThinger[In, Out])
    extends Encoder[In] {
  type U = Out
  def wrap(t: In): Out = thinger.fromValue(t)
  def create(acc: List[Variable]): (Out, List[Variable]) = {
    val (encd, vars) = thinger.withVariables
    (encd, vars ++ acc)
  }
  def extract(t: In, acc: List[Double]): List[Double] = {
    thinger.toDoubleList(t) ++ acc
  }
}

// This is the thing we want to implement with Shapeless
class CaseClassEncoder[In, Out] extends Encoder[In] {
  type U = Out
  def wrap(t: In): Out = ???
  def create(acc: List[Variable]): (Out, List[Variable]) = ???
  def extract(t: In, acc: List[Double]): List[Double] = ???
}
