package rainier.compute

import scala.collection.mutable.HashMap

private object Gradient {
  def derive(variables: Seq[Variable], output: Real): Seq[Real] = {
    val diffs = HashMap.empty[Real, CompoundDiff]
    def diff(real: Real): CompoundDiff = {
      diffs.getOrElseUpdate(real, new CompoundDiff)
    }

    diff(output).register(new Diff { val toReal = Real.one })

    var visited = Set[Real]()
    def visit(real: Real): Unit = {
      if (!visited.contains(real)) {
        visited += real
        real match {
          case v: Variable     => ()
          case Constant(value) => ()
          case b: BinaryReal =>
            diff(b.left).register(BinaryDiff(b, diff(b), true))
            diff(b.right).register(BinaryDiff(b, diff(b), false))
            visit(b.left)
            visit(b.right)

          case u: UnaryReal =>
            diff(u.original).register(UnaryDiff(u, diff(u)))
            visit(u.original)

          case f: If =>
            diff(f.test).register(TrinaryDiff(f, diff(f), 0))
            diff(f.whenNonZero).register(TrinaryDiff(f, diff(f), 1))
            diff(f.whenZero).register(TrinaryDiff(f, diff(f), 2))
            visit(f.test)
            visit(f.whenNonZero)
            visit(f.whenZero)
        }
      }
    }

    visit(output)
    variables.map { v =>
      diff(v).toReal
    }
  }

  private sealed trait Diff {
    def toReal: Real
  }

  private class CompoundDiff extends Diff {
    var parts = List.empty[Diff]

    def register(part: Diff): Unit = {
      parts = part :: parts
    }

    def toReal: Real = parts match {
      case head :: Nil => head.toReal
      case _           => Real.sum(parts.map(_.toReal))
    }
  }

  private case class BinaryDiff(child: BinaryReal,
                                gradient: Diff,
                                isLeft: Boolean)
      extends Diff {
    def toReal =
      child.op match {
        case AddOp => gradient.toReal
        case MultiplyOp =>
          if (isLeft)
            gradient.toReal * child.right
          else
            gradient.toReal * child.left
        case SubtractOp =>
          if (isLeft)
            gradient.toReal
          else
            gradient.toReal * -1
        case DivideOp =>
          if (isLeft)
            gradient.toReal * (Real.one / child.right)
          else
            gradient.toReal * -1 * child.left / (child.right * child.right)
      }
  }

  private case class UnaryDiff(child: UnaryReal, gradient: Diff) extends Diff {
    def toReal = child.op match {
      case LogOp => gradient.toReal * (Real.one / child.original)
      case ExpOp => gradient.toReal * child
      case AbsOp =>
        If(child.original, gradient.toReal * child.original / child, Real.zero)
    }
  }

  private case class TrinaryDiff(child: If, gradient: Diff, pos: Int)
      extends Diff {
    def toReal = pos match {
      case 0 => Real.zero
      case 1 => If(child.test, gradient.toReal, Real.zero)
      case 2 => If(child.test, Real.zero, gradient.toReal)
      case _ => sys.error("Invalid trinary position")
    }
  }
}
