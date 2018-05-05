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
        Optimizer(Unary(nc, op))
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
      case (nc1: NonConstant, nc2: NonConstant) =>
        Optimizer(new Product(nc1, nc2))
    }

  private def line(nc: NonConstant): Line =
    nc match {
      case l: Line => l
      case _       => new Line(Map(nc -> 1.0), 0.0)
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
        case p: Product  => loop(p.right, loop(p.left, acc))
        case w: Pow      => loop(w.original, loop(w.exponent, acc))
        case u: Unary    => loop(u.original, acc)
        case v: Variable => acc + v
        case l: Line     => l.ax.foldLeft(acc) { case (a, (r, d)) => loop(r, a) }
        case If(test, nz, z) =>
          val acc2 = loop(test, acc)
          val acc3 = loop(nz, acc2)
          loop(z, acc3)
      }

    loop(real, Set.empty)
  }
}
