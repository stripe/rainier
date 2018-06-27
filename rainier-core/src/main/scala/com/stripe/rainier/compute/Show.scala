package com.stripe.rainier.compute

/**2
  * Trait for serializable and deserializable types
  */
trait Show[-T, S] {
  def show(t: T): S
}

object Show {

  implicit val showRealString = new Show[Real, String] {

    def mapKeys[K, L, V](map: Map[K, V], f: K => L): Map[L, V] = {
      map.map { case (k, v) => (f(k), v) }.toMap
    }

    def show(real: Real): String = real match {
      case Constant(c) => s"C($c)"
      case Infinity    => "I"
      case NegInfinity => "NI"
      case v: Variable =>
        val hash = v.toString.split("@")(1)
        s"V($hash)"
      case Unary(nc, op) => s"Unary(${show(nc)}, ${op.toString})"
      case l: Line =>
        val hash = l.toString.split("@")(1)
        val axString = mapKeys(l.ax, show).toString
        s"Line($hash)($axString, b)"
      case LogLine(ax) =>
        val axString = mapKeys(ax, show).toString
        s"LogLine($axString)"
      case If(t, nz, z)        => s"If(${show(t)}, ${show(nz)}, ${show(z)})"
      case Pow(base, exponent) => s"Pow(${show(base)}, ${show(exponent)})"
    }
  }

}
