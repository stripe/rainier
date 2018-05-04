package rainier.compute

private object LogLineOps {
  def multiply(left: LogLine, right: LogLine): Real = {
    val merged = LineOps.merge(left.ax, right.ax)
    if (merged.isEmpty)
      Real.one
    else
      LogLine(LineOps.merge(left.ax, right.ax))
  }

  def log(line: LogLine): Real =
    Real.sum(line.ax.toList.map {
      case (x, a) =>
        x.log * a
    })

  def pow(line: LogLine, v: Double): LogLine =
    LogLine(line.ax.map { case (x, a) => x -> a * v })

  //if the result is Some((y,k)), then y.pow(k)==line, k != 1
  def factor(line: LogLine): Option[(NonConstant, Double)] =
    LineOps.factor(line.ax, 0.0) {
      case (newAx, _) => LogLine(newAx)
    }
}
