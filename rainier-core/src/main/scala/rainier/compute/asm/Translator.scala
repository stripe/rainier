package rainier.compute.asm

import rainier.compute._
import scala.collection.mutable

private class Translator {
  private val binary = mutable.Map.empty[(IR, IR, BinaryOp), Sym]
  private val unary = mutable.Map.empty[(IR, UnaryOp), Sym]

  def toIR(r: Real): IR = r match {
    case v: Variable         => Parameter(v)
    case Constant(value)     => Const(value)
    case Unary(original, op) => unaryIR(toIR(original), op)
    case Pow(original, Constant(-1.0)) =>
      binaryIR(Const(1.0), toIR(original), DivideOp)
    case Pow(original, Constant(2.0)) =>
      binaryIR(toIR(original), toIR(original), MultiplyOp)
    case Pow(original, exponent) =>
      binaryIR(toIR(original), toIR(exponent), PowOp)
    case p: Product =>
      (p.left, p.right) match {
        case (Pow(a, Constant(-1.0)), Pow(b, Constant(-1.0))) =>
          binaryIR(Const(1.0), binaryIR(toIR(a), toIR(b), MultiplyOp), DivideOp)
        case (Pow(a, Constant(-1.0)), right) =>
          binaryIR(toIR(right), toIR(a), DivideOp)
        case (left, Pow(b, Constant(-1.0))) =>
          binaryIR(toIR(left), toIR(b), DivideOp)
        case _ =>
          binaryIR(toIR(p.left), toIR(p.right), MultiplyOp)
      }
    case i: If   => toIR(i.whenNonZero) //will lead to NaNs for now
    case l: Line => lineIR(l)
  }

  private def unaryIR(original: IR, op: UnaryOp): IR = {
    val key = (original.consKey, op)
    unary.get(key) match {
      case Some(sym) => VarRef(sym)
      case None =>
        val sym = Sym.freshSym()
        unary(key) = sym
        new VarDef(sym, new UnaryIR(original, op))
    }
  }

  private def binaryIR(left: IR, right: IR, op: BinaryOp): IR = {
    val key = (left.consKey, right.consKey, op)
    val cached = binary.get(key).orElse {
      if (op.isCommutative) {
        val newKey = (right.consKey, left.consKey, op)
        binary.get(newKey)
      } else
        None
    }
    cached match {
      case Some(sym) =>
        VarRef(sym)
      case None =>
        val sym = Sym.freshSym()
        binary(key) = sym
        new VarDef(sym, new BinaryIR(left, right, op))
    }
  }

  private def lineIR(line: Line): IR =
    LineOps.factor(line) match {
      case Some((l: Line, scale)) => scaledLine(l, scale)
      case Some((nc, scale)) =>
        binaryIR(toIR(nc), Const(scale), MultiplyOp)
      case None => scaledLine(line, 1.0)
    }

  private def scaledLine(l: Line, scale: Double): IR = {
    val posTerms = l.ax.filter(_._2 > 0.0).toList
    val negTerms =
      l.ax.filter(_._2 < 0.0).map { case (r, d) => r -> d.abs }.toList

    val allPosTerms =
      if (l.b == 0.0)
        posTerms
      else
        (Constant(l.b), 1.0) :: posTerms

    val (ir, sign) =
      (allPosTerms.isEmpty, negTerms.isEmpty) match {
        case (true, true)  => (Const(0.0), 1.0)
        case (true, false) => (sumTerms(negTerms), -1.0)
        case (false, true) => (sumTerms(allPosTerms), 1.0)
        case (false, false) =>
          val posSum = sumTerms(allPosTerms)
          val negSum = sumTerms(negTerms)
          (binaryIR(posSum, negSum, SubtractOp), 1.0)
      }

    val newScale = scale * sign
    if (newScale == 1.0)
      ir
    else
      binaryIR(ir, Const(newScale), MultiplyOp)
  }

  private def sumTerms(terms: Seq[(Real, Double)]): IR = {
    val ir = terms.map {
      case (r, 1.0) => toIR(r)
      case (Pow(d, Constant(-1.0)), n) =>
        binaryIR(Const(n), toIR(d), DivideOp)
      case (r, d) =>
        binaryIR(toIR(r), Const(d), MultiplyOp)
    }
    sumTree(ir)
  }

  private def sumTree(terms: Seq[IR]): IR =
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
            binaryIR(left, right, AddOp)
          }
      })
}
