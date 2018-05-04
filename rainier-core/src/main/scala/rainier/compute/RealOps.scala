package rainier.compute

private object RealOps {

  def unary(original: Real, op: UnaryOp): Real =
    original match {
      case Constant(value) =>
        op match {
          case ExpOp => Constant(Math.exp(value))
          case LogOp => Constant(Math.log(value))
          case AbsOp => Constant(Math.abs(value))
        }
      case nc: NonConstant =>
        (op, nc) match {
          case (ExpOp, Unary(x, LogOp))     => x
          case (AbsOp, u @ Unary(_, AbsOp)) => u
          case (AbsOp, u @ Unary(_, ExpOp)) => u
          case (LogOp, Unary(x, ExpOp))     => x
          case (LogOp, l: LogLine) =>
            LogLineOps.log(l)
          case (LogOp, l: Line) =>
            LineOps.log(l).getOrElse {
              Unary(nc, op)
            }
          case _ => Unary(nc, op)
        }
    }

  def add(left: Real, right: Real): Real =
    (left, right) match {
      case (_, Constant(0.0))             => left
      case (Constant(0.0), _)             => right
      case (Constant(x), Constant(y))     => Constant(x + y)
      case (Constant(x), nc: NonConstant) => LineOps.translate(line(nc), x)
      case (nc: NonConstant, Constant(x)) => LineOps.translate(line(nc), x)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LineOps.sum(line(nc1), line(nc2))
    }

  def multiply(left: Real, right: Real): Real =
    (left, right) match {
      case (_, Constant(0.0))             => Real.zero
      case (Constant(0.0), _)             => Real.zero
      case (_, Constant(1.0))             => left
      case (Constant(1.0), _)             => right
      case (Constant(x), Constant(y))     => Constant(x * y)
      case (Constant(x), nc: NonConstant) => LineOps.scale(line(nc), x)
      case (nc: NonConstant, Constant(x)) => LineOps.scale(line(nc), x)
      case (l1: Line, l2: Line) =>
        LineOps.multiply(l1, l2).getOrElse {
          LogLineOps.multiply(logLine(l1), logLine(l2))
        }
      case (nc1: NonConstant, nc2: NonConstant) =>
        LogLineOps.multiply(logLine(nc1), logLine(nc2))
    }

  def pow(original: Real, exponent: Double): Real =
    (original, exponent) match {
      case (Constant(v), _) => Constant(Math.pow(v, exponent))
      case (_, 0.0)         => Real.one
      case (_, 1.0)         => original
      case (l: Line, _) =>
        LineOps.pow(l, exponent).getOrElse {
          LogLineOps.pow(logLine(l), exponent)
        }
      case (nc: NonConstant, _) =>
        LogLineOps.pow(logLine(nc), exponent)
    }

  private def line(nc: NonConstant): Line =
    nc match {
      case l: Line => l
      case _       => new Line(Map(nc -> 1.0), 0.0)
    }

  private def logLine(nc: NonConstant): LogLine =
    nc match {
      case l: LogLine => l
      case _          => new LogLine(Map(nc -> 1.0))
    }

  def isPositive(real: Real): Real =
    If(real, nonZeroIsPositive(real), Real.zero)

  def isNegative(real: Real): Real =
    If(real, Real.one - nonZeroIsPositive(real), Real.zero)

  private def nonZeroIsPositive(real: Real): Real =
    ((real.abs / real) + 1) / 2

  def variables(real: Real): Set[Variable] = {
    def loop(r: Real, acc: Set[Variable]): Set[Variable] =
      r match {
        case Constant(_) => acc
        case v: Variable => acc + v
        case u: Unary    => loop(u.original, acc)
        case l: Line     => l.ax.foldLeft(acc) { case (a, (r, d)) => loop(r, a) }
        case l: LogLine  => l.ax.foldLeft(acc) { case (a, (r, d)) => loop(r, a) }
        case If(test, nz, z) =>
          val acc2 = loop(test, acc)
          val acc3 = loop(nz, acc2)
          loop(z, acc3)
      }

    loop(real, Set.empty)
  }
}
