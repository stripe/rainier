package rainier.compute

private object Optimizer {
  def apply(unary: Unary): Real =
    (unary.op, unary.original) match {
      case (ExpOp, Unary(x, LogOp)) => x
      case (AbsOp, u @ Unary(_, AbsOp)) => u
      case (AbsOp, u @ Unary(_, ExpOp)) => u
      case (LogOp, Unary(x, ExpOp)) => x
      case (LogOp, Pow(x,y)) => x.log * y
      case (LogOp, l: Line) =>
        LineOptimizer.log(l).getOrElse(unary)
      case _ => unary
    }

  def apply(pow: Pow): Real =
    (pow.original, pow.exponent) match {
      case (x, Constant(1.0)) => x
      case (_, Constant(0.0)) => Real.one
      case (Constant(1.0), _) => Real.one
      case (Constant(x), Constant(y)) => Constant(Math.pow(x,y))
      case (Pow(x,y),z) => x.pow(y*z)
      case (Unary(y, ExpOp),z) => (y*z).exp
      case (l: Line, y) =>
        LineOptimizer.pow(l, y).getOrElse(pow)
      case _ => pow
    }

  def apply(product: Product): Real =
    (product.left, product.right) match {
      case (Pow(x,Constant(-1.0)), Pow(y,Constant(-1.0))) =>
        (x*y).pow(-1)
      case (left: Line, right: Line) =>
        LineOptimizer.multiply(left,right).getOrElse(product)
      case _ => product
    }

  def apply(line: Line): Real = 
    LineOptimizer(line)
}
