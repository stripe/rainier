package rainier.compute

import scala.annotation.tailrec

private object LogLineOps {
  def multiply(left: LogLine, right: LogLine): Real = {
    val merged = LineOps.merge(left.ax, right.ax)
    if (merged.isEmpty)
      Real.one
    else
      LogLine(LineOps.merge(left.ax, right.ax))
  }

  def log(line: LogLine): Option[Real] = None

  def pow(line: LogLine, v: Double): LogLine =
    LogLine(line.ax.map { case (x, a) => x -> a * v })

  //(y,k) such that y.pow(k) == line
  def factor(line: LogLine): (LogLine, Double) = {
    val exponents = line.ax.values.toList
    if (exponents.forall { x =>
          x % 1 == 0.0
        }) {
      val d =
        exponents.map(_.toInt).reduce(gcd)
      if (d == 1)
        (line, 1.0)
      else
        (pow(line, 1.0 / d), d.toDouble)
    } else
      (line, 1.0)
  }

  @tailrec
  private def gcd(x: Int, y: Int): Int = {
    if (y == 0)
      x.abs
    else
      gcd(y, x % y)
  }
}
