package com.stripe.rainier.compute

/**
  * Trait for serializable and deserializable types
  */
trait Show[T, S] {
  def show(t: T): S
//  def load(s: T): T
}

object Show {

  implicit val showRealString = new Show[Real, String] {

    def show(real: Real): String = real match {
      case Constant(c) => s"C($c)"
      case Infinity    => "Inf"
      case NegInfinity => "NegInf"
//      case v @ Variable => v.toString
      case Unary(nc, op) => s"Unary(${show(nc)}, ${op.toString})"
//      case Line(ax, b) =>
//        val axString = ax
//          .map{ case (nc, v) => (show(nc), v) }
//          .toMap
//          .toString
//        s"Line($axString, b)"
//      case LogLine(ax) =>
//        val axString = ax
//          .map{ case (nc, v) => (show(nc), v) }
//          .toMap
//          .toString
//        s"LogLine($axString)"
      case _ => "nope"
      /**
      case If(t, nz, z) => s"If(${show(t)}, ${show(nz)}, ${show(z)})"
      case Pow(base, exponent) => s"Pow(${show(base)}, ${show(exponent)})" **/
    }
//    def load(s: String): Real = ???
  }

}
