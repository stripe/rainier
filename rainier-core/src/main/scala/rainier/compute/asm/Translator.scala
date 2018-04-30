package rainier.compute.asm

import rainier.compute._
import scala.collection.mutable

private class Translator {
  private val binary = mutable.Map.empty[(IR, IR, BinaryOp), Sym]
  private val unary = mutable.Map.empty[(IR, UnaryOp), Sym]

  def toIR(r: Real): IR = r match {
    case Constant(value) => Const(value)
    case v: Variable     => Parameter(v)
    case u: Unary =>
      u.op match {
        case RecipOp => binaryIR(Const(1.0), toIR(u.original), DivideOp)
        case _       => unaryIR(toIR(u.original), u.op)
      }
    case p: Product =>
      (p.left, p.right) match {
        case (u1: Unary, u2: Unary) if u1.op == RecipOp && u2.op == RecipOp =>
          binaryIR(Const(1.0),
                   binaryIR(toIR(u1.original), toIR(u2.original), MultiplyOp),
                   DivideOp)
        case (u: Unary, _) if u.op == RecipOp =>
          binaryIR(toIR(p.right), toIR(u.original), DivideOp)
        case (_, u: Unary) if u.op == RecipOp =>
          binaryIR(toIR(p.left), toIR(u.original), DivideOp)
        case _ =>
          binaryIR(toIR(p.left), toIR(p.right), MultiplyOp)
      }
    case i: If   => toIR(i.whenNonZero) //will lead to NaNs for now
    case l: Line => lineIR(l)
  }

  private def unaryIR(original: IR, op: UnaryOp): IR = {
    val key = (original, op)
    unary.get(key) match {
      case Some(sym) => VarRef(sym)
      case None =>
        val sym = Sym.freshSym()
        unary(key) = sym
        new VarDef(sym, new UnaryIR(original, op))
    }
  }

  private def binaryIR(left: IR, right: IR, op: BinaryOp): IR = {
    val key = (left, right, op)
    val cached = binary.get(key).orElse {
      if (op.isCommutative) {
        val newKey = (right, left, op)
        binary.get(newKey)
      } else
        None
    }
    cached match {
      case Some(sym) => VarRef(sym)
      case None =>
        val sym = Sym.freshSym()
        binary(key) = sym
        new VarDef(sym, new BinaryIR(left, right, op))
    }
  }

  private def lineIR(line: Line): IR = {
    val terms =
      line.ax.foldLeft(List[(IR, Boolean)]((Const(line.b), false))) {
        case (terms, (x, a)) =>
          val (ir, isNeg) = a match {
            case 0.0  => (Const(0.0), false)
            case 1.0  => (toIR(x), false)
            case -1.0 => (toIR(x), true)
            case _ =>
              (x match {
                case u: Unary if u.op == RecipOp =>
                  binaryIR(Const(a), toIR(u.original), DivideOp)
                case _ =>
                  binaryIR(toIR(x), Const(a), MultiplyOp)
              }, false)
          }
          (ir, isNeg) :: terms
      }

    val (ir, isNeg) = sumTree(terms.reverse.filterNot { x =>
      x._1 == Const(0.0)
    })
    if (isNeg)
      binaryIR(ir, Const(-1), MultiplyOp)
    else
      ir
  }

  private def sumTree(terms: Seq[(IR, Boolean)]): (IR, Boolean) =
    if (terms.size == 1)
      terms.head
    else
      sumTree(terms.grouped(2).toList.map {
        case oneOrTwo =>
          if (oneOrTwo.size == 1)
            oneOrTwo.head
          else {
            val left = oneOrTwo(0)
            val right = oneOrTwo(1)
            (left._2, right._2) match {
              case (false, false) =>
                (binaryIR(left._1, right._1, AddOp), false)
              case (true, false) =>
                (binaryIR(left._1, right._1, SubtractOp), false)
              case (false, true) =>
                (binaryIR(right._1, left._1, SubtractOp), false)
              case (true, true) =>
                (binaryIR(left._1, right._1, AddOp), true)
            }
          }
      })
}
