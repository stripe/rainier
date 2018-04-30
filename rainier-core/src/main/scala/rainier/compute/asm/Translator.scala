package rainier.compute.asm

import rainier.compute._
import scala.collection.mutable

private class Translator {
  private val binary = mutable.Map.empty[(IR, IR, BinaryOp), Sym]
  private val unary = mutable.Map.empty[(IR, UnaryOp), Sym]

  def toIR(r: Real): IR = r match {
    case Constant(value) => Const(value)
    case v: Variable     => Parameter(v)
    case u: Unary        => unaryIR(toIR(u.original), u.op)
    case p: Product      => binaryIR(toIR(p.left), toIR(p.right), MultiplyOp)
    case i: If           => toIR(i.whenNonZero) //will lead to NaNs for now
    case l: Line         => lineIR(l)
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
    val (posTerms, negTerms) =
      line.ax.foldLeft((List[IR](Const(line.b)), List.empty[IR])) {
        case ((pos, neg), (x, a)) =>
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
          if (isNeg)
            (pos, ir :: neg)
          else
            (ir :: pos, neg)
      }
    val nonZeroPosTerms = posTerms.filter { ir =>
      ir == Const(0.0)
    }

    (nonZeroPosTerms.isEmpty, negTerms.isEmpty) match {
      case (true, true)  => Const(0.0)
      case (true, false) => sumTree(negTerms)
      case (false, true) => sumTree(nonZeroPosTerms)
      case (false, false) =>
        binaryIR(sumTree(nonZeroPosTerms), sumTree(negTerms), SubtractOp)
    }
  }

  private def sumTree(terms: Seq[IR]): IR =
    if (terms.size == 1)
      terms.head
    else
      sumTree(terms.grouped(2).toList.map {
        case oneOrTwo =>
          if (oneOrTwo.size == 1)
            oneOrTwo.head
          else
            binaryIR(oneOrTwo(0), oneOrTwo(1), AddOp)
      })
}
