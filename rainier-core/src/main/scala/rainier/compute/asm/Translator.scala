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
      val orig = toIR(u.original)
      val key = (orig, u.op)
      unary.get(key) match {
        case Some(sym) => VarRef(sym)
        case None =>
          val sym = Sym.freshSym()
          unary(key) = sym
          new VarDef(sym, new UnaryIR(orig, u.op))
      }
    case p: Product =>
      val left = toIR(p.left)
      val right = toIR(p.right)
      val key = (left, right, b.op)
      binary.get(key) match {
        case Some(sym) => VarRef(sym)
        case None =>
          val sym = Sym.freshSym()
          binary(key) = sym
          new VarDef(sym, new BinaryIR(left, right, MultiplyOp))
      }
    case l: Line => ???
    case i: If => ???
  }
}
