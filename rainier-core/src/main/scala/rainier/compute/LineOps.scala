package rainier.compute

private object LineOps {

  def sum(left: Line, right: Line): Line =
    new Line(merge(left.ax, right.ax), left.b + right.b)

  def scale(line: Line, v: Double): Line =
    new Line(line.ax.map { case (x, a) => x -> a * v }, line.b * v)

  def translate(line: Line, v: Double): Line =
    new Line(line.ax, line.b + v)

  def multiply(left: Line, right: Line): Option[Real] =
    if (left.ax.size == 1 && right.ax.size == 1)
      multiply1D(left.ax.head._2,
                 left.ax.head._1,
                 left.b,
                 right.ax.head._2,
                 right.ax.head._1,
                 right.b)
    else
      None

  def log(line: Line): Option[Real] =
    factor(line)
      .filter(_._2 >= 0)
      .map {
        case (y, k) =>
          y.log + Math.log(k)
      }

  def pow(line: Line, exponent: Real): Option[Real] =
    exponent match {
      case Constant(p) =>
        factor(line).map {
          case (y, k) =>
            y.pow(p) * Math.pow(k, p)
        }
      case _ => None
    }

  //if the result is Some((y,k)), then y*k==line, k != 1
  def factor(line: Line): Option[(NonConstant, Double)] =
    if (line.ax.size == 1 && line.b == 0) {
      val a = line.ax.head._2
      val x = line.ax.head._1
      if (a == 1.0)
        None
      else
        Some((x, a))
    } else {
      val mostSimplifyingFactor =
        line.ax.values
          .groupBy(_.abs)
          .map { case (d, l) => d -> l.size }
          .toList
          .sortBy(_._2)
          .last
          ._1

      if (mostSimplifyingFactor == 1.0)
        None
      else {
        val newAx = line.ax.map {
          case (r, d) => r -> d / mostSimplifyingFactor
        }

        val newB = line.b / mostSimplifyingFactor

        Some((new Line(newAx, newB), mostSimplifyingFactor))
      }
    }

  private def multiply1D(a: Double,
                         x: NonConstant,
                         b: Double,
                         c: Double,
                         y: NonConstant,
                         d: Double): Option[Real] = None

  private def merge(
      left: Map[NonConstant, Double],
      right: Map[NonConstant, Double]): Map[NonConstant, Double] = {
    val (big, small) =
      if (left.size > right.size)
        (left, right)
      else
        (right, left)

    small.foldLeft(big) {
      case (acc, (k, v)) =>
        val newV = big
          .get(k)
          .map { bigV =>
            bigV + v
          }
          .getOrElse(v)

        if (newV == 0.0)
          acc - k
        else
          acc + (k -> newV)
    }
  }
}
