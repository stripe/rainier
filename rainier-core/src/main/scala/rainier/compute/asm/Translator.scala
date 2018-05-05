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
    case i: If               => toIR(i.whenNonZero) //will lead to NaNs for now
    case l: Line             => lineIR(l)
    case l: LogLine          => logLineIR(l)
  }

  private def unaryIR(original: IR, op: UnaryOp): Var = {
    val key = (original.consKey, op)
    unary.get(key) match {
      case Some(sym) => VarRef(sym)
      case None =>
        val sym = Sym.freshSym()
        unary(key) = sym
        new VarDef(sym, new UnaryIR(original, op))
    }
  }

  private def binaryIR(left: IR, right: IR, op: BinaryOp): Var = {
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
      case Some((l: Line, factor)) =>
        factoredLine(l.ax, l.b, factor, multiplyRing)
      case Some((nc, factor)) =>
        binaryIR(toIR(nc), Const(factor), MultiplyOp)
      case None => factoredLine(line.ax, line.b, 1.0, multiplyRing)
    }

  private def logLineIR(line: LogLine): IR =
    LogLineOps.factor(line) match {
      case Some((l, factor)) =>
        factoredLine(l.ax, 0.0, factor, powRing)
      case Some((nc, factor)) =>
        factoredLine(Map(nc -> 1.0), 0.0, factor, powRing)
      case None =>
        factoredLine(line.ax, 0.0, 1.0, powRing)
    }

  private def factoredLine(ax: Map[NonConstant, Double],
                           b: Double,
                           factor: Double,
                           ring: Ring): Var = {
    val posTerms = ax.filter(_._2 > 0.0).toList
    val negTerms =
      ax.filter(_._2 < 0.0).map { case (x, a) => x -> a.abs }.toList

    val allPosTerms =
      if (b == 0.0)
        posTerms
      else
        (Constant(b), 1.0) :: posTerms

    val (ir, sign) =
      (allPosTerms.isEmpty, negTerms.isEmpty) match {
        case (true, true)  => (toVar(Const(0.0)), 1.0)
        case (true, false) => (combineTerms(negTerms, ring), -1.0)
        case (false, true) => (combineTerms(allPosTerms, ring), 1.0)
        case (false, false) =>
          val posSum = combineTerms(allPosTerms, ring)
          val negSum = combineTerms(negTerms, ring)
          (binaryIR(posSum, negSum, ring.minus), 1.0)
      }

    (factor * sign) match {
      case 1.0 => ir
      case -1.0 =>
        binaryIR(Const(ring.zero), ir, ring.minus)
      case 2.0 =>
        binaryIR(ir, VarRef(ir.sym), ring.plus)
      case k =>
        binaryIR(ir, Const(k), ring.times)
    }
  }

  private def combineTerms(terms: Seq[(Real, Double)], ring: Ring): Var = {
    val ir = terms.map {
      case (x, 1.0) => toVar(toIR(x))
      case (x, 2.0) =>
        binaryIR(toIR(x), toIR(x), ring.plus)
      case (l: LogLine, a) => //this can only happen for a Line's terms
        factoredLine(l.ax, a, 1.0, powRing)
      case (x, a) =>
        binaryIR(toIR(x), Const(a), ring.times)
    }
    combineTree(ir, ring)
  }

  private def combineTree(terms: Seq[Var], ring: Ring): Var =
    if (terms.size == 1)
      terms.head
    else
      combineTree(
        terms.grouped(2).toList.map {
          case oneOrTwo =>
            if (oneOrTwo.size == 1)
              oneOrTwo.head
            else {
              val left = oneOrTwo(0)
              val right = oneOrTwo(1)
              binaryIR(left, right, ring.plus)
            }
        },
        ring
      )

  private def toVar(ir: IR) =
    ir match {
      case v: Var => v
      case _      => new VarDef(Sym.freshSym(), ir)
    }

  case class Ring(times: BinaryOp,
                  plus: BinaryOp,
                  minus: BinaryOp,
                  zero: Double)
  val multiplyRing = Ring(MultiplyOp, AddOp, SubtractOp, 0.0)
  val powRing = Ring(PowOp, MultiplyOp, DivideOp, 1.0)
}
