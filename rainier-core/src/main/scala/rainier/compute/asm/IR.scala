package rainier.compute.asm

import rainier.compute

import scala.collection.mutable

sealed trait IR

case class Parameter(original: compute.Variable) extends IR
case class Const(value: Double) extends IR

case class BinaryIR(left: IR, right: IR, op: compute.BinaryOp) extends IR
case class UnaryIR(original: IR, op: compute.UnaryOp) extends IR

case class Sym private (id: Int)
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

case class MethodDef(sym: Sym, rhs: IR) extends IR
case class MethodRef(sym: Sym) extends IR
