package rainier.compute

private object Optimizer {
  def apply(unary: Unary): Real =
    unary match {
      case Unary(Unary(x, LogOp), ExpOp) => x
      case Unary(Unary(x, ExpOp), LogOp) => x
      case Unary(u @ Unary(_, AbsOp), AbsOp) => u
      case Unary(u @ Unary(_, ExpOp), AbsOp) => u
      case Unary(Pow(x,y),LogOp) => x.log * y
      case _ => unary
    }

  def apply(pow: Pow): Real =
    pow match {
      case Pow(x, Constant(1.0)) => x
      case Pow(_, Constant(0.0)) => Real.one
      case Pow(Constant(x), Constant(y)) => Constant(Math.pow(x,y))
      case Pow(Pow(x,y),z) => x.pow(y*z)
      case Pow(Unary(x, ExpOp),y) => (x*y).exp
      case _ => pow
    }

  def apply(product: Product): Real = product

  def apply(line: Line): Real = line
}
