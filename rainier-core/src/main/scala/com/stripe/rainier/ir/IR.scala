package com.stripe.rainier.ir

sealed trait IR

sealed trait Ref extends IR
class Parameter extends Ref
case class Const(value: Double) extends Ref
case class VarRef(sym: Sym) extends Ref

case class BinaryIR(left: IR, right: IR, op: BinaryOp) extends IR
case class UnaryIR(original: IR, op: UnaryOp) extends IR
case class IfIR(test: IR, whenNonZero: IR, whenZero: IR) extends IR

case class VarDef(sym: Sym, rhs: IR) extends IR

case class MethodDef(sym: Sym, rhs: IR) extends IR
case class MethodRef(sym: Sym) extends IR

case class Sym private (id: Int)
object Sym {
  private var curIdx = 0
  def freshSym(): Sym = {
    val r = Sym(curIdx)
    curIdx += 1
    r
  }
}
