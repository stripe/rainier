package rainier.compute

object Pruner {
  def prune(real: Real): Real = real match {
    case u: UnaryReal =>
      u.original match {
        case Constant(v) => Constant(u.op(v))
        case _           => u
      }
    case b: BinaryReal =>
      (b.left, b.right, b.op) match {
        case (Constant(left), Constant(right), op) =>
          Constant(op(left, right))
        case (Constant(0.0), right, AddOp) =>
          right
        case (Constant(1.0), right, MultiplyOp) =>
          right
        case (Constant(0.0), right, MultiplyOp) =>
          Constant(0.0)
        case (left, Constant(0.0), AddOp) =>
          left
        case (left, Constant(1.0), MultiplyOp) =>
          left
        case (left, Constant(0.0), MultiplyOp) =>
          Constant(0.0)
        case (left, Constant(1.0), DivideOp) =>
          left
        case (left, Constant(0.0), SubtractOp) =>
          left
        case _ => b
      }
    case _ => real
  }
}
