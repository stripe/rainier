package rainier.compute

import scala.collection.mutable.WeakHashMap

private object Table {
  val binary = WeakHashMap.empty[(Real, Real, BinaryOp), BinaryReal]
  val unary = WeakHashMap.empty[(Real, UnaryOp), UnaryReal]

  def intern(real: Real): Real = real match {
    case Real_+(original) => intern(original)
    case b: BinaryReal =>
      val key = (b.left, b.right, b.op)
      val hit = binary.get(key).orElse {
        b.op match {
          case o: CommutativeOp =>
            binary.get((b.right, b.left, b.op))
          case _ => None
        }
      }
      hit match {
        case Some(r) => r
        case None =>
          binary += key -> b
          b
      }
    case u: UnaryReal =>
      val key = (u.original, u.op)
      unary.get(key) match {
        case Some(r) => r
        case None =>
          unary += key -> u
          u
      }
    case _ => real
  }
}
