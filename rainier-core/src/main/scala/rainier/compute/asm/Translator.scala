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

  private def lineIR(line: Line): IR = {
    val (simplified, factor) = line.simplify

    val posTerms = simplified.ax.filter(_._2 > 0.0).toList
    val negTerms =
      simplified.ax.filter(_._2 < 0.0).map { case (r, d) => r -> d.abs }.toList

    val allPosTerms =
      if (simplified.b == 0.0)
        posTerms
      else
        (Constant(simplified.b), 1.0) :: posTerms

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

    val newFactor = factor * sign
    if (newFactor == 1.0)
      ir
    else
      binaryIR(ir, Const(newFactor), MultiplyOp)
  }

  private def sumTerms(terms: Seq[(Real, Double)]): IR = {
    val ir = terms.map {
      case (r, 1.0) => toIR(r)
      case (u: Unary, n) if u.op == RecipOp =>
        binaryIR(Const(n), toIR(u.original), DivideOp)
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
