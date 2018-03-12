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
    case s: SumReal =>
      val (reduced, remaining) = s.seq.foldLeft((0.0, List.empty[Real])) {
        case ((acc, list), Constant(c)) =>
          (acc + c, list)
        case ((acc, list), real) =>
          (acc, real :: list)
      }
      val constant = Constant(reduced)

      if (remaining.isEmpty)
        constant
      else
        prune(new BinaryReal(constant, new SumReal(remaining), AddOp))
    case _ => real
  }
}
