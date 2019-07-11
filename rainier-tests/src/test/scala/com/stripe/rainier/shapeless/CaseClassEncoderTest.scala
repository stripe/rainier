package com.stripe.rainier.shapeless

import org.scalatest._

import com.stripe.rainier.compute.{Real, Variable, Encoder}

class CaseClassEncoderTest extends FunSuite {
  case class SimpleDatum(foo: Double, bar: Double)
  case class SimpleFeature(foo: Real, bar: Real)

  class Nada {}
  case class ComplexDatum(foo: Double, bar: Int, ignored: Double, other: Nada)
  case class ComplexFeature(foo: Real, bar: Real)

  def checkSimple(enc: Encoder.Aux[SimpleDatum, SimpleFeature]) = {
    val simpleDatum = SimpleDatum(1, 2)

    assert(enc.wrap(simpleDatum) == SimpleFeature(Real(1), Real(2)))
    // TODO: Confirm this is the correct output ordering
    assert(enc.extract(simpleDatum, List(0)) == List(1, 2, 0))

    val aVar = new Variable
    val (encd, simpleVars) = enc.create(List(aVar))
    // TODO: Confirm this is the correct output ordering
    assert(simpleVars == List(encd.foo, encd.bar, aVar))
  }

  def checkComplex(enc: Encoder.Aux[ComplexDatum, ComplexFeature]) = {
    val complexDatum = ComplexDatum(1, 2, 3, new Nada)

    assert(enc.wrap(complexDatum) == ComplexFeature(Real(1), Real(2)))
    // TODO: Confirm this is the correct output ordering
    assert(enc.extract(complexDatum, List(0)) == List(1, 2, 0))

    val aVar = new Variable
    val (encd, complexVars) = enc.create(List(aVar))
    // TODO: Confirm this is the correct output ordering
    assert(complexVars == List(encd.foo, encd.bar, aVar))
  }

  test("CrappyCaseClassEncoder") {
    val simpleEnc = new CrappyCaseClassEncoder[SimpleDatum, SimpleFeature](
      new LameThinger[SimpleDatum, SimpleFeature] {
        def toDoubleList(v: SimpleDatum): List[Double] = List(v.foo, v.bar)
        def fromValue(v: SimpleDatum): SimpleFeature =
          SimpleFeature(v.foo, v.bar)
        def withVariables: (SimpleFeature, List[Variable]) = {
          val vars = List(new Variable, new Variable)
          (SimpleFeature(vars(0), vars(1)), vars)
        }
      })

    val complexEnc = new CrappyCaseClassEncoder[ComplexDatum, ComplexFeature](
      new LameThinger[ComplexDatum, ComplexFeature] {
        def toDoubleList(v: ComplexDatum): List[Double] =
          List(v.foo, v.bar.toDouble)
        def fromValue(v: ComplexDatum): ComplexFeature =
          ComplexFeature(v.foo, v.bar)
        def withVariables: (ComplexFeature, List[Variable]) = {
          val vars = List(new Variable, new Variable)
          (ComplexFeature(vars(0), vars(1)), vars)
        }
      })

    checkSimple(simpleEnc)
    checkComplex(complexEnc)
  }

  test("CaseClassEncoder") {
    checkSimple(new CaseClassEncoder[SimpleDatum, SimpleFeature])
    checkComplex(new CaseClassEncoder[ComplexDatum, ComplexFeature])
  }
}
