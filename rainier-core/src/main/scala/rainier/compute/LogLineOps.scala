package rainier.compute

private object LogLineOps {
  def multiply(left: LogLine, right: LogLine): Real = {
    val merged = LineOps.merge(left.ax, right.ax)
    if (merged.isEmpty)
      Real.one
    else
      LogLine(LineOps.merge(left.ax, right.ax))
  }

  def log(line: LogLine): Option[Real] = None
  /*    if (line.ax.size == 1)
      Unary(line, LogOp)
    else
      Real.sum(line.ax.toList.map {
        case (x, a) =>
          x.pow(a).log
      })*/

  def pow(line: LogLine, v: Double): LogLine =
    LogLine(line.ax.map { case (x, a) => x -> a * v })

  //(y,k) such that y.pow(k) == line
  def factor(line: LogLine): (LogLine, Double) = (line, 1.0)
}
