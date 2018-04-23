package rainier.compute.ir

import rainier.compute
import rainier.compute._

import scala.collection.mutable

sealed trait IR

case class Variable(original: compute.Variable) extends IR
case class Const(value: Double) extends IR

case class BinaryIR(left: IR, right: IR, op: BinaryOp) extends IR
case class UnaryIR(original: IR, op: UnaryOp) extends IR

case class Sym private(id: Int)
object Sym {
  private var curIdx = 0
  def freshSym(): Sym = {
    val r = Sym(curIdx)
    curIdx += 1
    r
  }
}
case class VarDef(sym: Sym, rhs: IR) extends IR
case class VarRef(sym: Sym) extends IR

object IR {
  private val alreadySeen: mutable.Map[Real, Sym] = mutable.Map.empty
  private val symDef: mutable.Map[Sym, VarDef] = mutable.Map.empty
  def toIR(r: Real): IR = {
    if (alreadySeen.contains(r))
      VarRef(alreadySeen(r))
    else r match {
      case compute.Constant(value) => Const(value)
        // variable access is treated like an atomic operation and is not stored in a VarDef
      case v: compute.Variable => Variable(v)
      case b: BinaryReal =>
        val bIR = BinaryIR(toIR(b.left), toIR(b.right), b.op)
        createVarDef(b, bIR)
      case u: UnaryReal =>
        val uIR = UnaryIR(toIR(u.original), u.op)
        createVarDef(u, uIR)
    }
  }
  def createVarDef(original: Real, rhs: IR): VarDef = {
    val s = Sym.freshSym()
    val vd = VarDef(s, rhs)
    alreadySeen(original) = s
    symDef(s) = vd
    vd
  }
}