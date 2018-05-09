package rainier.compute

private object Pruner {
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
        case (left, Constant(r), SubtractOp) =>
          BinaryReal(left, Constant(-r), AddOp)
        case (left, Constant(r), DivideOp) =>
          BinaryReal(left, Constant(1.0 / r), MultiplyOp)
        case (Constant(l), right, op: CommutativeOp) =>
          BinaryReal(b.right, b.left, op)
        case (left: BinaryReal, Constant(r), op: CommutativeOp)
            if left.op == b.op =>
          left.right match {
            case Constant(lr) =>
              BinaryReal(left.left, Constant(op(r, lr)), b.op)
            case _ => b
          }
        case (left: BinaryReal, right, op: CommutativeOp)
            if left.op == b.op && left.right
              .isInstanceOf[Constant] => //FIX this hack
          left.right match {
            case Constant(lr) =>
              BinaryReal(BinaryReal(left.left, right, b.op), left.right, b.op)
            case _ => b
          }
        case (left, right: BinaryReal, op: CommutativeOp) if right.op == b.op =>
          right.right match {
            case Constant(rr) =>
              BinaryReal(BinaryReal(left, right.left, b.op), right.right, b.op)
            case _ => b
          }
        case _ => b
      }
    case If(Constant(v), nz, z) =>
      if (v == 0.0)
        z
      else
        nz
    case _ => real
  }
}